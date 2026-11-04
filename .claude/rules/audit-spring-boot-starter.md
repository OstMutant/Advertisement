---
paths: ["audit-spring-boot-starter/**"]
---

## audit-spring-boot-starter

Auto-configures the full audit subsystem. Active whenever the jar is on the classpath.

Java package root: `org.ost.audit`

---

## What it owns

The class list and one-line roles (entities, key services, contracts) are generated from the code:
see the `audit-spring-boot-starter` node on the module page of
[`architecture-map.html`](../../docs/architecture/architecture-map.html) and in
[`architecture-model.json`](../../docs/architecture/data/architecture-model.json), not restated here.

**Autoconfiguration entry point:** `AuditAutoConfiguration`

---

## Schema

Liquibase changelog: `db/audit-changelog/audit-changelog-master.xml`  
Tables: `audit_log` (single table; snapshots stored in its `snapshot_data` column — no separate snapshot table)

---

## Key constraints

- `AuditPort`, `AuditDomainHook`, `AuditActivityEnrichHook` live in `platform-commons` — the starter implements `AuditPort` and calls the two Hooks; `marketplace-orchestrator`/`marketplace-app` implement the Hooks (`AuditActivityFieldsHook` does not exist — see `marketplace-app/CLAUDE.md`'s `AuditTimelineRowRenderer`).
- `@EnableJdbcRepositories(basePackages = "org.ost.audit.repository")` declared in `AuditAutoConfiguration` — required because marketplace's `@SpringBootApplication` scan covers only `org.ost.marketplace`.
- `DefaultAuditPort` and all `*HookImpl` classes are pure delegation — no business logic, no JSON parsing, no conditionals beyond routing. Logic belongs in services.
