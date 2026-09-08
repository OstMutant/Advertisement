# marketplace-rest-api

The external, non-Vaadin REST API adapter — a sibling to `marketplace-app` rather than a domain
starter, since it delivers the same `marketplace-orchestrator` use cases over plain HTTP instead of
Vaadin's server-push UI. No persistence, no Vaadin dependency, same shape as
`marketplace-orchestrator` itself.

## What it provides

- Full CRUD over advertisements and provider profiles, category/city catalog management, plus user
  self-registration and admin/moderator-only user listing — the same `marketplace-orchestrator`
  services `marketplace-app`'s own Vaadin forms call, so a REST write and a UI write go through
  identical validation/authorization/audit behavior.
- Bearer API-key authentication (issuance gated by HTTP Basic) for every write; reads are public,
  mirroring the existing public Vaadin browsing experience.
- Optimistic concurrency over HTTP's own mechanism: every `GET .../{id}` returns an `ETag` response
  header carrying the resource's version; every write reads the expected version back from an
  `If-Match` request header — `id`/`version` are never caller-writable body fields.
  `PATCH /api/users/me/settings` lets a caller change their own saved pagination-size preferences
  the same way.
- A live OpenAPI 3 spec and Swagger UI, generated from these same controllers/DTOs, with
  per-operation descriptions and request examples.

## Data flow

Every request enters through `ApiSecurityConfig`'s `/api/**` filter chain: `ApiKeyAuthenticationFilter`
resolves an `Authorization: Bearer <key>` header into a `PreAuthenticatedAuthenticationToken` —
skipped when a request already carries HTTP Basic authentication (the key-issuance endpoint) —
before Spring's own authorization check runs. From there, each of the five per-resource controllers
(`AdvertisementApiController`, `ProviderProfileApiController`, `TaxonApiController`,
`UserApiController`, `ApiKeyController`) calls straight into the same `marketplace-orchestrator`
service its Vaadin-side counterpart already uses — no intermediate service layer of its own. A write
endpoint builds its save DTO from a nested request record carrying no `id`/`version` field at all
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
