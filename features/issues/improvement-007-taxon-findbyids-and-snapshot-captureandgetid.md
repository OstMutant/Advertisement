# improvement-007: TaxonPort.findByIds bulk lookup + AttachmentSnapshotService.captureAndGetId

**Type:** improvement — follow-up gaps from advertisement-snapshot-redesign + category-ids-in-snapshot
**Module:** platform-commons + taxon-spring-boot-starter + attachment-spring-boot-starter
**Priority:** low — nothing currently depends on these; needed before AdvertisementTimelineEnrichService

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

## Why deferred

Neither gap currently blocks anything — `resolveCategoryNames()` already produces correct
results, and nothing calls `captureAndGetId()` yet. Both are prerequisites for the still-future
`AdvertisementTimelineEnrichService` (bulk timeline-page data loading), which the original
`advertisement-snapshot-redesign` spec explicitly deferred until after the core refactor.

## Suggested fix

1. Add `Map<Long, TaxonDto> findByIds(Set<Long> ids, Locale locale)` to `TaxonPort`
   (`platform-commons/.../taxon/spi/TaxonPort.java`), implemented in `DefaultTaxonPort` via a
   single `IN (:ids)` query including soft-deleted rows.
2. Add `Optional<Long> captureAndGetId(...)` to `AttachmentSnapshotService`, mirroring the
   existing `capture()` signature.
3. Only then build `AdvertisementTimelineEnrichService` for bulk timeline-page loading.
