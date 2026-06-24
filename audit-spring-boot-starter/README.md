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
| `AuditDiffService` | Computes field-level diffs between snapshots via `@AuditedField` |
| `AuditLogRepository` | Persists and queries `audit_log`; supports dynamic filter/sort |
| `AuditHistoryService` | Loads snapshot history for a single entity |
| `AuditQueryService` | Paged query of `audit_log` with filter and sort |
| `ActivityService` | Builds the merged activity feed timeline |

## Dependencies

- `platform-commons` — SPI interfaces (`AuditPort`, `AuditDomainHook`, etc.) and DTOs
- `query-lib` — `SqlFilterBuilder`, `OrderByBuilder` for dynamic queries
- Spring Boot, Spring JDBC, Liquibase
