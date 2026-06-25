## audit-spring-boot-starter

Auto-configures the full audit subsystem. Active whenever the jar is on the classpath.

Java package root: `org.ost.audit`

---

## What it owns

**Write side** — recording audit events:
- `DefaultAuditPort` — implements `AuditPort`; entry point for all audit writes
- `AuditDiffService` — computes field-level diffs between snapshots using `@AuditedField`
- `AuditLogRepository` — persists `audit_log` rows; supports dynamic filter/sort via query-lib

**Read side** — querying audit data:
- `AuditHistoryService` — loads snapshot history for a given entity
- `AuditQueryService` — paged query of `audit_log` with filter + sort
- `ActivityService` — builds the activity feed (merged timeline of domain events)

**Autoconfiguration entry point:** `AuditAutoConfiguration`

---

## Schema

Liquibase changelog: `db/changelog/audit-changelog.xml`  
Tables: `audit_log`, `audit_snapshot`

Starters own their own Liquibase changelogs — never merge into a shared file.

---

## Key constraints

- No Vaadin dependency. No UI code here.
- `AuditPort`, `AuditDomainHook`, `AuditActivityFieldsHook`, `AuditActivityEnrichHook` live in `platform-commons` — the starter implements them, marketplace-app calls/wires them.
- `@EnableJdbcRepositories(basePackages = "org.ost.audit")` declared in `AuditAutoConfiguration` — required because marketplace's `@SpringBootApplication` scan covers only `org.ost.marketplace`.
- `DefaultAuditPort` and all `*HookImpl` classes are pure delegation — no business logic, no JSON parsing, no conditionals beyond routing. Logic belongs in services.
