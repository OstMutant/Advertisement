# improvement-211: Investigation — convenient web interfaces for testing and visualizing the REST API, and MCP readiness

**Type:** improvement — investigation (hands-on trials, then a recommendation)
**Module:** `marketplace-rest-api` (`pom.xml`, `config/OpenApiConfig.java`, the five `api/*Controller.java` classes)
**Priority:** high — Top, first in the list; informs the MCP work planned after the REST API stabilises
**When:** independent; the trials run on the current API, ahead of [improvement-186](improvement-186-rest-hypermedia-media-subresource-and-patch-support.md) item 4, [improvement-210](../completed/tasks/improvement-210-rest-api-feedback-and-comments.md) and [improvement-196](improvement-196-rest-api-rate-limiting.md)

## Current state

Verified against the running app and code, 2026-10-17. `marketplace-rest-api` ships Swagger UI through
`springdoc-openapi-starter-webmvc-ui` 3.1.1 (`/swagger-ui/index.html`, spec at `/v3/api-docs`) with
`bearerKey` and `basicAuth` security schemes declared in `OpenApiConfig`. The live spec has 25
operations, each with a `summary`, but every `operationId` is auto-generated from the Java method
name and is ambiguous (`getById`, `getById_1`, `getById_2`, `list_1`..`list_4`, `create_3`).
`GET /api/provider-profiles/{id}`, `GET /api/advertisements/{id}` and `DELETE /api/api-keys/{id}`
have no description; `GET /api/users/{id}` has 21 characters. `/health` and `/sitemap.xml` appear
in the same spec as the `/api/**` operations. No request collection (Postman, Bruno, `.http`) exists in the repository. An MCP server is planned after the REST API; none exists.

## Why change

Swagger UI covers single requests only: there is no way to run a whole flow (register a user, issue
a key, create an advertisement) with values carried from one response to the next, and the
auto-generated operation names would become the tool names an AI agent has to choose between.

## Expected benefit

A decided, tried-out tooling set for manual API testing and visualisation, and a spec whose
operation names and descriptions are fit to become MCP tools — before MCP work starts.

## Approach

Trials, each judged on a real run against the local app, then one recommendation:

1. **Tool for whole flows — Postman (decided 2026-10-17).** A collection of eight chained requests
   (register a test user, issue an API key, create an advertisement, then read advertisements,
   taxons, settings and API keys) is in `docs/api-collections/postman/`; requests are sent from
   Postman (desktop app for `localhost`, the web app needs the Postman Agent), the whole chain via
   Collection Runner, and the visual canvas via Postman Flows, built by hand because a Flow cannot be
   exported or imported as a file. Postman is closed source and needs an account, which was accepted
   over the open-source preference. Rejected: Bruno (list of requests only, no canvas), self-hosted
   Hoppscotch and Yaade (a server just to run flows is too heavy; Yaade barely maintained),
   Node-RED/n8n/Kestra (workflow engines without response assertions, also servers), IntelliJ
   `.http` (no runner panel). Still to confirm in use: the collection imports cleanly, Flows are
   available on the free plan, and the chain runs green against the local app. Automated regression
   stays in the Level 3 REST scenario tests (`AbstractRestApiScenarioTest`). Possible follow-up,
   not started: an "API flows" diagram group in the architecture map generated from the collection.
2. **Spec quality for agents:** explicit `operationId` on all 25 operations, descriptions written
   for an agent (what it does, when to use it, required fields), the three missing descriptions
   filled. `/health` and `/sitemap.xml` stay in the spec; they are filtered out at the MCP level
   (item 3). Measure by re-reading the generated spec. Naming convention to fix: verb + entity in camelCase (`listAdvertisements`,
   `getAdvertisement`, `createAdvertisement`, `revokeApiKey`), unique across the whole API, set in
   `@Operation(operationId = ...)`, and never changed unless the operation's meaning changes. Why:
   the auto-generated names (`getById_2`, `list_4`, `create_3`) carry a suffix that depends on
   controller order, so adding a controller silently renames existing operations and breaks anything
   built on them (generated clients, collections, MCP tool names). Rule for new controllers
   (improvement-210 onward): explicit `operationId` from the first line, never the generated one.
3. **MCP — to be tried (second real trial, after Hoppscotch).** Record whether to build the MCP
   server as a sibling module over `marketplace-orchestrator` (like `marketplace-rest-api`) or
   generate it from the spec with an operation filter (administrative and destructive operations
   and `/health`, `/sitemap.xml` excluded), and what securing it needs (Spring AI MCP Security is
   community-maintained and works with WebMVC only; Spring Boot 4.1 compatibility is unconfirmed).
   Testing UI: MCP Inspector (`npx @modelcontextprotocol/inspector`, browser UI on port 6274):
   choose the Streamable HTTP transport, enter the server URL and a bearer token, then list tools and
   call them with edited parameters. Its CLI flags differ between sources and the project moved to a
   v2 line, so check `--help` on the installed version. Nuances to settle during the trial:
   - Paging: list endpoints return the count and navigation only in headers (`X-Total-Count`, RFC
     8288 `Link`) over a plain array body (decided earlier, not to be reversed here). Many MCP tools
     expose only the body, so the MCP layer must copy the total and the next page from the headers
     into its own tool result, or an agent cannot tell that more pages exist.
   - Tool names come from `operationId`; the current auto-generated names are ambiguous (see item 2).
   - Descriptions written for people need rewriting for an agent; keep the tool set small and
     focused instead of exposing all 25 operations.
   - Page size is not caller-supplied (it comes from the caller's saved settings), which an agent
     must be told.
   - Rate limiting matters more once an agent calls the API in a loop (see improvement-196 and
     improvement-186 item 4).
4. **Scalar — parked, not relevant for now.** It would sit next to Swagger UI via
   `springdoc-openapi-starter-webmvc-scalar` (3.1.1 exists on Maven Central), but it solves none of
   the current problems: no request chaining, and its gains over Swagger UI (built-in client with
   variables, code snippets, themes) are cosmetic for this project. Revisit only if Swagger UI
   proves insufficient for single-request use.
5. **Out of scope:** hypermedia/HAL links (deferred, see improvement-186 item 3) and building a
   custom in-app playground.

Deliverable: a short result section in this file stating what was kept, what was dropped and why,
followed by follow-up tasks for anything worth building.

## Related

- [improvement-186](improvement-186-rest-hypermedia-media-subresource-and-patch-support.md) — REST hypermedia, media, PATCH, Basic-auth brute-force gap
- [improvement-210](../completed/tasks/improvement-210-rest-api-feedback-and-comments.md) — feedback/comments endpoints the spec and collection will need to cover
- [improvement-196](improvement-196-rest-api-rate-limiting.md) — rate limiting, more pressing once an AI agent calls the API
- [improvement-073](../completed/tasks/improvement-073-rest-endpoint-infrastructure-test-seeding.md) — Phase 7 proposed the Postman collection

## Operational notes
- token_cost_review: n/a
- token_cost_research: n/a
- token_cost_verification: n/a
- review_signal_ratio: n/a
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: n/a
- flows_chosen: n/a
- flows_matched: n/a
- haiku_decomposition_used: n/a
- haiku_decomposition_dispatch_count: n/a
- haiku_decomposition_rework_needed: n/a
- adrs_referenced: none
