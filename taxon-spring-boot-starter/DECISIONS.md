# taxon-spring-boot-starter — Architectural Decisions

## Decision 1 — Filter resolves through `Set<Long>`, not SQL JOIN

**Status:** Accepted

**Context:**
Marketplace needs to filter advertisements by category. The naive approach is a SQL JOIN from `advertisement` to `taxon_assignment`. However, `taxon_assignment` is a table owned by this starter, and marketplace must not reference starter-owned table names directly — that would break compilation when the starter is excluded from the build.

**Decision:**
`TaxonPort.findEntityIdsWithAnyTaxon(EntityType, Set<Long> taxonIds)` returns a `Set<Long>` of matching entity ids in-memory. Marketplace pushes this set into the existing `advertisementIds` filter clause in `AdvertisementRepository`, which already supports id-set filtering. The repository SQL never mentions `taxon_assignment`.

**Consequences:**
- Decoupling is preserved: removing the starter from `marketplace-app/pom.xml` leaves `AdvertisementRepository` fully intact.
- The resolved id set is proportional to the number of ads carrying any of the selected categories — not the number of categories. For typical category + ad volumes this is acceptable.

**Trigger to revisit:**
If a single category routinely resolves to >10 000 advertisement ids in production (measure with a `WARN` log line in `AdvertisementService.list` when `resolvedIds.size() > 10_000`), or if multi-category AND-semantics is ever requested, switch to a starter-owned JOIN helper or a pre-computed materialised mapping.
