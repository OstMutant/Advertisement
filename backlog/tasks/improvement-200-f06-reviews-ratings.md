# improvement-200: F-06 — reviews & ratings

**Type:** feature — new product capability (private/roadmap.md Phase 3, F-06)
**Module:** new `feedback-spring-boot-starter` (`feedback` + `feedback_comment` +
`feedback_aggregate` tables, generic `EntityRef`-based attachment, `FeedbackPort` in
platform-commons — fully self-contained, no Hook, mirrors the existing
`contact-spring-boot-starter` precedent), `marketplace-app` (feedback list, threaded comment UI,
star rating UI, feedback form overlay, moderation admin UI)
**Priority:** 🟡 Top — active roadmap item, next unblocked item in `private/roadmap.md`'s Phase 2→3
order now that `improvement-199` (F-05) shipped 2026-09-29; user-requested Top placement, 2026-09-22
**When:** unblocked — depends only on F-04 (provider profile) and the advertisement domain, both
already shipped (`improvement-124`)

## Current state

Phase 1 done (2026-09-30): `feedback-spring-boot-starter` exists — `feedback` +
`feedback_aggregate` tables, `FeedbackPort`, and the UI (rating + text list, submission form) is
wired into `ProviderProfileCatalogViewModeHandler`'s public catalog overlay. Phases 2-5 (see
below) are not started — no `feedback_comment`/comment-tree table or UI, no moderation, no
advertisement-side wiring, no rate limiting yet.

## Why change

Per `private/roadmap.md`, reviews are the #1 trust signal and this project's stated "moat" vs
the Facebook group it's migrating users from — persistent reputation is the single strongest
reason to use the platform over the group, and the substance behind future PRO/badge
monetization (F-09).

## Expected benefit

A measurable trust signal (reviews/week, % entities with ≥10 reviews, contact-reveal uplift on
reviewed profiles) and a real competitive differentiator the FB group structurally cannot offer
(comment-thread reputation evaporates; a review — and its discussion tree — stays).

## Approach

Full spec: `private/features/F-06-reviews-ratings.md`. Summary:
- New `feedback-spring-boot-starter`, mirroring the project's existing modular-starter pattern
  (own Liquibase changelog, `FeedbackPort` in platform-commons): `feedback` table (`author_id`,
  `entity_type`, `entity_id` — generic `EntityRef` target, the same `entity_type`+`entity_id`
  convention `contact-spring-boot-starter`'s `contact_info`/`contact_view` tables already use),
  `rating`, sanitized text, `created_at`, moderation status; `feedback_comment` table
  (`feedback_id` real FK, `parent_comment_id` self-FK, `author_id`, sanitized text, `created_at`,
  moderation status) — unbounded discussion tree, read via Postgres `WITH RECURSIVE`.
- `feedback_aggregate` table (own schema, one row per `(entity_type, entity_id)`: `avg_rating`,
  `review_count`) — lives entirely inside this starter, exactly like `contact_info` does today.
  **No column is added to `provider_profile` or `advertisement`, no Hook, no cross-starter
  write** — a deliberate simplification over an earlier draft of this plan that would have
  denormalized onto those two starters' own tables.
- Rules: one feedback entry per author per `(entity_type, entity_id)` (unique constraint),
  registered users only, edit window (~48h). Any registered user may reply to a feedback entry or
  to another reply — no per-entry response cap.
- Validation: feedback/comment text length validated server-side via named `@Size(max=...)`
  constants on the save DTOs (raw-input cap + visible-text cap via `html-sanitizer-lib`),
  following `AdvertisementSaveDto`'s existing pattern; client-side field `maxLength` mirrors it.
- Moderation: visible by default (NEW), flag/report → hidden pending admin decision, applies at
  both feedback and comment level.
- Orphan cleanup: `feedback.entity_type`/`entity_id` has no real FK (targets whichever table
  matches the `EntityType`), so deleting a provider profile or advertisement can orphan its
  feedback/comments/aggregate row — handled by the existing cleanup-service scheduled-job pattern.
- Deleted-author display: reuse `UiLabelHook.markDeleted(String)` (already used by the audit UI)
  for an author who has deleted their account.
- UI: paginated feedback list + expandable/collapsible comment tree (Reddit/HN-style) on the
  entity's own detail view only (provider profile and advertisement overlay), placed directly
  under the existing main-info block — right after `contactRevealPanelFactory.build(...)`, before
  `metaPanelFactory.build(...)`, in both `AdvertisementViewOverlayModeHandler.buildPrimaryContent()`
  and `ProviderProfileCatalogViewModeHandler.buildPrimaryContent()`. No rating stars on
  cards/search results. Feedback form overlay, inline reply composer.
- Audit: only moderation actions (flag→hidden, admin approve/reject/delete) go through the
  existing audit starter — plain feedback/comment creation is not audited (append-only content,
  already self-describing via its own `author_id`/`created_at`).
- Anti-fraud minimum: registered-only, one-per-entity, rate limit entries/comments per day per
  author, admin delete with audit trail.
- Test plan: extend the existing Playwright suite (leave feedback, reply to tree, edit-window
  enforcement, flag/moderate, duplicate-entry rejection) — see spec's own Test plan section.

## Phases

1. ✅ **Done 2026-09-30** — Core feedback, provider profile only (`feedback` +
   `feedback_aggregate` tables, `FeedbackPort`, `FeedbackAccessService` in
   `marketplace-orchestrator`, `FeedbackPanel` UI wired into
   `ProviderProfileCatalogViewModeHandler.buildPrimaryContent()`). Covered by 15 new
   integration-tests (`FeedbackRepositoryTest`/`FeedbackServiceTest`) and a Playwright
   test.step in `04-provider-profile-flow.spec.js` (anonymous-visitor empty state + logged-in
   submission, list/header refresh).
   **Pending refinements (agreed 2026-09-30, not yet implemented):**
   - `FeedbackPanel.buildForm()`'s rating field is currently a `RadioButtonGroup<Integer>` with
     star-text item labels — works functionally (click a star label = assign that rating) but
     renders as a visible radio-button list, not a clean star row. Replace with a new
     `StarRatingField` component (`marketplace-app/ui/views/components/fields/`, alongside
     `UiTextArea`) — 5 clickable `VaadinIcon.STAR`/`STAR_O` icons, click sets the value and fills
     stars 1..N, no visible radio circles.
   - `FeedbackPanel.buildHeader()`'s two `Span`s (section label + `"{count} reviews, avg
     {rating}"`) have no CSS at all — render jammed together with no spacing (e.g. "Reviews1
     reviews, avg 5.0"). Same root cause as the card-separation finding in `improvement-203` item
     2 (no dedicated stylesheet for `.feedback-*`/`.contact-reveal-*` classes). Fix: new
     `marketplace-app/src/main/frontend/themes/my-app/feedback-panel.css` with a proper flex/gap
     layout for `.feedback-header`/`.feedback-list`/`.feedback-entry`; restyle the header as
     filled stars (rounded avg) + exact numeric average + count in parentheses (e.g. "★★★★★ 4.8
     (12 reviews)"), matching the `"★".repeat(n)` pattern `buildEntryRow` already uses; adjust the
     `feedback.aggregate.count` i18n message shape to fit (count text separate from the
     stars/number, not one combined sentence).
2. ⬜ Extend to advertisements (`entity_type=ADVERTISEMENT`, UI in
   `AdvertisementViewOverlayModeHandler`).
3. ⬜ Comment tree (`feedback_comment`, `WITH RECURSIVE`, expand/collapse UI, inline reply, any
   registered user, unbounded depth).
4. ⬜ Moderation (flag/report on feedback + comment, admin UI, audit for moderation actions only).
5. ⬜ Anti-fraud + hardening (rate limiting, orphan cleanup).

## Implementation Plan — Phase 1 (2026-09-29, ✅ done 2026-09-30 — backend + UI wiring both landed)

Scope: provider-profile-only feedback (rating + text), no comment tree (Phase 3), no UI wiring
into `marketplace-app` yet (separate follow-up once this backend slice builds and tests green).
Template throughout: `contact-spring-boot-starter` (own self-contained `entity_type`+`entity_id`
starter, no FK to the owning domain, `*Port` resolved generically) — same shape as this module.

**New module `feedback-spring-boot-starter`:**
- `pom.xml` — copy `contact-spring-boot-starter/pom.xml` structure: parent `advertisement-parent`;
  deps `platform-commons`, `query-lib`, `html-sanitizer-lib`, `spring-boot-starter`,
  `spring-boot-starter-data-jdbc`, `spring-boot-liquibase`, `spring-boot-starter-validation`,
  `postgresql` (runtime), `lombok`.
- `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  — `org.ost.feedback.config.FeedbackAutoConfiguration`
- `src/main/resources/db/feedback-changelog/feedback-changelog-master.xml` — includes
  `changes/01-feedback-schema.xml`
- `src/main/resources/db/feedback-changelog/changes/01-feedback-schema.xml` — Liquibase, every
  column with a `remarks` attribute (project convention):
  - `feedback` table: `id BIGSERIAL PK`, `author_id BIGINT NOT NULL` (no FK, references
    `user_information(id)`), `entity_type VARCHAR(30) NOT NULL`, `entity_id BIGINT NOT NULL` (no
    FK — generic `EntityRef`, same convention as `contact_info`), `rating SMALLINT NOT NULL`,
    `feedback_text VARCHAR(20000)` (sanitized HTML via `html-sanitizer-lib`), `moderation_status
    VARCHAR(20) NOT NULL DEFAULT 'NEW'`, `created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT
    NOW()`, `updated_at TIMESTAMP WITH TIME ZONE`, `version BIGINT NOT NULL DEFAULT 0`. Unique
    index `(author_id, entity_type, entity_id)`; plain index `(entity_type, entity_id)`.
  - `feedback_aggregate` table: `id BIGSERIAL PK`, `entity_type VARCHAR(30) NOT NULL`, `entity_id
    BIGINT NOT NULL` (no FK), `avg_rating NUMERIC(2,1) NOT NULL DEFAULT 0`, `review_count INTEGER
    NOT NULL DEFAULT 0`, `updated_at TIMESTAMP WITH TIME ZONE`. Unique index
    `(entity_type, entity_id)`.
- `org.ost.feedback.entity.Feedback` / `FeedbackAggregate` — `@Value @Builder @FieldNameConstants
  @Table(...)`, `@Id`/`@CreatedDate`/`@LastModifiedDate`/`@Version`, mirrors `ContactInfo` entity
  shape exactly.
- `org.ost.feedback.repository.FeedbackCrudRepository extends CrudRepository<Feedback, Long>`;
  `FeedbackAggregateCrudRepository extends CrudRepository<FeedbackAggregate, Long>`.
- `org.ost.feedback.repository.FeedbackRepository` — `@Repository` + `JdbcClient`, mirrors
  `ContactRepository`: `findByAuthorAndEntity(authorId, entityType, entityId)`,
  `findByEntity(entityType, entityId, pageable)` (paginated list for the future UI step),
  `save(Feedback)` delegates to `FeedbackCrudRepository` (lets `DuplicateKeyException` propagate
  naturally on the unique index — same convention `UserService.register()` already uses, no
  custom exception type), `upsertAggregate(entityType, entityId)` — recomputes
  `AVG(rating)`/`COUNT(*)` from `feedback` and upserts the one `feedback_aggregate` row in the
  same transaction as the feedback write (`INSERT ... ON CONFLICT (entity_type, entity_id) DO
  UPDATE`).
- `org.ost.feedback.services.FeedbackService` — `@Service @Transactional`: `save(FeedbackSaveDto)`
  sanitizes `feedbackText` via `HtmlSanitizer.sanitize(text, FeedbackSaveDto.TEXT_MAX_LENGTH)`,
  enforces the 48h edit window (reject update once `createdAt` is older than 48h), calls
  `repository.save(...)` then `repository.upsertAggregate(...)` in the same `@Transactional`
  method; `findForEntity(entityType, entityId, pageable)`; `getAggregate(entityType, entityId)`.
- `org.ost.feedback.spi.FeedbackPortImpl` — pure delegation to `FeedbackService`, mirrors
  `ContactPortImpl` exactly (one line per method, `@Transactional`/`@Transactional(readOnly =
  true)` split the same way).
- `org.ost.feedback.config.FeedbackAutoConfiguration` — mirrors `ContactAutoConfiguration`:
  `@AutoConfiguration(afterName = ".../LiquibaseAutoConfiguration")`, `@ConditionalOnClass
  (DataSource.class)`, `@ComponentScan({"org.ost.feedback.spi", "org.ost.feedback.services",
  "org.ost.feedback.repository"})`, `@EnableJdbcRepositories(basePackages =
  "org.ost.feedback.repository")`, `feedbackLiquibase` bean, `ComponentFactory<FeedbackPort>` bean.

**`platform-commons` additions:**
- `org.ost.platform.feedback.spi.FeedbackPort` — purpose Javadoc (no `Port:`/`Hook:` prefix per
  convention), methods: `find(EntityType, Long, Pageable)`→`Page<FeedbackDto>`,
  `save(FeedbackSaveDto)`→`FeedbackDto`, `getAggregate(EntityType, Long)`→`FeedbackAggregateDto`.
- `org.ost.platform.feedback.dto.FeedbackDto`, `FeedbackSaveDto` (`@NonNull entityType/entityId`,
  `@NotNull @Min(1) @Max(5) rating`, `@NotBlank @Size(max = TEXT_MAX_LENGTH) feedbackText`,
  named `TEXT_MAX_LENGTH`/`TEXT_RAW_MAX_LENGTH` constants — `AdvertisementSaveDto`'s
  `TITLE_MAX_LENGTH` pattern), `FeedbackAggregateDto` (`entityType`, `entityId`, `avgRating`,
  `reviewCount`). All `@FieldNameConstants`.

**Root `/app/pom.xml`:** add `<module>feedback-spring-boot-starter</module>` (after
`contact-spring-boot-starter`, same position as every other starter in the existing list).

**Verification (per `/autopilot` step 4):** `bash scripts/ci.sh --sonar` first, then
`scripts/sync-docs`, then `DECISIONS.md` (see below), then full `bash scripts/ci.sh`. New unit
tests for `FeedbackService` (sanitization, edit-window rejection, duplicate rejection) and an
`integration-tests` repository test for `FeedbackRepository` (mirrors the existing
`AbstractPostgresIntegrationTest` pattern used for `ContactRepository`).

**ADR:** `/record-decision platform-commons — reuse EntityRef for a new domain + self-contained
per-starter aggregate table over a cross-starter Hook`, referencing this task and the
`contact-spring-boot-starter` precedent it follows.

**Explicitly out of scope for this run:** `feedback_comment` table/tree (Phase 3), moderation
(Phase 4), rate limiting (Phase 5). UI wiring, originally deferred to a separate follow-up, was
completed the same day (2026-09-30) — see the Phases section above.

## Related

- `private/features/F-06-reviews-ratings.md` — full feature spec (goal, user story, scope, tech
  notes, test plan, phases, KPI, risks).
- `private/roadmap.md` — Phase 3 sequencing (F-06 + F-07 after F-05/F-11a).
- `improvement-199` — F-05/F-11a, sequenced immediately before this per roadmap order.
- improvement-124 (completed) — F-04 provider profile, one of the two dependencies this feature
  builds on (advertisement domain is the other, both already shipped).
