# improvement-207: whole-repo code review follow-ups (`/review all`, 2026-10-06)

**Type:** improvement — mix of real bugs, a defense-in-depth authorization gap, and DRY/YAGNI/
precedent cleanup, surfaced by a full-repo `/review all` pass.
**Module:** cross-cutting — `marketplace-app`, `marketplace-orchestrator`, `audit-spring-boot-starter`,
`feedback-spring-boot-starter`, `taxon-spring-boot-starter`, `attachment-spring-boot-starter`,
`apikey-spring-boot-starter`, `platform-commons`, `advertisement-spring-boot-starter`,
`marketplace-rest-api`, `integration-tests`.
**Priority:** 🟡 Top — user-requested, ranked first in the Top tier, 2026-10-13.
**When:** independent, no blockers.

## Current state

A full-repo `deep-review-orchestrator` pass (`/review all`, run 2026-10-06 against
`feature/marketplace-features` at `2f04bab5`, one module/chunk at a time, verification on Haiku
per `improvement-206`'s levers) raised 89 raw candidates across `dry-kiss-yagni-reviewer`/
`solid-reviewer`/`precedent-reviewer`, rejected 1, merged 4 as duplicate root cause, confirmed 85,
reported 35 as high-confidence and 48 as needs-human-review. The user pasted the "Most important"
narrative (5 items) and the high-confidence table (rows 1-30 — **rows 31-35 and the entire
"needs human review" section were not pasted and are not yet in this task** — ask the user for
the rest before considering this task's candidate list complete).

**Independently spot-verified against real code before filing this task** (not trusted from the
pasted review alone):
- Row 1 / "Most important" #1 — **CONFIRMED.** `SettingsFormModeHandler.updateButtons()`
  (`marketplace-app/.../ui/views/main/header/settings/SettingsFormModeHandler.java:323-326`) only
  toggles `saveButton`/`discardButton` enabled state — no `BeforeUnloadUtil.sync(...)` call, unlike
  `AccountNameFormModeHandler.java:214` and `ProviderProfileFormOverlayModeHandler.java:302`, both
  confirmed calling it.
- "Most important" #2 — **CONFIRMED.** `FeedbackAccessService.approveFeedback/approveComment/
  rejectFeedback/rejectComment` (`marketplace-orchestrator/.../services/FeedbackAccessService.java:215-247`)
  take `actorId` only for the audit-capture call, never pass it through an `AuthorizationService`
  check — confirmed no `AuthorizationService`/`requireIsPrivileged` call anywhere in the file, while
  `TaxonCatalogService.java:48,53,58,63` (the cited precedent) does call
  `authorizationService.requireIsPrivileged(actorId)` for its own mutating actions.
- Row 5 — **CONFIRMED.** `MarketplaceUiConfiguration` (`marketplace-app/.../config/MarketplaceUiConfiguration.java:24,29-33`)
  both `@ComponentScan`s the package containing `ValidationService` (itself `@Service`-annotated)
  and declares a second, explicit `@Bean` for the same type — only `@ConditionalOnMissingBean`
  (plus declaration order) prevents a real bean conflict.

The remaining ~27 table rows and 3 "most important" items (AttachmentGallery transactionality,
stale Liquibase remarks, audit-repository drift from ADR-015) were independently verified in
Phase 0 below — see that section for the real results, not a forward-looking plan.

### Raw candidate list (as pasted, unvalidated except the 3 items above)

**Most important (narrative):**
1. Settings tab has no unsaved-changes warning — see row 1 above (CONFIRMED).
2. Authorization gap (defence in depth) — feedback moderation — see above (CONFIRMED).
3. Non-transactional multi-step writes from the UI — `AttachmentGallery` sequences delete /
   commit-temp / snapshot / restore as separate calls on the thin forwarder
   `AttachmentMediaService` (no `@Transactional`, no composite operation) — a mid-sequence failure
   leaves the entity partly mutated. `AttachmentGallery.java:163,183`.
4. Stale Liquibase `remarks` describing behaviour the code no longer has: audit `snapshot_data`
   shape, `user_preferences` "created lazily", `user_information.version` "checked manually",
   `attachment_snapshot.version` "optimistic lock", taxon "seed entries".
5. Audit repository drift from its own ADR-015 — deserialization + platform DTOs in the
   repository, `@Component` RowMappers, duplicated window-function CTE, ADR text itself stale on
   `findTimeline`.

**High-confidence table (rows 1-30; 31-35 missing):**

| # | Module | Category | Finding | Locations |
|---|---|---|---|---|
| 1 | marketplace-app | dry / bug | `updateButtons/afterSave` copied into 5 form handlers; Settings copy never calls `BeforeUnloadUtil.sync` — **CONFIRMED** | `SettingsFormModeHandler.java:285`; `AccountNameFormModeHandler.java:211`; `ProviderProfileFormOverlayModeHandler.java:299`; `AdvertisementFormOverlayModeHandler:385`; `AbstractTaxonFormOverlayModeHandler:185` |
| 2 | audit | yagni | `DefaultAuditPort.resolveActor` + `CurrentActorHook` fallback unreachable (every `capture*` takes `@NonNull actorId`), yet hook still required/wired | `DefaultAuditPort.java:33,37`; `AuditAutoConfiguration.java:67` |
| 3 | audit | precedent | `audit_log` remarks describe a `{before,after}`/`{snapshot}` shape ADR-014 removed; ERD shows wrong meaning | `01-audit-schema.xml:13,26` |
| 4 | user | precedent | `user_preferences` remark says rows "created lazily"; code/ADR create them eagerly at registration | `01-user-schema.xml:63`; `UserService.java:129` |
| 5 | marketplace-app | kiss | `MarketplaceUiConfiguration` re-scans a package already covered + duplicate `@Bean ValidationService` next to its `@Service` — **CONFIRMED** | `MarketplaceUiConfiguration.java:26,29`; `ValidationService.java:11` |
| 6 | feedback | dry | `findAllCommentsByFeedbackIncludingHidden` byte-for-byte copy of `findCommentsByFeedback`; Javadoc wrongly says the other is moderation-filtered | `FeedbackRepository.java:212,235` |
| 7 | feedback + app | dry | 48h edit window hard-coded 3x (`FeedbackService`, `FeedbackPanel`, `CommentTreePanel`) | `FeedbackService.java:46`; `FeedbackPanel.java:68`; `CommentTreePanel.java:65` |
| 8 | feedback | kiss | `flagFeedback/flagComment` load the same row twice | `FeedbackService.java:205,217` |
| 9 | taxon | dry | `DefaultTaxonPort.getAllByType` re-implements `listAllByType(type, locale, false)` with an extra `findByIds` round-trip | `DefaultTaxonPort.java:80,111` |
| 10 | taxon | yagni | `TaxonFilter.name` + `like(tt.name)` + `LEFT JOIN taxon_translation` never used | `TaxonFilter.java:7`; `TaxonRepository.java:54,87` |
| 11 | rest-api | dry | `AdvertisementApiController.enrich()` re-implements `AdvertisementDisplayEnrichmentService.enrichSingle()` | `AdvertisementApiController.java:138`; `AdvertisementDisplayEnrichmentService.java:113` |
| 12 | orchestrator | dry | `AdvertisementSaveService.buildCurrentSnapshot` hand-splits CATEGORY/CITY instead of `CategoryAndCitySplit.of()` | `AdvertisementSaveService.java:143`; `CategoryAndCitySplit.java:14` |
| 13 | orchestrator | dry | `ProviderProfileSaveService`: 3 copies of find + `enrichWithCategoryAndCity`; `fetchEnriched == findById().orElse(null)` | `ProviderProfileSaveService.java:99,104,131` |
| 14 | audit | dry | `ROW_NUMBER/LAG` CTE duplicated in `findRows`/`findTimeline`; `getSnapshotContent` computes version a third way | `AuditLogRepository.java:98,127,168` |
| 15 | attachment | yagni | `captureAndGetId` runs extra `findLatestId` SELECT; its only production caller discards the result | `AttachmentSnapshotService.java:35,40` |
| 16 | platform-commons | dry | 4 snapshot DTOs each carry a private `typeToString(enum)`; `AuditableSnapshot` lacks an enum `diffField` overload | `AdvertisementSnapshotDto.java:84`; `ProviderProfileSnapshotDto.java:94`; `FeedbackSnapshotDto.java:50`; `FeedbackCommentSnapshotDto.java:50`; `AuditableSnapshot.java:38` |
| 17 | advertisement | dry | Same `advertisement` SELECT column list copied in 3 methods | `AdvertisementRepository.java:74,84,106` |
| 18 | marketplace-app | dry | `buildChipRow` hand-rebuilds `ChipRowUtil.labelled` markup in 3 view handlers | `AdvertisementViewOverlayModeHandler.java:138`; `ProviderProfileCatalogViewModeHandler.java:122`; `ProviderProfileViewModeHandler.java:162` |
| 19 | marketplace-app | dry | `SettingsEditDto` built field-by-field 3x + same 5-setter copy lambda 2x | `SettingsFormModeHandler.java:86,174,183,209,218` |
| 20 | marketplace-app | dry | Identical 7-line `IntegerField` setup copied 4x next to the `bindPageSizeField` helper | `SettingsFormModeHandler.java:235,243,251,259` |
| 21 | marketplace-app | yagni | `QueryStatusBar.Labels` never varies — 4 `*QueryConfig` pass the same 6 keys | `ProviderProfileQueryConfig.java:47`; `AdvertisementQueryConfig.java:46`; `UserQueryConfig.java:45`; `TimelineQueryConfig.java:42` |
| 22 | marketplace-app | kiss | `AttachmentGallery.discardTempUploads` duplicates its fall-through inside the `pendingSnapshotRestore` branch | `AttachmentGallery.java:198,209` |
| 23 | marketplace-app | yagni | Unused `createBreadcrumbButton(I18nKey, Runnable)` overload | `EntityOverlaySupport.java:33` |
| 24 | marketplace-app | yagni | Unused no-arg `buildBreadcrumbLinks()` | `AbstractEntityOverlay.java:67` |
| 25 | integration-tests | kiss | `*ServiceTestSupport.newService` are pure constructor pass-throughs; Javadoc claims "shared mock-wiring" | `AdvertisementServiceTestSupport.java:15`; `ProviderProfileServiceTestSupport.java:15` |
| 26 | attachment | precedent | `AttachmentSnapshotService` public methods lack `@NonNull` | `AttachmentSnapshotService.java:35,40,58,64,68,77` |
| 27 | apikey | precedent | `ApiKeyRepository` is the only bespoke `@Repository` without `@SuppressWarnings("java:S1192")` | `ApiKeyRepository.java:20` |
| 28 | marketplace-app | precedent | Hand-written `LoggerFactory` loggers instead of `@Slf4j` | `AuthContextService.java:17`; `VaadinLocaleProvider.java:21` |
| 29 | several | precedent | Ticket numbers in comments; one cites a path that no longer exists | `AbstractPostgresIntegrationTest.java:21,33`; `AccountOverlay.java:30`; `AccountNameFormModeHandler.java:54`; `AccountNameViewModeHandler.java:33`; `ProviderProfileViewModeHandler.java:46`; `application-dev.yml:4`; `application-prod.yml:24` |
| 30 | several | precedent | Multi-line comments ("one line or none"), rationale often already in an ADR | `SqlCondition.java:35,74`; `TaxonService.java:75`; `FeedbackService.java:283`; `FeedbackPortImpl.java:21`; `AdvertisementApiController.java:64`; `ProviderProfileApiController.java:64`; `UserApiController.java:53`; `ApiExceptionHandler.java:49` |

**Rows 31-35 and the "needs human review" section — dropped, not pursued (2026-10-13).** The
original review output that would have contained them is gone; the user decided not to chase it
down. This task's candidate list is permanently scoped to rows 1-30 plus the 5 "most important"
narrative items — the 26 confirmed findings below are the full scope, not a partial one pending
more data.

## Phase 0 — validation results (done 2026-10-13)

Every row/claim below was independently re-verified against real, current code via parallel
`model: "haiku"` dispatches (6 batches by module + 1 targeted follow-up) — not trusted from the
original review's own text. **26 CONFIRMED, 6 REJECTED, 1 inconclusive** (deferred, not acted on).

**CONFIRMED — real, worth fixing:**
- Row 1 / MI-1 — Settings tab missing `BeforeUnloadUtil.sync()` (bug).
- MI-2 — feedback-moderation `approve*`/`reject*` missing `AuthorizationService` check (defense-in-depth gap).
- MI-3 — `AttachmentGallery.commitTempUploads()` sequences delete/commit/snapshot/restore as separate
  calls on a thin, non-`@Transactional` forwarder (`AttachmentMediaService`) — confirmed no composite
  operation exists, a mid-sequence failure leaves state partly mutated.
- Row 4 — `user_preferences` remark "created lazily" is stale — `UserService.register()` creates it eagerly.
- Row 5 — `MarketplaceUiConfiguration` double-registers `ValidationService` (scan + explicit `@Bean`).
- Row 7 — 48h edit-window `Duration.ofHours(48)` hardcoded in 3 places (`FeedbackService`,
  `FeedbackPanel`, `FeedbackReplyPanel` — not `CommentTreePanel`, which no longer exists post-redesign).
- Row 8 — `flagFeedback`/`flagComment` each load the same row twice (DTO lookup + View lookup).
- Row 9 — `DefaultTaxonPort.getAllByType` re-fetches via an extra `findByIds` round-trip after
  `listByType()` already has the data.
- Row 10 — `TaxonFilter.name` + its join/condition are dead; no real caller ever sets a name value.
- Row 11 — `AdvertisementApiController.enrich()` duplicates `AdvertisementDisplayEnrichmentService.enrichSingle()`.
- Row 12 — `AdvertisementSaveService.buildCurrentSnapshot` hand-splits category/city instead of `CategoryAndCitySplit.of()`.
- Row 13 — `ProviderProfileSaveService` has 3 copies of find+enrich, plus a roundabout `fetchEnriched`.
- Row 14 — the `ROW_NUMBER/LAG` window-function CTE is duplicated verbatim in `findRows`/`findTimeline` (also ADR-015 sub-point).
- Row 15 — `AttachmentSnapshotService.captureAndGetId`'s extra `findLatestId` query — its only real caller discards the result.
- Row 16 — 4 snapshot DTOs each hand-roll their own `typeToString(enum)`; no shared helper.
- Row 17 — `AdvertisementRepository`'s SELECT column list copy-pasted across 3 methods.
- Row 19 — `SettingsEditDto` built field-by-field in `activate()`/`discardChanges()`/`loadRestored()`, plus a 6-setter copy lambda repeated twice.
- Row 20 — the `IntegerField` setup block is now copied **6** times (grew from 4 since the original review, after `feedbackPageSizeField`/`moderationPageSizeField` were added) — right next to an unused `bindPageSizeField` helper.
- Row 21 — `QueryStatusBar.Labels` never varies; all 4 `*QueryConfig` classes pass the identical 6 keys.
- Row 22 — `AttachmentGallery.discardTempUploads` duplicates its own clear-logic inside and outside the `pendingSnapshotRestore` branch.
- Row 23 — `createBreadcrumbButton(I18nKey, Runnable)` overload — confirmed zero callers.
- Row 24 — `buildBreadcrumbLinks()` no-arg — confirmed zero external callers.
- Row 25 — `*ServiceTestSupport.newService` are pure pass-throughs; Javadoc's "shared mock-wiring" claim is misleading.
- Row 26 — `AttachmentSnapshotService` public methods missing `@NonNull` (sibling `AttachmentService` has them).
- Row 27 — `ApiKeyRepository` missing `@SuppressWarnings("java:S1192")` (confirmed the only bespoke `@Repository` without it).
- Row 28 — `AuthContextService`/`VaadinLocaleProvider` use hand-written `LoggerFactory` instead of `@Slf4j`.
- Row 29 — ticket-number citations confirmed in all 7 locations (`AccountOverlay.java:30`,
  `AccountNameFormModeHandler.java:54`, `AccountNameViewModeHandler.java:33`,
  `ProviderProfileViewModeHandler.java:46`, `application-dev.yml:4`, `application-prod.yml:24`,
  plus `AbstractPostgresIntegrationTest.java:21,33` — the last one's path in the original review
  was slightly wrong, real file is under `integration-tests/src/main/java/`).
- Row 30 — **3 of 8** cited locations are still genuinely multi-line: `SqlCondition.java:35`,
  `TaxonService.java:75`, `ApiExceptionHandler.java:49`. The other 5 (`FeedbackService.java`,
  `FeedbackPortImpl.java`, the 3 REST controllers) are already single-line — most likely already
  fixed by this session's own earlier `/sync-docs` Javadoc sweep (`improvement-203`), run after
  this review but before this validation pass.
- MI-5 (ADR-015 drift), 2 of 4 sub-points — RowMappers (`ProjectionMapper`/`SnapshotContentMapper`
  in `AuditLogRepository.java`) are `@Component` beans, not `private static final` constants per
  this repo's own stated Repository pattern; and the CTE duplication (= Row 14).
- MI-4's `attachment_snapshot.version` sub-claim — remark says "optimistic-lock counter" but the
  real code only auto-increments sequentially on insert, no actual optimistic-lock validation exists.

**REJECTED — not real, or already stale by the time of this re-check:**
- Row 2 — `DefaultAuditPort.resolveActor`/`CurrentActorHook` is not truly unreachable: the method
  accepts a nullable `Long`, so the hook fallback would fire if ever called with `null` — dead *in
  practice* given every caller's `@NonNull` contract, but not dead code a compiler/analyzer could
  prove unreachable. No action — this is intentional defensive plumbing, not a bug.
- Row 3 — `audit_log` remarks still accurately describe the current `{before,after}`/`{snapshot}`
  shape; ADR-014 removed a *different* thing (`changes_summary` column), not this shape.
- Row 6 — the cited methods (`findAllCommentsByFeedbackIncludingHidden`/`findCommentsByFeedback`)
  no longer exist at all — stale finding from before the single-reply redesign; current
  `findCommentByFeedback`/`findCommentViewById` are not duplicates (different keys).
- Row 18 — `ChipRowUtil.labelled` takes a different shape (`List<String>` of already-filtered
  names) than what the 3 view handlers actually need (filtering `TaxonDto` by type + conditional
  deleted-chip styling `ChipRowUtil` doesn't support) — not real duplication.
- MI-4's `user_information.version` sub-claim — the remark's "checked manually in
  `UserRepository.updateProfile()`" claim is accurate; the code does genuinely bypass
  `@Version`/`CrudRepository.save()` with a hand-rolled update. The remark is correct, not stale.
- MI-5's "ADR text itself stale on findTimeline" sub-point — the ADR's text is not factually wrong
  about what was decided; the code simply never fully implemented that decision (a drift, not a
  documentation error) — this is a design question (update the ADR to reflect reality, or refactor
  code to match it), not a doc-typo fix. Left for a later decision, not folded into Phase B below.

**Inconclusive — deferred, not acted on:** MI-4's taxon "seed entries" remark — the verifying agent's
own answer was internally inconsistent (said "REJECTED" while describing evidence that sounds like
it supports the original claim). Needs a second, cleaner look before deciding; low-stakes either
way, not blocking the phases below.

## Phase 1 — exact technical plans (drafted 2026-10-13 via 4 parallel fork investigations, ready
for Haiku dispatch — **no code touched, nothing dispatched yet**, per explicit user instruction to
prepare first and launch later)

Every item below was independently re-read against real current source during drafting (not just
copied from the Phase 0 verification quotes) — two items changed shape as a result (A3 downgraded,
A2 gained 2 extra call sites). Treat this section, not Phase 0's summary, as the authoritative
scope once execution starts.

### Phase A — real bugs/gaps (highest priority)

**A1. `SettingsFormModeHandler.updateButtons()` missing `BeforeUnloadUtil.sync()`.**
File: `marketplace-app/src/main/java/org/ost/marketplace/ui/views/main/header/settings/SettingsFormModeHandler.java`.
Add `import org.ost.marketplace.ui.views.utils.BeforeUnloadUtil;`. In `updateButtons` (~line 323):
```java
private void updateButtons(boolean hasChanges) {
    saveButton.setEnabled(hasChanges);
    discardButton.setEnabled(hasChanges);
    BeforeUnloadUtil.sync(hasChanges);
}
```
Exact precedent: `AccountNameFormModeHandler.java:211-215`. Fully mechanical, zero ambiguity.

**A2. `FeedbackAccessService` moderation methods missing `AuthorizationService.requireIsPrivileged`.**
File: `marketplace-orchestrator/src/main/java/org/ost/orchestrator/services/FeedbackAccessService.java`
(already `@RequiredArgsConstructor` — adding a field is enough, no constructor edit). Add field:
```java
private final AuthorizationService authorizationService;
```
Insert `authorizationService.requireIsPrivileged(actorId);` as the first line of `approveFeedback`,
`approveComment`, `rejectFeedback`, `rejectComment` (~lines 215, 220, 235, 243). **Scope expanded
during drafting:** `confirmFeedbackViolation`/`confirmCommentViolation` (~lines 225-233) are the
same class of moderator-only action and were missed by the original review too — add the identical
one-line check to both. **Do NOT touch** `flagFeedback`/`flagComment` (correctly open to any
logged-in user by design).

**A3. `AttachmentGallery` "non-transactional" claim — downgraded, does not need a code fix as
originally framed.** Traced the real, only call path: `AttachmentGallery.commitTempUploads()` →
`AttachmentGalleryService.Handle.commit()` → `AdvertisementFormOverlayModeHandler.commitGallery()`
→ passed as a callback into `AdvertisementSaveService.save(dto, actorId, this::commitGallery)`,
whose entire body (including the gallery commit) already runs inside one
`TransactionTemplate`-bounded `tx.execute(...)` block — every JDBC call on that thread joins the
same transaction automatically, no `@Transactional` needed on `AttachmentMediaService` itself.
`ProviderProfileSaveService` has no gallery step at all (no second call path).

**Decided (2026-10-13): option (c)** — add one Javadoc line to `AttachmentMediaService`
documenting the implicit transactional guarantee, so a future caller that doesn't route through a
`TransactionTemplate`-bounded save method (like `AdvertisementSaveService.save()` does today)
notices the requirement instead of silently losing atomicity. File:
`marketplace-orchestrator/src/main/java/org/ost/orchestrator/services/AttachmentMediaService.java:17-22`.

Before:
```java
/**
 * Full attachment gallery lifecycle ({@link AttachmentPort}) plus audit-diff media state
 * ({@link AttachmentAuditPort}), reused by every marketplace-app adapter. Reuses
 * {@link AttachmentSnapshotReaderService} internally for {@code getLatestSnapshotId} instead of
 * re-wrapping that port call again.
 */
```
After (append one sentence to the existing paragraph, still one logical Javadoc block):
```java
/**
 * Full attachment gallery lifecycle ({@link AttachmentPort}) plus audit-diff media state
 * ({@link AttachmentAuditPort}), reused by every marketplace-app adapter. Reuses
 * {@link AttachmentSnapshotReaderService} internally for {@code getLatestSnapshotId} instead of
 * re-wrapping that port call again. None of this class's own methods are {@code @Transactional} --
 * a caller sequencing multiple calls (delete/commit/snapshot/restore) must already be running
 * inside its own transaction boundary (e.g. {@code AdvertisementSaveService}'s own
 * {@code TransactionTemplate} block) for the sequence to stay atomic.
 */
```
Mechanical, one file, one Javadoc edit — Haiku-eligible, no further design decision needed.

### Phase B — stale Liquibase remarks (docs-only, Haiku-eligible)

**B1.** File: `user-spring-boot-starter/src/main/resources/db/user-changelog/changes/01-user-schema.xml:63`.
Before: `remarks="One row per actor, created lazily on first settings write. Optimistic locking checked via (settings->>'version')::bigint = :expectedVersion, not user_information.version -- deliberately decoupled from profile-name edits."`
After (only the first clause changes): `remarks="One row per actor, created eagerly at registration via UserService.register(), never lazily. Optimistic locking checked via (settings->>'version')::bigint = :expectedVersion, not user_information.version -- deliberately decoupled from profile-name edits."`

**B2.** File: `attachment-spring-boot-starter/src/main/resources/db/attachment-changelog/changes/01-attachment-schema.xml:64`.
Before: `remarks="Optimistic-lock counter"`
After: `remarks="Sequential per-entity history counter, incremented on every insert via MAX(version)+1 -- not a real optimistic-lock check (this table is append-only, never updated)."`

**Confirmed NOT stale, no action:** `user_information.version` remark ("checked manually in
`UserRepository.updateProfile()`") — accurate, matches real hand-rolled-SQL bypass of
`@Version`/`CrudRepository.save()`.

### Phase C — precedent/style cleanup (mechanical, Haiku-eligible)

**C1.** File: `attachment-spring-boot-starter/.../services/AttachmentSnapshotService.java`. Add
`import lombok.NonNull;`. Add `@NonNull` to: `capture(@NonNull EntityType, @NonNull Long entityId, Long actorId)`
(actorId stays nullable), `captureAndGetId(@NonNull EntityType, @NonNull Long, Long actorId)`,
`getUrlsBySnapshotId(@NonNull Long snapshotId)`, `getLatestSnapshotId(@NonNull EntityType, @NonNull Long)`,
`getMediaStateForSnapshot(@NonNull EntityType, @NonNull Long, @NonNull Long)`,
`getChangesBySnapshotId(@NonNull Long)`.

**C2.** File: `apikey-spring-boot-starter/.../repository/ApiKeyRepository.java:20`. Add
`@SuppressWarnings("java:S1192")` directly above `public class ApiKeyRepository`, after
`@RequiredArgsConstructor` (matching every sibling repository's stacking order).

**C3.** Two files — remove hand-written SLF4J logger, add Lombok `@Slf4j`:
- `marketplace-app/.../services/auth/AuthContextService.java`: remove `import org.slf4j.Logger;`,
  `import org.slf4j.LoggerFactory;`, and the `private static final Logger log = ...` field; add
  `import lombok.extern.slf4j.Slf4j;` + `@Slf4j` above `@Service`. Existing `log.warn(...)` call
  sites (2) stay untouched.
- `marketplace-app/.../ui/views/services/VaadinLocaleProvider.java` (**real path — not where the
  original review guessed**): same transform; `@Slf4j` above `@SpringComponent`. 3 existing
  `log.warn(...)` call sites stay untouched.

**C4.** 7 ticket-number citations — drop the ticket clause, keep the rest of each sentence
(no unique technical content lost in any of them):
1. `AccountOverlay.java:30` — remove `" (see {@code improvement-178})"`.
2. `AccountNameFormModeHandler.java:54` — remove `" (see {@code improvement-178})"`.
3. `AccountNameViewModeHandler.java:33` — remove `" (see {@code improvement-178})"`.
4. `ProviderProfileViewModeHandler.java:46` — remove `" (see {@code improvement-178})"`.
5. `application-dev.yml:4` — remove `" See improvement-044."`.
6. `application-prod.yml:24` — remove `" (see improvement-022)"`.
7. `integration-tests/src/main/java/org/ost/integrationtests/AbstractPostgresIntegrationTest.java`
   (**real path** — not under `support/` as originally cited), lines 21 and 33, citing
   `improvement-047`/`improvement-027` — **re-read this file fresh before editing**, not
   independently re-confirmed during drafting (budget ran out); apply the same "drop the ticket
   clause, keep the rest" pattern once the exact current text is confirmed.

**C5.** 3 remaining multi-line comments, trim to one line:
- `query-lib/.../filter/SqlCondition.java:35-37` — `query-lib/DECISIONS.md` ADR already covers this
  exact rationale (titled around `SqlCondition.like()`'s escaping order). Replace with:
  `// Escapes LIKE metacharacters before wrapping, backslash first -- see .claude/nav/adr-index.md.`
  (never cite the specific ADR number per the standing rule).
- `taxon-spring-boot-starter/.../services/TaxonService.java:75-76` — no ADR covers this narrow
  detail; trim in place: `// Must return save()'s own result, not the pre-save "updated" object -- only save()'s return carries the auditing-refreshed updatedAt/version.`
- `marketplace-rest-api/.../exception/ApiExceptionHandler.java:49-50` — no ADR; trim in place:
  `// Reaching here is a genuine unexpected-state bug, not a rate-limit signal -- those throw TooManyAttemptsException, handled above.`
(The other 5 originally-cited locations — `FeedbackService.java`, `FeedbackPortImpl.java`, 3 REST
controllers — are already single-line, most likely already fixed by this session's earlier
`/sync-docs` Javadoc sweep. No action needed on those 5.)

**C6. Audit RowMappers — `@Component` beans → instance-field lambdas (real refactor, not a
one-liner — review the applied result more carefully than the rest of this phase).** File:
`audit-spring-boot-starter/.../repository/AuditLogRepository.java`. Root CLAUDE.md's
"`private static final` constant" convention assumes no injected dependency — both mappers here
need the `@Qualifier("auditObjectMapper") ObjectMapper` field for JSON deserialization, which a
`static` field can't hold, so the correct fix is **instance-level `private final RowMapper<T>`**
lambda fields (a narrow, justified deviation satisfying the rule's actual intent — no separate
injectable bean/class — not its most literal wording). Confirmed via repo-wide grep: these two
mapper classes are referenced only inside this one file, safe to delete entirely. Delete the two
`@Component`-annotated nested classes (`ProjectionMapper`, `SnapshotContentMapper`) and their two
constructor-injected fields; replace with:
```java
private final RowMapper<AuditLogProjection> projectionMapper = (rs, rowNum) -> new AuditLogProjection(
        rs.getObject("id", Long.class),
        EntityType.valueOf(rs.getString("entity_type")),
        rs.getObject("entity_id", Long.class),
        ActionType.valueOf(rs.getString("action_type")),
        parseSnapshot(rs.getString("snapshot_data")),
        rs.getObject("actor_id", Long.class),
        instant(rs, "created_at"),
        rs.getInt("version"),
        rs.getObject("prev_id", Long.class),
        parseSnapshot(rs.getString("prev_snapshot_data")));

private final RowMapper<AuditSnapshotContentDto<? extends AuditableSnapshot>> snapshotContentMapper = (rs, rowNum) ->
        new AuditSnapshotContentDto<>(parseSnapshot(rs.getString("snapshot_data")), rs.getInt("version"));

private static Instant instant(ResultSet rs, String col) throws SQLException {
    Timestamp ts = rs.getTimestamp(col);
    return ts != null ? ts.toInstant() : null;
}

private AuditableSnapshot parseSnapshot(String json) {
    if (json == null || json.isBlank()) return null;
    try {
        return objectMapper.readValue(json, AuditableSnapshot.class);
    } catch (Exception e) {
        log.warn("Failed to deserialize snapshot: {}", json.substring(0, Math.min(json.length(), 120)), e);
        return null;
    }
}
```
Update the 4 call sites (`getLastSnapshot`/`getSnapshotContent` etc.) from
`ProjectionMapper.parseSnapshot(objectMapper, json)` → `parseSnapshot(json)`. Remove now-unused
`org.springframework.stereotype.Component` import (confirm no other use first). The outer class's
existing `@Slf4j` already covers the moved `log.warn` call, no new annotation needed.

### Phase D — DRY duplication fixes (module by module)

**D1 (Row 7) — 48h edit window, 3 places.** Less mechanical than the rest of this phase — a real
design call, not copy-paste; review before dispatching. `FeedbackService`'s own
`Duration.ofHours(48)` is the authoritative server-side check and stays. Add a derived method to
the 2 platform-commons DTOs (matches the established "pure derivation over the type's own fields"
exception in `platform-commons/CLAUDE.md`):
```java
// FeedbackDto.java and FeedbackCommentDto.java, same addition in both:
private static final Duration EDIT_WINDOW = Duration.ofHours(48);
public boolean isEditable() {
    return Instant.now().isBefore(createdAt.plus(EDIT_WINDOW));
}
```
`FeedbackPanel.java`: delete the `EDIT_WINDOW` constant + its now-unused `Duration` import; change
`Instant.now().isBefore(entry.createdAt().plus(EDIT_WINDOW))` → `entry.isEditable()`.
`FeedbackReplyPanel.java`: same transform, `comment.isEditable()` (keep its `Instant` import if
used elsewhere in the file — check before removing). Honest framing for the report: this removes 2
of 3 duplicate re-derivations, not a single cross-module source of truth (the literal `48` still
exists in `FeedbackService` + both DTOs, since platform-commons can't depend on the starter) —
state this plainly, don't overclaim a full fix.

**D2 (Row 8) — double row-lookup in `flagFeedback`/`flagComment`.**
File: `feedback-spring-boot-starter/.../services/FeedbackService.java`. Both `FeedbackView`/
`FeedbackCommentView` already carry `authorId`. Drop the DTO lookup, read `authorId` off the
already-fetched View:
```java
// flagFeedback, before:
FeedbackDto feedback = findFeedbackById(feedbackId).orElseThrow(...);
if (feedback.authorId().equals(actorId)) throw ...;
FeedbackView existing = repository.findViewById(feedbackId).orElseThrow(...);
// after:
FeedbackView existing = repository.findViewById(feedbackId).orElseThrow(...);
if (existing.authorId().equals(actorId)) throw ...;
```
Identical transform for `flagComment` (drop `findCommentById`, read `existing.authorId()` off the
already-fetched `FeedbackCommentView`).

**D3 (Row 9) — `DefaultTaxonPort.getAllByType` extra round-trip.** Real file:
`taxon-spring-boot-starter/.../services/DefaultTaxonPort.java` (not `spi/`). `TaxonFilter.active()`
== `TaxonFilter.of(null, false)`; `listAllByType(type, locale, false)` already builds full DTOs
from the first fetch, no second lookup needed:
```java
@Override
public List<TaxonDto> getAllByType(@NonNull TaxonType type, @NonNull Locale locale) {
    return listAllByType(type, locale, false);
}
```

**D4 (Row 11) — `AdvertisementApiController.enrich()` duplicates `enrichSingle()`.**
`enrichmentService` (`AdvertisementDisplayEnrichmentService`) is already an injected field. Delete
the controller's private `enrich(AdvertisementInfoDto, Locale)` method entirely; replace its 3 call
sites with `enrichmentService.enrichSingle(ad, locale)` using the same arguments. No constructor
change needed.

**D5 (Row 12) — `AdvertisementSaveService.buildCurrentSnapshot` hand-splits category/city.**
`CategoryAndCitySplit` is package-private, same package — directly accessible:
```java
private AdvertisementSnapshotDto buildCurrentSnapshot(@NonNull AdvertisementInfoDto ad) {
    List<TaxonDto> assignments = taxonLookupService.getForEntity(EntityType.ADVERTISEMENT, ad.getId(), Locale.ENGLISH);
    CategoryAndCitySplit split = CategoryAndCitySplit.of(assignments);
    List<Long> catIds = split.categoryIds().stream().sorted().toList();
    Long attachmentSnapshotId = attachmentSnapshotReaderService.getLatestSnapshotId(EntityType.ADVERTISEMENT, ad.getId());
    return new AdvertisementSnapshotDto(ad.getTitle(), ad.getDescription(), ad.getAdKind(), catIds, split.cityTaxonId(), attachmentSnapshotId);
}
```

**D6 (Row 13) — `ProviderProfileSaveService` 3 copies + roundabout `fetchEnriched`.** Add one
private helper, reuse it from `findById`/`findByActorId`/`fetchEnriched`/`save()`:
```java
private Optional<ProviderProfileDto> enrich(Optional<ProviderProfileDto> maybe) {
    return maybe.map(p -> displayEnrichmentService.enrichWithCategoryAndCity(p, currentLocaleHook.getCurrentLocale()));
}
public Optional<ProviderProfileDto> findById(@NonNull Long id) { return enrich(providerProfilePortFactory.get().findById(id)); }
public Optional<ProviderProfileDto> findByActorId(@NonNull Long actorId) { return enrich(providerProfilePortFactory.get().findByActorId(actorId)); }
private ProviderProfileDto fetchEnriched(@NonNull Long entityId) { return findById(entityId).orElse(null); }
// inside save(): ProviderProfileDto saved = findById(id).orElseThrow();
```

**D7 (Row 14) — audit CTE duplicated between `findRows`/`findTimeline`.** Extract the identical
inner `SELECT ... FROM audit_log` block (not the full `WITH` wrapper, since each method's own
`WHERE`/closing clause differs) into a `private static final String NUMBERED_SELECT` constant in
`AuditLogRepository.java`; splice `"WITH numbered AS (" + NUMBERED_SELECT + ")..."` into both
methods, keeping each one's own trailing clause local.

**D8 (Row 16) — 4 snapshot DTOs duplicate `typeToString`.** Add a new default method to
`AuditableSnapshot` (qualifies under `platform-commons/CLAUDE.md`'s narrow default-method
exception — calls only the interface's own other methods):
```java
static void diffField(List<ChangeEntry> changes, String key, Enum<?> prev, Enum<?> curr) {
    diffField(changes, key, prev == null ? "" : prev.name(), curr == null ? "" : curr.name());
}
```
Delete the private `typeToString` from all 4 DTOs; change their `diffField` call sites to pass the
raw enum values directly (relies on overload resolution). Each DTO's `allFields()` bootstrap method
builds a `FieldChange` directly (not via `diffField`) — inline `x() == null ? "" : x().name()`
there instead of introducing a second helper for that one remaining call site.

**D9 (Row 17) — `AdvertisementRepository` SELECT list copied 3x.** Extract into
`private static final String ADVERTISEMENT_SELECT` (identical `SELECT a.id, a.title,
a.description, a.ad_kind, a.created_at, a.updated_at, a.created_by, a.version FROM advertisement a`
across all 3 methods); splice into each, keeping each method's own trailing clause.

**D10 (Row 19) — `SettingsEditDto` built 3x + lambda 2x.** Add 2 private static helpers to
`SettingsFormModeHandler.java`:
```java
private static SettingsEditDto toEditDto(Long userId, UserSettingsDto source) {
    return SettingsEditDto.builder().id(userId)
            .adsPageSize(source.getAdsPageSize()).usersPageSize(source.getUsersPageSize())
            .timelinePageSize(source.getTimelinePageSize()).providerProfilesPageSize(source.getProviderProfilesPageSize())
            .feedbackPageSize(source.getFeedbackPageSize()).moderationPageSize(source.getModerationPageSize())
            .version(source.getVersion()).build();
}
private static void copySettings(SettingsEditDto src, SettingsEditDto tgt) {
    tgt.setAdsPageSize(src.getAdsPageSize()); tgt.setUsersPageSize(src.getUsersPageSize());
    tgt.setTimelinePageSize(src.getTimelinePageSize()); tgt.setProviderProfilesPageSize(src.getProviderProfilesPageSize());
    tgt.setFeedbackPageSize(src.getFeedbackPageSize()); tgt.setModerationPageSize(src.getModerationPageSize());
    tgt.setVersion(src.getVersion());
}
```
Replace the 3 builder blocks (`activate()`/`discardChanges()`/`loadRestored()`) with
`toEditDto(params.getUserId(), current/fresh/restored)`; replace both 6-setter lambdas with the
method reference `SettingsFormModeHandler::copySettings`.

**D11 (Row 20) — `IntegerField` setup copied 6x** (grew from 4 since the original review — now
includes `feedbackPageSizeField`/`moderationPageSizeField`). Note: the existing `bindPageSizeField`
helper is already called 6 times for *binding* — this is a separate, still-duplicated
*construction* step. Add:
```java
private static IntegerField newPageSizeField(String label) {
    IntegerField field = new IntegerField(label);
    field.setMin(PaginationDefaults.MIN_PAGE_SIZE); field.setMax(PaginationDefaults.MAX_PAGE_SIZE);
    field.setStep(1); field.setStepButtonsVisible(true);
    field.setValueChangeMode(ValueChangeMode.EAGER); field.setWidthFull();
    return field;
}
```
Replace each of the 6 construction blocks with e.g. `adsPageSizeField = newPageSizeField(getValue(SETTINGS_ADS_PAGE_SIZE_LABEL));`.

**D12 (Row 22) — `AttachmentGallery.discardTempUploads` duplicate fall-through.**
```java
public void discardTempUploads() {
    pendingSnapshotRestore = false;
    if (!tempUploads.isEmpty()) {
        attachmentMediaService.discardTempUploads(tempUploads);
        tempUploads.clear();
        tempFileSet.clear();
    }
    pendingDeletions.clear();
    refresh();
}
```
(Setting `pendingSnapshotRestore = false` unconditionally is a safe no-op when already `false`.)

### Phase E — YAGNI dead-code removal

**E1 (Row 10) — `TaxonFilter.name` dead.** Confirmed via repo-wide grep: every real caller passes
`null`. `TaxonRepository`'s `tt` (taxon_translation) join exists only for this filter — the
`ROW_MAPPER` never reads a `tt.*` column. `TaxonFilter.java`: `record TaxonFilter(String name, boolean showDeleted)`
→ `record TaxonFilter(boolean showDeleted)`; delete the `of(String, boolean)` factory (no caller
needs it); `active()`/`all()` become `new TaxonFilter(false)`/`new TaxonFilter(true)`.
`TaxonRepository.java`: delete the `FILTER` constant and its `tt.name`-bound entry, and the
`LEFT JOIN taxon_translation tt ...` line. `DefaultTaxonPort.java:112`: update the one call site
from `TaxonFilter.of(null, includeDeleted)` to `new TaxonFilter(includeDeleted)`.

**E2 (Row 15) — `captureAndGetId`'s extra query.** `captureAndGetId` must stay public (3
integration tests call it directly) — extract the shared insert logic into a private
`insertSnapshot(...)` returning `boolean`; `capture()` calls it directly (skipping the
`findLatestId` round-trip entirely); `captureAndGetId` calls it then conditionally calls
`findLatestId` only when it actually needs to return an id. See the fork's full drafted method body
for the exact extraction (same file, 3-method split: `capture`/`captureAndGetId`/`insertSnapshot`).

**E3 (Row 21) — `QueryStatusBar.Labels` never varies.** Now **5** `*QueryConfig` classes (not 4 —
`ModerationQueryConfig` is new since the original review). Add a static factory to the `Labels`
record:
```java
public static Labels defaults() {
    return new Labels(I18nKey.QUERY_STATUS_FILTERS_NONE, I18nKey.QUERY_STATUS_FILTERS_PREFIX,
            I18nKey.QUERY_STATUS_SORT_NONE, I18nKey.QUERY_STATUS_SORT_PREFIX,
            I18nKey.SORT_DIRECTION_ASC, I18nKey.SORT_DIRECTION_DESC);
}
```
Replace all 5 `*QueryConfig` call sites with `QueryStatusBar.Labels.defaults()`.

**E4 (Row 23) — unused `createBreadcrumbButton(I18nKey, Runnable)`.** Confirmed zero callers
repo-wide. Delete it from `EntityOverlaySupport.java`; keep the `(String, Runnable)` overload.

**E5 (Row 24) — unused `buildBreadcrumbLinks()` no-arg.** Confirmed zero external callers (every
real overlay calls the `(List<BreadcrumbStep>)` overload directly). Delete it from
`AbstractEntityOverlay.java`; keep the `(List<BreadcrumbStep> steps)` overload.

### Deferred — needs a design decision before any fix, not mechanical, not in this phase's scope
- MI-5's deserialization-in-the-mapper drift from ADR-015's stated intent — either refactor further
  to match the ADR, or update the ADR to reflect what the code actually does (C6 above only fixes
  the RowMapper-registration-style sub-point, not this one).
- The inconclusive taxon "seed entries" remark — needs a second, cleaner verification pass first.

**Status: fully drafted, nothing dispatched, no code touched.** Next step is explicit per-phase
(or per-item) approval before any Haiku dispatch launches.

## Why change

Three of three spot-checked items (across 3 different modules/categories) confirmed true on direct
read of the real code — real signal the review's own methodology (independent verification,
self-caught Haiku backlog-hallucination via manual `grep`) is trustworthy, not just plausible-sounding.
At least 2 of the 5 "most important" items are genuine bugs/gaps (one correctness, one
defense-in-depth security), not just style — worth fixing, not just filing. Confirmed in full by
Phase 0's real validation results above: 26 of 32 candidates are real.

## Expected benefit

Stated per-phase in Phase 1 above, not as one blanket number. The two confirmed-real bugs alone
(Settings unsaved-changes warning, feedback-moderation authorization gap) are worth fixing
regardless of how the DRY/YAGNI tail resolves.

## Approach

Phase 0 (validation) and Phase 1 (exact technical plans) are both complete — see those sections
above for the real results; this section is not a separate forward-looking plan. Remaining work is
**Phase 2 onward — implement, one fix-phase at a time**, each with its own Approval-Rule technical
plan presented before touching code (per the standing rule that broad task approval doesn't waive
per-sub-change approval), verified via `build-and-test.sh`/relevant integration tests/Playwright
before moving to the next phase — not started yet, waits on explicit per-phase approval.

## Phase F — tooling meta-work, surfaced and done alongside this task (2026-10-13)

Not one of the original review's findings — a side discussion that came up while planning this
task's own Phase 2 dispatch strategy, recorded here per the user's explicit request rather than
left untracked.

**F1. `/autopilot` step 2 now applies the standing per-unit Haiku-dispatch rule.** An earlier
draft gated this behind an invented "3+ independent units" threshold — re-checked against
`.claude/rules.md`'s own standing "Well-specified mechanical implementation work defaults to a
Haiku-model agent" rule and found that rule carries no count/size threshold at all; it applies
per-unit, even to a single fully-specified change. Dropped the invented threshold: each file-level
fix in step 1's plan with an already-known exact before/after (no open judgment call) now
dispatches to Haiku regardless of how many such units exist; multiple units get grouped by
file/module (same-file fusion, mirroring `deep-review-orchestrator`'s step 4) purely as a batching
optimization, not as a gate on whether to use Haiku at all. A single cohesive design requiring
continuous judgment across files (the common single-feature `/autopilot` case) still goes direct —
that case was never Haiku-eligible under the standing rule either way. No real operational data
existed yet to justify a threshold specifically (checked `backlog/completed/tasks/` for prior
Agent-call breakdowns from autopilot runs — none exist) — the new metrics below are what should
justify one later, if real rework-rate evidence ever calls for it.

**F2. `/sync-docs` diff-mode gains the same Haiku-dispatch lever Full Audit Mode's Step A3
already has**, same per-file/no-threshold criterion as F1 — Step A3 itself never gated its own
dispatch on file-count volume either, so diff-mode doesn't invent one now.

**F3. New `## Operational notes` fields** (`.claude/rules.md`): `haiku_decomposition_used`,
`haiku_decomposition_dispatch_count`, `haiku_decomposition_rework_needed` — real evidence
accumulated over time on whether F1/F2's eligibility criteria are safe (a high rework rate means
they're too loose), aggregated by `/sync-docs --full-audit`'s existing Step A5.

**F4. Per-task ADR usage tracking.** New `adrs_referenced` Operational-notes field (every ADR
actually consulted/followed during a task, in the `ADR-NNN (module)` key shape `adr-index.md`
already uses) plus a new `Referenced` column on the generated `.claude/nav/adr-index.md`, tallied
by an extended `.claude/nav/scripts/generate-adr-index.sh` from every task file's own
`adrs_referenced:` line — so it becomes possible to tell, over time, which ADRs are actually
load-bearing versus never consulted again after being written.

**Recorded as `.claude/DECISIONS.md` ADR-003 (F1/F2/F3) and ADR-004 (F4)**, via `/record-decision`,
not hand-written. `.claude/nav/adr-index.md` regenerated — **one real bug caught and fixed in the
same pass**: the new tally pre-pass in `generate-adr-index.sh` used a bare `grep -o ... | sed | tr
| ... | grep -v ...` pipeline under `set -euo pipefail`; `grep` returns exit 1 on zero matches
(true today, since no task file has an `adrs_referenced:` line yet), which killed the whole script
immediately with no output. Fixed by wrapping each grep that can legitimately match nothing in
`{ grep ... || true; }`. Verified: ran the script against a throwaway output path first (exit 0,
237 entries, `Referenced` column present and defaulting to `0`), then against the real
`.claude/nav/adr-index.md` — regenerated clean.

## Related

- `.claude/agents/review/deep-review-orchestrator.md` — the agent that produced this review.
- `backlog/completed/tasks/improvement-206-review-haiku-verification-and-cache-investigation.md` —
  the Haiku-verification/same-file-fusion levers this review's own step 4 already used, and that
  this task's own Phase 0 validation dispatch reuses.
- `backlog/tasks/improvement-133-deferred-oversized-review-findings.md` — sibling bucket for
  findings too large to size immediately; not expected to apply here (every item above looks
  independently fixable), but check during Phase 1 grouping if any single item turns out larger
  than expected.
