# improvement-196: Add per-API-key rate limiting to marketplace-rest-api write endpoints

**Type:** improvement — security/robustness
**Module:** `marketplace-rest-api`, `platform-commons` (reusing/possibly extending `FailureRateLimiter`)
**Priority:** 🟡 High/medium ROI — real, unaddressed abuse surface; proportionate effort (reuse of an
existing class, one filter, config)
**When:** independent, no blockers

## Current state

Verified directly against current code (2026-09-17). `ApiSecurityConfig` (`marketplace-rest-api`)
makes `GET` on `/api/advertisements`, `/api/provider-profiles`, `/api/taxons` public, and
`POST /api/users` public (registration); every other `/api/**` request needs either HTTP Basic or a
bearer API key, resolved by `ApiKeyAuthenticationFilter` — the only filter this module has, and it
does authentication only, nothing else. `ApiKeyController.create()` (`POST /api/api-keys`) lets any
caller who can already authenticate via HTTP Basic (email + password of an existing account)
self-issue a new bearer key, with no approval step and no limit on how many keys or how often.

No rate limiting exists anywhere on `AdvertisementApiController`, `TaxonApiController`,
`ProviderProfileApiController`, or `ApiKeyController` itself (`create`/`revoke`) — confirmed by
grepping the whole module for `FailureRateLimiter`/`TooManyAttemptsException`/any filter or
interceptor. Once a caller holds a bearer key, every create/update/delete endpoint accepts unlimited
request volume.

One correction to the original finding this task is based on: it isn't literally true that *no*
endpoint is rate-limited — `POST /api/users` already inherits `UserService.register()`'s existing
`FailureRateLimiter`-backed limiter, since the REST controller calls the same shared service method
the Vaadin UI does (confirmed via `UserApiControllerTest#register_tooManyAttempts_returns429`,
already green). The real gap is every other endpoint: reads are already public where it's safe, but
every authenticated write endpoint (Advertisement/Taxon/ProviderProfile create/update/delete, and
API-key issuance/revocation itself) has no request-volume limit at all.

`platform-commons/src/main/java/org/ost/platform/core/FailureRateLimiter.java` already exists
(extracted for exactly this class of problem — see this project's own `DECISIONS.md` history around
its extraction) with `checkAllowed(key, message)`/`recordFailure(key)`/`clear(key)`, backed by a
Caffeine sliding-window counter. `ApiExceptionHandler` already maps `TooManyAttemptsException` to
`429 TOO_MANY_REQUESTS` generically — that precedent already covers whatever throws it, no new
exception-handling wiring needed for that part.

## Why change

An account holder who self-issues an API key currently faces zero limit on write-endpoint request
volume — a real resource-exhaustion/abuse surface (cost, load, potential denial-of-service against
shared infrastructure), not a hypothetical one, since key issuance itself requires no approval.

## Expected benefit

Bounded write-request volume per API key, using the same 429 response contract already established
for the UI-facing rate limiters, with no second hand-rolled Caffeine counter (`FailureRateLimiter`
would otherwise have been duplicated a third time after login and registration).

## Approach

**1. Decide the limit shape (needs a decision before implementation, not assumed here):**
- Key the counter on the authenticated principal's key id (or resolved actor id), never the raw key
  or caller IP — a single account can hold multiple keys per `ApiKeyController`.
- One global per-key budget across all of `/api/**`, or a stricter budget on `POST`/`PUT`/`DELETE`
  than on `GET` — recommended: the stricter split, since `GET` on the three list/detail endpoints is
  already public/unauthenticated (no key involved) and the real abuse surface is the authenticated
  write path.
- Response on limit-exceeded: `429 Too Many Requests` (via the existing `TooManyAttemptsException` →
  `ApiExceptionHandler` mapping) plus a `Retry-After` header — not currently set anywhere, would be a
  small addition to that handler.

**2. Enforcement point:** a single `OncePerRequestFilter` registered for `/api/**`, added after
`ApiKeyAuthenticationFilter` in the chain so the resolved principal/key id is already available —
mirrors how `ApiSecurityConfig` already centralizes auth for this module instead of scattering
per-controller checks.

**3. Reuse vs. extend `FailureRateLimiter`:** its current shape is failure-counting-specific
(`recordFailure` increments only on a caught failure — login/registration failures). A request-volume
limiter needs to increment on every request regardless of outcome. Check whether `recordFailure`'s
existing semantics can be reused as-is by simply calling it unconditionally per request (likely
workable, since the method itself has no failure-specific logic — it just increments), or whether a
small, additive change (e.g. a plain `increment(key)` alias) is cleaner than overloading
`recordFailure`'s name for a non-failure meaning. Do not fork a second Caffeine-based class — if
extension genuinely doesn't fit, propose a shared base rather than duplicating.

**4. Configuration:** limit values as `@ConfigurationProperties` (e.g. a
`marketplace.rest-api.rate-limit.*` prefix), not hardcoded constants, so `dev`/`prod` can differ
without a code change — matching how other environment-specific tunables already live in
`application-*.yml`.

**5. Tests:**
- A unit test for the filter/limiter logic (mirrors `AuthServiceTest`/`UserServiceTest`'s existing
  threshold-crossing shape).
- A `marketplace-rest-api` test (mirrors `ApiExceptionHandlerTest`'s shape) hitting a real endpoint
  past the threshold via `MockMvc` and asserting `429` — proving the filter is actually wired into
  the request path, not just unit-tested in isolation.
- Swagger/OpenAPI `@ApiResponse(responseCode = "429", ...)` on the affected write endpoints, matching
  `AdvertisementApiController`'s existing documentation density.

**6. Record the decision** in `marketplace-rest-api/DECISIONS.md` via `/record-decision` once
implemented — Context/Decision/Consequences, including why the chosen limit values are what they are.

## Constraints

- Do not touch `AuthService.login()`/`UserService.register()` — their rate limiting is a separate,
  already-correct mechanism; this task only adds a new enforcement point for `/api/**`'s other
  endpoints.
- Do not weaken `FailureRateLimiter`'s existing behavior for its current callers while adapting it —
  if extension isn't clean, a shared base/interface is the fallback, not bending the existing class.
- Stays out of `platform-commons`'s `*Port`/`*Hook` SPI surface — this is REST-adapter-specific
  infrastructure, not a cross-domain contract.

## Related

- `platform-commons/src/main/java/org/ost/platform/core/FailureRateLimiter.java` — the class this
  task reuses (extracted from `AuthService`/`UserService`'s previously-duplicated rate limiters).
- `marketplace-app/DECISIONS.md` — the existing rate-limiting ADRs (counts only real failures, never
  successes; rate limiting keyed off the real client IP) this task's design should stay consistent
  with where applicable.
- `marketplace-rest-api/src/main/java/org/ost/restapi/api/error/ApiExceptionHandler.java` — already
  maps `TooManyAttemptsException` to 429; this task's filter should throw that same exception type
  rather than introducing a second one.
