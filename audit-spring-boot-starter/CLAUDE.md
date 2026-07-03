## audit-spring-boot-starter

Auto-configures the full audit subsystem. Active whenever the jar is on the classpath.

Java package root: `org.ost.audit`

---

## What it owns

**Write side** — recording audit events:
- `DefaultAuditPort` — implements `AuditPort`; entry point for all audit writes
- `AuditLogRepository` — persists `audit_log` rows; supports dynamic filter/sort via query-lib

**Read side** — querying audit data:
- `AuditReadService` — entity activity rows, timeline pages, snapshot content, and entity history; diff computed at read time via `AuditableSnapshot.diff()` from snapshot pairs

**Housekeeping:**
- `AuditCleanupService` — scheduled cleanup for orphaned audit rows (uses `CleanupProperties`)

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
