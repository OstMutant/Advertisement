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

## Ongoing — Modular storage: contract vs implementation

**Decision:** `storage-api` defines the contract; `storage-s3-spring-boot-starter` is the S3 implementation. UI components use `ObjectProvider.ifAvailable()` to degrade gracefully when `storage.s3.enabled=false`.

**Why:** Allows running the app locally without S3. The contract/implementation split keeps domain logic independent of infrastructure.
