---
paths: ["marketplace-mcp/**"]
---

## marketplace-mcp

MCP server adapter that wraps the marketplace REST API for AI agents over plain HTTP, using the caller's own API key.

Java package root: `org.ost.mcp`

---

## What it owns

- `org.ost.mcp.config` — `McpAutoConfiguration` (`@ComponentScan("org.ost.mcp")`, registered in
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`) and
  `McpProperties` (`marketplace.mcp.rest-base-url`, default `http://localhost:8080`;
  `marketplace.mcp.allowed-origins`, default `http://localhost:*` and `http://127.0.0.1:*`),
  `McpSecurityConfig` — the module's own `@Order(3)` `SecurityFilterChain` for `/mcp` (stateless, no CSRF,
  permits all, since the forwarded API key is checked by the REST API) — and `McpOriginFilter`, added to that
  chain before `CsrfFilter`: no `Origin` passes, a disallowed `Origin` gets 403 (SDK
  `DefaultServerTransportSecurityValidator`); never derive the allowed origin from `Host`.
- `org.ost.mcp.rest` — `RestApiClient` (GET, POST, PUT, PATCH and DELETE against `marketplace.mcp.rest-base-url` through a
  `RestClient`: an optional JSON body, an optional `If-Match`, a multipart upload and a plain-text GET; it never throws on a non-2xx answer),
  `CallerAuthorization` (reads the caller's `Authorization` header off the current request), `RestPath` (builds a REST path with its
  query string), `JsonBody` (serializes a request body and names the accepted content types), `FilePart` (one file of a multipart upload)
  and `RestAnswer` (status, body and headers of one REST response).
- `org.ost.mcp.tools` — one tool for every operation of the REST API, grouped in one class per REST controller, with
  these exceptions: `AccountTools` and `UserTools` both wrap `UserApiController`, `InfrastructureTools` wraps
  `HealthController` and `SitemapController`, and `listAdvertisementMedia` sits in `AdvertisementTools` although it wraps
  `AdvertisementMediaApiController`. The classes are `AccountTools`, `AdvertisementMediaTools`, `AdvertisementTools`, `ApiKeyTools`,
  `AuditTools`, `ContactTools`, `FeedbackTools`, `InfrastructureTools`, `ModerationTools`, `ProviderProfileTools`, `TaxonTools` and
  `UserTools`; and `ToolResults`, which turns a
  `RestAnswer` into a tool result (paging fields, one item with its etag, a list, the parsed body, the outcome of a write, plain text,
  or a readable error).

---

## Key constraints

- No project dependency at all: no `platform-commons`, `marketplace-orchestrator`,
  `marketplace-rest-api`, starter, `marketplace-app` or Vaadin. The module talks to the REST API over HTTP only.
  Classes of this module may depend on no other package of the project, enforced by `ArchitectureTest`.
- Forwards the caller's `Authorization` header to the REST API unchanged; it holds no credential
  of its own and never authenticates on the caller's behalf.
- `/mcp` is secured by this module's own chain, never by an exemption in `marketplace-app`; it must stay
  disjoint from the REST API's `/api/**` chains and the application's catch-all chain.
- A tool is named after the REST operation's `operationId` it wraps — those names are stable by
  contract in `marketplace-rest-api`.
- A tool does what the REST operation does, with the same body, headers and rules (maximum symmetry): a new REST operation gets a
  tool of the same name, and the tool names are guarded two ways, `ToolNamesTest` (the set of names) and the end-to-end test in
  `integration-tests` (the tools are exactly the operation ids of the live OpenAPI). The tool forwards the caller's `Authorization`
  header unchanged, so `issueApiKey` works on a connection made with Basic authentication and answers HTTP 401 on a Bearer key, as REST does.
