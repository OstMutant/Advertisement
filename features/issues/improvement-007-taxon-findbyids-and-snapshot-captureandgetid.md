# improvement-007: TaxonPort.findByIds bulk lookup + AttachmentSnapshotService.captureAndGetId

**Type:** improvement — follow-up gaps from advertisement-snapshot-redesign + category-ids-in-snapshot
**Module:** platform-commons + taxon-spring-boot-starter + attachment-spring-boot-starter
**Priority:** medium (raised from low) — part A is no longer only a future prerequisite: the
missing bulk lookup causes a real N+1 on the advertisement list hot path (see part C)
**When:** Wave 1 — bundle with the city/geo feature PR (taxon load grows exactly there)

## Problem

Both `advertisement-snapshot-redesign` and `category-ids-in-snapshot` specced a bulk taxon
lookup that was never added:

### A — `TaxonPort.findByIds(Set<Long> ids, Locale locale) → Map<Long, TaxonDto>`

Only `findById(Long, Locale)` (singular) exists. Current category-name resolution in
`AdvertisementEnrichService.resolveCategoryNames()` works around this by calling
`taxonPort.listAllByType(TaxonType.CATEGORY, locale, includeDeleted=true)` and filtering
client-side — functionally correct (includes soft-deleted), but loads every category of the
type on every enrich call instead of just the requested ids.

### B — `AttachmentSnapshotService.captureAndGetId() → Optional<Long>`

Spec called for this alongside the void `capture()` that already exists. Never added.

### C — N+1 taxon loading inside `DefaultTaxonPort` (found during app review, 2026-07-03)

`DefaultTaxonPort.resolveDtos()` and `buildDtoIndex()` (`DefaultTaxonPort.java:185,196`) fetch
each taxon row with an individual `taxonService.findById(id)` call inside a stream:

```java
return taxonIds.stream()
        .map(id -> taxonService.findById(id).orElse(null))   // one SELECT per taxon id
        .filter(t -> t != null && (!activeOnly || t.getDeletedAt() == null))
        ...
```

Translations right next to it are already batched via `getTranslationsForMany(taxonIds)` — only
the taxon rows themselves are loaded one-by-one. These helpers back `getForEntity()` /
`getForEntities()`, which run on the advertisement list hot path (`AdvertisementService.
enrichWithCategories()`), so a page of 50 ads with a few categories each issues dozens of
per-row SELECTs on every list render. The same `findByIds` bulk method from part A fixes this:
both helpers should load all taxa with a single `IN (:ids)` query.

## Why deferred

Neither gap currently blocks anything — `resolveCategoryNames()` already produces correct
results, and nothing calls `captureAndGetId()` yet. Both are prerequisites for the still-future
`AdvertisementTimelineEnrichService` (bulk timeline-page data loading), which the original
`advertisement-snapshot-redesign` spec explicitly deferred until after the core refactor.

## Suggested fix

1. Add `Map<Long, TaxonDto> findByIds(Set<Long> ids, Locale locale)` to `TaxonPort`
   (`platform-commons/.../taxon/spi/TaxonPort.java`), implemented in `DefaultTaxonPort` via a
   single `IN (:ids)` query including soft-deleted rows.
2. Rewrite `DefaultTaxonPort.resolveDtos()` and `buildDtoIndex()` on top of the same bulk
   query — removes the N+1 from part C without touching callers.
3. Add `Optional<Long> captureAndGetId(...)` to `AttachmentSnapshotService`, mirroring the
   existing `capture()` signature.
4. Only then build `AdvertisementTimelineEnrichService` for bulk timeline-page loading.
