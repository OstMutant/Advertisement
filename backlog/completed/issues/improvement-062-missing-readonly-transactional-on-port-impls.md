# improvement-062: `UserPortImpl`/`AdvertisementPortImpl`/`DefaultTaxonPort` have no `@Transactional(readOnly = true)`, unlike `DefaultAuditPort`

**Type:** improvement — consistency/read-correctness. Found via direct code review, verified
against current source (2026-07-16).
**Module:** `user-spring-boot-starter` (`UserPortImpl`), `advertisement-spring-boot-starter`
(`AdvertisementPortImpl`), `taxon-spring-boot-starter` (`DefaultTaxonPort`).
**Priority:** low-medium — no known bug from this today (each Port method's individual repository
calls already run under Postgres's own connection-level transaction), but a genuine inconsistency
with the established `DefaultAuditPort` pattern, and Port methods that make more than one
repository call have no guarantee of a single consistent read transaction across them.
**When:** independent, no blockers.

## Problem

`DefaultAuditPort` has class-level `@Transactional(readOnly = true)` with individual `@Transactional`
overrides on its write methods:
```java
@Transactional(readOnly = true)
public class DefaultAuditPort implements AuditPort { ... }
```
`UserPortImpl`, `AdvertisementPortImpl`, and `DefaultTaxonPort` have no `@Transactional` annotation
at all, anywhere in their classes — confirmed via direct grep, not assumed.

Note: this is **not** a Hibernate/JPA dirty-checking concern (this project uses pure Spring Data
JDBC — `JdbcClient`/`NamedParameterJdbcTemplate`, no JPA session cache exists to avoid dirty-checking
overhead on). The real, applicable benefit is narrower: any Port method that makes more than one
repository call currently has no guarantee those calls execute against one consistent read
snapshot/connection rather than several independent auto-committed ones, and some connection
pools/drivers can apply minor optimizations to a connection explicitly marked read-only.

## Suggested fix

Add class-level `@Transactional(readOnly = true)` to `UserPortImpl`, `AdvertisementPortImpl`, and
`DefaultTaxonPort`, matching `DefaultAuditPort`'s existing pattern, with per-method `@Transactional`
(non-read-only) overrides on any write methods in each class — same shape `DefaultAuditPort` already
uses.

## Related

- `audit-spring-boot-starter/src/main/java/org/ost/audit/services/DefaultAuditPort.java` —
  the reference pattern this issue proposes matching.
