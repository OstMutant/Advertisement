# Architecture & Technical Decisions — audit-spring-boot-starter

---

## 2026-05-13 — Module created: write-side audit extracted from advertisement-app

**Decision:** `audit-spring-boot-starter` owns the write side of the audit subsystem: `DefaultAuditPort`, `NoOpAuditPort`, `AuditDiffEngine`, `AuditFieldCache`, `AuditSnapshotMapper`, and generic `AuditLogRepository` operations. Auto-configured via `AuditAutoConfiguration`. Enabled by default (`audit.enabled=true`). Read side (history queries with domain JOINs) stays in `advertisement-app`.

**Why:** Audit is infrastructure. Extracting it as a starter allows: (a) running the app without audit overhead, (b) reuse in future modules, (c) symmetry with `attachment-spring-boot-starter`. The module boundary is: "write generic audit_log rows" (starter) vs "query audit_log joined with domain tables" (app).

**Key patterns:**
- `@ConditionalOnAuditEnabled` gates `DefaultAuditPort` bean creation
- `NoOpAuditPort` is the unconditional fallback via `@ConditionalOnMissingBean` — wiring never fails
- `AuditableSnapshot` marker interface (in contracts) carries `entityType()` — eliminates stringly-typed entity-type strings
- `AuditUserProvider` SPI (in contracts) — the starter calls it without knowing about Spring Security or session context

**Rejected:** `@ConditionalOnAuditEnabled` in `advertisement-contracts` — contracts must be Spring-free pure Java.
