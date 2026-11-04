---
paths: ["contact-spring-boot-starter/**"]
---

## contact-spring-boot-starter

Auto-configures the Contact domain. Active whenever the jar is on the classpath.

Java package root: `org.ost.contact`

---

## What it owns

- `ContactInfo` entity (`contact_info` — phone/telegram/viber for an owning entity) +
  `ContactView` entity (`contact_view` — append-only reveal/click log)
- `ContactRepository` — bespoke `JdbcClient` queries (`findByEntity`, `recordView`,
  `countViewsThisMonth`, `deleteByEntity`); trivial CRUD delegates to
  `ContactInfoCrudRepository`/`ContactViewCrudRepository`
- `ContactService` — `find`/`save` (upsert keyed by `(entityType, entityId)`, there is no
  caller-known `id` to key on directly), `recordView`, `countViewsThisMonth`, `delete`
- `ContactPortImpl` — implements `ContactPort`; thin delegation to `ContactService`

**Autoconfiguration entry point:** `ContactAutoConfiguration`

---

## Schema

Liquibase changelog: `db/contact-changelog/contact-changelog-master.xml`
Tables: `contact_info` (at most one row per `(entity_type, entity_id)`, enforced by the unique
`idx_contact_info_entity` index), `contact_view` (append-only, one row per reveal/click, read via a
`GROUP BY channel` monthly aggregate — `idx_contact_view_entity`)

---

## Key constraints

- `ContactPort`/`ContactInfoDto`/`ContactViewCountDto`/`ContactChannel` live in `platform-commons`'s
  own `contact.spi`/`contact.dto`/`contact.model` packages — matching every other domain with its
  own Maven starter module.
- No Maven dependency on any sibling starter (enforced by this module's own
  `maven-enforcer-plugin` `bannedDependencies` rule) — resolution stays entity-type/entity-id
  generic, never a direct call into `provider-profile-spring-boot-starter`/
  `advertisement-spring-boot-starter`.
- `ContactRepository.save` translates Spring Data JDBC's `OptimisticLockingFailureException` into
  `StaleWriteException` on a stale `version`.
- `ContactPortImpl` is pure delegation — no business logic inside the port.
- Split out as its own bounded context rather than folded into
  `provider-profile-spring-boot-starter`/`advertisement-spring-boot-starter` (the same reasoning
  `audit-spring-boot-starter` is its own module for): the concern is shared across those two
  domains, and either one owning it would mean the other reaching across the sibling-starter
  boundary to resolve a fallback.
