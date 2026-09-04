# provider-profile-spring-boot-starter

Auto-configures the provider-profile domain — a user's public, self-service listing of what kind
of provider they are (`ProviderKind.MASTER`/`SHOP`/`SUPPORT`), shown on the public Providers
catalog. One profile per user, category/city-assigned via `taxon-spring-boot-starter`'s generic
assignment mechanism rather than a domain-specific table of its own.

## What it provides

- CRUD for a single `ProviderProfile` row per user, including the visible-text-length-bounded
  "about" rich-text field (sanitized via `html-sanitizer-lib`, same pattern as
  `advertisement-spring-boot-starter`'s description field).
- **SPI implementation:** `ProviderProfilePort` (called by `marketplace-orchestrator`'s
  `ProviderProfileSaveService`/`ProviderProfileReadService`/`ProviderProfileDisplayEnrichmentService`).

## Key classes

| Class | Role |
|---|---|
| `ProviderProfileService` | The only business-logic class — save/delete plus filtered/paginated reads; sanitizes `about` via `HtmlSanitizer.sanitize(dto.about(), ProviderProfileSaveDto.ABOUT_MAX_LENGTH)` on every save, the same call shape `AdvertisementService` uses for its own description field. |
| `ProviderProfileRepository` | `JdbcClient`-based bespoke queries (dynamic filter/sort via `query-lib`); `ProviderProfileCrudRepository` handles the trivial `save`/`findById`. |
| `ProviderProfilePortImpl` | Pure delegation implementing `ProviderProfilePort` — no logic of its own. |

## Schema

Liquibase changelog under `db/*-changelog/`. Table: `provider_profile` (one row per user —
`user_id` uniquely identifies the owner; category/city assignments live in
`taxon-spring-boot-starter`'s own `taxon_assignment` table, not a column here).

## Dependencies

- `platform-commons` — `ProviderProfilePort`/`ProviderProfileDto`/`ProviderProfileSaveDto`/
  `ProviderProfileFilterDto`/`ProviderProfileSnapshotDto`/`ProviderKind`.
- `query-lib` — `SqlFilterBuilder`/`OrderByBuilder` for the public catalog's filter/sort query.
- `html-sanitizer-lib` — sanitizes and visible-text-length-validates the `about` field, same
  pattern `advertisement-spring-boot-starter` uses for its own rich-text field.
- No dependency on `taxon-spring-boot-starter` or any other sibling starter — category/city
  assignment is composed by `marketplace-orchestrator`'s `TaxonAssignmentWriteService` at the
  application layer, not a direct Maven dependency (enforced by `enforce-no-starter-to-starter-deps`).
