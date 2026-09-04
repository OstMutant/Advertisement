---
paths: ["apikey-spring-boot-starter/**"]
---

## apikey-spring-boot-starter

Auto-configures the API-key credential domain. Active whenever the jar is on the classpath.

Java package root: `org.ost.apikey`

---

## What it owns

- `ApiKey` entity + `ApiKeyRepository`/`ApiKeyCrudRepository` — SHA-256 key hash, prefix, label,
  timestamps
- `ApiKeyHasher` (`org.ost.apikey.security`) — `generate()` (32-byte `SecureRandom`, base64url) and
  `hash()` (SHA-256) for the raw key
- `ApiKeyService` — issue/resolve/list/revoke/`deleteAllForActor`, single-domain credential logic
  with no cross-domain composition
- `ApiKeyPortImpl` — implements `ApiKeyPort`; thin delegation to `ApiKeyService`

**Autoconfiguration entry point:** `ApiKeyAutoConfiguration`

---

## Schema

Liquibase changelog: `db/apikey-changelog/apikey-changelog-master.xml`
Tables: `api_key` (`actor_id`, **no FK** — matches this codebase's actor-reference-column
convention; a user's key rows are removed via `ApiKeyPort.deleteAllForActor`, called from
`marketplace-orchestrator`'s `UserDeleteService`, not a DB-level cascade)

---

## Key constraints

- `ApiKeyPort`/`ApiKeySummaryDto` live in `platform-commons`'s own `apikey.spi`/`apikey.dto`
  packages — matching every other domain with its own Maven starter module.
- Not folded into `user-spring-boot-starter` despite the natural credential-domain fit: this
  starter is consumed only by `marketplace-rest-api` (the external REST API's only auth
  mechanism), never by a Vaadin flow — a real starter is still the only shape that adds zero
  exceptions to the persistence-stays-in-starters invariant (`marketplace-orchestrator`'s
  `orchestrator_has_no_persistence_access` ArchUnit rule).
- `apikeyLiquibase` bean has no `@DependsOn` on `userLiquibase` — `api_key.actor_id` carries
  no FK, so this starter's own changelog can run against a database that has never seen
  `user-spring-boot-starter` at all.
- `ApiKeyPortImpl` is pure delegation — no business logic inside the port.
- `touchLastUsed()` is deliberately best-effort: it catches and logs any failure rather than
  propagating, since a failure to update `last_used_at` must never break the request the key is
  authenticating.
