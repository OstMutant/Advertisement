## taxon-spring-boot-starter

Auto-configures the Taxonomy domain (categories, tags, classifiers). Active whenever the jar is on the classpath.

Java package root: `org.ost.taxon`

---

## What it owns

- `Taxon` entity + `TaxonCrudRepository` + `TaxonRepository` — soft-deletable taxonomy entries
- `TaxonTranslation` entity + `TaxonTranslationRepository` — locale-keyed translations per taxon
- `TaxonAssignment` entity + `TaxonAssignmentRepository` — many-to-many: (entity_type, entity_id) → taxon_id
- `TaxonService` — CRUD taxon entries, soft-delete/restore, translation management
- `TaxonAssignmentService` — assign/unassign taxons to entities, batched lookup, usage counts
- `DefaultTaxonPort` — implements `TaxonPort`; coordinates TaxonService + TaxonAssignmentService
- `TaxonFilter` — value object for repository filter conditions (active / all / deleted)
- `TaxonTranslationData` — internal record for passing translation data to services
- `TaxonProperties` — configuration properties (defaultLocale)

**Autoconfiguration entry point:** `TaxonAutoConfiguration`

---

## Schema

Liquibase changelog: `db/taxon-changelog/master.xml`  
Tables: `taxon`, `taxon_translation`, `taxon_assignment`

- `taxon` — core entry: type (VARCHAR), optional stable code, deleted_at/deleted_by for soft-delete, `version` (optimistic locking via `@Version`, see `marketplace-app/DECISIONS.md` ADR-029)
- `taxon_translation` — PK: (taxon_id, locale), stores name + description per locale
- `taxon_assignment` — PK: (entity_type, entity_id, taxon_id), records which entities carry which taxons

Partial unique index: `uidx_taxon_type_code ON taxon (type, code) WHERE code IS NOT NULL` — only enforced for named entries.

Starters own their own Liquibase changelogs — never merge into a shared file.

---

## Key constraints

- No Vaadin dependency. No UI code here. UI (`TaxonManagementView`, `TaxonOverlay`) lives in `marketplace-app`.
- `TaxonPort` and `TaxonAuditHook` live in `platform-commons` (`org.ost.platform.taxon.spi`).
- `TaxonType` enum lives in `platform-commons` (`org.ost.platform.taxon.model`). Adding a new type is a release-level change requiring UI, audit translations, and seed entries.
- `@EnableJdbcRepositories(basePackages = "org.ost.taxon.repository")` declared in `TaxonAutoConfiguration`.
- `DefaultTaxonPort` is a coordination layer, not pure delegation — it resolves translations, filters active records, and builds DTOs. Business logic stays in `TaxonService` and `TaxonAssignmentService`.
- `TaxonAuditHook` is called by this starter when assignments change; `TaxonAuditHookImpl` in marketplace-app records it to the audit log via `TaxonActivityService`.
- `Taxon.version` (`@Version`) enforces optimistic locking on `save()` and `softDelete()`.
  `TaxonService.update()` must always forward the caller-supplied `version` when rebuilding the
  entity via `Builder` — never re-derive it from the `existing` row fetched in the same method,
  or the check silently stops detecting conflicts. See `marketplace-app/DECISIONS.md` ADR-029.
