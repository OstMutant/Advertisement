## advertisement-spring-boot-starter

Auto-configures the Advertisement domain. Active whenever the jar is on the classpath.

Java package root: `org.ost.advertisement`

---

## What it owns

- `Advertisement` entity + `AdvertisementRepository` — CRUD and filter/sort queries
- `AdvertisementService` — create, update, delete, ownership checks; sanitizes HTML description via OWASP HTML Sanitizer; wires category assignments through `TaxonPort` via `ComponentFactory`
- `AdvertisementPortImpl` — implements `AdvertisementPort`; thin delegation to `AdvertisementService`

**Autoconfiguration entry point:** `AdvertisementAutoConfiguration`

---

## Schema

Liquibase changelog: `db/advertisement-changelog/advertisement-changelog-master.xml`  
Tables: `advertisement`

Starters own their own Liquibase changelogs — never merge into a shared file.

---

## Key constraints

- No Vaadin dependency. No UI code here.
- `AdvertisementPort` lives in `platform-commons`.
- `@EnableJdbcRepositories(basePackages = "org.ost.advertisement")` declared in `AdvertisementAutoConfiguration`.
- `AdvertisementPortImpl` is pure delegation — no business logic inside the port.
- `AdvertisementService` depends on `ComponentFactory<TaxonPort>` — category assignment is optional (guard via `taxonPortFactory.ifAvailable(...)`).
- `AdvertisementService` also depends on `ComponentFactory<UserPort>` for author name/email
  enrichment (`enrichWithActorInfo()`, called in `getFiltered()`/`findById()`), mirroring
  `enrichWithCategories()`'s shape exactly. `AdvertisementRepository` never joins
  `user_information` directly — it only ever selects `advertisement.created_by` (a plain
  `BIGINT`, populated via `@CreatedBy`/`JdbcAuditingConfig`'s `AuditorAware<Long>`); the
  name/email lookup goes through `UserPort.findByIds()`, a bulk lookup, so no raw SQL in this
  starter ever references another starter's table/column names. See `marketplace-app/DECISIONS.md`
  ADR-034.
- Actor-reference columns on `advertisement` are named `created_by`/`updated_by`/`deleted_by` —
  no `_user_id` suffix — matching `taxon`'s convention (see ADR-034). Sort-by-author is not an
  exposed feature (`AdvertisementSortMeta` has no such option); if it's ever added, do not
  re-introduce a JOIN or sort in memory after pagination — denormalize `created_by_user_name`
  onto `advertisement`, synced via a hook (same shape hooks already take elsewhere, e.g.
  `UserSettingsChangedHook`), never a query-time join.
- `advertisement` does **not** store `media_url`/`media_content_type`/`media_count` — no
  denormalized attachment columns on this table at all. `AdvertisementService` depends on
  `ComponentFactory<AttachmentPort>` for media-summary enrichment (`enrichWithMediaSummary()`,
  called in `getFiltered()`/`findById()`, same shape as `enrichWithCategories()`/
  `enrichWithActorInfo()`) via `AttachmentPort.getMediaSummaries()`, a bulk lookup computed at
  read time from the `attachment` table — never cached on the `advertisement` row. There is no
  write-triggered sync path (the old `MediaChangeHookImpl` → `AdvertisementService
  .onMediaChanged()` → `AdvertisementRepository.updateMedia()` chain was removed entirely — see
  `marketplace-app/DECISIONS.md` ADR-035); `AttachmentMediaChangeHook` still fires from
  `AttachmentService` on every media change, but currently has no implementation to receive it,
  which is a valid, gracefully-degraded state for this optional SPI.
- HTML description is sanitized using OWASP HTML Sanitizer (`Sanitizers.FORMATTING.and(LINKS).and(BLOCKS)`). Never trust raw HTML from UI.
- Description visible-text length is enforced server-side via a Jsoup-based check in
  `AdvertisementService.sanitizeHtml()` (`Jsoup.parse(html).text().length()`), in addition to
  the raw-size `@Size` cap on `AdvertisementSaveDto.description` — see
  `marketplace-app/DECISIONS.md` ADR-024.
- `Advertisement.version` (`@Version`) enforces optimistic locking on `save()` and `softDelete()`.
  `AdvertisementService.buildEntity()` must always forward the incoming DTO's `version` when
  rebuilding the entity for an update — never re-derive it from a fresh `findById()` in the same
  method, or the check silently stops detecting conflicts. See `marketplace-app/DECISIONS.md`
  ADR-029.
