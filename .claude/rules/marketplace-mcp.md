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
- `org.ost.mcp.rest` — `RestApiClient` (GET against `marketplace.mcp.rest-base-url` through a
  `RestClient`), `CallerAuthorization` (reads the caller's `Authorization` header off the current
  request), `RestPath` (builds a REST path with its query string) and `RestAnswer` (status, body and
  paging headers of one REST response).
- `org.ost.mcp.tools` — the 11 read-only tools in `AccountTools`, `AdvertisementTools`,
  `FeedbackTools`, `ProviderProfileTools` and `TaxonTools`, and `ToolResults`, which turns a
  `RestAnswer` into a tool result (paging fields on success, a readable error on failure).

---

## Key constraints

- No project dependency at all: no `platform-commons`, `marketplace-orchestrator`,
  `marketplace-rest-api`, starter, `marketplace-app` or Vaadin. The module talks to the REST API over HTTP only.
  Enforced by `ArchitectureTest` in this module.
- Forwards the caller's `Authorization` header to the REST API unchanged; it holds no credential
  of its own and never authenticates on the caller's behalf.
- `/mcp` is secured by this module's own chain, never by an exemption in `marketplace-app`; it must stay
  disjoint from the REST API's `/api/**` chains and the application's catch-all chain.
- A tool is named after the REST operation's `operationId` it wraps — those names are stable by
  contract in `marketplace-rest-api`.
