# provider-profile-spring-boot-starter

Auto-configures the Provider Profile domain — a single, self-service `ProviderProfile` row per
actor describing what kind of provider they are (`ProviderKind.MASTER`/`SHOP`/`SUPPORT`), their
city, and a sanitized "about" write-up, shown on the public Providers catalog.

## What it provides

- CRUD for a single `ProviderProfile` row per actor (`kind`, `about`, `cityTaxonId`), created
  lazily on the actor's first "become a provider" save rather than eagerly at registration.
- Filtered/paginated/sorted reads (`kind`, `createdAt`/`updatedAt` range, `cityTaxonId`, plus a
  query-time category filter resolved via `TaxonPort.findEntityIdsWithAnyTaxon()`) for the public
  Providers catalog.
- Sanitizes the `about` rich-text field via `html-sanitizer-lib`'s `HtmlSanitizer.sanitize()`, the
  same pattern `advertisement-spring-boot-starter` uses for its own description field.
- Enforces the one authorization-shaped rule this starter checks server-side: only a privileged
  acting actor may save a profile with `kind == SUPPORT`.
- A purge-safety check (`findOwnerIds`), queried by `user-spring-boot-starter` before deleting a
  user account, that blocks the purge while a profile still exists.
- An existence check (`findExistingIds`), queried by `marketplace-orchestrator`'s
  `EntityExistenceService` when validating a set of referenced entity ids.
- **SPI implementation:** `ProviderProfilePort` (called by `marketplace-orchestrator`'s
  `ProviderProfileSaveService`/`ProviderProfileReadService`/`ProviderProfileDisplayEnrichmentService`/
  `EntityExistenceService`).

## Data flow

Every operation enters through `ProviderProfilePortImpl`, the sole implementation of
`ProviderProfilePort` — it delegates every call, unchanged, straight to `ProviderProfileService`,
the module's only business-logic class.

- **Save:** `ProviderProfilePort.save(dto, targetUserId, actingUserId, actingUserIsPrivileged)` →
  `ProviderProfileService.save` rejects a `kind == SUPPORT` save from a non-privileged actor, reads
  the existing row via `ProviderProfileRepository.findById` when updating (to keep `actorId`/
  `createdAt` immutable across edits), sanitizes `about`, and persists via
  `ProviderProfileRepository.save` → `ProviderProfileCrudRepository.save`.
- **Read:** `findById`/`findByActorId` → `ProviderProfileRepository`'s matching `JdbcClient` query,
  mapped by its `RowMapper` into a `ProviderProfileDto`.
- **Filtered listing:** `getFiltered`/`count` → `ProviderProfileService` first resolves a
  `categoryIds` filter into an allow-list of profile ids via `TaxonPort.findEntityIdsWithAnyTaxon()`
  (short-circuiting to an empty result once that lookup returns none), then
  `ProviderProfileRepository.findByFilter`/`countByFilter` build the `WHERE`/`ORDER BY`/pagination
  clauses via `query-lib`'s `SqlFilterBuilder`/`OrderByBuilder`.
- **Delete:** `delete(id, version)` → `ProviderProfileRepository.delete` runs a real
  `DELETE ... WHERE id = :id AND version = :version`, throwing `OptimisticLockingFailureException`
  on a stale version — no soft-delete columns, no restore path.

## Schema

Liquibase changelog: `db/provider-profile-changelog/provider-profile-changelog-master.xml`. Table:
`provider_profile` — `actor_id` carries a unique index (at most one profile per actor);
`city_taxon_id` is a plain column, not a `taxon_assignment` row, since a provider has exactly one
city. Category assignments (many-to-many) live in `taxon-spring-boot-starter`'s own
`taxon_assignment` table instead, written by `marketplace-orchestrator`'s
`TaxonAssignmentWriteService` — this starter only resolves them read-only, via
`TaxonPort.findEntityIdsWithAnyTaxon()`, for query-time filtering.

## Dependencies

- `platform-commons` — `ProviderProfilePort`/`ProviderProfileDto`/`ProviderProfileSaveDto`/
  `ProviderProfileFilterDto`/`ProviderProfileSnapshotDto`/`ProviderKind`, plus `TaxonPort`
  (query-time category filter resolution only — never a write).
- `query-lib` — `SqlFilterBuilder`/`OrderByBuilder` for the public catalog's filter/sort/pagination
  query.
- `html-sanitizer-lib` — sanitizes and visible-text-length-validates the `about` field, the same
  pattern `advertisement-spring-boot-starter` uses for its own rich-text field.
- No dependency on `taxon-spring-boot-starter` or any other sibling starter (enforced by this
  module's own `enforce-no-starter-to-starter-deps` rule) — category-assignment writing is composed
  by `marketplace-orchestrator`'s `TaxonAssignmentWriteService` at the application layer instead.
- Spring Boot (`spring-boot-starter`, `spring-boot-starter-data-jdbc`, `spring-boot-liquibase`,
  `spring-boot-starter-validation`), PostgreSQL JDBC driver (runtime).
