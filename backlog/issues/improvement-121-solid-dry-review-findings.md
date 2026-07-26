# improvement-121: repo-wide SOLID/DRY review findings (11-agent pass, 2026-07-25)

**Type:** improvement — code-quality/tech-debt cleanup, no live user-facing bug (the two real bugs
this same review found were already fixed directly in improvement-119's cleanup commit).
**Module:** `attachment-spring-boot-starter`, `audit-spring-boot-starter`, `platform-commons`,
`query-lib`, `taxon-spring-boot-starter`, `user-spring-boot-starter`, `marketplace-app` (several
packages).
**Priority:** medium — no live bug, but one item (query-lib) is a latent crash risk shared by every
repository in the app.
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

1. `AuditLogRepository.findRows()`/`findTimeline()` both inline the identical 6-line `numbered AS
   (...)` CTE verbatim — extract into a `private static final String` constant, matching this
   class's own `FILTER`/`SORT_ALIASES` convention.
2. `AuditReadService`, `AuditLogRepository`, `AuditCleanupService` have zero `@NonNull` annotations
   on parameters that must not be null (`entityType`, `entityId`, `filter`, `snapshotData`, ...),
   unlike `DefaultAuditPort` in the same module — inconsistent with this project's own `@NonNull`
   rule.
3. `AuditLogRepository.getSnapshotContent()`'s `snapshotContentMapper()` builds a new `RowMapper`
   lambda per call instead of a `private static final` field (captures `objectMapper`, which is
   likely why it wasn't hoisted — needs a small static nested mapper class instead, the way
   `ProjectionMapper` already does it).

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

## Related

- [improvement-119](../completed/issues/improvement-119-f02-city-dictionary-geo-filter.md) — the
  F-02 city feature whose implementation prompted this review; its own two real bugs
  (`AdvertisementActivityFieldsHookImpl.labelFor()` missing a `cityTaxonId` case,
  `AdvertisementService.findById()` dropping `categoryNames`/`cityName` on every edit-save card
  refresh) were already found and fixed directly, not deferred here — see
  `marketplace-app/DECISIONS.md` ADR-065's "Update (2026-07-25, post-implementation SOLID/DRY
  review)" section for details.
