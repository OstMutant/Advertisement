# feedback-spring-boot-starter

Split out as its own bounded context rather than folded into `provider-profile-spring-boot-starter`
or `advertisement-spring-boot-starter`, mirroring why `contact-spring-boot-starter` is its own
module: the concern (rating+text feedback) is shared across those two domains, and either one
owning it would mean the other reaching across the sibling-starter boundary (`.claude/rules.md`'s
"no direct imports between sibling modules") to resolve a fallback.

## What it provides

- Create/edit for `feedback`, enforced at most one entry per `(author_id, entity_type,
  entity_id)` via the `idx_feedback_author_entity` unique index; edits are rejected once the
  entry is older than the 48h edit window (`FeedbackService.checkWithinEditWindow`); text is
  sanitized via `html-sanitizer-lib`'s `HtmlSanitizer`. A stale `version` on save is translated
  from Spring Data JDBC's `OptimisticLockingFailureException` into `StaleWriteException` in
  `FeedbackRepository.save`.
- `feedback_aggregate` — `avg_rating`/`review_count` recomputed and upserted in the same
  transaction as every feedback write (`FeedbackRepository.upsertAggregate`); no column is added
  to `provider_profile`/`advertisement` for this.
- **SPI implementation:** `FeedbackPort` (`platform-commons`) — Phase 1 is backend-only; no
  caller exists yet outside this module's own tests. Intended for consumption by
  `marketplace-orchestrator`/`marketplace-app` once UI wiring lands in a later phase.

## Data flow

A caller (via `FeedbackPort`) passes an `(EntityType, Long entityId)` pair plus the feedback
payload. `FeedbackPortImpl` delegates straight through to `FeedbackService`, which resolves
persistence through `FeedbackRepository`:
- `save` looks up any existing entry for `(authorId, entityType, entityId)` first (create vs.
  edit-within-window), sanitizes the text, then writes `feedback` and recomputes
  `feedback_aggregate` in one `@Transactional` method.
- `findForEntity`/`count` read paginated entries for one owning entity, ordered by `created_at
  DESC`.
- `getAggregate` reads the denormalized `feedback_aggregate` row, or a zero-value default when no
  feedback exists yet for that entity.

## Dependencies

- `platform-commons` — `FeedbackPort`/`FeedbackDto`/`FeedbackSaveDto`/`FeedbackAggregateDto`/
  `FeedbackModerationStatus` (`feedback.*`) is this module's own compile-time contract;
  `EntityType` (`core.model`) is the generic entity key; `StaleWriteException` (`core`) is the
  optimistic-lock failure type this module's repository translates into.
- `html-sanitizer-lib` — `HtmlSanitizer.sanitize` for `feedback_text`.
- `query-lib` — `PaginationSqlBuilder.pageLimit` for the paginated entity-feedback read.
- No Maven dependency on any sibling starter (enforced by this module's own `maven-enforcer-plugin`
  rule) — resolution stays entity-type/entity-id generic, never a direct call into
  `provider-profile-spring-boot-starter`/`advertisement-spring-boot-starter`.
