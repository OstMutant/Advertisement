# Architecture & Technical Decisions — marketplace-app

---

## Ongoing — Descriptor + repository pattern (Read/Write namespaces)

**Decision:** Every table has one `final class implements SqlEntityDescriptor` with a private constructor. SQL, field constants, and param factories are split into two inner namespaces:
- `Read` — `PROJECTION` (`SqlEntityProjection<T>` with inline `mapRow`), `SELECT_*`/`BY_*_WHERE` constants, read-side param factories, `FILTER` (`SqlFilterBuilder` instance).
- `Write` — `SqlCommand` constants for INSERT/UPDATE/DELETE, write-side param factories.

Repositories hold `private final RepositoryCustom repo` or `private final FilterableRepository<T,F> query` via composition — never extend either class. No inline SQL strings, no inline `MapSqlParameterSource` construction at call sites — all of these belong on the descriptor.

**Why:** One grep target (`SqlEntityDescriptor`) finds all dual-side descriptors. The namespace makes the SQL boundary explicit (`Descriptor.Read.PROJECTION` ↔ `Descriptor.Write.SOFT_DELETE`). Composition over inheritance keeps repository constructors package-private (avoids leaking package-private CrudRepository types into the public API).

**How to apply:** New descriptors follow this shape. `SqlFilterBuilder` is concrete — instantiate directly as `Read.FILTER`; for filters with a base predicate (e.g. always `deleted_at IS NULL`) use an anonymous subclass overriding `build()`.

---

## 2026-05-12 — Dependency versions locked

**Decision:** Spring Boot 4.0.6, Vaadin 25.1.5, AWS S3 SDK 2.44.4.

**Why:** Spring Boot 4.0.6 is the latest stable patch; Vaadin 25.1.5 aligns with the Spring Boot 4.x BOM; AWS SDK bumped from 2.25.60 to pick up security patches and API improvements.

**Rejected:** Jackson 3 migration (`tools.jackson:jackson-databind:3.1.2`) — Maven artifacts for the `tools.jackson` groupId are unverified. Revisit when official release is confirmed.

---

## Ongoing — No JPA / no Hibernate

**Decision:** All database access via `JdbcClient` with custom `*Descriptor` + `RepositoryCustom`/`FilterableRepository`. No JPA, no Hibernate, no Spring Data JPA. `CrudRepository<T, Long>` is used only for trivial `save`/`findById`/`deleteById` — never for custom queries.

**Why:** Explicit SQL gives full control over queries, avoids N+1, and eliminates hidden lazy-loading bugs. `SqlFixedQuery<T>` and `SqlEntityProjection<T>` in `sql-engine` enforce a consistent pattern.

**Rejected:** Spring Data JPA — too much hidden magic, incompatible with the "explicit over implicit" architecture principle.

---

## 2026-05-12 — @EnableMethodSecurity added; @PreAuthorize not used on Vaadin services

**Decision:** `@EnableMethodSecurity` added to `SecurityConfig`. `@PreAuthorize("isAuthenticated()")` is NOT applied at class level on services.

**Why:** Vaadin view beans are initialized on first HTTP request — before the user authenticates. Class-level `@PreAuthorize` on services breaks this initialization with `AuthorizationDeniedException`. The `/health` REST endpoint is intentionally public (load balancer / monitoring). Future non-public REST endpoints should use `@PreAuthorize` at the method level on the controller.

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

**Decision:** `StorageService` interface lives in `attachment-spring-boot-starter`. `S3StorageService` and `NoOpStorageService` implementations live in the same module. UI components use `ObjectProvider.ifAvailable()` to degrade gracefully when `attachment.enabled=false`.

**Why:** Storage only exists to serve attachments — there is no use case for storage without attachments. Contract/implementation split keeps domain logic independent of infrastructure.

**Rejected:** Keeping `storage-s3-spring-boot-starter` as a separate module — two modules with a mandatory one-way dependency and no realistic decoupling scenario.

---

## 2026-05-13 — Audit subsystem extracted to audit-spring-boot-starter

**Decision:** The full audit subsystem (write side: `DefaultAuditPort`, `AuditDiffEngine`, `AuditLogRepository`; read side: `AuditHistoryService`, `AuditQueryService`, `ActivityService`, Vaadin audit UI) lives in `audit-spring-boot-starter`. Domain services call `AuditPort` (contract interface). `NoOpAuditPort` is the fallback when `audit.enabled=false`. The starter contains zero advertisement-specific knowledge — all domain coupling is expressed through SPIs (`AuditActorNameResolver`, `AuditEntityExistenceChecker`, `EntityDisplayNameResolver`) implemented in `marketplace-app`.

**Why:** Audit is infrastructure, not domain. Enables deploying audit-free variants. `AuditableSnapshot` marker interface carries `entityType()` — eliminates stringly-typed entity-type strings.

**Rejected:** `@ConditionalOnAuditEnabled` in `platform-contracts` — contracts must be Spring-free pure Java.

---

## 2026-05-21 — SnapshotBinder coupling in marketplace-app UI is intentional

**Decision:** `SettingsOverlay` and `UserViewOverlayModeHandler` import `org.ost.audit.ui.SnapshotBinder` directly. This is a known, accepted coupling — not a decoupling violation to fix.

**Why:** `SnapshotBinder` is a Vaadin/Spring component and cannot live in `platform-contracts` (which must stay framework-free). Extracting a `SnapshotBinder.Builder` SPI to platform-contracts would add complexity with no practical benefit — there is only one implementation and no realistic scenario for swapping it. The dependency direction is correct (`marketplace-app → audit-starter → platform-contracts`); marketplace-app is the consumer and is allowed to reference concrete types from starters it depends on.

**Rejected:** Abstracting `SnapshotBinder.Builder` behind an SPI in `platform-contracts` — over-engineering for a single implementation.
