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

---

## 2026-05-13 — SQL coupling to domain tables removed via SPI batch pattern

**Decision:** `ActivityProjection` and `AdvertisementHistoryProjection` no longer JOIN `user_information` or use EXISTS subqueries against `advertisement`/`user_information`. Instead: (a) raw `actor_id` is returned from the query; (b) `AuditActorNameResolver` SPI (`advertisement-contracts`) performs a single bulk `SELECT id, name FROM user_information WHERE id = ANY(:ids)` after the query; (c) `AuditEntityExistenceChecker` SPI performs a single bulk `SELECT id FROM <table> WHERE id = ANY(:ids)` grouped by entity type. Both SPIs are wired via `ObjectProvider` — if absent, actor names stay `null` and entity existence defaults to `false`.

`AdvertisementHistoryDto` gained an `actorId` field so the service can do bulk resolution without a secondary per-row query. Implementations (`AuditActorNameResolverImpl`, `AuditEntityExistenceCheckerImpl`) live in `advertisement-app`.

**Why:** The starter previously coupled directly to `user_information` and `advertisement` tables, making it unusable in any context that does not have those tables. The SPI pattern mirrors `AuditUserProvider` — the starter knows nothing about the domain schema and calls back to the host application for any domain-specific data.

**Rejected:** Per-row secondary queries (`getActorIdForSnapshot`) — single bulk SELECT with `ANY(:ids)` is one round-trip vs N.

---

## Goal (not yet done) — Full decoupling from advertisement domain

**Target state:** `audit-spring-boot-starter` must contain zero knowledge of advertisement-specific entities, field names, or business logic. The module must be reusable in any Spring Boot + Vaadin project without modification.

**What is still coupled (as of 2026-05-15):**
- `AdvertisementHistoryProjection` — SQL hardcodes `entity_type = 'ADVERTISEMENT'` and extracts `title`, `description` from snapshot JSON. Belongs in `advertisement-app`.
- `AuditHistoryService` — service method `getAdvertisementHistory()` is advertisement-specific. Belongs in `advertisement-app`.
- `AdvertisementHistoryPanel` — Vaadin UI component for advertisement history. Belongs in `advertisement-app`.
- `ActivityProjection` — UNION SQL hardcodes `'ADVERTISEMENT'`, `'USER'`, `'USER_SETTINGS'` and their snapshot field names (`title`, `name`, `email`, `role`). Must become configurable via SPI.
- `AuditLogRepository.getSnapshotContent()` — extracts `title` and `description` from snapshot JSON, hardcodes `entity_type = 'ADVERTISEMENT'`. Must be removed; callers use raw `getSnapshotData()` and parse themselves.
- `ActivityRowRenderer` — contains `EntityType.ADVERTISEMENT`-specific UI rendering logic. Must delegate to a per-entity-type SPI.

**How to achieve this is not yet decided.**
