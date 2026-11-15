---
paths: ["promotion-spring-boot-starter/**"]
---

## promotion-spring-boot-starter

Auto-configures the Promotion domain. Active whenever the jar is on the classpath.

Java package root: `org.ost.promotion`

---

## What it owns

- `PromotionPlacementRepository` — bespoke `JdbcClient` queries over `promotion_placement` (the promoted
  rows of an entity type for every reason known to this version, delete the rows of a reason missing
  from a set, insert the missing ones, delete one row); there is no trivial save or find, so no entity class and no
  `CrudRepository`
- `PromotionPlacementService` — reads and the three writes, each write in one transaction
- `PromotionPlacementPortImpl` — implements `PromotionPlacementPort`; thin delegation to
  `PromotionPlacementService`

**Autoconfiguration entry point:** `PromotionAutoConfiguration`

---

## Schema

Liquibase changelog: `db/promotion-changelog/promotion-changelog-master.xml`
Table: `promotion_placement` (one row per promoted entity and reason, enforced by the unique
`idx_promotion_placement_entity_reason` index; a row exists while the entry is promoted, and its writer
removes the row when it no longer is)

---

## Key constraints

- `PromotionPlacementPort`/`PromotionPlacementDto`/`PromotionReason` live in `platform-commons`'s own
  `promotion.spi`/`promotion.dto`/`promotion.model` packages.
- No Maven dependency on any sibling starter (enforced by this module's own `maven-enforcer-plugin`
  `bannedDependencies` rule) — the table is keyed by entity type and id with no foreign key, and
  nothing here knows why an entity qualifies.
- Each reason has exactly one writer; every write is scoped to one reason, so replacing the rows of
  one reason never touches another's. An empty set given to a replace removes every row of that
  reason, so callers do not guard against it.
- `PromotionAutoConfiguration` declares `@EnableJdbcRepositories` like every starter that ships a
  repository, although there is no `CrudRepository` here: the shared test auto-configuration always
  loads Spring Data JDBC and needs the base package.
- The read of promoted rows selects only the reasons this version's `PromotionReason` knows, so a row left
  by another version never fails the read that every list request depends on.
- `PromotionPlacementPortImpl` is pure delegation — no business logic inside the port.
