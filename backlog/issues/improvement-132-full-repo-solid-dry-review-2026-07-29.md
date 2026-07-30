# improvement-132: repo-wide SOLID/DRY/KISS review findings (merged, 2026-07-29)

**Type:** improvement — mixed: one genuine live bug (item 1), the rest are DRY/scalability/governance
tech-debt with no currently-triggered failure.
**Module:** `query-lib`, `advertisement-spring-boot-starter`, `attachment-spring-boot-starter`,
`audit-spring-boot-starter`, `taxon-spring-boot-starter`, `user-spring-boot-starter`,
`platform-commons`, `marketplace-app`, `integration-tests` — all 9 modules.
**Priority:** 🟡 medium-high — driven by item 1, a genuinely live, currently-triggered i18n bug;
everything else in this batch is 🔵 (no live bug, structural/scalability/governance debt).
**When:** independent, no blockers.

## How this was found, and why this supersedes `improvement-121`

Run via `.claude/skills/deep-review/` (full mode, all 9 modules, no module argument). One read-only
subagent per module, cross-checking its own `CLAUDE.md`/`DECISIONS.md` first. Every fresh candidate
was then re-verified by a **separate, fresh** validation subagent reopening the real current file
from scratch.

This issue **merges and replaces `improvement-121`** (2026-07-25's 11-agent pass, now moved to
`backlog/completed/issues/` as superseded). Rather than leave two overlapping tracking documents,
every one of `improvement-121`'s 24 original findings was individually re-verified against current
code before merging — some agent-based (attachment/audit/taxon-starter sections), the remainder
done directly (query-lib items 2-4, platform-commons, user-spring-boot-starter, marketplace-app —
the dedicated validation subagents for these hit a session usage limit mid-run, so verification
continued via direct file reads with the same evidence-first discipline, not by relaying the
original claims). Outcome: **18 of 24** original findings are still accurate and are folded into
the module sections below (each marked `[from improvement-121]`); **6 were resolved as follows**
and are *not* carried forward:

| Original item | Verdict | Why |
|---|---|---|
| query-lib #1 (`SqlFilterBuilder.toParams()` duplicate-key crash) | **Merged, not duplicated** | This run's own query-lib agent independently re-derived the exact same finding — see query-lib section below, cited as one item with both origins. |
| query-lib #2 (three different null policies across `OrderByBuilder`/`PaginationSqlBuilder`/`SqlFilterBuilder`) | **Disputed — not a defect** | `Sort`/`Pageable` are framework value types with a legitimate null/unpaged absence state; a filter DTO is always caller-constructed (per this project's "no defensive empty checks" rule). The three-way difference is justified by call-site semantics, not an inconsistency worth normalizing away. |
| audit-starter #1 (extract `AuditLogRepository`'s duplicated CTE into a shared SQL constant) | **Invalid — contradicts a documented decision** | This project deliberately suppresses SQL-literal duplication within a repository class (`@SuppressWarnings("java:S1192")`, "Repository SQL pattern": SQL inlined per-method, text blocks, never extracted into shared constants even when similar). |
| platform-commons #2 (`AttachmentMediaChangeHook` listed as current in `CLAUDE.md`) | **Stale — already fixed** | Both `platform-commons/CLAUDE.md` and `attachment-spring-boot-starter/CLAUDE.md` now correctly state it was "removed entirely, improvement-102 — zero implementations." |
| marketplace-app #1 (`DeleteActionButton`/`EditActionButton`/`ShareActionButton` boilerplate) | **Stale — already fixed** | `BaseActionButton` now exists; all three subclasses already extend it and call a shared `applyConfig(...)` helper. |
| marketplace-app #3 (`SettingsFormModeHandler` reimplementing `buildContentWithActivity()`'s tab-gating) | **Stale — machinery deleted entirely** | `buildContentWithActivity()`/`ActivityTabParams`/the whole tab-based activity pattern were removed today (2026-07-29) during the breadcrumb/nested-activity-overlay redesign (see `marketplace-app/DECISIONS.md` ADR-067). Neither the method nor anything resembling it exists anymore in `AbstractFormOverlayModeHandler.java` or `SettingsFormModeHandler.java`. |

One further correction, not from `improvement-121` but caught the same way: `improvement-121`'s own
`user-spring-boot-starter` item 4 (`applyUserRestore()` "no longer exists") turned out to be
**still accurate and slightly worse than described** — `user-spring-boot-starter/CLAUDE.md` *itself*
currently still describes `UserService.applyUserRestore()` as an existing method reusing
`updateProfile()`, but a repo-wide grep for `applyUserRestore` across every `.java` file returns
zero hits and `UserService.java`'s full public method list has no such method. The doc is
self-contradicting its own module's actual source, not just outdated relative to some external
description.

## Execution batches (added 2026-07-30)

31 findings regrouped into 11 batches — same shape as `BACKLOG.md`'s "one batch = one pass":
items that share a file/module/mechanical-fix pattern ship together. Execution order below is
worst-first by batch; work through them one at a time, checking each batch off here as it lands.

| Batch | Priority | Items | Scope |
|---|---|---|---|
| A | ✅ Done (2026-07-30) | 1, 2 | `AdvertisementService.findById()` i18n live bug (locale hardcoded to English) + stale `CLAUDE.md` line in the same file |
| D | ✅ Done (2026-07-30) | 11, 18, 19 | `View.refresh()` catch-branch consistency across `AdvertisementsView`/`UserView`/`TimelineView` — one pattern, one real user-visible bug (18) |
| B | 🟡 medium | 3, 4, 5, 6 | taxon-starter: unbounded `IN` → `= ANY(:array)` (3, 4, mechanical, proven pattern) + `DefaultTaxonPort` dedup (5) + style fix in same file (6) |
| H | 🟡 medium | 23, 24, 25 | query-lib `SqlCondition`/`SqlFilterBuilder` — duplicate-key guard (23, widest blast radius), shared `applyIfNotEmpty()` (24), Javadoc precision (25) |
| I | 🟡 medium | 26, 27, 28 | attachment-starter: `AttachmentService` SRP split (26) + consolidate video/embed classification onto `AttachmentMediaContentType` (27, related to 26) + RowMapper hoist (28) |
| C | 🔵 low-medium | 7, 8, 9, 10 | user-starter: doc fix (7), `UserDto.from(User)` factory (8), `@NonNull` sweep (9), informational SRP note (10, no action) |
| F | 🔵 low-medium | 13, 14, 15, 16, 17 | marketplace-app small DRY/`@NonNull`: `thumbSrc()` dedup (13), triplicated field-copy (14), `@NonNull` on buttons/fields (15), dead `BaseDialog.buildLayout()` (16), `AccessEvaluator` dedup (17) |
| J | 🔵 low | 29, 30 | audit-starter: `@NonNull` sweep + `RowMapper` hoist |
| K | 🔵 low-medium | 31 | integration-tests: dedup `TestConfig` `@ImportAutoConfiguration` array across 4-5 test classes |
| G | 🔵 low | 20, 21, 22 | platform-commons governance: DTO boundary (20), `AttachmentAuditHook`→`*Port` rename (21, spans attachment-starter + marketplace-app, do last within this group), undocumented `security` package role (22) |
| E | 🔵 low (needs a decision) | 12 | `TaxonFormOverlayModeHandler`/`CityFormOverlayModeHandler` — pure duplication, but fixing it means picking an approach (shared prototype-scoped base vs. extending ADR-065's stated exception) rather than a mechanical edit |

**Suggested execution order:** A → D → B → H → I → C → F → J → K → G → E.

## Findings, grouped by module, worst-first within each group

### advertisement-spring-boot-starter

1. **[HIGH — live bug] `AdvertisementService.findById()` hardcodes `Locale.ENGLISH` for taxon
   enrichment; `getFiltered()` respects the caller's actual locale.**
   `advertisement-spring-boot-starter/src/main/java/org/ost/advertisement/services/AdvertisementService.java:175-180`:
   ```java
   public Optional<AdvertisementInfoDto> findById(@NonNull Long id) {
       return repository.findAdvertisementById(id)
               .map(dto -> enrichWithTaxons(List.of(dto), Locale.ENGLISH).getFirst())
               .map(dto -> enrichWithActorInfo(List.of(dto)).getFirst())
               .map(dto -> enrichWithMediaSummary(List.of(dto)).getFirst());
   }
   ```
   vs. `getFiltered()` (line 63): `enrichWithMediaSummary(enrichWithActorInfo(enrichWithTaxons(ads, locale)))`
   using the caller-supplied `locale` parameter. **Effect: a Ukrainian (or any non-English) user
   opening an advertisement's detail view sees its category names in English, while the same
   advertisement's card in the list view correctly shows Ukrainian category names.** No overload of
   `findById()` accepting a `Locale` exists. The highest-severity item in this batch.

2. **[LOW — doc-only] `advertisement-spring-boot-starter/CLAUDE.md:12` is stale.**
   States: *"`AdvertisementService` — create, update, delete, ownership checks; sanitizes HTML
   description via OWASP HTML Sanitizer; wires category assignments through `TaxonPort` via
   `ComponentFactory`"* — but `save()`/`buildEntity()` never touch `taxonPortFactory`; the only
   write-side `TaxonPort` use in this service is `delete()` clearing assignments
   (`replaceAssignments(EntityType.ADVERTISEMENT, id, Set.of())`, line 204).

### taxon-spring-boot-starter

3. **[MEDIUM — scalability] `TaxonRepository.findByIds()`/`findExistingIds()` still use an
   unbounded `IN (:ids)` clause** — the exact pattern `improvement-067`/`improvement-054` fixed
   elsewhere in this same module. `TaxonRepository.java:88-97` (`findByIds`, SQL line 92) and
   `:120-125` (`findExistingIds`, SQL line 121). `findByIds()` backs `DefaultTaxonPort.indexById()`,
   called on every `getForEntity()`/`getForEntities()`.

4. **[MEDIUM — scalability, same class as #3] `TaxonAssignmentRepository
   .findEntityIdsByTaxonIds()`/`countByTaxonIds()` — same unbounded `IN (:taxonIds)` pattern**, in
   the same file whose own `findAllByEntities()` (line 91) already uses `= ANY(:array)`.
   `findEntityIdsByTaxonIds()` (lines 100-111, SQL line 104), `countByTaxonIds()` (lines 120-132,
   SQL line 124).

5. **[from improvement-121, MEDIUM — DRY] `DefaultTaxonPort.resolveDtos()`/`buildDtoIndex()` are
   near-duplicate** — both call `indexById()`, both group translations the same way, both apply the
   same `activeOnly` filter and `toDto` mapping; only the terminal collector (`List` vs `Map`)
   differs. Extract a shared `buildDtoLookup(...) -> Map<Long, TaxonDto>`, make `resolveDtos()` a
   thin ordering wrapper over it.

6. **[from improvement-121, LOW — style] `TaxonAssignmentRepository.countByTaxonIds()` uses a
   fully-qualified `java.util.stream.Collectors.toMap(...)`** instead of the imported `Collectors`
   every sibling method in the file uses.

### user-spring-boot-starter

7. **[from improvement-121, MEDIUM — doc/code mismatch, worse than originally described]
   `user-spring-boot-starter/CLAUDE.md` documents `UserService.applyUserRestore()` as an existing
   method, but it does not exist anywhere in current source.** Repo-wide grep for
   `applyUserRestore` across every `.java` file: zero hits. `UserService.java`'s full public method
   list (16 methods) has no restore-specific method. The doc doesn't just describe old behavior — it
   actively misdescribes the module's own current `updateProfile()`-based restore path.

8. **[from improvement-121, LOW-MEDIUM — DRY] `User → UserDto` field-by-field mapping is duplicated
   verbatim in `UserService.toDto()` (lines 194-197) and `UserPrincipal.toUserDto()` (lines
   17-20)** — same 8 fields, same order, no shared factory. Extract `UserDto.from(User)`.

9. **[from improvement-121, LOW — `@NonNull`/fail-fast rule] `RoleChecker`/`OwnershipChecker`
   public methods take `UserDto`/`Long`/`UserIdMarker` params with no `@NonNull`, defensively
   null-check instead** (`RoleChecker.hasRole()`, line 18-19: `user != null && user.role() == role`)
   — every other class in this module uses `@NonNull` consistently; these two are the outliers.

10. **[from improvement-121, LOW — informational, not urgent] `UserService` spans five distinct
    concerns** (query/listing, profile CRUD, registration+rate-limiting, security-context refresh,
    retention/cleanup) — confirmed still true, `cleanup()` still depends on `AdvertisementPort`
    (`UserService.java:9,61`) for its cross-domain check. Not yet unmanageable; the clearest future
    split point if the class grows further.

**Also confirmed still live, not re-filed here (see `improvement-052`):** this run's own
user-spring-boot-starter reviewer independently re-derived the exact same first-admin-registration
TOCTOU race already tracked as
[improvement-052](improvement-052-first-admin-registration-toctou-race.md) — confirms that issue is
accurate, not stale. A related, smaller, non-atomic check-then-increment race in the same
`register()` method's Caffeine-based rate limiter was also noted but judged too low-severity to
file separately (soft, best-effort limiter; worst case is a couple of extra failed attempts let
through) — noted here for the record only.

### marketplace-app

11. **[MEDIUM — real behavior gap] `AdvertisementsView.refresh()` is missing the
    `ConstraintViolationException` catch branch that `.claude/rules.md`'s View Pattern template
    mandates and that the sibling `UserView.refresh()` actually implements.**
    `AdvertisementsView.java`'s `refresh()` (lines 192-227) has only `catch (Exception ex)` (line
    221) — no `ConstraintViolationException` branch, no `showValidationErrors()` call anywhere in
    the file. `UserView.java`'s `refresh()` has both (lines 118-130).

12. **[MEDIUM — DRY, ADR-065's stated exception doesn't actually cover this]
    `TaxonFormOverlayModeHandler.java`/`CityFormOverlayModeHandler.java` are a pure mechanical
    rename of each other** — every method, field, and control-flow path matches line-for-line.
    `marketplace-app/DECISIONS.md` ADR-065's stated reason for not sharing a generic base
    (`TaxonOverlay` is a `@UIScope` singleton needing distinct bean instances per simultaneous tab)
    applies to the `*Overlay` classes, not to these two `@Scope("prototype")` handlers, which by
    construction get a fresh instance per lookup regardless of parameterization. Real risk already
    realized per ADR-065/066's own "fixed in one copy, not the sibling" bug history.

13. **[from improvement-121, LOW-MEDIUM — DRY] `AttachmentThumbnail.thumbSrc()` (lines 83-89) and
    `CardLightboxStrip.thumbSrc()` (lines 35-41) duplicate identical YouTube/video/plain-url
    branching logic**, differing only in unpacked-params vs. DTO-accessor access. Extract to a
    shared static helper in `ui/views/utils/`.

14. **[LOW-MEDIUM — DRY, proven bug-prone] `AdvertisementFormOverlayModeHandler.java` repeats the
    same 5-field copy operation (`title`/`description`/`adKind`/`categoryIds`/`cityTaxonId`)
    inline, three times** — `loadRestored()` (259-265), `discardChanges()` create-mode (290-296),
    `discardChanges()` edit-mode (305-311). No shared helper exists; `TaxonFormOverlayModeHandler
    .copyLocaleFields()` is the established precedent shape for the fix. Already the source of at
    least two past bugs when a field (`cityTaxonId`, `adKind`) was added and one copy site was
    missed.

15. **[from improvement-121, LOW — `@NonNull` rule, widespread] Public constructors across
    `ui/views/components/buttons/`, `fields/`, and `dialogs/` carry no `@NonNull`** (e.g.
    `UiIconButton`, `UiPrimaryButton`, `UiTertiaryButton` constructors all take unconstrained
    `String`/`Icon` params) — inconsistent, since `AttachmentLightbox`/`AuditActivityPanel`/
    `CardMediaLightbox` do follow the rule correctly.

16. **[from improvement-121, LOW — dead code] `BaseDialog.buildLayout()` (no-arg overload, empty
    body, line 7) is never called** — repo-wide grep for `.buildLayout()` call sites finds none;
    only the `buildLayout(DialogLayout)` overload is used.

17. **[from improvement-121, LOW — DRY] `AccessEvaluator.canNotEdit`/`canNotDelete`/`canOperate`
    are each duplicated verbatim for `UserIdMarker` and `Long` overloads** (6 methods, 3 pairs) —
    `canOperate(UserIdMarker)` (lines 43-47) and `canOperate(Long)` (lines 57-61) have near-identical
    bodies, neither delegates to the other.

18. **[from improvement-121, LOW-MEDIUM — real bug] `UserView.refresh()`'s
    `ConstraintViolationException` branch (lines 121-125) never calls `refreshButton.setVisible
    (false)` the way the success path does (line 118)** — a stale "changes available" icon can be
    left visible after a validation error clears the grid.

19. **[from improvement-121, LOW — consistency] `TimelineView.refresh()` has only a single generic
    `catch (Exception ex)` (line 111), not the two-tier `ConstraintViolationException`/`Exception`
    shape `.claude/rules.md`'s View Pattern documents** — likely harmless in practice if `AuditPort`
    never throws that exception, but undocumented as a deliberate exception rather than an
    oversight.

### platform-commons

20. **[LOW — governance boundary] `SettingsSnapshotDto.from(UserSettingsDto settings)` reaches into
    a *different* record's fields to construct itself**, exceeding `platform-commons/CLAUDE.md`'s
    documented `*.dto` exception ("a pure derivation over a `*.dto` record's own fields... do not
    stretch this to anything that... produces a different DTO type"). `SettingsSnapshotDto.java:32-34`.

21. **[from improvement-121, LOW — SPI naming/direction mismatch] `AttachmentAuditHook`'s own
    Javadoc says "Hook: marketplace → attachment-starter"** (line 10) — but per this module's own
    `*Port`/`*Hook` naming table, that call direction (marketplace calls into the starter) is the
    `*Port` semantic, not `*Hook` (`*Hook` = starter calls back into marketplace). Confirmed:
    `AttachmentAuditHookImpl` is implemented in the starter, called from marketplace's
    `ActivityEnrichHookImpl` (Javadoc line 12) — marketplace is the caller, the starter the
    implementor, exactly the `*Port` direction. Rename to `AttachmentAuditPort`, or add a
    `DECISIONS.md` entry explicitly carving out this one naming exception.

22. **[from improvement-121, LOW — undocumented package role] `UserIdMarker` sits in
    `user.security`**, a package role `platform-commons/CLAUDE.md`'s package-semantics section
    doesn't define (only `api`/`spi`/`dto` are documented). Either move it into `user.spi` or
    document `security` as a fourth legitimate role.

### query-lib

23. **[MEDIUM — defensive/diagnostic gap, widest blast radius in the codebase; independently
    re-derived by this run, same as `improvement-121`'s own query-lib item 1]
    `SqlFilterBuilder.toParams()` throws an opaque `IllegalStateException` on a duplicate
    `filterProperty`, with no construction-time guard anywhere to prevent it.**
    `SqlFilterBuilder.java:48`: `Collectors.toMap(SqlCondition::filterProperty, SqlCondition::value)`
    — no merge function. Neither `SqlBoundFilter`'s nor `SqlFilterBuilder`'s constructor validates
    `filterProperty` uniqueness across bindings. Not currently triggered (no live collision found in
    any repository's actual `FILTER` constant), but every repository in the app depends on this
    class.

24. **[from improvement-121, LOW — DRY] `inSet()`/`anyOf()` each hand-roll the same
    `CollectionUtils.isEmpty(values) ? null : ...` guard** (`SqlCondition.java:68-83`) instead of a
    shared `applyIfNotEmpty(...)` helper, unlike the scalar factories (`equalsTo`/`after`/`before`),
    which already share one `applyIfPresent(...)` (lines 37-66, 85-92).

25. **[from improvement-121, LOW — doc precision] `SqlCondition`'s class Javadoc (lines 11-15) says
    factories return `null` "when the value is absent"** — true for scalar factories, imprecise for
    `inSet`/`anyOf`, which return `null` for absent *or empty* (`CollectionUtils.isEmpty`, lines
    70/79).

### attachment-spring-boot-starter

26. **[from improvement-121, MEDIUM — SRP] `AttachmentService` has grown into five distinct
    concerns with no internal separation**: gallery queries, upload/video-ingestion,
    commit/restore orchestration, snapshot delegation, media-summary DTO shaping. Extract the
    video/embed-URL logic into its own class, mirroring how `AttachmentSnapshotService` is already
    split out.

27. **[from improvement-121, MEDIUM — DRY] "Is this a video/embed" classification is duplicated in
    three independent, non-identical forms**: `AttachmentService.isVideo()` (own `CT_YOUTUBE`/
    `CT_EMBED` constants), `AttachmentMediaContentType.isEmbedded()` (the shared authority
    elsewhere), `AttachmentSnapshotService.filename()`'s inline `YoutubeUtil.extractId(url) != null`
    check (narrower — doesn't even check `EMBED`, only YouTube). Consolidate onto
    `AttachmentMediaContentType`.

28. **[from improvement-121, LOW — repository convention, location corrected 2026-07-29] Inline
    `RowMapper` lambdas not hoisted to `private static final` constants.** Originally attributed to
    `AttachmentSnapshotRepository`/`AttachmentCleanupService` — re-verified: the second offender is
    actually `AttachmentRepository.java` itself (3 more inline `(rs, _) -> ...` lambdas beyond its
    own correctly-hoisted `ROW_MAPPER` constant); `AttachmentCleanupService` declares no `RowMapper`
    lambdas of its own. `AttachmentSnapshotRepository.java:58,65` also affected.

### audit-spring-boot-starter

29. **[from improvement-121, LOW-MEDIUM — `@NonNull` rule] `AuditReadService`, `AuditLogRepository`,
    `AuditCleanupService` have zero `@NonNull` annotations on parameters that must not be null**
    (`entityType`, `entityId`, `filter`, `snapshotData`, ...), unlike `DefaultAuditPort` in the same
    module.

30. **[from improvement-121, LOW — repository convention] `AuditLogRepository
    .getSnapshotContent()`'s `snapshotContentMapper()` builds a new `RowMapper` lambda per call**
    instead of a `private static final` field (captures `objectMapper` — needs a small static nested
    mapper class instead, the way `ProjectionMapper` already does it in the same file).

### integration-tests

31. **[LOW-MEDIUM — DRY, crosses the module's own documented extraction threshold]
    `AuditLogRepositoryTest`/`AttachmentRepositoryTest`/`AttachmentSnapshotRepositoryTest`/
    `AttachmentServiceTransactionTest` each declare a byte-identical local `TestConfig` with the
    same 8-class `@ImportAutoConfiguration` array**, instead of factoring it out into a small shared
    constant. `integration-tests/CLAUDE.md`'s own convention ("extract once ≥2 consumers need the
    same helper") is crossed 4x over. `RepositoryTestSupport.java:66-75` carries a near-identical
    7-class list (missing only `ConfigurationPropertiesAutoConfiguration.class`) — the array is
    effectively duplicated 5 times total.

## Suggested fix

Work through worst-first per module, same "small and independently shippable" shape as the original
`improvement-121`:

- **Item 1 (live i18n bug)** — smallest real fix in this batch: give `findById()` a `Locale`
  parameter and thread the caller's actual locale through, matching `getFiltered()`'s shape.
- **Items 3, 4 (unbounded IN, taxon)** — mechanical, `IN (:set)` → `= ANY(:array)`, same shape
  already proven twice in this codebase.
- **Item 5 (`DefaultTaxonPort` duplication)** — extract `buildDtoLookup(...)`, make `resolveDtos()`
  a thin wrapper.
- **Items 7-10 (user-starter)** — item 7 (doc fix) and item 9 (`@NonNull` + drop defensive checks)
  are quick wins; item 8 (`UserDto.from(User)` factory) is small; item 10 is informational only.
- **Item 11 (missing catch branch)** — copy `UserView`'s existing pattern into
  `AdvertisementsView.refresh()` verbatim.
- **Item 12 (Taxon/City handler duplication)** — largest single item in the batch; needs a
  deliberate decision (extract a shared prototype-scoped base, or explicitly extend ADR-065's
  reasoning to cover these classes too) rather than a mechanical fix.
- **Item 14 (triplicated copy block)** — extract `copyEditFields(...)`, mirroring
  `copyLocaleFields()`'s shape.
- **Items 16-19 (marketplace-app smaller items)** — each independently small; item 18 is the one
  with real (if minor) user-visible impact, worth prioritizing over 16/17/19.
- **Items 20-22 (platform-commons)** — item 20 (DTO boundary) needs a mapper-class home; item 21
  (Hook→Port rename) is the largest single item here, spans 3 modules, do deliberately and last
  within this group; item 22 is a one-line doc-or-move decision.
- **Item 23 (`SqlFilterBuilder` collision)** — add an explicit merge function or constructor-time
  uniqueness validation.
- **Items 26-28 (attachment-starter)** — item 26 (extract video/embed class) and 27 (consolidate
  onto `AttachmentMediaContentType`) are related, do together; item 28 is a smaller follow-on.
- **Items 29-30 (audit-starter)** — both small, `@NonNull` sweep + `RowMapper` hoist.
- **Item 31 (integration-tests config duplication)** — extract the shared array into a constant or
  minimal shared `@TestConfiguration`, reconciling the one-class discrepancy with
  `RepositoryTestSupport`.
- **Items 2, 6, 13, 15, 24, 25 (remaining low-severity/doc items)** — free wins alongside whichever
  code change next touches each respective file.

## Related

- [improvement-121](../completed/issues/improvement-121-solid-dry-review-findings.md) — superseded
  by this issue (see "How this was found" above for the full reconciliation).
- [improvement-052](improvement-052-first-admin-registration-toctou-race.md) — re-confirmed still
  accurate during this run (user-spring-boot-starter), not re-filed here.
- [improvement-067](../completed/issues/improvement-067-taxontranslationrepository-unbounded-in-clause.md),
  [improvement-054](../completed/issues/improvement-054-unbounded-in-clause-taxon-assignment-attachment.md)
  — the prior fixes items 3/4 above extend.
- `marketplace-app/DECISIONS.md` ADR-065 (Taxon/City mirroring rationale, item 12), ADR-067
  (breadcrumb/activity-overlay redesign that made the original marketplace-app item 3 stale).
