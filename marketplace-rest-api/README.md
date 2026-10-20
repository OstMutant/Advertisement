# marketplace-rest-api

The external, non-Vaadin REST API adapter — a sibling to `marketplace-app` rather than a domain
starter, since it delivers the same `marketplace-orchestrator` use cases over plain HTTP instead of
Vaadin's server-push UI. No persistence, no Vaadin dependency, same shape as
`marketplace-orchestrator` itself.

## What it provides

- Full CRUD over advertisements and provider profiles, category/city catalog management, plus user
  self-registration (the response says which role the caller got), admin/moderator-only user listing,
  administrator account management (change a name or role, delete an account) and a caller's own
  language switch — the same `marketplace-orchestrator`
  services `marketplace-app`'s own Vaadin forms call, so a REST write and a UI write go through
  identical validation/authorization/audit behavior.
- Bearer API-key authentication (issuance gated by HTTP Basic) for every write; reads are public,
  mirroring the existing public Vaadin browsing experience. The caller's own key list hides revoked
  keys unless `includeRevoked=true` is passed, which returns them with a non-null `revokedAt`.
- Optimistic concurrency over HTTP's own mechanism: every `GET .../{id}` returns an `ETag` response
  header carrying the resource's version; every write reads the expected version back from an
  `If-Match` request header — `id`/`version` are never caller-writable body fields.
  `PATCH /api/users/me/settings` lets a caller change their own saved pagination-size preferences
  the same way.
- Read-only audit history: a global timeline for administrators and moderators
  (`GET /api/audit/timeline`, filtered and paged), and a `/history` sub-collection on advertisements,
  provider profiles, taxons, users and a caller's own settings, newest first. Administrators and
  moderators see every entry, anyone else only the entries they authored. Each entry can be read as a
  snapshot (`.../history/{snapshotId}`) carrying the recorded state and its version. `POST
  .../history/{snapshotId}/restore` (with `If-Match`) returns the resource to that state through the
  normal save path; a snapshot outside the caller's visible activity is `403`. A soft-deleted taxon
  is restored with `POST /api/taxons/{id}/restore`, which answers `409` when the taxon is not deleted.
- Contacts: a public list of which channels (phone, Telegram, Viber) an advertisement or provider
  profile has, and a public reveal of one channel that records the view and, for Telegram and Viber,
  returns a deep link that opens the chat; reveals are rate-limited per client address and viewer.
  View counts per channel for the current month are readable by the owner, administrators and
  moderators.
- Provider-profile feedback: a public, paged list (newest first) and rating summary, one review per user and profile, edits by the author within the edit window, one reply per review written by the profile's owner, and flags that hide a review or reply pending moderation. A hidden review or reply is returned without its text (and a hidden review without its rating).
- A live OpenAPI 3 spec and Swagger UI, generated from these same controllers/DTOs, with
  per-operation descriptions and request examples. The key entered through Authorize is kept in the
  browser across page reloads (persistent authorization), so one issued key can be reused.

## Data flow

Every request enters through one of two `ApiSecurityConfig` filter chains. `POST /api/api-keys` has
its own chain, the only one that accepts HTTP Basic, where `BasicLoginRateLimitFilter` first blocks
a client address and email after 5 failed logins in 15 minutes. Every other `/api/**` request goes
through the second chain: `ApiKeyAuthenticationFilter` resolves an `Authorization: Bearer <key>`
header into a `PreAuthenticatedAuthenticationToken` before Spring's own authorization check runs,
a Basic header is ignored, and a request without a valid key is answered 401 with a Bearer
challenge. From there, each of the five per-resource controllers
(`AdvertisementApiController`, `ProviderProfileApiController`, `TaxonApiController`,
`UserApiController`, `ApiKeyController`) calls straight into the same `marketplace-orchestrator`
service its Vaadin-side counterpart already uses — no intermediate service layer of its own.
`AuditApiController` serves the global timeline, and the `/history` methods of the four resource
controllers read through `ActivityHistorySupport` (`api.audit`) instead of mapping audit data
themselves. A write endpoint builds its save DTO from a nested request record carrying no `id`/`version` field at all
(`id` from the path variable on update, `version` parsed from the `If-Match` header via
`concurrency.ETagUtil`); a read endpoint attaches the resource's version as an `ETag` response
header the same way. Any exception a controller or the orchestrator service throws is caught
centrally by `ApiExceptionHandler` (`api.error`, `@RestControllerAdvice` scoped to
`org.ost.restapi.api`) and mapped to the matching HTTP status. `OpenApiConfig` declares the two auth
schemes Swagger UI's "Authorize" button offers, matching `ApiSecurityConfig`'s real dual-auth shape;
springdoc itself generates the rest of the spec live from the controllers/DTOs, no hand-written copy
to keep in sync.

## Dependencies

- `marketplace-orchestrator` — every controller composes results from this module's services only,
  never a domain `*Port` directly (`AuthenticatedPrincipal` is the one allow-listed
  `platform-commons` exception, same as `marketplace-app`).
- `springdoc-openapi-starter-webmvc-ui` — generates `/v3/api-docs`/`/swagger-ui/index.html` live
  from the controllers/DTOs already here; no hand-written endpoint descriptions to keep in sync.
- No dependency on any starter's internal `util`/`services`/`repository` package — enforced by
  ArchUnit the same way as `marketplace-app` (`marketplace_must_not_import_starter_internals`,
  `marketplace_app_must_not_depend_on_platform_commons_spi_directly`, both scoped to also cover
  this module's own `org.ost.restapi..` packages).
