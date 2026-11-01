# marketplace-mcp

The MCP (Model Context Protocol) server adapter of the marketplace — a sibling to
`marketplace-rest-api` that lets AI agents work with the marketplace through the same public REST
API every other client uses, instead of a second path into the domain. It is a pure HTTP client of
that API: no persistence, no Vaadin, and no project dependency at all.

## What it provides

- An auto-configured MCP server over Spring MVC (Streamable HTTP), registered through Spring Boot's
  auto-configuration import file, so any application that puts this module on its classpath exposes
  the endpoint without extra wiring.
- Its own stateless security chain for `/mcp`: no CSRF protection and every request permitted, because the
  endpoint is authenticated by the forwarded API key, which `marketplace-rest-api` checks, never by a cookie.
- Eleven read-only tools in five classes, each named after the REST `operationId` it wraps:
  `AccountTools` (`getMe`, `getMySettings`), `AdvertisementTools` (`listAdvertisements`,
  `getAdvertisement`, `listAdvertisementMedia`), `FeedbackTools` (`listProviderProfileFeedback`,
  `getProviderProfileFeedbackAggregate`), `ProviderProfileTools` (`listProviderProfiles`,
  `getProviderProfile`) and `TaxonTools` (`listTaxons`, `getTaxon`).
- A request check on the `Origin` header (`McpOriginFilter`): a request without `Origin` passes, one whose
  `Origin` is not in `marketplace.mcp.allowed-origins` is answered with 403 before a session opens. The
  allowed origins are never derived from the request's `Host`.
- Two settings: `marketplace.mcp.rest-base-url`, naming where the REST API it wraps is reachable, and
  `marketplace.mcp.allowed-origins`, the browser origins allowed to call `/mcp` (default `http://localhost:*`
  and `http://127.0.0.1:*`; a `*` after the colon matches any port).

To try the tools in a browser, see [The MCP page](../docs/api-collections/README.md#the-mcp-page).

To use the tools from Claude Code, create an API key (`POST /api/api-keys`), put it in the environment variable
`MARKETPLACE_MCP_KEY` (for example under `env` in the git-ignored `.claude/settings.local.json`) and approve the
`marketplace` server from the project's `.mcp.json` on the first start; `/mcp` shows whether it is connected.
`claude mcp add` is not suitable: the entry it writes to the user configuration can be lost when the session ends.

## Data flow

An AI agent connects to the MCP endpoint and sends its own API key in the `Authorization` header.
When the agent calls a tool, a `*Tools` class asks `RestPath` for the REST path, and `RestApiClient`
issues the GET to `marketplace.mcp.rest-base-url`, forwarding the header `CallerAuthorization` took
from the current request unchanged, so authentication, permissions, validation and audit are decided by
`marketplace-rest-api` exactly as for any direct REST caller. The REST response (or error) is wrapped
in a `RestAnswer`, which `ToolResults` turns into the tool result returned to the agent — paging
headers become `totalCount`, `hasMore` and `nextPage`, and a REST error becomes a readable tool error.
The module holds no credentials of its own and never reaches the database or any domain service.

## Dependencies

- No project module: no `platform-commons`, `marketplace-orchestrator`, `marketplace-rest-api`,
  domain starter, `marketplace-app` or Vaadin. An ArchUnit test in this module fails the build if a
  class here starts depending on any of them.
- Spring AI (MCP server starter for Spring MVC) — the MCP protocol implementation and tool registry.
- Spring Security — the filter chain that exempts `/mcp` from CSRF protection.
- Spring Boot web — the HTTP server that hosts the MCP endpoint.
- Spring Boot REST client starter — the `RestClient` the module calls the REST API with.
- Jackson — serializes the tool payload to the text content of a tool result.
