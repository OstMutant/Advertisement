# Feature: Category IDs in Snapshot + Deleted Category Display

## Goal

Replace `categoryName` string in `CategoryChangeSnapshotDto` with a list of IDs.
Rename to `AdvertisementCategoriesChangeSnapshotDto` — one record per save, not one per category.
Show soft-deleted categories with strikethrough in advertisement view; exclude from picker on create/edit.

---

## Problem

Current `CategoryChangeSnapshotDto` stores `categoryName` (a string) and one record per
assigned/unassigned category. Problems:

- Name is a snapshot of the name at the time of change — if the category is renamed, history
  shows the old name with no way to resolve it
- One record per category → multiple hidden audit entries per save
- Locale problem: `categoryName` is in a single language (whichever locale was active at save time)

---

## Proposed Design

### `AdvertisementCategoriesChangeSnapshotDto`

```java
public record AdvertisementCategoriesChangeSnapshotDto(
    List<Long> categoryIds   // IDs of all assigned categories at the time of save
) implements AuditableSnapshot {
    @Override public boolean isVisible() { return false; }
}
```

One record per save — full category ID set, not a diff.

### Category name resolution

IDs are resolved to names at read time via `TaxonPort`.
Add a short-lived cache (e.g. `Map<Long, TaxonDto>` per request or Caffeine with short TTL)
to avoid N+1 queries when rendering the timeline.

### Deleted category display

- In advertisement view (assigned categories list): if a category is soft-deleted,
  show its name with CSS strikethrough (`text-decoration: line-through`)
- In create/edit picker (MultiSelectComboBox): exclude soft-deleted categories —
  they are not available for new assignments
- Existing assignments to deleted categories remain valid until explicitly removed

---

## Scope

| Area | Change |
|------|--------|
| `platform-commons/.../taxon/dto/CategoryChangeSnapshotDto.java` | Rename + redesign → `AdvertisementCategoriesChangeSnapshotDto` with `List<Long> categoryIds` |
| `taxon-spring-boot-starter` | Adjust `TaxonAuditHook` impl — emit one snapshot with full ID set instead of per-category records |
| `marketplace-app` — category name cache | Add cache for `TaxonPort.getById()` lookups |
| `marketplace-app` — advertisement view | Show deleted category names with strikethrough |
| `marketplace-app` — category picker | Filter out soft-deleted categories from MultiSelectComboBox |

---

## Open Questions

- Cache scope: per-request (simple) vs shared Caffeine cache (more efficient for high traffic)?
- When a deleted category is shown with strikethrough — tooltip explaining it's deleted?

---

## Status

Implemented differently than proposed here, but the goal is met: instead of renaming
`CategoryChangeSnapshotDto` → `AdvertisementCategoriesChangeSnapshotDto`, the
`advertisement-snapshot-redesign` feature deleted the old DTO entirely and folded
`categoryIds` directly into `AdvertisementSnapshotDto` — one snapshot record per save, as
intended here.

- Name resolution at read time (including soft-deleted) — done, via
  `AdvertisementEnrichService.resolveCategoryNames()` (bulk `listAllByType(..., includeDeleted=true)`,
  filtered client-side — no separate cache needed, avoids N+1 without one).
- Category picker excludes soft-deleted — done (`getAllByType()` filters to active only).
- Deleted category strikethrough in advertisement view — **not done**, tracked in
  `features/issues/improvement-008-deleted-category-strikethrough.md`.
- `TaxonPort.findByIds` bulk method — **not done**, tracked in
  `features/issues/improvement-007-taxon-findbyids-and-snapshot-captureandgetid.md`.
