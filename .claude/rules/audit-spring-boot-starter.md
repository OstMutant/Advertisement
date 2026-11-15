---
paths: ["audit-spring-boot-starter/**"]
---

## audit-spring-boot-starter

Auto-configures the audit subsystem whenever a `DataSource` is on the classpath; Java package root `org.ost.audit`.

---

## What it owns

Classes and roles are generated, not restated here: see the `audit-spring-boot-starter` node of [`architecture-map.html`](../../docs/architecture/architecture-map.html) and [`architecture-model.json`](../../docs/architecture/data/architecture-model.json).

**Autoconfiguration entry point:** `AuditAutoConfiguration`

---

## Schema

Liquibase changelog `db/audit-changelog/audit-changelog-master.xml`; one table `audit_log`, snapshots in its `snapshot_data` column (no separate snapshot table).

---

## Key constraints

- `AuditPort`, `AuditDomainHook`, `AuditActivityEnrichHook` live in `platform-commons`. The starter implements `AuditPort` and calls the two Hooks; `marketplace-orchestrator` implements the Hooks. Field labels are resolved in `AuditTimelineRowRenderer` (see `.claude/rules/marketplace-app.md`), not in a hook.
- Declare `@EnableJdbcRepositories(basePackages = "org.ost.audit.repository")` in `AuditAutoConfiguration`: the application's scan covers only `org.ost.marketplace`.
- `DefaultAuditPort` and all `*HookImpl` classes are pure delegation: no business logic, no JSON parsing, no conditionals beyond routing; logic belongs in services.
