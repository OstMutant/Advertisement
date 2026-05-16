# Architecture & Technical Decisions — advertisement-app

---

## 2026-05-12 — Dependency versions locked

**Decision:** Spring Boot 4.0.6, Vaadin 25.1.5, AWS S3 SDK 2.44.4.

**Why:** Spring Boot 4.0.6 is the latest stable patch; Vaadin 25.1.5 aligns with the Spring Boot 4.x BOM; AWS SDK bumped from 2.25.60 to pick up security patches and API improvements.

**Rejected:** Jackson 3 migration (`tools.jackson:jackson-databind:3.1.2`) — Maven artifacts for the `tools.jackson` groupId are unverified. Revisit when official release is confirmed.

---

## Ongoing — No JPA / no Hibernate

**Decision:** All database access via `NamedParameterJdbcTemplate` with custom `*Projection` query objects. No JPA, no Hibernate, no Spring Data JPA.

**Why:** Explicit SQL gives full control over queries, avoids N+1, and eliminates hidden lazy-loading bugs. The `SqlFixedQuery<T>` abstraction (`sql-engine` module) enforces a consistent pattern.

**Rejected:** Spring Data JPA — too much hidden magic, incompatible with the "explicit over implicit" architecture principle.

---

## 2026-05-12 — @EnableMethodSecurity added; @PreAuthorize not used on Vaadin services

**Decision:** `@EnableMethodSecurity` added to `SecurityConfig`. `@PreAuthorize("isAuthenticated()")` is NOT applied at class level on services.

**Why:** Vaadin view beans (`advertisementsView` etc.) are initialized on first HTTP request — before the user authenticates. Class-level `@PreAuthorize` on services breaks this initialization with `AuthorizationDeniedException`. The `/health` REST endpoint is intentionally public (load balancer / monitoring). Any future non-public REST endpoints should use `@PreAuthorize` at the method level directly on the controller.

**Rejected:** Class-level `@PreAuthorize("isAuthenticated()")` on `AdvertisementService`, `ActivityService`, `AuditHistoryService`, `AuditQueryService`, `UserSettingsService` — confirmed broken via smoke tests.

---

## 2026-05-13 — Double-click guard on save buttons via setEnabled

**Decision:** Save buttons in `AdvertisementFormOverlayModeHandler`, `UserFormOverlayModeHandler`, and `SettingsOverlay` are guarded with `setEnabled(false)` at the start of the click listener and re-enabled in a `finally` block.

**Why:** Rapid double-click submits the form twice, causing duplicate saves. The guard lives in `activate()` of the FormModeHandler (not in the Overlay) because the save button is a Spring-injected field of the handler.

---

## 2026-05-13 — ValidRange.List does NOT carry @Constraint; ValidRangeValidator caches field lookups

**Decision:** The inner `@List` annotation on `@ValidRange` intentionally omits `@Constraint(validatedBy = {})`. `ValidRangeValidator` caches reflected fields in a static `ConcurrentHashMap` keyed by `FieldKey(class, start, end)`.

**Why:** Jakarta Bean Validation 3.0 spec says container annotations should carry `@Constraint(validatedBy = {})`, but Hibernate Validator (as used in Spring Boot 4.0.6) does NOT support this for class-level (`ElementType.TYPE`) constraints — adding it causes `HV000030: No validator could be found` at runtime. HV handles repeatable constraints automatically via `@Repeatable` without requiring the container to be annotated as a constraint. Caching avoids repeated `getDeclaredField` + `setAccessible` on every validation call.

**Rejected:** Adding `@Constraint(validatedBy = {})` to `ValidRange.List` — confirmed broken at runtime via smoke tests.

---

## Ongoing — Modular storage: contract vs implementation

**Decision:** `StorageService` interface lives in `advertisement-contracts`. `S3StorageService` and `NoOpStorageService` implementations live in `attachment-spring-boot-starter` (merged from the former `storage-s3-spring-boot-starter` on 2026-05-13). UI components use `ObjectProvider.ifAvailable()` to degrade gracefully when `storage.s3.enabled=false`.

**Why:** Allows running the app locally without S3. The contract/implementation split keeps domain logic independent of infrastructure. Storage was merged into `attachment-spring-boot-starter` because storage only exists to serve attachments — there is no use case for storage without attachments.

**Rejected:** Keeping `storage-s3-spring-boot-starter` as a separate module — two modules with a mandatory one-way dependency and no realistic decoupling scenario.


## 2026-05-16 — Developer scripts moved to scripts/

**Decision:** All root-level `.bat` and `.sh` helper scripts (except `mvn.bat`) moved to `scripts/`. Each script resolves the project root via `cd /d "%~dp0.."` (bat) or `$(dirname "$0")/..` (sh), so they work correctly when run from any directory.

**Why:** Root was accumulating unrelated files; grouping scripts improves discoverability and keeps the root clean.

**Rejected:** Keeping `mvn.bat` in `scripts/` — it is invoked constantly during development and is more ergonomic at the root.

---

## 2026-05-13 — Audit subsystem extracted to audit-spring-boot-starter

**Decision:** The write side of the audit subsystem (`AuditCaptureService`, `AuditDiffEngine`, `AuditFieldCache`, `AuditSnapshotMapper`, `AuditLogRepository` generic operations) was extracted into a new `audit-spring-boot-starter` module. Domain services (`AdvertisementService`, `UserService`, `SettingsOverlay`) now call `AuditPort` (contract interface) instead of `AuditCaptureService` (concrete service). History/activity read side (domain JOINs) stays in `advertisement-app`.

**Why:** Symmetry with `attachment-spring-boot-starter`. Audit is infrastructure, not domain. Enables deploying audit-free variants by setting `audit.enabled=false`. `AuditableSnapshot` marker interface eliminates stringly-typed entity-type strings. `NoOpAuditPort` is the fallback when `audit.enabled=false`, wiring never fails.

**Update 2026-05-13:** `AuditHistoryService`, `AuditQueryService`, `ActivityService`, and all Vaadin audit UI components were subsequently moved to `audit-spring-boot-starter` when `audit-read-spring-boot-starter` was merged in.

**Update 2026-05-15:** The goal is full decoupling — `audit-spring-boot-starter` should contain zero advertisement-specific knowledge. See `audit-spring-boot-starter/DECISIONS.md` → "Goal: Full decoupling from advertisement domain".
