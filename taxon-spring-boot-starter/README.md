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
  `TaxonCatalogService`/`TaxonLookupService`/`TaxonAssignmentWriteService`/`EntityExistenceService`,
  and directly by `advertisement-spring-boot-starter`'s `AdvertisementService` and
  `provider-profile-spring-boot-starter`'s `ProviderProfileService` via their own
  `ComponentFactory<TaxonPort>`).

## Data flow

Every operation enters through `DefaultTaxonPort`, the sole `TaxonPort` implementation, which
coordinates two independent services and never touches a repository directly:

- **Catalog write:** `TaxonPort.create`/`update`/`softDelete`/`restore` → `DefaultTaxonPort` →
  `TaxonService`, which saves/updates the `Taxon` row (via `TaxonRepository`, backed by
  `TaxonCrudRepository` for the trivial save/find and bespoke `JdbcClient` SQL for filtered
  listing/soft-delete/restore), upserts its `TaxonTranslation` rows (`TaxonTranslationRepository`),
  and — when an actor id is present — captures a `TaxonSnapshotDto` via the optional
  `ComponentFactory<AuditPort>`.
- **Catalog/assignment read:** `TaxonPort.getForEntity`/`getForEntities`/`getAllByType`/
  `findById`/`findByIds`/`listAllByType` → `DefaultTaxonPort` resolves the raw
  `Taxon`/`TaxonTranslation`/`TaxonAssignment` rows (via `TaxonService` and, for entity-scoped
  lookups, `TaxonAssignmentService`) into locale-aware `TaxonDto`s — the requested locale first,
  falling back to `TaxonProperties.defaultLocale()`, then to any available translation.
  `TaxonPort.getUsageCounts` follows the same read path but returns raw assignment counts
  (`TaxonAssignmentService.countByTaxonIds`) rather than `TaxonDto`s.
- **Assignment write:** `TaxonPort.replaceAssignments` → `DefaultTaxonPort` →
  `TaxonAssignmentService.replaceAssignments`, which diffs the new taxon id set against the current
  one (`TaxonAssignmentRepository.findAllByEntity`) and issues only the resulting `assign`/`unassign`
  calls.

## Schema

Liquibase changelog under `db/taxon-changelog/`. Tables: `taxon` (the catalog entry itself, typed
by `TaxonType`, soft-deletable and optimistically locked via `version`), `taxon_translation`
(per-locale name/description, PK `(taxon_id, locale)`, cascades on `taxon` deletion),
`taxon_assignment` (the generic entity↔taxon many-to-many, PK `(entity_type, entity_id, taxon_id)`,
keyed by `EntityType` + entity id rather than a domain-specific FK). A partial unique index enforces
`(type, code)` uniqueness only among rows that carry a non-null `code`.

## Dependencies

- `platform-commons` — `TaxonPort`/`TaxonDto`/`TaxonSnapshotDto`/
  `TaxonTranslationDto`/`TaxonType` (own SPI contract), plus `AuditPort`/`ComponentFactory`/
  `EntityType` (the shared audit/assignment contracts every domain starter uses).
- `query-lib` — `SqlFilterBuilder`/`OrderByBuilder`/`PaginationSqlBuilder` for `TaxonRepository`'s
  dynamic, paginated catalog queries.
- No dependency on any sibling starter — the generic `EntityType`-keyed assignment table is what
  lets `provider-profile-spring-boot-starter` reuse this starter's assignment mechanism without a
  direct starter-to-starter dependency (enforced by the shared `enforce-no-starter-to-starter-deps`
  Maven Enforcer rule).
