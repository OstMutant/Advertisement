# taxon-spring-boot-starter

Auto-configures the taxonomy/reference-data domain — category and city catalog management
(`TaxonType.CATEGORY`/`CITY`) plus the generic entity-to-taxon assignment mechanism other domains
reuse for "this advertisement/provider profile belongs to these categories and this city."

## What it provides

- Catalog CRUD (create/update/soft-delete/restore/list/find) for taxons and their per-locale
  translations, independent of which entities are assigned to them.
- A generic assignment mechanism (`EntityType` + entity id → taxon ids) — not specific to
  advertisements, so `provider-profile-spring-boot-starter` reuses the same table/service shape
  for its own category assignments instead of each domain rolling its own many-to-many table.
- **SPI implementation:** `TaxonPort` (called by `marketplace-orchestrator`'s
  `TaxonCatalogService`/`TaxonLookupService`/`TaxonAssignmentWriteService`).

## Key classes

| Class | Role |
|---|---|
| `TaxonService` | Catalog-side operations — a taxon's own lifecycle and translations, no awareness of what's assigned to it. |
| `TaxonAssignmentService` | Assignment-side operations — which taxon ids are attached to a given `(EntityType, entityId)` pair; the two services stay separate since a catalog change (renaming a category) and an assignment change (tagging one ad) are independent write paths with different callers. |
| `DefaultTaxonPort` | The `TaxonPort` SPI implementation — thin delegation to both services above, the only class outside this starter ever needs to know about. |
| `TaxonRepository`/`TaxonTranslationRepository`/`TaxonAssignmentRepository` | `JdbcClient`-based repositories, one per table (`taxon`/`taxon_translation`/`taxon_assignment`) — `TaxonCrudRepository` handles the trivial `taxon` CRUD, the other two are bespoke-query `@Repository` classes per this project's repository pattern. |

## Schema

Liquibase changelog under `db/*-changelog/`. Tables: `taxon` (the catalog entry itself, typed by
`TaxonType`), `taxon_translation` (per-locale name/description), `taxon_assignment` (the generic
entity↔taxon many-to-many, keyed by `EntityType` + entity id rather than a domain-specific FK).

## Dependencies

- `platform-commons` — `TaxonPort`/`TaxonDto`/`TaxonTranslationDto`/`TaxonSnapshotDto`/`TaxonType`.
- `query-lib` — `SqlFilterBuilder`/`OrderByBuilder` for `TaxonRepository`'s dynamic catalog queries.
- No dependency on any sibling starter — the generic `EntityType`-keyed assignment table is what
  lets `provider-profile-spring-boot-starter` reuse this starter's assignment mechanism without a
  direct starter-to-starter dependency (enforced by the shared `enforce-no-starter-to-starter-deps`
  Maven Enforcer rule).
