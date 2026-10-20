---
paths: ["marketplace-rest-api/**"]
---

## marketplace-rest-api

External REST API adapter — a non-Vaadin HTTP delivery channel over `marketplace-orchestrator`,
sibling to `marketplace-app` rather than a domain starter. No persistence, no Vaadin dependency —
same shape as `marketplace-orchestrator`. `marketplace-app` depends on it as a mandatory
compile-scope dependency (same pattern as its `marketplace-orchestrator` dependency) and stays the
sole `@SpringBootApplication` entry point — one deployable jar, no runtime/deployment split.

Java package root: `org.ost.restapi`

---

## What it owns

- `HealthController`, `SitemapController` (top-level `org.ost.restapi`) — trivial, unauthenticated
  endpoints
- `org.ost.restapi.api` — the authenticated external-API family: `ApiKeyController` (its key list hides revoked keys unless `includeRevoked=true`),
  `UserApiController`, `AdvertisementApiController`, `ProviderProfileApiController`,
  `TaxonApiController`, `AuditApiController`, `ContactApiController`, `FeedbackApiController`. Each controller's own request/response records that no
  other class needs are nested as public static records inside that controller (e.g.
  `ApiKeyController.ApiKeyCreateRequest`/`ApiKeyCreatedResponse`,
  `TaxonApiController.TaxonCreateRequest`/`TaxonUpdateRequest`/`TaxonTranslationRequest`,
  `UserApiController.UserCreatedResponse`/`UserSettingsWriteRequest`/`UserUpdateRequest`/
  `LocaleWriteRequest`,
  `AdvertisementApiController.AdvertisementWriteRequest`,
  `ProviderProfileApiController.ProviderProfileWriteRequest` — the two `*WriteRequest` shapes carry
  no `id`/`version` field at all, server-managed via the path variable / `If-Match` header instead)
  rather than living as separate top-level files. Snapshot restore endpoints live in the same
  controllers: `POST …/history/{snapshotId}/restore` on advertisements, provider profiles, taxons and
  users, `POST /api/users/me/settings/history/{snapshotId}/restore` for the caller's own settings,
  and `POST /api/taxons/{id}/restore` for a soft-deleted taxon. Every snapshot restore requires
  `If-Match` (the current version); the restore itself is delegated to `SnapshotRestoreService`
  in `marketplace-orchestrator`.
- `org.ost.restapi.api.error` — `ApiExceptionHandler` (`@RestControllerAdvice`) plus its two
  response records, `ErrorResponse`/`ValidationErrorResponse` — kept in their own package rather
  than nested, since the advice maps exceptions from every controller in this module, not owned by
  any single one. `TooManyAttemptsException` maps to 429, with `Retry-After` taken from the
  exception's window when it carries one.
- `org.ost.restapi.api.concurrency` — `ETagUtil`, the sole conversion point between a resource's
  `version` and the HTTP `ETag`/`If-Match` header representation: `withVersion()` sets a response's
  `ETag` (skipped when the version is null), `parseIfMatch()` reads the expected version back off an
  incoming `If-Match` request header. Used by every controller's read (`ETag`) and write
  (`If-Match`) endpoints in `org.ost.restapi.api` — `id`/`version` are never caller-writable body
  fields, only path variable / header.
- `org.ost.restapi.api.paging` — shared list-endpoint plumbing, reused by
  `AdvertisementApiController`/`ProviderProfileApiController`'s `list()` methods: `SortQueryParser`
  (parses `?sort=field,dir` against a per-controller allow-list built from that domain's own
  response DTO's `Fields.*` constants — an unknown field throws `IllegalArgumentException`, mapped
  to 400 by `ApiExceptionHandler`), `PageLinkHeaderBuilder` (builds the RFC 8288 `Link` header),
  `PagedResponseBuilder` (assembles the final `ResponseEntity` — body plus `X-Total-Count` and
  `Link`). `TaxonApiController.list()` is reference data only — always the full matching set, no
  filter/sort/pagination query params, no dependency on this package.
- `org.ost.restapi.api.audit` — `ActivityHistorySupport`, a Spring component over `AuditQueryService`
  that is the shared read side of every resource's history; its nested records `HistoryEntryResponse`
  and `SnapshotResponse` are the history response shapes. Reused by the `/history` methods of
  `AdvertisementApiController`, `ProviderProfileApiController`, `TaxonApiController` and
  `UserApiController` (including `/me/settings/history`), so no resource maps audit data itself.
- `org.ost.restapi.config` — `ApiSecurityConfig` (two `SecurityFilterChain`s: `@Order(1)` matching
  `POST /api/api-keys` only, the one chain with HTTP Basic, and `@Order(2)` for the rest of
  `/api/**`, bearer-only, which also requires authentication for `*/history` and `*/history/*` under
  advertisements, provider-profiles and taxons ahead of their public GET rules, lets `POST …/contact/reveals` through publicly, and requires authentication for `GET …/provider-profiles/*/contact/views`; both coexist with
  `marketplace-app`'s own Vaadin security chain via Spring Security's ordered multi-chain matching), `BasicLoginRateLimitFilter` (blocks a client
  address and email with 429 after 5 failed Basic logins in 15 minutes, on the key-issuance chain),
  `BearerChallengeEntryPoint` (answers an unauthenticated `/api/**` request with 401 and a Bearer
  challenge), `ApiKeyAuthenticationFilter` (resolves `Authorization: Bearer <key>` into
  a `PreAuthenticatedAuthenticationToken` carrying a plain `Long` user id — the standard Spring
  Security type for an identity already verified by an external mechanism), `OpenApiConfig`
  (declares the `bearerKey`/`basicAuth` `@SecurityScheme`s springdoc renders in Swagger UI, matching
  the two chains' real authentication)

**Autoconfiguration entry point:** `RestApiAutoConfiguration` (`@ComponentScan("org.ost.restapi")`)

**OpenAPI/Swagger:** `springdoc-openapi-starter-webmvc-ui` generates the spec live from these
controllers/DTOs — `GET /v3/api-docs` (spec JSON), `GET /swagger-ui/index.html` (interactive UI).
Every authenticated endpoint carries `@SecurityRequirement(name = "bearerKey")` or `"basicAuth"`
(key-issuance only) so Swagger UI's own "Authorize" flow matches the real auth model per-endpoint. Swagger UI persists the entered authorization in the browser (`springdoc.swagger-ui.persist-authorization`).

---

## Key constraints

- Zero direct `*Port`/`*Hook` usage from `platform-commons` — cross-domain composition routes
  through `marketplace-orchestrator` only, same rule `marketplace-app` follows (`AuthenticatedPrincipal`
  is the one allow-listed exception, same as `marketplace-app`). Enforced by
  `ArchitectureRulesTest.marketplace_app_must_not_depend_on_platform_commons_spi_directly`, scoped
  to also cover `org.ost.restapi..`.
- Must not import a starter's internal `util`/`services`/`repository` package — only
  `platform-commons` contracts. Enforced by
  `ArchitectureRulesTest.marketplace_must_not_import_starter_internals`, scoped to also cover
  `org.ost.restapi..`.
- `ApiKeyAuthenticationFilter` only resolves a bearer key when no authentication already exists on
  the security context — it never overwrites an authentication another mechanism already
  established.
- HTTP Basic is accepted only by the key-issuance chain: `POST /api/api-keys` takes
  `@AuthenticationPrincipal AuthenticatedPrincipal` (HTTP Basic) while every other authenticated
  endpoint takes `@AuthenticationPrincipal Long actorId` (bearer key). A Basic header anywhere else
  is ignored and the request is answered 401; a Bearer header on the key endpoint is answered 401
  with a Basic challenge. Never re-enable `httpBasic` on the general `/api/**` chain — a Basic
  principal there reaches bearer-only methods as a null id.
- Hidden feedback (a review or a reply whose moderation status is hidden) is returned without its
  text, and a hidden review also without its rating, for every caller including its author -- the
  same censored placeholder the Vaadin feedback panel shows.
- Any new endpoint under a prefix that the general chain makes public for GET (advertisements,
  provider-profiles, taxons) must get its own `authenticated()` rule ahead of the public one, because
  a public match leaves the controller without a user id.
