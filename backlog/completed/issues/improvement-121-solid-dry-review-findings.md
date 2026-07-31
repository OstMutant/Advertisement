# improvement-121: repo-wide SOLID/DRY review findings (11-agent pass, 2026-07-25)

**Superseded 2026-07-29 by [improvement-132](../issues/improvement-132-full-repo-solid-dry-review-2026-07-29.md):**
every one of this issue's 24 findings was individually re-verified against current code (18 still
accurate and merged into improvement-132's module sections, 6 resolved as stale/invalid/fixed —
see improvement-132's "How this was found" table for the full reconciliation). This file is kept
for its original discussion/plan history; act on improvement-132 instead, not this one.

**Type:** improvement — code-quality/tech-debt cleanup, no live user-facing bug (the two real bugs
this same review found were already fixed directly in improvement-119's cleanup commit).
**Module:** `attachment-spring-boot-starter`, `audit-spring-boot-starter`, `platform-commons`,
`query-lib`, `taxon-spring-boot-starter`, `user-spring-boot-starter`, `marketplace-app` (several
packages).
**Priority:** ⚪ lowest — no live bug; deprioritized 2026-07-26 after an autopilot execution attempt
across all 8 batches was aborted before landing anything (rolled back cleanly, no code changes from
this issue are in the tree). Revisit opportunistically, not on a schedule.
**When:** independent, no blockers.

## Problem

A repo-wide SOLID/DRY/KISS audit (11 parallel read-only review agents, one per module, run after
improvement-119/F-02 shipped) found real violations of this project's own documented conventions
across almost every module. None of these were introduced by F-02 — they're pre-existing, found
opportunistically while the reviewers also checked whether the new city code was consistent with
its surroundings (it was; see improvement-119's completed issue). Grouped by module below, worst
first within each group.

### query-lib (shared by every repository in the app — highest blast radius)

1. `SqlFilterBuilder.toParams()` builds params via `Collectors.toMap(SqlCondition::filterProperty,
   SqlCondition::value)` — throws an unchecked `IllegalStateException` at request time if two
   active filter bindings ever resolve to the same `filterProperty` (e.g. a copy-pasted
   `SqlBoundFilter.of(...)` with an unrenamed property string). No compile-time or
   declaration-time protection today. Fix: use a merge function (`(a, b) -> b`) at minimum, or
   better, validate the bindings list for duplicate `filterProperty` values at
   `SqlFilterBuilder` construction time.
2. `OrderByBuilder.build(...)`/`PaginationSqlBuilder.pageLimit(...)` tolerate `null`
   `Sort`/`Pageable` as "nothing to add", but `SqlFilterBuilder.build(...)` requires `@NonNull`
   on the filter — three sibling "build a SQL fragment" entry points, three different null
   policies. Pick one policy and apply it consistently.
3. `inSet()`/`anyOf()` each hand-roll the same `CollectionUtils.isEmpty(values) ? null : ...`
   guard instead of pushing it into a shared `applyIfNotEmpty(...)` helper the way the scalar
   factories (`equalsTo`/`after`/`before`) already share `applyIfPresent(...)`.
4. `SqlCondition`'s class Javadoc says factories return `null` "when the value is absent" — true
   for scalar factories, imprecise for the collection-based ones (`null` *or* empty).

### attachment-spring-boot-starter

1. `AttachmentService` has grown into five distinct concerns with no internal separation: gallery
   queries, upload/video-ingestion, commit/restore orchestration, snapshot delegation, and
   media-summary DTO shaping. Extract the video/embed-URL logic (`resolveVideoDescriptor`,
   `validateEmbedUrl`, `embedFilename`, `VideoDescriptor`, `isVideo`) into its own class, mirroring
   how `AttachmentSnapshotService` is already split out for the snapshot concern.
2. "Is this a video/embed" classification is duplicated in three independent, non-identical forms:
   `AttachmentService.isVideo()` (own `CT_YOUTUBE`/`CT_EMBED` constants),
   `AttachmentMediaContentType.isEmbedded()` (platform-commons, already the shared authority per
   `AttachmentCleanupService`'s usage), and `AttachmentSnapshotService.filename()`'s own
   `YoutubeUtil.extractId(url) != null` check. Consolidate onto `AttachmentMediaContentType`.
3. `AttachmentSnapshotRepository`/`AttachmentCleanupService` declare `RowMapper` lambdas inline
   per-call instead of `private static final` constants, unlike `AttachmentRepository.ROW_MAPPER`
   — inconsistent with this project's own documented repository convention.

### audit-spring-boot-starter

1. ~~`AuditLogRepository.findRows()`/`findTimeline()` both inline the identical 6-line `numbered AS
   (...)` CTE verbatim — extract into a `private static final String` constant.~~ **Corrected
   2026-07-29 (found via `improvement-132`'s `/deep-review full` pass): invalid, no action.** This
   project deliberately suppresses SQL-literal duplication within a repository class
   (`@SuppressWarnings("java:S1192")`, documented "Repository SQL pattern" — SQL is inlined
   per-method, text blocks, never extracted into shared constants across methods even when
   similar). The CTE duplication is real but is exactly the class of thing this project has already
   decided not to extract. Does not affect item 3 below (a `RowMapper` object, not SQL text — a
   separate, still-valid convention).
2. `AuditReadService`, `AuditLogRepository`, `AuditCleanupService` have zero `@NonNull` annotations
   on parameters that must not be null (`entityType`, `entityId`, `filter`, `snapshotData`, ...),
   unlike `DefaultAuditPort` in the same module — inconsistent with this project's own `@NonNull`
   rule. **Re-confirmed still accurate 2026-07-29** via `improvement-132`'s independent re-review.
3. `AuditLogRepository.getSnapshotContent()`'s `snapshotContentMapper()` builds a new `RowMapper`
   lambda per call instead of a `private static final` field (captures `objectMapper`, which is
   likely why it wasn't hoisted — needs a small static nested mapper class instead, the way
   `ProjectionMapper` already does it). **Re-confirmed still accurate 2026-07-29.**

### platform-commons

1. `AttachmentAuditHook` is called *marketplace → attachment-starter* (confirmed: implemented by
   `AttachmentAuditHookImpl` in the starter, called from `AdvertisementEnrichService` in
   marketplace-app) — that's the `*Port` direction per this module's own naming table, not `*Hook`.
   Its own Javadoc even says "marketplace → attachment-starter," contradicting the table. Rename to
   `AttachmentAuditPort` (or fold into `AttachmentPort`), or add a `DECISIONS.md` entry explicitly
   carving out this one exception.
2. `platform-commons/CLAUDE.md`'s SPI table and `attachment-spring-boot-starter/CLAUDE.md` both
   still list `AttachmentMediaChangeHook` as current, but it no longer exists anywhere in
   `platform-commons/src/main/java` (confirmed via grep, zero hits) — stale documentation, update
   or reconcile with whatever replaced it.
3. `UserIdMarker` sits in `user.security`, a package role this module's own CLAUDE.md doesn't
   define (only `api`/`spi`/`dto`). Either move it into `user.spi` or document `security` as a
   fourth legitimate package role.

### taxon-spring-boot-starter

1. `DefaultTaxonPort.resolveDtos()`/`buildDtoIndex()` are near-duplicate: both call `indexById()`,
   both group translations the same way, both apply the same `activeOnly` filter and `toDto`
   mapping — only the final collector (`List` vs `Map`) differs. Extract a shared
   `buildDtoLookup(...) -> Map<Long, TaxonDto>` and have `resolveDtos()` become a one-line wrapper
   over it.
2. `TaxonAssignmentRepository.countByTaxonIds()` uses a fully-qualified
   `java.util.stream.Collectors.toMap(...)` instead of the imported `Collectors` every sibling
   method in the file uses — add the import, drop the fully-qualified reference.

### user-spring-boot-starter

1. `RoleChecker`/`OwnershipChecker` public methods take `UserDto`/`Long`/`UserIdMarker` params with
   no `@NonNull`, then defensively null-check instead (`user != null`) — violates both this
   project's `@NonNull` rule and its "no defensive null checks, fail fast" rule. Every other class
   in this module (`UserService`, `UserRepository`, `UserPortImpl`) uses `@NonNull` consistently;
   these two are the outliers.
2. `User → UserDto` field-by-field mapping is duplicated verbatim in `UserService.java` and
   `UserPrincipal.java` (same 8 fields, same order, no compiler link tying them together). Extract
   one `UserDto.from(User)` factory and call it from both places.
3. `UserService` spans five distinct concerns (query/listing, profile CRUD,
   registration+rate-limiting, security-context refresh, retention/cleanup) — not yet unmanageable,
   but `cleanup()`'s cross-domain `AdvertisementPort` dependency is the clearest candidate to split
   out into `UserCleanupService` if the class grows further.
4. `user-spring-boot-starter/CLAUDE.md` documents a `UserService.applyUserRestore()` method that no
   longer exists in the current source — doc/code drift, reconcile.

### marketplace-app

1. `DeleteActionButton`/`EditActionButton`/`ShareActionButton` are byte-for-byte identical except
   for the `VaadinIcon` and whether `LUMO_ERROR` is added — collapse the shared two-constructor
   boilerplate into `BaseActionButton` via a protected constructor/helper taking
   `(VaadinIcon, ButtonVariant[], tooltip, onClick, cssClassName, small)`.
2. `AttachmentThumbnail.thumbSrc()` and `CardLightboxStrip.thumbSrc()` duplicate identical
   YouTube/video/plain-url branching logic — extract to a shared static helper in `ui/views/utils/`.
3. `SettingsFormModeHandler` manually reimplements the tab-gating/audit-availability control flow
   that `AbstractFormOverlayModeHandler.buildContentWithActivity()` already provides (the same
   method `UserFormOverlayModeHandler` already calls) — duplicates ~10 lines instead of building an
   `ActivityTabParams` and delegating.
4. Public constructors across `ui/views/components/buttons/`, `fields/`, and `dialogs/` (e.g.
   `Runnable onClick`, `String tooltip/label/title`) carry no `@NonNull` at all — a widespread,
   low-risk-but-real deviation from this project's own mandatory rule; several sibling classes
   (`AttachmentLightbox`, `AuditActivityPanel`, `CardMediaLightbox`) do follow it correctly, so the
   omission is inconsistent rather than universal.
5. `BaseDialog.buildLayout()` (no-arg overload, empty body) is dead code — only the
   `buildLayout(DialogLayout)` overload is ever called.
6. `AccessEvaluator.canNotEdit`/`canNotDelete`/`canOperate` are each duplicated verbatim for
   `UserIdMarker` and `Long` overloads (6 methods, 3 pairs).
7. `UserView.refresh()`'s `ConstraintViolationException` branch never calls
   `refreshButton.setVisible(false)` the way its success path does — a stale "changes available"
   icon can be left visible after a validation error clears the grid.
8. `TimelineView.refresh()` has only a single generic `catch (Exception ex)`, not the two-tier
   `ConstraintViolationException`/`Exception` shape `.claude/rules.md`'s View Pattern documents
   (likely fine in practice since `AuditPort` probably never throws that exception, but undocumented
   as an intentional exception to the pattern).

## Suggested fix

Work through the list above module by module — each item is small and independently shippable, so
this does not need to land as one giant PR; splitting into a few batches by module (matching this
project's existing "Execution batches" convention in `BACKLOG.md`) is reasonable if picked up
incrementally. The `query-lib` `SqlFilterBuilder.toParams()` item is the one with real production
risk (a silent crash under a specific filter-binding-name collision) and is worth prioritizing
first regardless of batching; the two `CLAUDE.md` doc-drift items (`AttachmentMediaChangeHook`,
`applyUserRestore()`) are free wins alongside whichever code change touches those files next.

## Detailed execution plan

Eight batches, ordered by risk (lowest/most-isolated first) so early batches build confidence
before the two batches that touch shared UI components or a cross-module SPI rename. Each batch is
one pass = one PR, one test run, per this project's own batching convention. `git commit` only on
explicit request, per standing project rule — this plan does not change that.

**Batch 121-A — query-lib** (isolated library, zero Spring context, highest production risk item)
1. `SqlFilterBuilder` constructor: validate the `List<SqlBoundFilter<F,R>>` for duplicate
   `filterProperty` values and fail fast (throw `IllegalArgumentException` at construction time,
   not `IllegalStateException` at request time) — construction happens once per repository class
   at classload, so this turns a rare runtime crash into an always-caught startup/test failure.
2. Pick one null policy for the three sibling "build a SQL fragment" entry points
   (`SqlFilterBuilder.build`, `OrderByBuilder.build`, `PaginationSqlBuilder.pageLimit`) and apply it
   to all three — recommend adopting "tolerate `null` as no-op" everywhere (2 of 3 already do this;
   changing `SqlFilterBuilder.build`'s `@NonNull` filter param to tolerate `null` is the smaller
   diff than adding null-handling to the other two).
3. Extract `applyIfNotEmpty(mapping, collection, operator, mapper)` shared by `inSet()`/`anyOf()`.
4. Fix `SqlCondition`'s class Javadoc to describe both the scalar (`null`) and collection
   (`null`-or-empty) absence conditions precisely.
- Test: `bash scripts/unit-tests.sh query-lib` (existing `SqlConditionTest`/`SqlOperatorTest`) +
  new test cases for the duplicate-`filterProperty` fail-fast and the null-tolerant `build()`.

**Batch 121-B — audit-spring-boot-starter** (small, isolated, no UI surface)
1. Extract the `numbered AS (...)` CTE text shared by `findRows()`/`findTimeline()` into a
   `private static final String NUMBERED_CTE` constant, matching this class's own `FILTER`/
   `SORT_ALIASES` convention.
2. Add `@NonNull` to the not-null public parameters on `AuditReadService`, `AuditLogRepository`,
   `AuditCleanupService` (mirror `DefaultAuditPort`'s existing coverage in the same module).
3. Replace `getSnapshotContent()`'s per-call `snapshotContentMapper()` lambda with a small static
   nested `RowMapper` class taking `objectMapper` via constructor, mirroring `ProjectionMapper`'s
   existing shape in the same file.
- Explicitly deferred from this batch: the `rawtypes`/`unchecked` suppression in
  `AuditReadService` — the reviewing agent flagged it as "awareness, not a hard defect" since it's
  tied to a deliberate generic-erasure tradeoff elsewhere in the DTO hierarchy; revisit only if it
  causes an actual bug.
- Test: `bash scripts/integration-tests.sh --sandbox AuditLogRepositoryTest` +
  `bash scripts/unit-tests.sh`.

**Batch 121-C — taxon-spring-boot-starter** (small, isolated)
1. Extract `DefaultTaxonPort.buildDtoLookup(List<Long> taxonIds, Locale locale, boolean
   activeOnly) -> Map<Long, TaxonDto>` (this is what `buildDtoIndex()` already computes) and
   rewrite `resolveDtos()` as a thin ordering wrapper over it
   (`taxonIds.stream().map(lookup::get).filter(Objects::nonNull).toList()`).
2. Fix `TaxonAssignmentRepository.countByTaxonIds()`'s fully-qualified
   `java.util.stream.Collectors` reference — add the normal import, matching every sibling method.
- Test: `bash scripts/integration-tests.sh --sandbox` for `TaxonRepositoryTest`,
  `TaxonServiceTest`, `TaxonPortTranslationFallbackTest`, `TaxonAssignmentRepositoryTest` + spec 03
  Playwright (category/city admin flows, since `DefaultTaxonPort` backs both).

**Batch 121-D — user-spring-boot-starter**
1. Add `@NonNull` to `RoleChecker`/`OwnershipChecker` public parameters, drop the now-redundant
   defensive `!= null` checks.
2. Extract `UserDto.from(User)` (static factory on the DTO, or a small mapper method) and call it
   from both `UserService.java` and `UserPrincipal.java` instead of duplicating the 8-field
   mapping.
3. Change `UserPort.findActorNames`/`UserService.findActorNames` to take `Set<Long>` directly
   (matching `findExistingIds`/`findDeletedIds`/`findByIds`), dropping the one-off
   `instanceof Set` branch.
4. Unify `cleanup()`'s two different `ComponentFactory<AdvertisementPort>` access patterns
   (`ifAvailable`/`findIfAvailable().orElse(...)`) into one `findIfAvailable()` call branched once.
5. Fix `user-spring-boot-starter/CLAUDE.md`'s stale reference to `UserService.applyUserRestore()`
   (confirm whether it was renamed/removed and correct the doc accordingly).
- Explicitly deferred from this batch: splitting `UserService` into
  `UserCleanupService`/`UserRegistrationService` — the reviewing agent flagged this as "a trend,
  not yet a hard violation"; revisit only once the class grows further, not preemptively.
- Test: `bash scripts/unit-tests.sh` (`AccessEvaluatorTest`, `AuthServiceTest`) +
  `bash scripts/integration-tests.sh --sandbox UserServiceTest UserRepositoryTest` + Playwright
  spec 02 (auth) and spec 03 (role promotion, since `RoleChecker` backs the UI's edit/delete gating).

**Batch 121-E — marketplace-app: services/query layer**
1. Collapse `AccessEvaluator.canNotEdit`/`canNotDelete`/`canOperate`'s `UserIdMarker`/`Long`
   overload pairs into one implementation each (have the `UserIdMarker` overload delegate to the
   `Long` overload via `.getOwnerUserId()`, or vice versa — whichever reads more naturally at each
   call site).
2. Rewrite `SettingsFormModeHandler` to build an `ActivityTabParams` and call the inherited
   `buildContentWithActivity(...)` instead of manually reimplementing the same tab-gating control
   flow — delete the now-dead manual `.map(...).orElse(...)` block.
3. `UserView.refresh()`: add `refreshButton.setVisible(false)` to the `ConstraintViolationException`
   catch branch (or move it to `finally`), matching the success path's bookkeeping.
4. `TimelineView.refresh()`: either add the same two-tier `ConstraintViolationException`/`Exception`
   catch shape `.claude/rules.md`'s View Pattern documents, or — if confirmed `AuditPort` genuinely
   never throws that exception — leave the single catch but add a one-line comment stating that
   explicitly, so it reads as a deliberate exception rather than an oversight.
- Test: `bash scripts/unit-tests.sh` + Playwright spec 02/03 (settings page-size changes, user
  promotion/edit) and spec 05 (timeline filters) — this batch touches security gating and two
  `refresh()` methods, so the relevant e2e flows need a real pass, not just unit coverage.

**Batch 121-F — marketplace-app: shared UI components** (broadest Playwright blast radius —
these components render on every tab)
1. Collapse `DeleteActionButton`/`EditActionButton`/`ShareActionButton`'s identical
   two-constructor boilerplate into a protected `BaseActionButton` constructor/helper taking
   `(VaadinIcon icon, ButtonVariant[] extraVariants, String tooltip, Runnable onClick, String
   cssClassName, boolean small)`; keep the three named subclasses (call sites/readability), just
   remove the duplicated bodies.
2. Extract `AttachmentThumbnail.thumbSrc()`/`CardLightboxStrip.thumbSrc()`'s identical
   YouTube/video/plain-url branching into one shared static helper in `ui/views/utils/` (e.g.
   `LightboxUtil.thumbSrc(contentType, url)`).
3. Add `@NonNull` to the non-optional constructor parameters across `ui/views/components/buttons/`,
   `fields/`, and `dialogs/` that are currently missing it.
4. Delete `BaseDialog`'s dead no-arg `buildLayout()` overload.
- Test: full `bash scripts/playwright.sh e2e --full --ux` — action buttons, thumbnails, and dialogs
  appear across every tab (advertisements, users, reference data, timeline), so this is the one
  batch that needs the complete suite, not a targeted subset.

**Batch 121-G — attachment-spring-boot-starter** (larger extraction, needs careful media-flow
testing; do after the smaller/safer batches above have re-established confidence in the process)
1. Consolidate the three independent "is this a video/embed" checks
   (`AttachmentService.isVideo()`, `AttachmentMediaContentType.isEmbedded()`,
   `AttachmentSnapshotService.filename()`'s inline check) onto `AttachmentMediaContentType` as the
   single source of truth; delete `AttachmentService`'s own `CT_YOUTUBE`/`CT_EMBED` constants once
   nothing references them.
2. Extract `AttachmentService`'s video/embed-URL logic (`resolveVideoDescriptor`,
   `validateEmbedUrl`, `embedFilename`, `VideoDescriptor`) into its own class (e.g.
   `VideoAttachmentResolver`), mirroring how `AttachmentSnapshotService` is already split out.
3. Convert `AttachmentSnapshotRepository`/`AttachmentCleanupService`'s inline `RowMapper` lambdas
   to `private static final` constants, matching `AttachmentRepository.ROW_MAPPER`.
- Test: `bash scripts/integration-tests.sh --sandbox` for `AttachmentRepositoryTest`,
  `AttachmentServiceTest`, `AttachmentServiceTransactionTest`, `AttachmentSnapshotRepositoryTest`,
  `AttachmentSnapshotServiceTest` + full Playwright `e2e --full --ux` (media upload/YouTube/gallery
  flows exercise this class end-to-end in specs 04/05).

**Batch 121-H — platform-commons: `AttachmentAuditHook` → `AttachmentAuditPort` rename** (do
last, deliberately isolated — the one item in this issue that is an SPI-naming/direction fix
spanning three modules, not a same-module refactor; flag for explicit confirmation before starting
given its blast radius)
1. Rename `AttachmentAuditHook` → `AttachmentAuditPort` in `platform-commons` (interface + package
   stays `attachment.spi`, since `*Port` already lives there for `AttachmentPort`).
2. Rename the implementation `AttachmentAuditHookImpl` → `AttachmentAuditPortImpl` in
   `attachment-spring-boot-starter`, and its Spring bean/`ComponentFactory<AttachmentAuditPort>`
   wiring.
3. Update the one consumer, `AdvertisementEnrichService` (marketplace-app), to inject
   `ComponentFactory<AttachmentAuditPort>` instead of `...Hook`.
4. Update `platform-commons/CLAUDE.md`'s SPI table and `attachment-spring-boot-starter/CLAUDE.md`
   in the same change (also fixes the separate stale-doc finding about `AttachmentMediaChangeHook`
   no longer existing, while both files are already open).
5. Add a `platform-commons/DECISIONS.md` entry recording the rename and why (naming/direction
   mismatch against this module's own SPI table, found by the improvement-121 audit).
6. (Optional, smaller, same batch) Move `UserIdMarker` from `user.security` into `user.spi`, or add
   a one-line note to `platform-commons/CLAUDE.md` documenting `security` as a legitimate fourth
   package role — whichever the implementer judges less disruptive once they're looking at the file.
- Test: full reactor build (`./mvnw install -DskipTests` across all modules, since this is an
  interface rename crossing module boundaries) + `bash scripts/unit-tests.sh` +
  `bash scripts/integration-tests.sh --sandbox` (full suite, not just attachment) + Playwright
  `e2e --full --ux` (Activity-tab media rendering depends on this port through
  `AdvertisementEnrichService`).

## Related

- [improvement-119](../completed/issues/improvement-119-f02-city-dictionary-geo-filter.md) — the
  F-02 city feature whose implementation prompted this review; its own two real bugs
  (`AdvertisementActivityFieldsHookImpl.labelFor()` missing a `cityTaxonId` case,
  `AdvertisementService.findById()` dropping `categoryNames`/`cityName` on every edit-save card
  refresh) were already found and fixed directly, not deferred here — see
  `marketplace-app/DECISIONS.md` ADR-065's "Update (2026-07-25, post-implementation SOLID/DRY
  review)" section for details.
