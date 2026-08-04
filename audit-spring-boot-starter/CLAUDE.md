## audit-spring-boot-starter

Auto-configures the full audit subsystem. Active whenever the jar is on the classpath.

Java package root: `org.ost.audit`

---

## What it owns

See `audit-spring-boot-starter/README.md`'s "Key classes" table for the class list and one-line
roles — not restated here.

**Autoconfiguration entry point:** `AuditAutoConfiguration`

---

## Schema

Liquibase changelog: `db/audit-changelog/audit-changelog-master.xml`  
Tables: `audit_log` (single table; snapshots stored in its `snapshot_data` column — no separate snapshot table)

---

## Key constraints

- `AuditPort`, `AuditDomainHook`, `AuditActivityFieldsHook`, `AuditActivityEnrichHook` live in `platform-commons` — the starter implements them, marketplace-app calls/wires them.
- `@EnableJdbcRepositories(basePackages = "org.ost.audit.repository")` declared in `AuditAutoConfiguration` — required because marketplace's `@SpringBootApplication` scan covers only `org.ost.marketplace`.
- `DefaultAuditPort` and all `*HookImpl` classes are pure delegation — no business logic, no JSON parsing, no conditionals beyond routing. Logic belongs in services.
