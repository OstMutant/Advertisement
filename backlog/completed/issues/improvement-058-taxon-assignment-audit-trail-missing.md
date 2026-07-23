# improvement-058: Timeline tab shows raw category IDs instead of names (Activity tab already resolves them)

**Type:** bug fix — inconsistency between two audit-rendering paths that both exist in code today;
one resolves category names, the other doesn't. Rewritten 2026-07-17 after direct code tracing
disproved the original "TaxonAuditHook was never implemented" framing as the actual user-facing
problem.
**Module:** `marketplace-app` (`AdvertisementEnrichService`), `platform-commons` (`TaxonPort`,
`TaxonAuditHook` — see audit findings below).
**Priority:** medium — real, user-visible bug (admins see meaningless numeric IDs in one of the
two places category changes are shown), no data-loss risk, small and precisely scoped fix.
**When:** independent, no blockers.

## Resolution (2026-07-17) — implemented, pending Playwright verification

Both the fix and the full audit-findings recommendation below have been implemented:
- `AuditTimelineItemDto` (`platform-commons`) gained a typed `prevSnapshotData` field, populated
  in `AuditReadService.toTimelineItem()` from `AuditLogProjection.prevSnapshot()` (already
  available, previously unused). `AdvertisementEnrichService.mergeMediaChanges()` now resolves
  category names the same fully-typed way `enrichActivityItems()` already did — no string parsing
  of diff values, both paths share one `resolveCategories()` helper.
- `resolveCategoryNames()` switched from `TaxonPort.listAllByType()` (scan-all) to
  `TaxonPort.findByIds()` (targeted batch lookup) as suggested below.
- `TaxonAuditHook` removed entirely (interface + all call sites), per the audit findings'
  recommendation. `TaxonPort.assign()`/`unassign()`/`findByCode()` removed (zero callers), along
  with `TaxonService.findByCode()` and `TaxonRepository.findByTypeAndCode()` (the latter's
  regression test, `TaxonRepositoryTest.findByTypeAndCode_*`, removed too — a deliberate choice to
  finish the cleanup rather than leave the method half-removed).
- `ChangeEntry` (`platform-commons`) gained a `replaceIfField()` default method, consolidating the
  one unavoidable `instanceof FieldChange` check into a single place in the codebase, used by
  `resolveCategories()` instead of a hand-rolled pattern match.
- All documentation referencing `TaxonAuditHook` corrected across `CLAUDE.md`, `docs/architecture/`
  (02-spi-map, 03-bounded-contexts, 05-sequence-diagrams, README), `marketplace-app/DECISIONS.md`
  (ADR-019 resolution note + new ADR-043), `platform-commons/DECISIONS.md` (ADR-017 note),
  `taxon-spring-boot-starter/CLAUDE.md` and `DECISIONS.md` (ADR-004 marked Superseded).

**Not yet done:** full test/build verification (`scripts/unit-tests.sh`) and Playwright
verification that the Timeline tab actually renders resolved category names in the browser. Issue
stays open until both are confirmed green.

## Problem — precisely, traced end to end

Every advertisement save (`AdvertisementSaveService.save()`, lines 47-58) unconditionally captures
an audit snapshot via `AuditPort.captureUpdate()`/`captureCreation()`, and
`AdvertisementSnapshotDto.diff()` already produces a `FieldChange("categoryIds", ...)` whenever the
category set changes — **this part works**: no category change is ever silently lost. The bug is
specifically about how that diff's *values* are rendered in two different UI surfaces:

- **Advertisement's own Activity tab** (inside the entity overlay) — calls
  `AuditReadService.getEntityActivity()` → `AuditActivityEnrichHook.enrichActivity()` →
  `AdvertisementEnrichService.enrichActivityItems()`, which calls `resolveCategoryNames()` +
  `resolveCategories()` (lines 94-126) to replace raw taxon IDs with resolved names in the diff
  before display. **This already works correctly today.**
- **Top-level Timeline tab** (global activity feed) — calls `AuditReadService.getTimelinePage()` →
  `AuditActivityEnrichHook.merge()` → `AdvertisementEnrichService.mergeMediaChanges()` (lines
  35-54), which only merges in attachment/media changes — **it never calls
  `resolveCategoryNames()`/`resolveCategories()` at all.** A category change shown in the Timeline
  tab renders as `Category: 3, 5 → 3, 5, 7` (raw numeric taxon IDs) instead of
  `Category: Electronics, Books → Electronics, Books, Toys`.

This is the entire, precise bug: **one of the two rendering paths (Timeline) is missing a call the
other path (Activity tab) already makes correctly.**

## Suggested fix

Apply the same category-name resolution `mergeMediaChanges()` already applies for media to
category IDs too — either by having it call the existing `resolveCategoryNames()`/
`resolveCategories()` helpers (generalizing their signatures to accept `AuditTimelineItemDto` the
same way they accept `AuditActivityItemDto` today, since both types expose
`snapshotData()`/`prevSnapshotData()`/`changes()`), or by extracting a small shared method both
`mergeMediaChanges()` and `enrichActivityItems()` call.

**Secondary, smaller fix (surfaced by the port audit below):** `resolveCategoryNames()` currently
resolves names via `taxonPortFactory.get().listAllByType(TaxonType.CATEGORY, Locale.ENGLISH,
true)` — fetching and filtering *every* category to find the handful of IDs actually needed.
`TaxonPort.findByIds(Set<Long> taxonIds, Locale locale)` already exists and is exactly the batched
lookup this should use instead — its own Javadoc says "used by audit rendering," but it currently
has zero real callers anywhere in the codebase (verified directly). Switch `resolveCategoryNames()`
to call `findByIds()` instead of `listAllByType()` while fixing the Timeline-tab gap.

## Audit findings — `TaxonAuditHook` and `TaxonPort` (requested 2026-07-17)

Traced every call site of taxon-assignment changes and every `TaxonPort` method's real usage
across `marketplace-app` and `advertisement-spring-boot-starter`:

- **Only two places in the entire codebase ever change a taxon assignment**, both via
  `TaxonPort.replaceAssignments()`: `AdvertisementSaveService.save()` (on every advertisement
  save) and `AdvertisementService.softDelete()`-equivalent path (on advertisement delete, clears
  assignments). Both already happen *inside* an advertisement save/delete that produces its own
  audit snapshot — there is no code path today where a taxon assignment changes independently of
  an advertisement save.
- **`TaxonAuditHook` (SPI, `platform-commons`) has zero implementing classes anywhere in the repo**
  — confirmed, matches the original issue's finding. Given the point above, this hook's
  `onAssignmentChanged(ASSIGNED/UNASSIGNED)` events would only ever fire from inside a flow that
  *already* produces an equivalent, aggregate audit entry (the advertisement's own snapshot diff,
  once the fix above lands and shows resolved names). **Recommendation: remove `TaxonAuditHook`
  entirely** (the SPI interface, and the `auditHook.ifAvailable(...)` call sites in
  `TaxonAssignmentService.assign()/unassign()/replaceAssignments()`) rather than implement it —
  there is no current consumer need for a separate, finer-grained assignment-event trail, and
  building one now would be speculative. If a future entity type gets taxon assignments through a
  path *not* tied to that entity's own save (unlike `ADVERTISEMENT` today), this hook (or
  equivalent) can be reintroduced then, informed by an actual concrete need.
- **Dead `TaxonPort` methods (zero real callers found):** `assign(EntityType, Long, Long)` and
  `unassign(EntityType, Long, Long)` (only `replaceAssignments()` is ever called — the singular
  variants are unused API surface) and `findByCode(TaxonType, String, Locale)`. Recommend removing
  all three, or confirming with a wider search before removal that no near-term planned feature
  needs them (`findByCode` in particular reads like it was added for a "well-known code" lookup
  that may still be wanted — verify before deleting).
- **`findByIds(Set<Long>, Locale)` — zero *current* callers but should be wired up, not removed**
  (see Suggested fix above) — this is the one dead method that turns out to be exactly the right
  tool for the real bug, just never connected.

## Related

- `marketplace-app/DECISIONS.md` ADR-019 — the original decision citing "must be audited"; this
  issue's original framing (based on ADR-019's text) overstated the gap as "not audited at all,"
  when the real, narrower issue is a raw-ID-vs-name display inconsistency between two already-
  working rendering paths. ADR-019 should be corrected to reflect this once the fix lands.
- [feature-002](../completed/issues/feature-002-advertisement-snapshot-redesign.md) —
  `AdvertisementSnapshotDto.categoryIds`, the snapshot mechanism that already captures every
  category change (confirmed working, not part of the bug).
- `platform-commons/DECISIONS.md` — Hook and Port Implementation Rules (relevant if `TaxonAuditHook`
  removal is carried out, since removing an SPI is itself a decision worth recording there).
