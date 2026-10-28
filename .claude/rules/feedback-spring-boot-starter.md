---
paths: ["feedback-spring-boot-starter/**"]
---

## feedback-spring-boot-starter

Auto-configures the Feedback domain. Active whenever the jar is on the classpath.

Java package root: `org.ost.feedback`

---

## What it owns

- `Feedback` (the link row), `FeedbackContent` (shared text+moderation+edit-tracking atom reused by
  both a feedback entry and its single reply), `FeedbackRating` (1:1 per feedback),
  `FeedbackComment` (the single reply), `FeedbackAggregate` (denormalized `avg_rating`/
  `review_count` per owning entity) entities
- `FeedbackRepository` — bespoke `JdbcClient` queries across all 5 tables (view joins, moderation
  status transitions, aggregate upsert, hard-delete cascades); trivial CRUD delegates to
  `FeedbackCrudRepository`/`FeedbackContentCrudRepository`/`FeedbackCommentCrudRepository`
- `FeedbackService` — CRUD for feedback entries and their single reply, 48h edit-window
  enforcement (`checkWithinEditWindow`), daily submission rate limiting via `FailureRateLimiter`
  (10 feedback/30 comments per day, `recordAttempt` — a neutrally-named alias for
  `recordFailure`, since this call site counts successful submissions against a quota, not
  failures), moderation transitions (flag/approve/confirm-violation/reject)
- `FeedbackPortImpl` — implements `FeedbackPort`; thin delegation to `FeedbackService`

**Autoconfiguration entry point:** `FeedbackAutoConfiguration`

---

## Schema

Liquibase changelog: `db/feedback-changelog/feedback-changelog-master.xml`
Tables: `feedback_content` (shared text+`moderation_status`+`moderation_reason`+edit-tracking atom,
pointed at by both `feedback.content_id` and `feedback_comment.content_id`), `feedback` (at most one
row per `(author_id, entity_type, entity_id)`, enforced by the unique `idx_feedback_author_entity`
index; immutable after creation — edits touch `feedback_content`/`feedback_rating` instead),
`feedback_rating` (1:1 with `feedback`), `feedback_comment` (the single reply, at most one per
`feedback_id`, enforced by a `UNIQUE` constraint on that column), `feedback_aggregate` (one
denormalized row per owning entity, recomputed and upserted in the same transaction as every
feedback write/flag/approve)

---

## Key constraints

- `FeedbackPort`/`FeedbackDto`/`FeedbackSaveDto`/`FeedbackCommentDto`/`FeedbackAggregateDto`/
  `FeedbackModerationStatus` live in `platform-commons`'s own `feedback.spi`/`feedback.dto`/
  `feedback.model` packages.
- No Maven dependency on any sibling starter (enforced by this module's own
  `maven-enforcer-plugin` `bannedDependencies` rule) — resolution stays entity-type/entity-id
  generic, never a direct call into `provider-profile-spring-boot-starter`/
  `advertisement-spring-boot-starter`.
- `FeedbackModerationStatus` has three values: `NEW` (visible by default), `HIDDEN` (pending
  moderator review after a report), `CONFIRMED_HIDDEN` (a moderator's permanent confirmation of a
  genuine violation — permanently excluded from the moderation queue, but still rendered as the
  same censored placeholder as `HIDDEN`, per `isHidden()`).
- `FeedbackRepository.upsertAggregate` only counts `feedback_content.moderation_status = 'NEW'`
  rows — a hidden or confirmed-violation entry never inflates the public `avg_rating`/
  `review_count`; the aggregate is recomputed in the same transaction as every feedback write,
  flag, and approve.
- No FK cascade between `feedback`/`feedback_rating`/`feedback_comment`/`feedback_content` — hard
  deletes (`deleteCommentHard`, `deleteFeedbackHard`) issue each `DELETE` explicitly, in FK-safe
  order.
- Who may write the single reply (only the reviewed provider-profile's owner) is **not** enforced
  in this starter — that authorization check lives in `marketplace-orchestrator`'s
  `FeedbackCommentWriteService`, since this starter must not depend on `ProviderProfilePort`. This
  starter only enforces the mechanical "at most one reply" invariant (the DB `UNIQUE` constraint)
  and the shared 48h edit window.
- `FeedbackPortImpl` is pure delegation — no business logic inside the port.
- Split out as its own bounded context rather than folded into
  `provider-profile-spring-boot-starter`/`advertisement-spring-boot-starter`, the same reasoning
  `contact-spring-boot-starter` is its own module for: the concern is shared across those two
  domains, and either one owning it would mean the other reaching across the sibling-starter
  boundary to resolve a fallback.
