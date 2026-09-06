---
paths: ["advertisement-spring-boot-starter/**"]
---

## advertisement-spring-boot-starter

Auto-configures the Advertisement domain. Active whenever the jar is on the classpath.

Java package root: `org.ost.advertisement`

---

## What it owns

See `advertisement-spring-boot-starter/README.md`'s "Key classes" table for the class list and
one-line roles — not restated here. One constraint worth stating locally since it's not just a
class role: `AdvertisementService` owns only advertisement-domain CRUD and query-time category/city
filter resolution — it does not clear or write taxon assignments, does not enrich display fields
(category/city names, author name/email, media summary), and does not cascade-clean attachments on
delete. All of that cross-domain composition lives in `marketplace-orchestrator`'s
`AdvertisementSaveService`/`AdvertisementDisplayEnrichmentService` — see
`marketplace-orchestrator/CLAUDE.md`.

**Autoconfiguration entry point:** `AdvertisementAutoConfiguration`

---

## Schema

Liquibase changelog: `db/advertisement-changelog/advertisement-changelog-master.xml`  
Tables: `advertisement`

---

## Key constraints

- `AdvertisementPort` lives in `platform-commons`.
- `@EnableJdbcRepositories(basePackages = "org.ost.advertisement.repository")` declared in `AdvertisementAutoConfiguration`.
- `AdvertisementPortImpl` is pure delegation — no business logic inside the port.
- `AdvertisementService`'s only remaining cross-domain dependency is `ComponentFactory<TaxonPort>`,
  used solely for query-time category/city filter resolution
  (`resolveTaxonIdFilter()` → `TaxonPort.findEntityIdsWithAnyTaxon()`, translating a category-id
  filter into an advertisement-id set before `SqlFilterBuilder` builds the `WHERE` clause) — not
  for writing or clearing taxon assignments, and not for display enrichment.
- Actor-reference columns on `advertisement` are named `created_by`/`updated_by`/`deleted_by` —
  no `_user_id` suffix — matching `taxon`'s convention (see `.claude/nav/adr-index.md`). Sort-by-author
  is not an exposed feature (`AdvertisementSortMeta` has no such option);
  if it's ever added, do not re-introduce a JOIN or sort in memory after pagination — denormalize
  `created_by_user_name` onto `advertisement`, synced via a hook (same shape hooks already take
  elsewhere, e.g. `UserSettingsChangedHook`), never a query-time join.
- `advertisement` does **not** store `media_url`/`media_content_type`/`media_count` — no
  denormalized attachment columns on this table at all, and this starter has no `AttachmentPort`
  dependency of its own. `AdvertisementPort.getFiltered()`/`findById()` return raw
  (unenriched) `AdvertisementInfoDto`s — category/city names, author name/email, and media summary
  are populated afterward by `marketplace-orchestrator`'s `AdvertisementDisplayEnrichmentService`,
  never cached on the `advertisement` row. There is no write-triggered media sync path —
  `AttachmentMediaChangeHook` does not exist, and no `MediaChangeHookImpl` →
  `AdvertisementService.onMediaChanged()` → `AdvertisementRepository.updateMedia()` chain exists
  either. See `.claude/nav/adr-index.md`.
- HTML description is sanitized via the shared `html-sanitizer-lib` module's `HtmlSanitizer.sanitize()`,
  called from `AdvertisementService.buildEntity()`. Never trust raw HTML from UI.
- `HtmlSanitizer.sanitize()` also enforces the visible-text-length cap server-side (Jsoup-based),
  in addition to the raw-size `@Size` cap on `AdvertisementSaveDto.description` — see
  `.claude/nav/adr-index.md`.
- `Advertisement.version` (`@Version`) enforces optimistic locking on `save()` and `softDelete()`.
  `AdvertisementService.buildEntity()` must always forward the incoming DTO's `version` when
  rebuilding the entity for an update — never re-derive it from a fresh `findById()` in the same
  method, or the check silently stops detecting conflicts. See `.claude/nav/adr-index.md`.
