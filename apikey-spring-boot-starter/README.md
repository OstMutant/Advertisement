# apikey-spring-boot-starter

Auto-configures the API-key credential domain — issuance, resolution, listing, and revocation of
per-user bearer API keys, the sole authentication mechanism for the external
`marketplace-rest-api` module (see `marketplace-app/DECISIONS.md` for why a long-lived bearer key
was chosen over OAuth2 for this).

## What it provides

- Key issuance (`create`) — generates a high-entropy raw key, stores only its SHA-256 hash, and
  returns the raw value exactly once (never retrievable again).
- Key resolution (`resolveActorId`) — hashes an incoming raw key and looks up its owner, used on
  every bearer-authenticated request; best-effort `last_used_at` touch on a successful resolve.
- Listing (`listForActor`), revocation (`revoke`), and bulk cleanup (`deleteAllForActor`, called by
  `UserDeleteService` when the owning account is deleted), all scoped to a single actor's own keys.
- **SPI implementation:** `ApiKeyPort` (called by `marketplace-orchestrator`'s
  `ApiKeyManagementService`/`UserDeleteService`, which `marketplace-rest-api`'s `ApiKeyController`/
  `ApiKeyAuthenticationFilter` go through — this starter is never imported directly by either).

## Data flow

Every operation enters through `ApiKeyPortImpl`, the sole implementation of `ApiKeyPort` — it
delegates every call, unchanged, straight to `ApiKeyService`, the module's only business-logic
class.

- **Issuance:** `ApiKeyPort.create(actorId, label)` → `ApiKeyService.create` calls
  `ApiKeyHasher.generate()` for a raw 32-byte `SecureRandom` key, hashes it (`ApiKeyHasher.hash`),
  and persists an `ApiKey` row (hash, prefix, label, actor id) via `ApiKeyRepository.save` — the
  raw key is returned to the caller and never stored or logged.
- **Resolution:** `ApiKeyPort.resolveActorId(rawKey)` → `ApiKeyService.resolveActorId` hashes the
  incoming raw key, looks it up via `ApiKeyRepository.findActiveByKeyHash`, and on a hit fires a
  best-effort `ApiKeyRepository.touchLastUsed` before returning the owning actor id.
- **Listing/revocation/cleanup:** `ApiKeyPort.listForActor`/`revoke`/`deleteAllForActor` →
  `ApiKeyService` → the matching `ApiKeyRepository` query, each scoped to the calling actor id;
  `ApiKey.toSummaryDto()` shapes each row into the caller-facing `ApiKeySummaryDto` (never exposes
  the hash or raw key).

## Schema

Liquibase changelog: `db/apikey-changelog/`. Table: `api_key` — `actor_id` with **no FK**
(matches this codebase's actor-reference-column convention; a deleted actor's key rows are removed
via `ApiKeyPort.deleteAllForActor`, an application-level cascade, not a DB-level one), `key_hash`
(unique), `key_prefix` (first ~10 chars in the clear, for a human-readable list without ever
re-showing the secret), `label`, `created_at`, `last_used_at`, `revoked_at`.

## Dependencies

- `platform-commons` — `ApiKeyPort`/`ApiKeySummaryDto`, in their own `apikey.spi`/`apikey.dto`
  packages, matching every other domain with its own Maven starter module.
- No dependency on `user-spring-boot-starter` — `api_key.actor_id` carries no FK, so this
  starter's own Liquibase changelog can run against a database that has never seen
  `user-spring-boot-starter` at all.
- Consumed only by `marketplace-rest-api` (via `ApiKeyPort`, never directly) and
  `marketplace-orchestrator` (account-deletion cascade) — no Vaadin flow ever issues or checks an
  API key, which is why this stayed its own starter rather than folding into
  `user-spring-boot-starter` despite the natural credential-domain fit.
