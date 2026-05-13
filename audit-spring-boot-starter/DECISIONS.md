# Architecture & Technical Decisions — audit-spring-boot-starter

---

## 2026-05-13 — Module created: write-side audit extracted from advertisement-app

**Decision:** `audit-spring-boot-starter` owns the full audit subsystem — write side (`DefaultAuditPort`, `NoOpAuditPort`, `AuditDiffEngine`, `AuditFieldCache`, `AuditSnapshotMapper`, `AuditLogRepository`) and read side (`AuditReadRepository`, `AuditHistoryService`, `AuditQueryService`, `ActivityService`, `ActivityPanel`, `ProfileActivityPanel`). Auto-configured via a single `AuditAutoConfiguration`. Enabled by default (`audit.enabled=true`).

**Why:** Audit is infrastructure. Extracting it as a starter allows: (a) running the app without audit overhead, (b) reuse in future modules, (c) symmetry with `attachment-spring-boot-starter`. Write and read sides were initially separate modules (`audit-core-spring-boot-starter` + `audit-read-spring-boot-starter`) but merged on 2026-05-13 — fewer modules is simpler when there is no concrete scenario requiring the write side without the read side.

**Key patterns:**
- `@ConditionalOnAuditEnabled` gates `DefaultAuditPort` bean creation
- `NoOpAuditPort` is the unconditional fallback via `@ConditionalOnMissingBean` — wiring never fails
- `AuditableSnapshot` marker interface (in contracts) carries `entityType()` — eliminates stringly-typed entity-type strings
- `AuditUserProvider` SPI (in contracts) — the starter calls it without knowing about Spring Security or session context

**Rejected:** `@ConditionalOnAuditEnabled` in `advertisement-contracts` — contracts must be Spring-free pure Java.

---

## 2026-05-13 — audit-core + audit-read merged into one module

**Decision:** `audit-core-spring-boot-starter` (write side) and `audit-read-spring-boot-starter` (read side + Vaadin UI) were merged into a single `audit-spring-boot-starter`. The `read/` subpackage was dropped; all classes live flat under `org.ost.advertisement.audit.*` (mirroring `org.ost.attachment.*` in `attachment-spring-boot-starter`).

**Why:** The split was premature. The only motivation for keeping them separate was "write side without Vaadin for a future REST service", but no such service exists. Two modules with a mandatory one-way dependency add complexity without benefit. The merged module uses a single `AuditAutoConfiguration`.

**Rejected:** Keeping the split to preserve the option of a Vaadin-free write-side jar — deferred until a concrete REST use case materialises.
