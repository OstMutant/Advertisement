# contact-spring-boot-starter

Auto-configures contact data and reveal analytics for an owning entity: one contact row per entity
(only a provider profile owns one today) and an append-only log of reveals, counted per channel per
month. It is its own starter, mirroring `audit-spring-boot-starter`, so no domain starter has to reach
across the sibling-starter boundary (`.claude/rules.md`'s "no direct imports between sibling
modules") to resolve it.

## What it provides

- Upsert for `contact_info`, enforced unique per `(entity_type, entity_id)` via the
  `idx_contact_info_entity` index; a stale `version` on save is translated from Spring Data JDBC's
  `OptimisticLockingFailureException` into `StaleWriteException` in `ContactRepository.save`.
- Append-only write + per-channel monthly read aggregation for `contact_view`.
- **SPI implementation:** `ContactPort` (`platform-commons`), consumed by `marketplace-orchestrator`.

## Data flow

A caller (via `ContactPort`) passes an `(EntityType, Long entityId)` pair. `ContactPortImpl`
delegates straight through to `ContactService`, which resolves persistence through
`ContactRepository`:
- `find`/`save` go against `contact_info`, upserted by looking up the existing row for that
  `(entityType, entityId)` pair first (there is no caller-known `id` to key on directly) before
  delegating to `ContactInfoCrudRepository.save`.
- `recordView` inserts one `contact_view` row per reveal/click via `ContactViewCrudRepository`.
- `countViewsThisMonth` runs a `GROUP BY channel` aggregate over `contact_view` for the current
  calendar month.

## Dependencies

- `platform-commons` — `ContactPort`/`ContactInfoDto`/`ContactViewCountDto`/`ContactChannel`
  (`contact.*`) is this module's own compile-time contract; `EntityType` (`core.model`) is the
  generic entity key; `StaleWriteException` (`core`) is the optimistic-lock failure type this
  module's repository translates into.
- `query-lib` — declared per the starter baseline; this module's own queries are simple enough not
  to need `SqlFilterBuilder`/`OrderByBuilder` yet.
- No Maven dependency on any sibling starter (enforced by this module's own `maven-enforcer-plugin`
  rule) — resolution stays entity-type/entity-id generic, never a direct call into
  `provider-profile-spring-boot-starter`/`advertisement-spring-boot-starter`.
