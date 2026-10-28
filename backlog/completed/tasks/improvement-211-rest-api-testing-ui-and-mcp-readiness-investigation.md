# improvement-211: Investigation — convenient web interfaces for testing and visualizing the REST API, and MCP readiness

**Type:** improvement — investigation (hands-on trials, then a recommendation)
**Module:** `marketplace-rest-api` (`pom.xml`, `config/OpenApiConfig.java`, the five `api/*Controller.java` classes)
**Priority:** high — Top, first in the list; informs the MCP work planned after the REST API stabilises
**When:** independent; the trials run on the current API, ahead of [improvement-186](../../tasks/improvement-186-rest-hypermedia-media-subresource-and-patch-support.md) item 4, [improvement-210](./improvement-210-rest-api-feedback-and-comments.md) and [improvement-196](../../tasks/improvement-196-rest-api-rate-limiting.md)

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

**Postponed by decision (2026-10-22):** the manual Postman checks of item 1 (clean import, Flows on the free plan, the chain green against the local app) wait until someone is at the Postman app; they do not block closing this task, and item 1's "still to confirm" sentence stays as the record of what was not verified.

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
2. **Spec quality for agents:** the `operationId` part is done 2026-10-21 under improvement-219 (all
   68 operations of today's API carry an explicit, unique one). Done 2026-10-22: every request of the
   Postman collection is named by its `operationId`, `PostmanCollectionTest` fails the build when a
   request names no existing operation or its method and path differ, and the missing operation
   descriptions are filled (`getAdvertisement`, `getProviderProfile`,
   `getProviderProfileFeedbackAggregate`, `changeMyLocale`). `/health` and `/sitemap.xml` stay in the spec; they are filtered out at the MCP level
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

**Plan for item 3 (2026-10-22, awaiting approval):**
- Step 1, research (web search, no code): Spring AI MCP server starters (artifacts, versions, Spring Boot 4.1 compatibility), Spring AI MCP Security (maintainer, WebMVC only, Boot 4.1), MCP Java SDK, MCP Inspector (installed version, `--help` flags), existing OpenAPI-to-MCP generators. Output: facts with sources, written under "Result".
- Step 2, trial: a throwaway MCP server with two tools (`listAdvertisements`, `getAdvertisement`) calling the running REST API with a bearer token, driven from MCP Inspector over Streamable HTTP; checks paging headers reaching the tool result, bearer passthrough, and tool names from `operationId`. Trial code is not committed.
- Step 3, record in "Result": sibling module vs generated-from-spec, operation filter list, securing, paging approach, rate-limit note.

**Item 3, step 1 findings (research, 2026-10-22; blogs and official docs, not yet verified by a build):**
- Spring AI 2.0.x (reference docs show 2.0.1 stable) with `spring-ai-starter-mcp-server-webmvc` is the servlet starter; tutorials report it running on Spring Boot 4.0.x and 4.1. No official compatibility matrix was found.
- `org.springaicommunity:mcp-server-security` 0.1.14 is a community project, "not officially endorsed" by Spring AI or the MCP project and marked work in progress. 0.1.x requires the Spring AI 2.0.x branch (0.0.6 for 1.1.x). Server: Streamable HTTP and stateless supported, SSE not; OAuth2 only with JWT (no opaque tokens); API key supported in a basic form; OAuth2 and API key require WebMVC. Spring Boot 4 / Spring Security 7 compatibility is not stated.
- MCP Inspector v2: sources disagree on flags (`--cli`, `--transport http`, `--server-url`, `--catalog`); needs Node 22.19+; check `--help` of the installed version.
- Generators from an OpenAPI spec exist outside Spring (a Java CLI with operationId/tag/path/method include filters; TypeScript, Python and Go tools); none reads springdoc output specifically.

**Item 3, step 2 outcome (2026-10-22):** a throwaway project (outside the repository) with Spring Boot 4.1.0, Spring AI BOM 2.0.1 and `spring-ai-starter-mcp-server-webmvc`, two `@Tool` methods (`listAdvertisements`, `getAdvertisement`) calling the REST API, resolved, compiled and packaged into a jar. Not run: starting the server and driving it from MCP Inspector were stopped because the repository's hook forbids raw `docker run` and no project script runs a standalone service. Therefore verified: the stack compiles together. Not verified: Streamable HTTP endpoint behaviour, bearer passthrough from the MCP request to the REST call, `X-Total-Count`/`Link` reaching the tool result, tool names from `operationId`, Inspector flags. Item 3's remaining questions moved to [improvement-221](../../tasks/improvement-221-mcp-server-trial-run.md), which also carries the research findings above.

## Result (2026-10-22)

- Kept: Swagger UI for single requests; Postman for whole flows (collection in `docs/api-collections/postman/`, eight chained requests, each named by its `operationId` and checked against the live operations by `PostmanCollectionTest`); explicit `operationId`s for all 68 operations.
- Dropped: Bruno, Hoppscotch, Yaade, Node-RED/n8n/Kestra, IntelliJ `.http` (reasons in item 1); Scalar parked.
- Not verified: the manual Postman checks (clean import, Flows on the free plan, a green chain against the local app), postponed by decision.
- MCP: the stack Spring Boot 4.1.0 + Spring AI 2.0.1 compiles and packages; starting the server and the remaining questions moved to [improvement-221](../../tasks/improvement-221-mcp-server-trial-run.md) together with the research findings.
- Follow-ups: [improvement-212](improvement-212-api-flow-runner-page.md) (flow runner page, already filed), improvement-221.

## Related

- [improvement-186](../../tasks/improvement-186-rest-hypermedia-media-subresource-and-patch-support.md) — REST hypermedia, media, PATCH, Basic-auth brute-force gap
- [improvement-210](./improvement-210-rest-api-feedback-and-comments.md) — feedback/comments endpoints the spec and collection will need to cover
- [improvement-196](../../tasks/improvement-196-rest-api-rate-limiting.md) — rate limiting, more pressing once an AI agent calls the API
- [improvement-073](./improvement-073-rest-endpoint-infrastructure-test-seeding.md) — Phase 7 proposed the Postman collection

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
