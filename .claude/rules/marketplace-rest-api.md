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
- `org.ost.restapi.api` — the authenticated external-API family: `ApiKeyController`,
  `UserRegistrationController`, `AdvertisementApiController`, `ProviderProfileApiController`,
  `TaxonApiController`. Each controller's own request/response records that no other class needs
  are nested as public static records inside that controller (e.g.
  `ApiKeyController.ApiKeyCreateRequest`/`ApiKeyCreatedResponse`,
  `TaxonApiController.TaxonCreateRequest`/`TaxonUpdateRequest`/`TaxonTranslationRequest`,
  `UserRegistrationController.UserCreatedResponse`) rather than living as separate top-level files.
- `org.ost.restapi.api.error` — `ApiExceptionHandler` (`@RestControllerAdvice`) plus its two
  response records, `ErrorResponse`/`ValidationErrorResponse` — kept in their own package rather
  than nested, since the advice maps exceptions from every controller in this module, not owned by
  any single one.
- `org.ost.restapi.config` — `ApiSecurityConfig` (the `/api/**` `SecurityFilterChain`, `@Order(1)`,
  coexisting with `marketplace-app`'s own Vaadin security chain via Spring Security's ordered
  multi-chain matching), `ApiKeyAuthenticationFilter` (resolves `Authorization: Bearer <key>` into
  a `PreAuthenticatedAuthenticationToken` carrying a plain `Long` user id — the standard Spring
  Security type for an identity already verified by an external mechanism), `OpenApiConfig`
  (declares the `bearerKey`/`basicAuth` `@SecurityScheme`s springdoc renders in Swagger UI, matching
  `ApiSecurityConfig`'s real dual-auth shape)

**Autoconfiguration entry point:** `RestApiAutoConfiguration` (`@ComponentScan("org.ost.restapi")`)

**OpenAPI/Swagger:** `springdoc-openapi-starter-webmvc-ui` generates the spec live from these
controllers/DTOs — `GET /v3/api-docs` (spec JSON), `GET /swagger-ui/index.html` (interactive UI).
Every authenticated endpoint carries `@SecurityRequirement(name = "bearerKey")` or `"basicAuth"`
(key-issuance only) so Swagger UI's own "Authorize" flow matches the real auth model per-endpoint.

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
  the security context — it never overwrites an authentication another mechanism (HTTP Basic, on
  the key-issuance endpoint) already established.
- The key-issuance endpoint (`POST /api/api-keys`) is the one place in this module with dual
  authentication: it accepts `@AuthenticationPrincipal AuthenticatedPrincipal` (HTTP Basic) while
  every other authenticated endpoint accepts `@AuthenticationPrincipal Long actorId` (bearer key) —
  a deliberate, narrow asymmetry reflecting that this is genuinely the only endpoint needing a
  human login rather than an already-issued key.
