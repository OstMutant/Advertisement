# advertisement-spring-boot-starter

Auto-configures the Advertisement domain — CRUD, dynamic filter/sort/pagination, HTML
sanitization, and scheduled retention cleanup for the `advertisement` table.

## What it provides

- Advertisement CRUD with optimistic locking (`Advertisement.version`) and soft delete
  (`deleted_at`/`deleted_by`), including a hand-rolled version-checked `UPDATE` for delete rather
  than a full row rewrite.
- Dynamic filter/sort/pagination over `advertisement` (title, created/updated date ranges, ad
  kind) via `query-lib`'s `SqlFilterBuilder`/`OrderByBuilder`; a category/city constraint is
  resolved query-time through `TaxonPort`, optional and degrading gracefully if the taxon starter
  is absent.
- HTML description sanitization and a visible-text-length cap via `html-sanitizer-lib`'s
  `HtmlSanitizer.sanitize()`.
- Scheduled retention cleanup — a cron trigger (configurable via `CleanupProperties`) hard-deletes
  rows that have been soft-deleted past the retention window.
- Account-deletion support: `clearActorReferences` nulls `updated_by`/`deleted_by` for a set of
  purge-candidate actor ids; `findOwnerIds` reports which of those ids still own an advertisement
  (via `created_by`), so a still-owning user is skipped from that purge run.
- **SPI implementation:** `AdvertisementPort` — called by `marketplace-orchestrator` for every
  regular CRUD/query use case, and directly by `user-spring-boot-starter`'s `UserService.cleanup()`
  for account-purge actor-reference cleanup (`clearActorReferences`/`findOwnerIds`); never called
  directly by `marketplace-app` or `marketplace-rest-api`.

## Data flow

Every operation enters through `AdvertisementPortImpl`, the sole implementation of
`AdvertisementPort` — it delegates every call, unchanged, straight to `AdvertisementService`, the
module's only business-logic class.

- **Filtered query:** `AdvertisementPort.getFiltered`/`count` → `AdvertisementService` resolves the
  filter's category/city constraint by calling `TaxonPort.findEntityIdsWithAnyTaxon()` through
  `ComponentFactory<TaxonPort>` (skipped entirely if the filter carries no category/city, or if the
  taxon starter isn't on the classpath) and intersects the two constraints when both are present —
  then `AdvertisementRepository.findByFilter`/`countByFilter` build the `WHERE`/`ORDER BY`/`LIMIT`
  clauses via `SqlFilterBuilder`/`OrderByBuilder`/`PaginationSqlBuilder` and query `advertisement`.
- **Save:** `AdvertisementPort.save(dto)` → `AdvertisementService.save`, transactional — loads the
  existing `Advertisement` row when updating, sanitizes the description through
  `HtmlSanitizer.sanitize()`, carries `createdAt`/`createdBy` forward from the existing row and the
  incoming DTO's `version` unchanged, then persists via `AdvertisementRepository.save` →
  `AdvertisementCrudRepository.save` (Spring Data JDBC, optimistic-locked on `version`).
- **Delete:** `AdvertisementPort.delete(id, actingUserId, version)` → `AdvertisementService.delete`
  → `AdvertisementRepository.softDelete` issues a version-checked `UPDATE` setting `deleted_at`/
  `deleted_by`, throwing `OptimisticLockingFailureException` when no row matches both id and
  version.
- **Scheduled cleanup:** `AdvertisementAutoConfiguration`'s `SchedulingConfigurer` fires a
  `CronTrigger` (from `CleanupProperties`) that calls `AdvertisementService.cleanup(retentionDays)`
  → `AdvertisementRepository.deleteOlderThan` hard-deletes soft-deleted rows past the retention
  window.
- **Account-deletion cleanup:** `user-spring-boot-starter`'s `UserService.cleanup()` calls
  `AdvertisementPort.clearActorReferences(candidateIds)` → `AdvertisementService` →
  `AdvertisementRepository` nulls `updated_by`/`deleted_by` (never `created_by`) for each purge
  candidate, and separately calls `AdvertisementPort.findOwnerIds(candidateIds)` to read which
  candidates still own a `created_by` row — a user who still owns an advertisement is skipped from
  that purge run, so `created_by` never dangles.

## Dependencies

- `platform-commons` — `AdvertisementPort` SPI and its DTOs (`AdvertisementFilterDto`,
  `AdvertisementInfoDto`, `AdvertisementSaveDto`), the `AdKind` model, `EntityType` (identifies the
  advertisement side of a taxon-assignment lookup), and `CleanupProperties` (retention-cleanup
  cron/timezone/retention-days config), plus `TaxonPort`, wired solely through
  `ComponentFactory<TaxonPort>` for query-time category/city filter resolution —
  `AdvertisementService` writes or clears no taxon assignment, and enriches no display field
  (category/city names, author name/email, media summary); that composition lives in
  `marketplace-orchestrator`.
- `query-lib` — `SqlFilterBuilder`, `OrderByBuilder`, `PaginationSqlBuilder` for dynamic filter/
  sort/pagination.
- `html-sanitizer-lib` — `HtmlSanitizer` for description sanitization and the visible-text-length
  cap.
- **No Maven dependency on any sibling starter** (`audit-`/`attachment-`/`taxon-`/`user-`/
  `provider-profile-spring-boot-starter`) — Maven Enforcer's `bannedDependencies` rule fails the
  build on a real starter-to-starter dependency; the `TaxonPort` wiring above stays genuine
  runtime-only decoupling through `platform-commons`' SPI type.
- Spring Boot (`spring-boot-starter`, `spring-boot-starter-data-jdbc`, `spring-boot-liquibase`,
  `spring-boot-starter-validation`), PostgreSQL JDBC driver (runtime).
