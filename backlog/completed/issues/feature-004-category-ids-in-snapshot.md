# feature-004: Category IDs in Snapshot + Deleted Category Display — ✅ DONE (implemented differently than proposed)

**Type:** feature — audit snapshot design, condensed from the original
`category-ids-in-snapshot/SPEC.md` (pre-issue-file convention).
**Module:** `platform-commons`, `taxon-spring-boot-starter`, `marketplace-app`.
**Status:** goal met, but implemented as part of a larger redesign rather than as proposed here.

## Problem (as originally scoped)

`CategoryChangeSnapshotDto` stored a `categoryName` string, one record per assigned/unassigned
category per save: the name froze at save-time in a single locale (unresolvable if the category
was later renamed), and one record per category meant multiple hidden audit entries per save.

## Original proposal vs. what actually shipped

This issue proposed renaming `CategoryChangeSnapshotDto` → `AdvertisementCategoriesChangeSnapshotDto`
with a `List<Long> categoryIds` field, resolved to names at read time via a request-scoped or
Caffeine cache. What actually shipped, via
[feature-002](feature-002-advertisement-snapshot-redesign.md) (advertisement-snapshot-redesign):
`CategoryChangeSnapshotDto` was deleted entirely rather than renamed, and `categoryIds` was folded
directly into `AdvertisementSnapshotDto` — one snapshot record per save, achieving the same goal
(ID-based soft reference, resolved at read time) with one less DTO in the system.

## Resolution

- **Name resolution at read time, including soft-deleted categories** — done, via
  `AdvertisementEnrichService.resolveCategoryNames()` (bulk `listAllByType(..., includeDeleted=true)`,
  filtered client-side). No separate cache was needed — the bulk lookup itself avoids N+1, so the
  cache-scope open question (per-request vs. Caffeine) never had to be answered.
- **Category picker excludes soft-deleted categories** — done, `getAllByType()` filters to active
  only.
- **`TaxonPort.findByIds()` bulk method** — done, shipped via
  [improvement-007](improvement-007-taxon-findbyids-and-snapshot-captureandgetid.md).
- **Deleted-category strikethrough in the advertisement view** — **not done**, still tracked as
  [improvement-008](../../issues/improvement-008-deleted-category-strikethrough.md) (open,
  deferred — batch into any nearby UI-touching PR).

## Related

- [feature-002](feature-002-advertisement-snapshot-redesign.md) — the redesign this feature's goal
  was actually delivered through.
