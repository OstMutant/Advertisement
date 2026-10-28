# improvement-221: MCP server trial run — start a Spring AI MCP server over the REST API and verify what the research could not

**Type:** improvement — investigation (hands-on trial, then a recommendation)
**Module:** `marketplace-rest-api` (the spec the tools come from: `config/OpenApiConfig.java`, the `api/*Controller.java` classes); no MCP module exists yet
**Priority:** medium-high — the last open question of [improvement-211](../completed/tasks/improvement-211-rest-api-testing-ui-and-mcp-readiness-investigation.md) before any MCP module is built
**When:** independent; ordered after [improvement-196](improvement-196-rest-api-rate-limiting.md) because an agent calling the API in a loop makes rate limiting matter more

## Current state

Carved out of improvement-211 item 3 on 2026-10-22. No MCP server exists. Research (web search, official docs and blogs, not verified by a build):

- Spring AI 2.0.x (the reference docs show 2.0.1 stable) with `spring-ai-starter-mcp-server-webmvc` is the servlet starter; tutorials report it running on Spring Boot 4.0.x and 4.1. No official compatibility matrix was found.
- `org.springaicommunity:mcp-server-security` 0.1.14 is a community project, "not officially endorsed" by Spring AI or the MCP project and marked work in progress. 0.1.x requires the Spring AI 2.0.x branch (0.0.6 for 1.1.x). Server side: Streamable HTTP and stateless supported, SSE not; OAuth2 only with JWT (no opaque tokens); API key supported in a basic form; OAuth2 and API key both require WebMVC. Spring Boot 4 / Spring Security 7 compatibility is not stated.
- MCP Inspector v2: sources disagree on flags (`--cli`, `--transport http`, `--server-url`, `--catalog`); needs Node 22.19+; the installed version's `--help` is the only reliable source.
- Generators from an OpenAPI spec exist outside Spring (a Java CLI with operationId/tag/path/method include filters; TypeScript, Python and Go tools); none reads springdoc output specifically.

Trial already done (2026-10-22, throwaway project outside the repository): Spring Boot 4.1.0 + Spring AI BOM 2.0.1 + `spring-ai-starter-mcp-server-webmvc`, two `@Tool` methods (`listAdvertisements`, `getAdvertisement`) calling the REST API through `RestClient`, reading the caller's `Authorization` header from `RequestContextHolder`. It resolved, compiled and packaged into a jar. It was **not started**: the repository hook forbids raw `docker run`, and no project script runs a standalone service outside the reactor.

Verified on the running app: `GET /api/advertisements` with `Authorization: Bearer <key>` returns the array body and `X-Total-Count`; `Link` appears only when more pages exist.

## Why change

The decision between "sibling module over `marketplace-orchestrator`" and "generated from the spec with an operation filter", and what securing needs, cannot be made from documentation alone. Unverified: Streamable HTTP endpoint behaviour on Boot 4.1, whether the bearer token reaches the REST call from inside a tool, whether the paging headers can be copied into the tool result, tool names from `operationId`, and the Inspector flags.

## Expected benefit

A recorded, run-backed answer to those five points, so the MCP module (or generator) can be filed with a decided shape instead of an assumed one.

## Approach

1. Decide where a standalone server can run under the repository's script rules: a temporary reactor module started by a project script, or a script option added for it. Raw `docker run` is blocked by the hook and is not the way.
2. Run the trial server with the two tools; drive it from MCP Inspector over Streamable HTTP with a bearer token (check `--help` of the installed version first). Record, with the server response for each: the endpoint works; the token reaches the REST call; `X-Total-Count` and `Link` end up in the tool result; tool names equal the `operationId`s; what a request without a token returns.
3. Try `mcp-server-security` against Boot 4.1 only if step 2 shows the pass-through needs it; record whether it starts at all.
4. Record the recommendation: sibling module vs generated, the operation filter (administrative and destructive operations, `/health`, `/sitemap.xml` excluded), securing, paging approach, the note that page size comes from the caller's saved settings, and the rate-limiting dependency.
5. Clean up: the trial user `mcp.trial.*@example.com` and its API key in the dev database, the local Docker image `mcp-trial-img`, and the scratchpad project.

## Related

- [improvement-211](../completed/tasks/improvement-211-rest-api-testing-ui-and-mcp-readiness-investigation.md) — parent investigation, items 1–2 done, this is its item 3
- [improvement-196](improvement-196-rest-api-rate-limiting.md) — rate limiting, more pressing once an agent calls the API in a loop
- [improvement-186](improvement-186-rest-hypermedia-media-subresource-and-patch-support.md) — hypermedia and PATCH, affects what an agent sees

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
