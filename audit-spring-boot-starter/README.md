# audit-spring-boot-starter

Auto-configured audit subsystem for the Advertisement Platform: captures every entity
create/update/delete/restore as an immutable snapshot row, and serves that history back as
per-entity activity, a cross-entity timeline feed, and snapshot lookups for restore.

## What it provides

- **Write side:** an audit trail entry for every entity create/update/delete/restore, triggered by
  a domain service with a single `AuditPort` call after its own write — the calling service never
  touches `audit_log` itself.
- **Read side:** per-entity activity history, a filtered/sorted/paginated cross-entity timeline
  feed, and snapshot lookups (latest-only, or a specific point in time) for restore flows.
- **Scheduled cleanup** keeps `audit_log` from growing unbounded, on a configurable cron schedule.
- **SPI implementation:** `AuditPort` (called by `marketplace-orchestrator`'s domain services after
  every write, and by `marketplace-app`'s activity/history UI for reads).

## Data flow

- **Write:** `AuditPort.captureCreation`/`captureUpdate`/`captureDeletion`/`captureRestore` →
  `DefaultAuditPort` → `AuditLogRepository.save`, which inserts the snapshot into `audit_log`.
- **Entity activity read:** `AuditPort.getEntityActivity` → `DefaultAuditPort` →
  `AuditReadService.getEntityActivity` → `AuditLogRepository.findRows` (window-function query,
  most recent row first) → `AuditReadService` re-derives each row's previous snapshot by walking
  the list for the last snapshot of the same concrete type — not the SQL window function's
  chronologically-previous row — diffs each row against it, and lets every matching
  `AuditActivityEnrichHook` enrich the result before it's returned.
- **Latest-snapshot lookup:** `AuditPort.getLastSnapshot` → `DefaultAuditPort` →
  `AuditReadService.getLastSnapshot` → `AuditLogRepository.getLastSnapshot`, returning only the
  single most recent snapshot with no diff — for callers that need current state, not full history.
- **Timeline read:** `AuditPort.getTimelinePage` → `DefaultAuditPort` →
  `AuditReadService.getTimelinePage` → `AuditLogRepository.findTimeline` (dynamic filter/sort built
  with `query-lib`'s `SqlFilterBuilder`/`OrderByBuilder`) → every `AuditActivityEnrichHook` merges
  its own domain enrichment into the page before it's returned. `AuditPort.countTimeline` follows
  the same path down to `AuditLogRepository.countTimeline` only — a plain row count, no enrichment.
- **Restore lookup:** `AuditPort.getSnapshotContent` → `DefaultAuditPort` →
  `AuditLogRepository.getSnapshotContent` → `AuditDomainHook.castIfKnown` narrows the result to the
  caller's expected snapshot type.
- **Cleanup:** `AuditAutoConfiguration`'s `SchedulingConfigurer` bean fires
  `AuditCleanupService.cleanup` on the `CleanupProperties` cron schedule, which calls
  `AuditLogRepository.deleteOlderThan` with the configured retention window.

## Schema

Liquibase changelog: `db/audit-changelog/`. Single table `audit_log` — write-only, never updated,
holding `entity_type`/`entity_id`/`action_type`, the JSONB `snapshot_data`, and `actor_id` with
**no FK** (matches this codebase's actor-reference-column convention). Row version and the
previous same-entity snapshot are derived at query time via SQL window functions rather than
stored, so no separate snapshot-history table exists.

## Dependencies

- `platform-commons` — `AuditPort`/`AuditDomainHook`/`AuditActivityEnrichHook` SPI contracts,
  `AuditableSnapshot` and the `Audit*Dto` types, plus `CleanupProperties`/`CurrentActorHook`/
  `EntityType`/`ActionType`/`EntityRef`.
- `query-lib` — `SqlFilterBuilder`/`OrderByBuilder` for the timeline's dynamic filter/sort.
- Spring Boot (`spring-boot-starter`, `spring-boot-starter-data-jdbc`, `spring-boot-liquibase`) and
  Jackson (`jackson-databind`) for snapshot (de)serialization.
- No dependency on any sibling starter — every entity-specific lookup (actor name resolution,
  entity existence, media enrichment) is delegated to `marketplace-orchestrator`/`marketplace-app`
  through `AuditDomainHook`/`AuditActivityEnrichHook`, never called directly.
