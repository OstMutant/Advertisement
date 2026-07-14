# audit-spring-boot-starter

Auto-configured audit subsystem for the Advertisement Platform.

## What it provides

- **Write side:** records audit log entries with field-level diffs on every create/update/delete
- **Read side:** paged timeline query, snapshot history, and activity feed aggregation
- **SPI implementations:** `AuditPort` (called by marketplace-app to trigger audit writes)

## Key classes

| Class | Role |
|---|---|
| `DefaultAuditPort` | Entry point — implements `AuditPort`, delegates to services |
| `AuditLogRepository` | Persists and queries `audit_log`; supports dynamic filter/sort via query-lib |
| `AuditLogProjection` | Generic repository-row DTO shared by both the activity and timeline queries |
| `AuditReadService` | Entity activity rows, timeline pages, snapshot content, entity history — diff computed at read time via `AuditableSnapshot.diff()` |
| `AuditCleanupService` | Scheduled cleanup of orphaned audit rows (`CleanupProperties`) |

## Dependencies

- `platform-commons` — SPI interfaces (`AuditPort`, `AuditDomainHook`, etc.) and DTOs
- `query-lib` — `SqlFilterBuilder`, `OrderByBuilder` for dynamic queries
- Spring Boot, Spring JDBC, Liquibase
