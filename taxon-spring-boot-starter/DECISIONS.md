# taxon-spring-boot-starter — Decisions

## 1. Filter resolves through `Set<Long>`, not SQL JOIN

**Decision:** `TaxonPort.findEntityIdsWithAnyTaxon(entityType, taxonIds)` returns a `Set<Long>` of matching entity ids. Marketplace pushes this set into its existing `advertisementIds` filter clause — no SQL JOIN between marketplace tables and starter tables.

**Why:** Keeps `AdvertisementRepository` SQL free of starter-owned table names (`taxon_assignment`, `taxon_translation`). If the starter is removed from the classpath, marketplace SQL remains valid and the build succeeds. A direct JOIN would couple marketplace SQL to starter schema, breaking compile-time decoupling.

**How to apply:** Any new filtering that involves taxon data must follow the same pattern — resolve to a `Set<Long>` of entity ids first, then hand off to the caller's existing id-restriction clause.

**Trigger to revisit:** If a single category routinely resolves to >10k advertisement ids in production (log a `WARN` in `AdvertisementService` when `resolvedIds.size() > 10_000`), or if multi-category AND-semantics is requested, consider switching to a starter-owned JOIN helper or a pre-computed materialised mapping.
