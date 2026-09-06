# marketplace-rest-api

The external, non-Vaadin REST API adapter — a sibling to `marketplace-app` rather than a domain
starter, since it delivers the same `marketplace-orchestrator` use cases over plain HTTP instead of
Vaadin's server-push UI. No persistence, no Vaadin dependency, same shape as
`marketplace-orchestrator` itself.

## What it provides

- Full CRUD over advertisements, provider profiles, and the taxon catalog, plus user
  self-registration — the same `marketplace-orchestrator` services `marketplace-app`'s own Vaadin
  forms call, so a REST write and a UI write go through identical validation/authorization/audit
  behavior.
- Bearer API-key authentication (issuance gated by HTTP Basic) for every write; reads are public,
  mirroring the existing public Vaadin browsing experience.
- A live OpenAPI 3 spec and Swagger UI, generated from these same controllers/DTOs.

## Key classes

| Class | Role |
|---|---|
| `ApiSecurityConfig` | The `/api/**` `SecurityFilterChain` (`@Order(1)`) — coexists with `marketplace-app`'s own Vaadin security chain via Spring Security's ordered multi-chain matching, never edits that chain. |
| `ApiKeyAuthenticationFilter` | Resolves `Authorization: Bearer <key>` into a `PreAuthenticatedAuthenticationToken` — only when no authentication already exists on the context, so it never overwrites HTTP Basic on the key-issuance endpoint. |
| `OpenApiConfig` | Declares the `bearerKey`/`basicAuth` `@SecurityScheme`s Swagger UI's own "Authorize" button reads — kept separate from `ApiSecurityConfig` since one configures real enforcement and the other only documentation metadata. |
| `ApiExceptionHandler` (`api.error`) | Maps every exception this module's controllers can throw to an HTTP status — the one place that needs visibility across all five controllers, which is why it (and its two response records) live in their own package instead of being nested into any single controller. |
| Per-resource controllers (`AdvertisementApiController`, `ProviderProfileApiController`, `TaxonApiController`, `UserApiController`, `ApiKeyController`) | Each wraps exactly the `marketplace-orchestrator` service(s) its own Vaadin-side counterpart already uses; each owns its own request/response records as nested static types, since none of those shapes are reused outside their controller. |

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
