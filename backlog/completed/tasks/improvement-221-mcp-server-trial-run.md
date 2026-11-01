# improvement-221: MCP server trial run — start a Spring AI MCP server over the REST API and verify what the research could not

**Type:** improvement — investigation (hands-on trial, then a recommendation)
**Module:** `marketplace-rest-api` (the spec the tools come from: `config/OpenApiConfig.java`, the `api/*Controller.java` classes); no MCP module exists yet
**Priority:** medium-high — the last open question of [improvement-211](../completed/tasks/improvement-211-rest-api-testing-ui-and-mcp-readiness-investigation.md) before any MCP module is built
**When:** independent; improvement-196 (write rate limit) is done, which an agent calling the API in a loop depends on

## Main goal

Build the MCP interaction with our own application. The owner's vision: a separate module of the project that implements MCP by wrapping our REST API, convenient for AI agents. The trial run below is only a means to prepare it; it is not the goal.


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

## Plan (for people)

**What we build.** A new module of the project (working name `marketplace-mcp`), plugged into the application the same way `marketplace-rest-api` is. It lets an AI agent such as Claude work with the marketplace. It has no database code of its own: it calls our REST API with the key the agent was given, and turns the answers into MCP tools.

**Is the idea valid?** Yes. A throwaway version of exactly this wrapper was built and run during the trial (see "Trial results"). An agent read the advertisement list, moved between pages, read its own settings, and saw a clear message when the login limit was hit.

**How the agent gets a key.** By hand for now: a person issues a key (`POST /api/api-keys`, with login and password) and puts it into the agent's connection settings. Signing in through a browser so the agent gets the key itself is a possible later task; the REST authentication decision already rejected OAuth, so it would be a decision of its own.

**Two things we fix inside this task, because the module needs them:**
1. The link to the next page in the REST answers is incomplete (the path is missing). Fixed in the REST API, so the module does not need a workaround. This is Phase 2 of improvement-197.
2. An agent cannot learn who it is, so it cannot tell which advertisements are its own. We add `GET /api/me`. The rest of improvement-230 (links to available actions in every answer) stays in that task.

**First release: reading only, 11 tools** named after their REST `operationId`: `listAdvertisements`, `getAdvertisement`, `listAdvertisementMedia`, `listProviderProfiles`, `getProviderProfile`, `listProviderProfileFeedback`, `getProviderProfileFeedbackAggregate`, `listTaxons`, `getTaxon`, `getMySettings`, and `getMe` (new). Writes come in a second wave.

**Not yet known (checked in the steps below, not assumed):** how the module's address `/mcp` passes through the application's security when it sits inside the application, and how the module reaches the REST API (planned: an HTTP call to the application's own address, set in a property, which is how the trial worked).

**Known risk:** the public advertisement list shows the author's email (improvement-197 Phase 3). An agent would read it too, so that question is answered before release.

## Decisions taken

1. Shape: a separate module of the project that wraps the REST API and is plugged into the application (the owner's vision). Exact name and place in the build are fixed in step 3.
2. The module forwards the caller's own API key to the REST API unchanged. Recorded as one decision (ADR) in step 8.
3. Keys are issued by hand for now; browser sign-in is a later task.
4. The `Link` header fix (improvement-197 Phase 2) is done inside this task.
5. `GET /api/me` is added inside this task; improvement-230 keeps only the links to available actions.
6. A rate-limit answer (429) reaches the agent as a tool error saying "rate limit reached, retry after N seconds".
7. Paging reaches the agent as `{items, totalCount, page, hasMore, nextPage}`, with the note that the page size comes from the caller's saved settings.
8. First wave, reads only: the 11 tools named in the plan. Excluded: health, sitemap, key issuance and management, registration, moderation, audit timeline, user administration, deletes and restores, taxon writes, contact reveals.
9. `mcp-server-security` is not used.
10. The author-email question (improvement-197 Phase 3) is answered before release. Answered 2026-10-31: the email is public in the UI, so it stays public over REST and MCP; recorded in the REST list-endpoints ADR.

## Related

- [improvement-197](improvement-197-rest-api-contract-bugs.md) — Phase 2 (`Link` header) is fixed here; Phase 3 (author email) is decided before release
- [improvement-230](improvement-230-rest-hypermedia-decision.md) — `GET /api/me` moves into this task; links to available actions stay there
- [improvement-211](../completed/tasks/improvement-211-rest-api-testing-ui-and-mcp-readiness-investigation.md) — parent investigation
- [improvement-196](../completed/tasks/improvement-196-rest-api-rate-limiting.md) — write limit, done; every authenticated write of an account counts in one budget
- [improvement-186](../completed/tasks/improvement-186-rest-hypermedia-media-subresource-and-patch-support.md) — hypermedia and PATCH

## Trial results (2026-10-31)

Run against the CI e2e app on 18081 (115 advertisements, page size 20), Spring Boot 4.1.1, Spring AI 2.0.2 (MCP Java SDK 2.1.0), Streamable HTTP, MCP Inspector 2.13.0 on Node 24. Step 9 skipped by decision; step 8 run.

| Point | Observed | Answer |
|---|---|---|
| a. Endpoint works on Boot 4.1 | `initialize` 200 with `Mcp-Session-Id`; `tools/list` with a session lists all five tools; `DELETE` of the session 200. Asking for protocol `2026-07-28` is answered with `2025-11-25`. Without a session `tools/list` gives 400 and `GET` gives 400, so the server keeps sessions. Start-up showed only three `BeanPostProcessorChecker` warnings from Spring AI's annotation scanner | yes |
| b. Bearer token reaches the REST call | A custom transport provider with a context extractor was needed (the auto-configured one is replaced; no extra router bean needed). From inside a tool the header is present both in the transport context and in `RequestContextHolder`, on a servlet thread. `getMySettings` with the key returned the settings; without a key and with a bad key it returned the 401 text as an error result | yes |
| c. Paging into the tool result | `totalCount` 115, `returned` 20, `hasMore` true, `nextPage` 1 then 2, both from the `X-Total-Count` and `Link` headers. The raw `Link` has no path (`<http://localhost:18081?page=1&size=20>`), the known contract bug; building `nextPage` from `rel="next"` and `page` is therefore the only usable part. `structuredContent` is delivered next to the text | yes |
| d. Names = operationIds | `listAdvertisements`, `getAdvertisement`, `getMySettings` match; all 70 operationIds are valid MCP tool names | yes |
| e. Inspector flags | 2.13.0 has `--cli --transport http --server-url --header --method --tool-name --tool-args-json --format`; a tool error result gives exit code 5 | yes |
| No key | The trial endpoint is open: `tools/list` works without a key; the 401 appears only when a tool calls the REST API | recorded; the in-jar endpoint must decide itself |
| 429 | After 6 failed Basic logins the login limit arrives as an error result with "Retry after 900 seconds; do not retry earlier" and the server message | yes |
| Hypermedia input (improvement-230) | Haiku agent (5 turns, 0.006 USD) read totals and paging correctly, but could not tell which advertisements are its own (list items carry `ownerUserId`, no tool returns the caller's id), did not know whether edit or delete exists, and asked whether `totalCount` is global. All four are hints a `GET /api/me` and per-item allowed actions would answer | confirmed |

Side findings: the error body for a request without a session contains a full stack trace (`stackTrace`, class names) and the JSON-RPC code `-32601`; the public list item carries `createdByUserEmail` (improvement-197 Phase 3); the second page's `Link` has `first` and `prev` both pointing to page 0 (consistent with the missing path bug).

### What the trial means for the module

The wrapper idea works. The module takes the key from the agent's connection, calls the REST API with it, copies the total and the next page into the tool result, returns REST errors as readable tool errors and names tools after `operationId`. What the trial did not cover: running inside the application (security of `/mcp`), and the real list of 11 tools. Both are steps below. The agent test showed which hints an agent lacked: who it is (fixed by `GET /api/me`), whether edit and delete exist (second wave), whether the total counts everything (said in the tool description), and why the page size is fixed (said in the tool description).

### Check of the real module (2026-10-31)

Run on the application with the module built in (port 8081), MCP Inspector 2.13.0 and a headless Claude on Haiku as clients; two temporary users and 21 advertisements, all removed afterwards.

| Point | Observed |
|---|---|
| Tools | all 11 listed, names equal to the REST `operationId` values |
| Own account | `getMe` returns the id of the key's owner |
| Paging | `listAdvertisements` page 0: `totalCount` 21, `returned` 20, `hasMore` true, `nextPage` 1; page 1: `returned` 1, `hasMore` false, `nextPage` null; the `Link` fix makes the raw header unnecessary |
| Filter | `title` filter returned the 3 matching advertisements |
| Single resource | `getAdvertisement` returns `item` and `etag`; `getMySettings` returns `settings` and `etag` |
| No key / bad key | `getMe` answers the 401 text as an error result (exit 5); public lists work without a key |
| Agent (Haiku, 7 turns, 0.005 USD) | read the total and the next page correctly; named its own three advertisements by comparing `getMe` with `ownerUserId` (the trial server could not do this); said correctly that no tool edits or deletes |
| Still missing for the agent | write tools (second wave); an explicit "yours" flag on list items; a reason for the page size |
| Not reproducible through MCP | the 429 text (the trial probe tool is gone); it is covered by unit tests of the error mapping |

Side finding: the public advertisement list returns `createdByUserEmail` to anonymous callers over REST as well and it reaches agents; decided as intended because the UI shows it to every visitor (improvement-197 Phase 3).

## Execution plan

Ground rules: tests are written in the same step as the code they cover, never afterwards (unit tests for logic, integration scenarios for REST and MCP behavior); a step is not done until its tests are green; every step needs the person's approval (Approval Rule) unless the run is started with `/autopilot`; nothing is committed without "commit"; no raw key in any file or log; code comments one line or none and never a task number.

Steps 1 and 2 are changes in the REST API, step 3 onward builds the module. Steps 1 and 2 do not depend on each other.

#### Step 1 - Fix the `Link` header (improvement-197 Phase 2) — done 2026-10-31
- **Files:** `marketplace-rest-api/.../api/paging/PageLinkHeaderBuilder.java` and the list controllers that pass it a `UriComponentsBuilder`; a unit test for the builder; an integration scenario that checks the header on a list with several pages.
- **Executor:** `sonnet` (the cause is a suspected argument passed by the controllers, to be confirmed first).
- **Verification:** `Link` URLs contain `/api/advertisements` and the filters of the request; first, prev, next and last are correct; unit and integration tests green.

#### Step 2 - `GET /api/users/me` (operationId `getMe`) — done 2026-10-31; the path follows the existing `/me/settings` and `/me/locale`, not `/api/me`
- **Files:** a controller in `marketplace-rest-api` returning the caller's id, name, role and email-less profile data; OpenAPI annotations with an `operationId`; an integration scenario; the API collection under `docs/api-collections`.
- **Executor:** `sonnet`.
- **Verification:** with a key it returns the caller's id; without a key 401; the tests green; improvement-230 edited so it no longer lists `GET /api/me`.

#### Step 3 - Module skeleton — done 2026-10-31
- **Files:** new module `marketplace-mcp` in the root `pom.xml`, its `pom.xml` (header comment, dependency `spring-ai-starter-mcp-server-webmvc`, Spring AI BOM 2.0.2), auto-configuration, properties (REST base address), `README.md`, `.claude/rules/marketplace-mcp.md`, pointers in root `CLAUDE.md` and `.claude/rules/README.md`, a `flows`/`nav` update if required, `.claude/nav/scripts/check-rule-path-globs.sh` run.
- **Executor:** `sonnet`.
- **Verification:** the reactor builds; ArchUnit module-boundary tests green (extended for the new module); a test that the auto-configuration registers the tools; the module has no dependency on domain starters.

#### Step 4 - The 11 tools — done 2026-10-31
- **Files:** tool classes, a REST client that forwards the caller's `Authorization`, result and error mapping as in decisions 6 and 7.
- **Executor:** `sonnet` for the first tool, then `haiku` for the others by the same pattern.
- **Verification:** unit tests with a mocked REST server for success, 401, 404, 429 and paging; tool names equal the `operationId` values.

#### Step 5 - Plug into the application and check security — done 2026-10-31 (an automated test for it is part of step 6)
- **Files:** `marketplace-app/pom.xml` dependency; a security chain for `/mcp` only if the check shows one is needed.
- **Executor:** `sonnet`.
- **Verification:** an integration test covers the next line; with the application running, `POST /mcp` initialize and tools/list answer correctly; a call with a key and without a key behaves as in the trial; the write limit does not count MCP messages unless they are real REST writes.

#### Step 6 - Integration test — done 2026-10-31 (end-to-end MCP test in integration-tests, CSRF test in marketplace-app)
- **Files:** a scenario in `integration-tests` that starts the application, connects an MCP client and calls the tools against test data.
- **Executor:** `sonnet`.
- **Verification:** the scenario is green in the integration stage.

#### Step 7 - Real-world check — done 2026-10-31
- **Executor:** `haiku` runs the commands.
- **Change:** repeat the trial's Inspector calls and the Haiku agent run against the real module; record the agent's missing hints.
- **Verification:** the agent can tell which advertisements are its own; results appended to this task.

#### Step 8 - Documentation and decision
- **Files:** the key-forwarding decision extends the bearer-key authentication ADR of `marketplace-app` (with `Also affects: marketplace-mcp`); one new ADR for the module's place in the build; the author-email decision is added to the REST list-endpoints ADR; `/sync-docs`.
- **Executor:** `opus`.
- **Done 2026-10-31:** the three ADR edits, the improvement-197 Phase 3 note, the ADR index, the architecture model and `/sync-docs` (diff against `HEAD`, nine documentation fixes).

#### Step 9 - Full verification — done 2026-10-31
- `bash scripts/ci.sh` green (unit, integration, e2e, Sonar, ArchUnit, docs); `/deep-review` on the diff with findings applied.
- **Result:** the first CI run failed in the integration stage (15 of 904 errors, `too many clients already` on the shared Postgres, 100 connections) and Sonar with it; fixed by `max_connections=300` in `AbstractPostgresIntegrationTest`, confirmed by a standalone run (904 of 904). `/deep-review` found one DRY issue (the caller-header setup repeated in seven tests); fixed with a shared `CallerRequest` test helper. The second full CI run: all 13 stages green (24m25s).

#### Step 10 - Close (only after Phase 2 and Phase 3 below are done and the owner has reviewed the result)
- The owner reviews the finished work first; the task is not moved before that review is given.
- Move this task to `backlog/completed/tasks/`, remove its `BACKLOG.md` row, add the archive entry, add 1 to `Applied` of the ADRs in `adrs_referenced`, regenerate `.claude/nav/adr-index.md`, update improvement-197 (Phase 2 done) and improvement-230.

## Phase 2: admin page for MCP

Decided by the owner: it belongs to this task as a separate phase, after steps 1-9 of the module. Two parts, like the tools we already have for the REST API:

1. A catalog of the MCP tools, similar to Swagger: every tool with its description and parameters, and a way to call it.
2. A flow runner, similar to the one written for the REST collections: ready sequences of tool calls with checks, kept in a file, with a check that every tool is covered by a flow or listed as an exception.

### Plan (for people)

- **Shape (b):** a separate MCP flow file (`postman/marketplace-mcp.postman_collection.json`, plain HTTP JSON-RPC requests in the Postman v2.1 format) and a separate page `mcp.html`, both on the existing runner code. The REST collection, its checker and its page keep their behavior. Rejected: MCP requests inside the REST collection (mixes two coverage reports, a failed MCP step stops REST flows, breaks the "collection of the REST API" rule); a second runner (about 1,200 duplicated lines).
- **Where and how it is switched on (L1):** `mcp.html` is served next to the REST runner page at `/dev-tools/api-flows/mcp.html` by the existing `DevToolsConfig`, behind the same `marketplace.dev-tools.enabled`. No new property, no Java code, no `pom.xml` change.
- **Flows get a key by themselves:** the first flow registers a fresh user (run id in the e-mail), issues that user's key through REST and opens the MCP session. No administrator. Flows create an advertisement and a provider profile for the reads, delete them and close the session at the end.
- **Catalog (own, about 150 lines of JavaScript):** filled live from `tools/list`: name, description, parameter table, JSON box, optional API key field (memory only), Call button. MCP Inspector stays usable as an outside check.
- **Coverage check:** `check-collection` gets an MCP mode chosen by a second config; it fails when a tool has no named call and is not in `mcp-coverage-allowlist.txt`, when a call names an unknown tool, and when an exception is covered or names no tool.
- **Tests:** two self-contained Node tests (MCP client, test-script layer), the page-serving integration test extended, two new tests in the existing Playwright flow-runner spec.
- **Decisions to record:** one new in `docs/api-collections` (MCP flows as a separate file on the shared runner with a coverage check against `tools/list`); extensions of the page-serving and repeatable-flows decisions.
- **Cost:** about 820k sonnet tokens, 100k opus, 70k haiku, plus `/deep-review` (about 150k).
- **Risks:** the module has no `Origin` check (handled by Phase 3); unverified facts to confirm in step 6: JSON or event-stream answers, a profile without category and city, how long an abandoned session lives; step 5 touches the REST page code (its Playwright spec is the safety net); step cards show the temporary user's key like the REST page does.

### Decisions taken (Phase 2), 2026-11-01

1. Shape (b). 2. Page served by `DevToolsConfig` (L1). 3. Own catalog. 4. `getTaxon` is checked as an unknown id giving a readable 404 tool error, plus a success call only when a taxon already exists; no administrator. 5. The missing `Origin` check becomes Phase 3 of this task.

### Phase 2 execution plan

Steps use the prefix P2 so they are not confused with the module steps above. Every step needs its own approval (Approval Rule).

#### Ground rules for every step

- Read first: `/app/.claude/rules.md`, `/app/CLAUDE.md`, `docs/api-collections/README.md`,
  `docs/api-collections/scripts/README.md`, `docs/api-collections/postman/README.md`, and the header of
  every file you edit. Each JS/MJS/SH/BAT file has a `── Header ──` block (Description, Usage, Uses, Env,
  Input, Outputs, Returns). Keep it accurate in the same edit.
- Code comments: one line or none, no ticket or ADR numbers. Markdown: no ADR numbers and no ticket
  numbers. A real file is referenced as a markdown link.
- `.bat` files: ASCII only.
- Plain JavaScript modules, no build step, no npm dependency. Every module under `flow-runner/page/js/`
  must work both in the browser and in Node 22 (`node:22-alpine`). It may use only globals both share:
  `fetch`, `Headers`, `TextEncoder`, `btoa`/`atob`, `URL`.
- Run Node scripts only through `bash docs/api-collections/scripts/run-node.sh <file.mjs>` or the `.sh`
  wrappers. Never use raw `docker`, `node`, `npx` or `mvn`. Build, deploy and tests go through
  `scripts/*.sh`, wrapped by `scripts/activity-monitor.sh` as `.claude/rules.md` says.
- Write the tests in the same step as the code. A step is done only when its verification is green.
- No raw API key in any file or log.
- Facts the plan relies on, with the evidence for each (do not re-derive them):
  - `/mcp` needs `Accept: application/json, text/event-stream`. `initialize` answers 200 with an
    `Mcp-Session-Id` header. `notifications/initialized` answers 202. Later calls send `Mcp-Session-Id`
    and `MCP-Protocol-Version: 2025-11-25`. Without a session, `tools/list` answers 400. Evidence:
    `integration-tests/.../level3/mcp/McpEndToEndTest.java` and the improvement-221 trial table.
  - A request may be answered as `application/json` or as `text/event-stream` (`data: {...}` lines).
    The spec makes support for both a client MUST. Which one this server uses is **unverified**, so
    handle both.
  - A tool error is a normal JSON-RPC `result` with `isError: true` and text starting `HTTP <status>`.
    Success carries `structuredContent` and the same object as text. List tools return
    `{items, totalCount, page, returned, hasMore, nextPage}`.
  - `/mcp`'s chain (`McpSecurityConfig`) is `permitAll`, stateless, without CSRF. The app's catch-all
    chain (`marketplace-app/.../SecurityConfig.java`) ends in `anyRequest().permitAll()`, so GET
    `/dev-tools/**` passes.
  - `DevToolsConfig` (`marketplace-rest-api`) serves `classpath:/dev-tools/api-flows/`. That path is
    packed by `marketplace-rest-api/pom.xml` from `docs/api-collections/flow-runner/page` and
    `docs/api-collections/postman` (without `README.md`). Any new file in those two folders is served
    with no Java change.

#### Steps

##### P2 step 0 - Write the approved plan into the task - done 2026-11-01
- **Depends on:** the owner's answers to decisions 1-4.
- **Files:** `backlog/tasks/improvement-221-mcp-server-trial-run.md`, section "Phase 2: admin page for MCP".
- **Executor:** main thread (the Approval Rule requires the plan in the task file before execution).
- **Input:** this file and the owner's answers.
- **Change:** replace the last paragraph of "Phase 2" ("The detailed plan ... is written into this task
  when the phase starts ...") with a short step list (steps 1-11 below, one line each, with executor),
  plus the owner's answers as "Decisions taken (Phase 2)".
- **Verification:** the task file names every step and the chosen shape and placement.
- **Sources:** none (project process).

##### P2 step 1 - Start the local stack with dev tools
- **Depends on:** 0.
- **Files:** none.
- **Executor:** haiku.
- **Input:** the current branch, already building.
- **Change:** run, backgrounded and watched as `.claude/rules.md` says:
  `bash scripts/activity-monitor.sh -- scripts/deploy-and-run.sh --dev-tools`.
- **Verification:** `curl -s -o /dev/null -w '%{http_code}' http://localhost:8081/dev-tools/api-flows/index.html`
  prints `200`. `bash docs/api-collections/api-collection-tests.sh` is green, which is the REST
  baseline before any change. Report both.
- **Sources:** none.

##### P2 step 2 - Response headers and text in the test-script layer - done 2026-11-01 (pm-shim 4/4, runner-429 3/3, REST `--role user` green)
- **Depends on:** 0.
- **Files:** `docs/api-collections/flow-runner/page/js/pm-shim.js`,
  `docs/api-collections/flow-runner/page/js/runner.js`, new
  `docs/api-collections/scripts/pm-shim.test.mjs`, `docs/api-collections/scripts/README.md` (one
  sentence next to the existing 429-test sentence).
- **Executor:** sonnet.
- **Input:** the current `createPm(response, variables)`, which receives `{code, bodyText}`, and
  `runFlow`, which already holds `response.headers` as `[name, value]` pairs from `res.headers.entries()`
  (names lower-cased by `fetch`).
- **Exact behavior:**
  - `runFlow` passes the headers to `createPm`: `{ code, bodyText, headers }`.
  - `pm.response.headers.get(name)` returns the first value whose name equals `name` ignoring case, or
    `undefined`. `pm.response.text()` returns the body text. These are real Postman calls
    (`pm.response.headers` is a HeaderList with `get`; `pm.response.text()`).
  - Do not add anything Postman does not have. `pm.response.json()` stays `JSON.parse(bodyText)`. Every
    unsupported call keeps throwing through the existing `strict` proxy. `headers` must be reachable
    through the proxy, so build it as a plain object holding a `get` function.
  - Update the headers of both files (Description, Input, Outputs).
- **Test** (`pm-shim.test.mjs`, self-contained, same style as `runner-429.test.mjs`: stub global
  `fetch`, no API):
  1. A script reading `pm.response.headers.get("Mcp-Session-Id")` stores the value from a response whose
     header is `mcp-session-id`.
  2. `pm.response.text()` returns the raw body of an event-stream answer.
  3. An unknown header gives `undefined` without failing the step.
  4. `pm.response.headers.foo` still fails the step with `unsupported pm call`.
- **Verification:** `bash docs/api-collections/scripts/run-node.sh pm-shim.test.mjs` and
  `bash docs/api-collections/scripts/run-node.sh runner-429.test.mjs` both exit 0.
- **Sources:** https://learning.postman.com/docs/tests-and-scripts/write-scripts/postman-sandbox-reference/pm-response/ ,
  https://www.postmanlabs.com/postman-collection/HeaderList.html

##### P2 step 3 - Shared MCP client module - done 2026-11-01 (mcp-client 6/6; live: initialize JSON, tools/list event-stream, DELETE 200, 11 tools)
- **Depends on:** 0.
- **Files:** new `docs/api-collections/flow-runner/page/js/mcp-client.js`, new
  `docs/api-collections/scripts/mcp-client.test.mjs`, `docs/api-collections/scripts/README.md` (one
  sentence for the new test).
- **Executor:** sonnet.
- **Input:** the wire protocol as written in `McpEndToEndTest` (request ids, headers, the `data:`
  extraction) and the facts in the ground rules.
- **Exact behavior.** Export one factory, `createMcpClient({ endpoint, fetchImpl = fetch })`. It returns
  an object with these members. All of them are async except `parseBody`.
  - `open()`: POST `initialize` with `{protocolVersion: "2025-11-25", capabilities: {}, clientInfo:
    {name: "marketplace-dev-tools", version: "1"}}`. Keep the `Mcp-Session-Id` response header and
    `result.protocolVersion`. Then POST `{"jsonrpc":"2.0","method":"notifications/initialized"}` and
    expect 202. Throw an `Error` with the status and body text on anything else.
  - `listTools()`: `tools/list`, following `result.nextCursor` until it is absent. Returns the
    concatenated `tools` array.
  - `callTool(name, args, apiKey)`: `tools/call` with `{name, arguments: args}`. Sends
    `Authorization: Bearer <apiKey>` only when `apiKey` is a non-empty string. Returns the JSON-RPC
    `result`, or throws on a JSON-RPC `error` with its code and message.
  - `close()`: DELETE the endpoint with the session header. The caller may ignore a failure. The page
    calls it on `pagehide` with `keepalive: true`.
  - Every POST sends `Content-Type: application/json` and `Accept: application/json, text/event-stream`.
    After `open()`, every POST also sends `Mcp-Session-Id` and `MCP-Protocol-Version` (the negotiated
    version).
  - A 404 on a request that carried a session id: call `open()` once and resend that request once. The
    spec says the client MUST then start a new session.
  - `parseBody(contentType, text)`: when the content type starts with `text/event-stream`, return the
    JSON of the last `data:` line that is not empty. Otherwise return `JSON.parse(text)`.
  - JSON-RPC ids: an increasing counter per client.
  - No DOM, no storage access. The header block names this module as used by the MCP page and by
    `check-collection.mjs`.
- **Test** (`mcp-client.test.mjs`, stub `fetchImpl`, no API):
  1. `open()` sends `initialize` without a session header, then `notifications/initialized` with it.
  2. A JSON answer and an event-stream answer (`event: message\ndata: {...}\n\n`) both parse.
  3. `listTools()` follows two pages of `nextCursor`.
  4. `callTool` with a key sends `Authorization`, and without a key sends none.
  5. A 404 is followed by exactly one new `initialize` and one resend.
  6. A JSON-RPC `error` throws with its code.
- **Verification:** `bash docs/api-collections/scripts/run-node.sh mcp-client.test.mjs` exits 0.
- **Sources:** https://modelcontextprotocol.io/specification/2025-11-25/basic/transports ,
  https://modelcontextprotocol.io/specification/2025-11-25/server/tools

##### P2 step 4 - Coverage check: MCP mode, config, exceptions file, `--config` in the entry script - done 2026-11-01 (REST check unchanged 71/71; MCP mode proven on a temporary collection; the real MCP file comes in step 6)
- **Depends on:** 3.
- **Files:** `docs/api-collections/scripts/check-collection.mjs`, new
  `docs/api-collections/scripts/mcp-collection.config.json`, new
  `docs/api-collections/scripts/mcp-coverage-allowlist.txt`,
  `docs/api-collections/scripts/check-collection.sh` (header only, if its usage text changes),
  `docs/api-collections/api-collection-tests.sh`, `docs/api-collections/api-collection-tests.bat`
  (header only; it already forwards `%*`), `docs/api-collections/scripts/README.md`.
- **Executor:** sonnet.
- **Input:** the current `check-collection.mjs` (OpenAPI operations against request names, step-metadata
  checks, allow-list rules) and `run-flows.mjs` (already takes `--config` and reads `baseUrl` and
  `collectionFile`).
- **Exact behavior:**
  - The config gains an optional `source` field: `"openapi"` (default when absent, today's behavior,
    unchanged) or `"mcp"`. In `mcp` mode, the required fields are `baseUrl`, `collectionFile`,
    `allowListFile`, `hostPlaceholder`, `mcpPath` and `stepMetadata`. `openApiPath` is not required.
  - `mcp-collection.config.json` content:
    `{"source": "mcp", "baseUrl": "http://localhost:8081", "mcpPath": "/mcp", "collectionFile":
    "../postman/marketplace-mcp.postman_collection.json", "allowListFile": "mcp-coverage-allowlist.txt",
    "hostPlaceholder": "{{host}}", "stepMetadata": {"maxLabelLength": 24, "kinds": ["required",
    "optional"]}}`.
  - In `mcp` mode, the "operations" are the tool names from `createMcpClient({endpoint: baseUrl +
    mcpPath}).listTools()`. The script opens and closes its own session.
  - A request counts as a tool call when its path equals `mcpPath` and its raw body contains
    `"method": "tools/call"` (whitespace-tolerant regex). Its tool is the request name. A finding is
    reported when the body's `"name": "<x>"` differs from the request name. Every other request (REST
    setup, `initialize`, `notifications/initialized`, `tools/list`, `DELETE`) is excluded from coverage
    and from method/path matching.
  - The same rules as REST, worded for tools: the step-metadata checks apply to every request; a tool not
    called and not allow-listed is a finding; a call naming an unknown tool is a finding; an allow-list
    entry that is covered or names no tool is a finding. The summary line counts tools, called tools and
    allow-listed entries.
  - `mcp-coverage-allowlist.txt`: the same two comment lines as `coverage-allowlist.txt`, with
    "operationId" replaced by "tool name". No entries.
  - `api-collection-tests.sh` gains `--config <file>`, forwarded unchanged to both
    `check-collection.sh` and `run-flows.sh`. Document it in its header and usage text. `--role` and
    `--flow` keep their meaning, and flow names are the selected collection's own.
  - `scripts/README.md`: add `mcp-collection.config.json` to "Configuration" (JSON has no comments, so
    the README is that file's canonical home) with the fields `source` and `mcpPath`. Add the MCP mode to
    the mermaid flow (the MCP client module is read by `check-collection.mjs` in MCP mode). Show the run
    command `bash docs/api-collections/api-collection-tests.sh --config scripts/mcp-collection.config.json`.
    `--config` resolves against `/work` inside the container, so the path is relative to
    `docs/api-collections`.
- **Verification:**
  1. With the stack from step 1, `bash docs/api-collections/scripts/check-collection.sh` (REST) prints
     the same summary line as the step-1 baseline.
  2. `bash docs/api-collections/scripts/check-collection.sh --config scripts/mcp-collection.config.json`
     exits 2 or 1 before step 6 exists, with a clear "cannot read ..." or "not called" message. After
     step 6 it exits 0.
- **Sources:** https://modelcontextprotocol.io/specification/2025-11-25/server/tools (tools/list
  pagination by `nextCursor`).

##### P2 step 5 - Pass the page's REST-specific settings in - done 2026-11-01 (redeployed with --dev-tools; spec 11: setup + 3 tests passed, 39s)
- **Depends on:** 0.
- **Files:** `docs/api-collections/flow-runner/page/js/main.js`, `flow-map.js`, `templates.js`,
  `templates-bar.js` (only if it calls into `templates.js` with the settings), `index.html`, new
  `docs/api-collections/flow-runner/page/js/rest-page.js`.
- **Executor:** sonnet.
- **Input:** the hard-coded constants `COLLECTION_URL` (`main.js`), `REQUIRED_FLOW` (`flow-map.js`,
  `templates.js`), `STORAGE_KEY`, `MINIMAL_FLOWS` and `MINIMAL_STEPS_OFF` (`templates.js`), and the
  localStorage probe key in `main.js`.
- **Exact behavior:**
  - `main.js` stops starting itself. It exports `startRunnerPage(profile)`. `profile` is
    `{collectionUrl, requiredFlow, storageKey, minimalFlows, minimalStepsOff}`. The function threads
    `requiredFlow` into `createFlowMap` and the templates functions, `storageKey` into storage access,
    and `minimalFlows`/`minimalStepsOff` into `builtIns`. Use a parameter or an options field, matching
    each module's existing argument style. No module keeps a REST constant.
  - `rest-page.js` holds today's values exactly (`postman/marketplace-rest-api.postman_collection.json`,
    `Authentication`, `api-flow-runner.templates`, `["Authentication", "Advertisements"]`, the current
    `MINIMAL_STEPS_OFF` list). It calls `startRunnerPage` after DOM ready, the way `main.js` does today.
  - `index.html` loads `js/rest-page.js` instead of `js/main.js` and adds a link
    `<a href="mcp.html">MCP tools and flows</a>` next to the Postman collection link.
  - Saved REST templates must keep working: the storage key value is unchanged.
  - Update every touched file's header (Usage and Uses change).
- **Verification:** the page files are packed into the jar, so rebuild and redeploy with
  `bash scripts/activity-monitor.sh -- scripts/deploy-and-run.sh --dev-tools` (not `--reuse-jar` over an
  old jar). Then `bash playwright/run.sh 11-api-flow-runner --ux` passes all three existing tests
  unchanged. This step changes no behavior.
- **Sources:** none (project refactor).

##### P2 step 6 - The MCP flow file - done 2026-11-01 (24 steps: 23 passed, 1 skipped, twice in a row; 11 of 11 tools covered; observed: initialize JSON, tools/list and tools/call event-stream, DELETE 200, unknown taxon id = isError with HTTP 404, profile accepted without category and city)
- **Depends on:** 2, 4, and the running stack (step 1). The Node run reads the file from the docs folder,
  so no redeploy is needed.
- **Files:** new `docs/api-collections/postman/marketplace-mcp.postman_collection.json`,
  `docs/api-collections/postman/README.md`.
- **Executor:** sonnet.
- **Input:** the REST collection (copy the request shape, the description header lines and the
  test-script style), `postman/README.md` (format rules), the api-collections decision on repeatable
  flows (empty or used database, `runId`, never a limiter, delete what you create).
- **File-level content:** Postman v2.1, `info.name` `Marketplace MCP`. Collection variables:
  `host` `http://localhost:8081`, `runId` `1`, `mcpUserName` `MCP Flow User`, `mcpEmailPrefix`
  `mcp.flow.user`, `mcpPassword` `password123`, `apiKeyLabel` `mcp-flow-runner`, `mcpProtocol`
  `2025-11-25`, `adKind` `OFFER`, `profileKind` `MASTER`, `profilePhone` `+380501234567`,
  `missingId` `999999999`.
- **Request conventions:**
  - Every MCP request is `POST {{host}}/mcp` with headers `Content-Type: application/json` and
    `Accept: application/json, text/event-stream`. After `initialize` it also sends
    `Mcp-Session-Id: {{mcpSession}}` and `MCP-Protocol-Version: {{mcpProtocol}}`.
  - Raw JSON-RPC body with a fixed numeric `id` per request.
  - Request name: the tool name for `tools/call`, otherwise the JSON-RPC method (`initialize`,
    `notifications/initialized`, `tools/list`), or `endSession` for the DELETE.
  - REST setup requests keep their REST `operationId` names.
  - Test scripts read a JSON-RPC answer with exactly this one line (portable to real Postman, which has
    no event-stream shortcut):
    `const body = pm.response.text(); const rpc = JSON.parse(body.includes("data:") ? body.slice(body.lastIndexOf("data:") + 5) : body);`
  - Where the shim has no assertion, throw an `Error` inside `pm.test`. Do not add assertions to the shim.
- **Flows (folders, in this order; step ids are `<prefix>.<name>`):**
  1. `Setup` (required, prefix `setup.`):
     - `registerUser` with e-mail `{{mcpEmailPrefix}}.{{runId}}@example.com`, expect 201, store
       `mcpEmail`.
     - `issueApiKey` with Basic `{{mcpEmail}}`/`{{mcpPassword}}`, expect `rawKey` string, store `mcpKey`.
     - `initialize`: expect 200, store `mcpSession` from `pm.response.headers.get("Mcp-Session-Id")`,
       test that it is a non-empty string and that `rpc.result.protocolVersion` is a string.
     - `notifications/initialized`: expect 202.
     - `tools/list`: expect `rpc.result.tools` to be an array with at least one entry. The exact list
       is the coverage check's job.
  2. `Account` (prefix `account.`):
     - `getMe` with bearer `{{mcpKey}}`: not `isError`, `structuredContent.item.id` a number.
     - `getMe` without auth (step `account.nokey`): `isError` true, text starts with `HTTP 401`.
     - `getMySettings` with key: not `isError`, `structuredContent.settings` an object.
  3. `Advertisements` (prefix `ads.`):
     - REST `createAdvertisement` with `{{mcpKey}}`, body `{"title": "MCP flow ad {{runId}}",
       "description": "MCP flow ad {{runId}}", "adKind": "{{adKind}}"}`, expect 201, store
       `mcpAdId`/`mcpAdVersion`.
     - `listAdvertisements {}`: not an error, `items` an array, `totalCount` a number.
     - `listAdvertisements {"title": "MCP flow ad {{runId}}"}` (step `ads.filter`): `totalCount` 1.
     - `getAdvertisement {"id": {{mcpAdId}}}`: `item.title` equals the created title, `etag` a string.
     - `listAdvertisementMedia {"id": {{mcpAdId}}}`: `items` an array.
     - REST `deleteAdvertisement` with `If-Match: {{mcpAdVersion}}`, expect 204.
  4. `Provider profiles` (prefix `profiles.`):
     - REST `createProviderProfile` with `{{mcpKey}}`, body `{"kind": "{{profileKind}}", "about": "MCP
       flow profile {{runId}}", "phone": "{{profilePhone}}"}`, expect 201, store
       `mcpProfileId`/`mcpProfileVersion`. **Unverified** whether a profile without category and city is
       accepted. If it answers 400, stop and report to the main thread, which then applies
       [if decision 4 = admin taxon].
     - `listProviderProfiles {}`: `items` an array.
     - `getProviderProfile {"id": {{mcpProfileId}}}`: not an error.
     - `listProviderProfileFeedback {"id": {{mcpProfileId}}}`: `items` an array. Use the parameter
       names from `tools/list`, not guesses: read `FeedbackTools.java` and `ProviderProfileTools.java`.
     - `getProviderProfileFeedbackAggregate {...}`: not an error.
     - REST `deleteProviderProfile` with `If-Match: {{mcpProfileVersion}}`, expect 204.
  5. `Taxons` (prefix `taxons.`):
     - `listTaxons {}`: not an error, `items` an array. Store `taxonExists` `yes`/`no`, and `mcpTaxonId`
       from the first item when present.
     - `getTaxon {"id": {{mcpTaxonId}}}` with `when: taxonExists=yes`: not an error.
     - `getTaxon {"id": {{missingId}}}` (step `taxons.missing`): `isError` true, text starts with
       `HTTP 404`. If the real answer for an unknown id differs, record the observed status and use it.
  6. `Close` (prefix `close.`): `DELETE {{host}}/mcp` with the session header, test status in
     `[200, 204]`.
  - **[if decision 4 = admin taxon]:** add to `Setup` the three administrator steps of the REST
    Authentication flow (`auth.key`, `auth.admin`, `auth.adminkey`, with their `when` conditions and the
    `email`/`password`/`adminName` variables). Add to `Taxons` a REST `createTaxon` CATEGORY with
    `{{adminKey}}` before the reads and a `deleteTaxon` after them. Use that id for the success
    `getTaxon` and drop the `when` condition. Also send `categoryIds`/`cityTaxonId` in the profile body
    if the profile requires them.
- **`postman/README.md`:** add a section "The MCP collection" covering four things. (1) Its purpose,
  with a file link. (2) The naming rule above. (3) The one-line JSON-RPC parse convention and why it is
  used: event-stream answers, Postman portability. (4) Its collection variables and run-time variables,
  in the same table form as the REST ones. Keep the intro line accurate: the folder now holds two
  collections.
- **Verification:**
  - `bash docs/api-collections/api-collection-tests.sh --config scripts/mcp-collection.config.json`
    exits 0 **twice in a row** (repeatable on a used database).
  - REST `bash docs/api-collections/api-collection-tests.sh` is still green.
  - Report the observed answer content type (JSON or event stream), the DELETE status and the unknown-id
    status. Those were unverified facts.
- **Sources:** https://modelcontextprotocol.io/specification/2025-11-25/basic/transports ,
  https://modelcontextprotocol.io/specification/2025-11-25/server/tools ,
  https://learning.postman.com/docs/postman-ai/mcp-requests/create (why plain HTTP and not Postman's MCP
  request type)

##### P2 step 7 - The MCP page: catalog and runner - done 2026-11-01 (checked in the browser by the step 8 tests); reworked 2026-11-01 at the owner's request: typed example arguments, id picking from list results, tool cards styled like the flow cards, the key and the taxon id filled after a run, the parameter table only for tools with parameters
- **Depends on:** 3, 5, 6.
- **Files:** new `docs/api-collections/flow-runner/page/mcp.html`, new
  `docs/api-collections/flow-runner/page/js/mcp-page.js`, new
  `docs/api-collections/flow-runner/page/js/mcp-catalog.js`, new
  `docs/api-collections/flow-runner/page/css/catalog.css`.
- **Executor:** sonnet.
- **Input:** `index.html` (structure, ids the runner needs), `main.js` `startRunnerPage(profile)` from
  step 5, `mcp-client.js` from step 3.
- **Exact behavior:**
  - `mcp.html`: title and `h1` `MCP tools and flows`, the same line about `marketplace.dev-tools.enabled`
    as `index.html`, links to `postman/marketplace-mcp.postman_collection.json` and back to `index.html`
    (`REST API flows`). A `<section id="catalog">` sits above the runner. Below it are exactly the runner
    elements of `index.html` (templates, flow map, flow detail, run bar, diagram, dialogs). It loads
    `js/mcp-page.js`.
  - `mcp-page.js`: calls `startRunnerPage({collectionUrl: "postman/marketplace-mcp.postman_collection.json",
    requiredFlow: "Setup", storageKey: "mcp-flow-runner.templates", minimalFlows: ["Setup", "Account",
    "Close"], minimalStepsOff: []})`, then `createCatalog(document.getElementById("catalog"), {endpoint:
    location.origin + "/mcp"})`.
  - `mcp-catalog.js` (`createCatalog(container, {endpoint})`):
    - On start: `open()` then `listTools()`. On failure, show the error text in the section and stop.
      Do not block the runner below.
    - One field `API key (optional)` at the top, kept only in a JS variable. Never write it to storage
      or to the URL.
    - Per tool, a collapsible entry (`<details>`): name, `annotations.title` or `title` when present,
      description, and a parameters table (name, type, required yes/no, description) from
      `inputSchema.properties` and `inputSchema.required`.
    - A textarea prefilled with a JSON object of the tool's required parameters (value `null`). A
      `Call` button. Invalid JSON shows an error without a call.
    - The result shows `isError` as a status label, then pretty-printed `structuredContent` when present,
      otherwise the text content items. A thrown protocol error shows its message.
    - Accessible names: the `Call` button reads `Call <toolName>`. Each entry has
      `data-tool="<toolName>"`, so Playwright can select it.
    - On `pagehide`, call `close()`, ignoring errors.
  - Headers of all new files follow the module header format.
- **Verification:** after a redeploy with `--dev-tools`, open
  `http://localhost:8081/dev-tools/api-flows/mcp.html`, or rely on step 8's Playwright tests. The
  catalog shows 11 tools. `listTaxons` with `{}` answers without error. `getMe` without a key shows an
  error starting with `HTTP 401`. "Run selected" with every flow ends with nothing failed.
- **Sources:** https://modelcontextprotocol.io/specification/2025-11-25/server/tools (tool fields;
  "clients SHOULD show tool inputs before calling"),
  https://modelcontextprotocol.io/specification/2025-11-25/basic/transports (session, 404, DELETE).

##### P2 step 8 - Integration and Playwright tests - done 2026-11-01 (DevToolsConfigTest 3/3; spec 11: setup + 5 tests passed in 40s; Playwright lint 837 warnings under the 875 limit)
- **Depends on:** 6, 7.
- **Files:** `integration-tests/src/test/java/org/ost/integrationtests/level2/restapi/DevToolsConfigTest.java`,
  `playwright/e2e/11-api-flow-runner.spec.js`.
- **Executor:** sonnet.
- **Input:** both files as they are. Read `.claude/rules/integration-tests.md` and
  `.claude/rules/playwright.md` (and `playwright/CLAUDE.md`) first.
- **Exact behavior:**
  - `DevToolsConfigTest`: add `MCP_PAGE = "/dev-tools/api-flows/mcp.html"` and `MCP_COLLECTION =
    "/dev-tools/api-flows/postman/marketplace-mcp.postman_collection.json"`. The off cases assert 404 for
    both. The on case asserts 200, with `MCP tools and flows` in the page and `"info"` in the collection.
    Update the class Javadoc line (one line).
  - Spec 11, two new tests in the same `describe`, reusing its skip guard:
    1. The catalog lists exactly the 11 tool names (`[data-tool]` count 11, the names equal the set in
       `McpEndToEndTest`). `Call listTaxons` with `{}` shows a non-error status. `Call getMe` without a
       key shows a text starting with `HTTP 401`. Take a screenshot.
    2. `Run selected` on `mcp.html` with the default selection ends with `.step.failed` count 0, every
       node passed or skipped, and the `setup.initialize` node code `200`.
  - Update spec 11's header: Description, Input and Outputs. The MCP run creates one user, one key, one
    advertisement and one profile, and deletes the last two.
- **Verification:**
  - `bash scripts/activity-monitor.sh -- scripts/build-and-test.sh --integration --sandbox --skip-vaadin`,
    or narrower to `DevToolsConfigTest` if the script supports it (check `scripts/CLAUDE.md`), is green.
  - After a redeploy with `--dev-tools`, `bash playwright/run.sh 11-api-flow-runner --ux` passes all
    five tests.
- **Sources:** none beyond the project's rules.

##### P2 step 9 - Documentation - done 2026-11-01
- **Depends on:** 4, 6, 7.
- **Files:** `docs/api-collections/README.md`,
  `marketplace-rest-api/src/main/java/org/ost/restapi/config/DevToolsConfig.java` (Javadoc line only),
  `.claude/rules/marketplace-rest-api.md` (the `DevToolsConfig` bullet), `INFRASTRUCTURE.md` (a row next
  to "API flow runner page"), `marketplace-mcp/README.md` (one sentence linking the page's README for
  trying the tools in a browser), `scripts/deploy-and-run/run.sh` (the `--dev-tools` header line: "pages"
  instead of "page", naming both), `.claude/commands/deploy-and-run.md` (same wording).
- **Executor:** sonnet. Read `.claude/skills/module-doc-standards/SKILL.md`,
  `module-readme-standards`, `infra-doc-standards`, `infra-readme-standards` and `app-readme-standards`
  for the files they govern.
- **Exact behavior:**
  - `docs/api-collections/README.md`: the first paragraph covers the REST collection and the MCP
    collection. The `flow-runner/` row names both pages. A short section "The MCP page" covers the
    catalog from live `tools/list`, the runner on the MCP collection, the session handling, and the key
    kept in memory only. The "Entry points" section mentions `--config`. Do not restate what each
    script's header already says.
  - One fact, one home: the URL and switch stay in `DevToolsConfig`'s Javadoc and in this README. Other
    files link, they do not restate.
  - No ADR numbers, no ticket numbers.
- **Verification:** `bash .claude/nav/scripts/check-rule-path-globs.sh` is not needed (no `paths:`
  change). Read every edited paragraph once against the code it describes.
- **Sources:** none.

##### P2 step 10 - Decisions - done 2026-11-01 (new decision in docs/api-collections; the page-serving and repeatable-flows decisions extended; ADR index and architecture model regenerated)
- **Depends on:** 6, 7 (the facts the decisions record are then verified).
- **Files:** `docs/api-collections/DECISIONS.md`, `.claude/nav/adr-index.md` (regenerated), the
  generated `marketplace-mcp/DECISIONS.md` (regenerated through
  `bash docs/architecture/scripts/generate-architecture-model.sh`), and the task file's
  `adrs_referenced`.
- **Executor:** opus, through `/record-decision`, never a hand-written entry.
- **Input:** the owner's answers to decisions 1-4, this plan's comparison (`notes.md`, "Variants and
  comparison").
- **Change:**
  1. New decision in `docs/api-collections`: "MCP flows are a separate Postman collection of plain HTTP
     JSON-RPC requests, run by the shared runner on their own page, with a coverage check against the
     live `tools/list`". `Also affects: marketplace-mcp`. Rejected alternatives: (a) folders in the REST
     collection, (c) a separate runner, Postman's native MCP request type, MCP Inspector as the only
     catalog.
  2. Extend the page-serving decision ("The flow runner page is served only behind a property, from the
     REST module's classpath ..."): the MCP page and collection are served from the same path behind the
     same property, with no new property. Add `Also affects: marketplace-mcp`.
  3. Annotate the active repeatable-flows decision (never the superseded earlier one): it applies to the MCP collection too, and an MCP flow
     closes its session at the end.
  4. Regenerate the ADR index (`bash .claude/nav/scripts/generate-adr-index.sh`) and the architecture
     model, as `.claude/rules.md` requires.
- **Verification:** the index lists the new and annotated entries. `marketplace-mcp/DECISIONS.md` (the
  generated file) lists the new decision.
- **Sources:** all of `sources.md`.

##### P2 step 11 - Full verification - done 2026-11-01 (all checks green; `/deep-review` on the Phase 2 files: no auto-report findings, one low-confidence finding fixed; `bash scripts/ci.sh` 14 of 14 stages green in 24m31s: lint, shellcheck, unit 6m30s, integration 8m25s, E2E 18m6s, Sonar 2m26s, ArchUnit, docs)
- **Depends on:** 1-10.
- **Files:** none.
- **Executor:** haiku (runs commands and reports output, makes no fixes).
- **Change:** run, each backgrounded through `scripts/activity-monitor.sh` where it applies:
  1. `bash scripts/deploy-and-run.sh --dev-tools`
  2. `bash docs/api-collections/api-collection-tests.sh` (REST), then
     `bash docs/api-collections/api-collection-tests.sh --config scripts/mcp-collection.config.json`
     twice. The REST run uses `--role user` (decided 2026-11-01: the local database has an account that is not an administrator, so the full REST run stops at `taxons.probe`).
  3. `bash docs/api-collections/scripts/run-node.sh runner-429.test.mjs`, `pm-shim.test.mjs` and
     `mcp-client.test.mjs`.
  4. `bash playwright/run.sh 11-api-flow-runner --ux`
  5. `bash scripts/build-and-test.sh --unit --integration --sandbox --skip-vaadin`
  6. `bash scripts/ci.sh` (unit, integration, e2e with `--dev-tools`, Sonar).
- **Verification:** every command exits 0. Report counts (tests, steps passed/skipped/failed) and the
  exact error lines of any failure.
- **Sources:** none.

#### Verification of the whole

- The MCP page at `/dev-tools/api-flows/mcp.html` answers 404 with `marketplace.dev-tools.enabled`
  unset or `false`, and 200 when `true` (`DevToolsConfigTest`).
- The catalog shows the 11 tools from live `tools/list` and calls them with and without a key
  (Playwright spec 11).
- The MCP flows pass in the browser (Playwright) and headless (`run-flows` through `--config`), twice in
  a row on a used database.
- The MCP coverage check passes with an empty exceptions file. Removing any tool call from the MCP
  collection makes it fail naming that tool. Try this once by hand and revert.
- The REST page, REST check, REST flows and their three Playwright tests are unchanged and green.
- `marketplace-mcp` is untouched, so its `ArchitectureTest` and the module's no-dependency rule hold.
- `bash scripts/ci.sh` green. Then step 10 of improvement-221 (close) can run.

## Phase 3: `Origin` check on `/mcp`

The MCP transport standard says a server MUST validate the `Origin` header of incoming connections and answer 403 on an invalid one, to stop DNS-rebinding attacks from a web page (a page the visitor opens can make their browser call a local MCP server and reuse a key the browser holds). Agents and scripts are not browsers and send no `Origin`.

### Findings (checked 2026-11-01 in the real jars, Spring AI 2.0.2 and MCP SDK 2.1.0)

- The SDK ships `DefaultServerTransportSecurityValidator` (allowed origins and hosts, `http://host:*` matches any port, a missing `Origin` header is allowed, an invalid one carries status 403). The Streamable transport provider takes a validator through its builder and **defaults to a no-op validator**.
- The Spring AI auto-configuration builds that provider without a validator and has no property for one. So today no `Origin` is checked at all: any web page can call `/mcp`.
- The module's `/mcp` chain (`McpSecurityConfig`) is the one place that already sees every `/mcp` request and belongs to the module.
- Playwright and the CI e2e stack reach the app at `http://localhost:8081` and `http://localhost:18081`; the local Docker stack is on `localhost:8081`. A page served by the app therefore sends `Origin: http://localhost:<port>`.

### Design

- A request filter placed in the module's own `/mcp` security chain (an instance added to the chain, not a Spring bean, so Boot does not register it for every path). It reads the `Origin` header; when present it asks the SDK's `DefaultServerTransportSecurityValidator` (built once from the configured list) and answers the validator's own status (403) with no body on rejection. No hand-written origin matching.
- New setting `marketplace.mcp.allowed-origins` in `McpProperties`, a list of origins, default `http://localhost:*` and `http://127.0.0.1:*` (the port wildcard is the SDK's). A real deployment on a public address lists its own origin; agents without `Origin` need nothing.
- The allowed origin is **never derived from the request's own `Host`**: a rebinding page arrives with its own domain as both `Host` and `Origin`, so deriving would let it through.
- Host validation is left out: with an explicit origin list a browser page is already stopped, and non-browser clients are not the threat this check answers.

### Decisions for the owner (proposals)

1. Check in a filter inside the module's chain using the SDK validator (proposal). Other way: build the module's own Streamable provider bean with the validator, which copies every setting the auto-configuration applies to that bean and breaks when the library changes them.
2. Default list `http://localhost:*` and `http://127.0.0.1:*` (proposal), so the dev page, Playwright and CI keep working with no setting. Other way: empty default (nothing with an `Origin` passes until configured), which breaks the MCP page and tests until each environment sets it.
3. No Host validation (proposal).
4. Setting name `marketplace.mcp.allowed-origins` (proposal).

### Phase 3 execution plan

Every step needs its own approval (Approval Rule); the same ground rules as the Phase 2 plan apply (read the rules and the module rule file first, comments one line or none and no ticket or ADR numbers, tests in the same step as the code, scripts only through the project wrappers).

##### P3 step 1 - Filter, setting and module tests - done 2026-11-01 (McpOriginFilter on the SDK validator, setting `allowed-origins`; marketplace-mcp 63/63, integration 904/904; the deprecated validator call replaced by the header-accessor one)
- **Files:** `marketplace-mcp/src/main/java/org/ost/mcp/config/McpProperties.java` (new `allowedOrigins` list with the two defaults), new `marketplace-mcp/src/main/java/org/ost/mcp/config/McpOriginFilter.java`, `marketplace-mcp/src/main/java/org/ost/mcp/config/McpSecurityConfig.java` (add the filter to the chain; the chain method receives `McpProperties`), tests `marketplace-mcp/src/test/java/org/ost/mcp/config/McpOriginFilterTest.java` and an extension of `McpSecurityConfigTest` (the probe config gets a `McpProperties` bean).
- **Executor:** sonnet.
- **Behavior:** `McpOriginFilter extends OncePerRequestFilter`, `@NonNull` on parameters, built once with the SDK validator from the configured list. No `Origin` header: continue the chain. Header present: `validator.validateHeaders(Map.of("Origin", List.of(value)))`; on `ServerTransportSecurityException` send status `getStatusCode()` (403) and stop. Class Javadoc one line. Update the Javadoc of `McpSecurityConfig` and `McpProperties` (the setting).
- **Tests:** no `Origin` passes; `http://localhost:8081` and `http://127.0.0.1:3000` pass with the defaults; `http://evil.example` and `http://localhost.evil.example` give 403 and the chain is not continued; a configured extra origin passes; through the real chain (probe) a foreign `Origin` on `POST /mcp` gives 403 and an allowed one 200, other paths unaffected. `ArchitectureTest` stays green (the module may use the SDK, it forbids only project modules).
- **Verification:** `bash scripts/activity-monitor.sh -- bash scripts/build-and-test.sh --unit --sandbox --skip-vaadin` green.

##### P3 step 2 - Integration test and live regression - done 2026-11-01 (McpEndToEndTest 8/8 with two Origin cases; live curl: evil.example 403, localhost 200, none 200; spec 11 6/6; MCP flows 23 passed, 1 skipped)
- **Files:** `integration-tests/src/test/java/org/ost/integrationtests/level3/mcp/McpEndToEndTest.java` (new cases), `playwright/e2e/11-api-flow-runner.spec.js` only if the live run needs a change.
- **Executor:** sonnet writes the cases; the main thread runs the deploy and the suites.
- **Cases:** `initialize` with `Origin: http://evil.example` gives 403 and creates no session; with `Origin: http://localhost:<port>` it gives 200; the existing cases without `Origin` stay as they are.
- **Verification:** `build-and-test.sh --integration` (the end-to-end MCP test), redeploy with `--dev-tools`, `bash scripts/playwright.sh 11-api-flow-runner --ux` (the MCP page and its flows are the live proof that the page's own origin is allowed), `bash docs/api-collections/api-collection-tests.sh --config scripts/mcp-collection.config.json` (no `Origin`, must pass), and a manual `curl` with a foreign `Origin` answering 403.

##### P3 step 3 - Documentation - done 2026-11-01 (README and the module rule file; the api-collections README states nothing untrue)
- **Files:** `marketplace-mcp/README.md` (the setting and what the filter does), `.claude/rules/marketplace-mcp.md` ("What it owns": `McpOriginFilter`), `docs/api-collections/README.md` only if it states anything now untrue. One fact, one home: the setting is described once, in `McpProperties` Javadoc and the README.
- **Executor:** sonnet.

##### P3 step 4 - Decision - done 2026-11-01 (the existing MCP module decision in marketplace-app extended, Verified stamped; adr-index and architecture model regenerated)
- **Files:** the decision on the MCP module's shape and its `/mcp` chain in `marketplace-app/DECISIONS.md` is **extended** (no new one): the chain also validates `Origin` against `marketplace.mcp.allowed-origins`, the SDK validator is used, the default list, no derivation from `Host`, rejected: own transport provider bean, empty default, Host validation. Then regenerate `.claude/nav/adr-index.md` and the architecture model.
- **Executor:** main thread through `/record-decision`; the draft is shown for approval before writing.

##### P3 step 5 - Full verification - done 2026-11-01 (deep-review: no defects, four cosmetic remarks fixed; ci.sh 14/14 green in 25m0s)
- `/deep-review` on the Phase 3 files, then `bash scripts/ci.sh --foreground`. Then the owner reviews; only after that does step 10 (close) run.

### Connecting Claude Code to the server - 2026-11-01

- `claude mcp add --scope local` reported success, but the entry was gone after the session restarted; the working way is the project `.mcp.json` with the header `Authorization: Bearer ${MARKETPLACE_MCP_KEY}`, the key in the git-ignored `.claude/settings.local.json` under `env`, and one approval on the first start.
- Through that connection (a temporary user and API key created over REST) All 11 tools were called from this session: `getMe`, `getMySettings`, `listAdvertisements` (with a title filter and a sort), `getAdvertisement`, `listAdvertisementMedia`, `listProviderProfiles`, `listTaxons` answered as expected, `getTaxon` with an unknown id gave the tool error `HTTP 404: no such resource.`; `getProviderProfile`, `listProviderProfileFeedback` and `getProviderProfileFeedbackAggregate` were called on a profile created over REST (no reviews: empty list, average 0, count 0). Two advertisements and the profile created over REST served as data.

### Cost

About 60k sonnet tokens for step 1, 50k for step 2, 30k for step 3, 30k for step 4, plus a `/deep-review` (about 80k) and the CI run (about 25 minutes). One redeploy (7 minutes) in step 2.

### Risks

- A real deployment on a public address with a browser-based client must list its origin, or that client gets 403; agents are unaffected.
- A tool such as MCP Inspector running in a browser sends `Origin: http://localhost:6274` and is refused by the defaults; it is added to `allowed-origins` when used (its server-side proxy sends no `Origin`).
- The check protects against browser pages only; it does not authenticate anyone (the REST API still does).

## Operational notes
- token_cost_review: 146310
- token_cost_research: 210679
- token_cost_verification: 127673
- review_signal_ratio: 1 / 1
- context_loading_task_type: Resuming a research or a planning run
- context_loading_consulted: yes
- context_loading_matched: yes
- flows_situation: deep researched plan for a task before approving it
- flows_chosen: /deep-research, then /autopilot
- flows_matched: yes
- haiku_decomposition_used: no
- haiku_decomposition_dispatch_count: 0
- haiku_decomposition_rework_needed: 0 / 0
- adrs_referenced: ADR-078 (marketplace-app), ADR-080 (marketplace-app), ADR-087 (marketplace-app), ADR-091 (marketplace-app), ADR-092 (marketplace-app), ADR-002 (docs/api-collections), ADR-003 (docs/api-collections), ADR-004 (docs/api-collections)

### Agent calls
- deep research of the MCP server shape and trial plan | subagent_type=deep-researcher | tokens=210679 | tool_uses=66 | duration_s=996 | mode=background | batch=solo
- P2 step 2 test-script headers and text | subagent_type=general-purpose (sonnet) | tokens=72440 | tool_uses=4 | duration_s=42 | mode=background | batch=solo
- P2 step 3 shared MCP client module | subagent_type=general-purpose (sonnet) | tokens=78661 | tool_uses=6 | duration_s=57 | mode=background | batch=solo
- P2 step 4 coverage check MCP mode | subagent_type=general-purpose (sonnet) | tokens=89920 | tool_uses=9 | duration_s=87 | mode=background | batch=solo
- P2 step 5 page settings passed in | subagent_type=general-purpose (sonnet) | tokens=85144 | tool_uses=7 | duration_s=97 | mode=background | batch=solo
- P2 step 6 MCP flow file | subagent_type=general-purpose (sonnet) | tokens=105290 | tool_uses=14 | duration_s=143 | mode=background | batch=solo
- P2 step 7 MCP page catalog and runner | subagent_type=general-purpose (sonnet) | tokens=79221 | tool_uses=6 | duration_s=67 | mode=background | batch=solo
- P2 step 8 integration and Playwright tests | subagent_type=general-purpose (sonnet) | tokens=85296 | tool_uses=8 | duration_s=71 | mode=background | batch=solo
- P2 step 9 documentation | subagent_type=general-purpose (sonnet) | tokens=78132 | tool_uses=4 | duration_s=38 | mode=background | batch=solo
- Phase 2 /deep-review on the Phase 2 files | subagent_type=deep-review-orchestrator | tokens=76245 | tool_uses=13 | duration_s=228 | mode=background | batch=solo
- P2 step 7 rework, arguments box | subagent_type=general-purpose (sonnet) | tokens=76524 | tool_uses=6 | duration_s=77 | mode=background | batch=solo
- P2 step 7 rework, page like the REST page and filled after a run | subagent_type=general-purpose (sonnet) | tokens=87239 | tool_uses=7 | duration_s=81 | mode=background | batch=solo
- P3 step 1 Origin filter and module tests | subagent_type=general-purpose (sonnet) | tokens=89085 | tool_uses=10 | duration_s=462 | mode=background | batch=solo
- deep research of the MCP admin page (Phase 2 plan) | subagent_type=deep-researcher | tokens=n/a (report: about 235k) | tool_uses=62 | duration_s=n/a (about 17 min) | mode=background | batch=solo
- CI precheck of the ci DAG | subagent_type=dagu-analyst | tokens=43687 | tool_uses=6 | duration_s=21 | mode=background | batch=solo
- P3 step 1 Tools tab defaults and examples | subagent_type=general-purpose (sonnet) | tokens=81296 | tool_uses=n/a | duration_s=n/a | mode=background | batch=solo
- Phase 3 /deep-review on the Phase 3 files | subagent_type=deep-review-orchestrator | tokens=70065 | tool_uses=5 | duration_s=53 | mode=background | batch=solo
- trial MCP server build | subagent_type=general-purpose (sonnet) | tokens=83986 | tool_uses=10 | duration_s=234 | mode=background | batch=solo

### Script/command runs
- build-and-test.sh --unit --integration --sandbox --skip-vaadin (Phase 3 step 1) | duration_s=429 | mode=background | result=pass
- build-and-test.sh --no-unit --integration --integration-test McpEndToEndTest | duration_s=141 | mode=background | result=pass
- deploy-and-run.sh --dev-tools | duration_s=165 | mode=background | result=pass
- playwright.sh 11-api-flow-runner --ux | duration_s=74 | mode=background | result=pass
- api-collection-tests.sh --config scripts/mcp-collection.config.json | duration_s=n/a | mode=foreground | result=pass
- ci.sh --foreground (Phase 2) | duration_s=1471 | mode=background | result=pass
- ci.sh --foreground (Phase 3) | duration_s=1500 | mode=background | result=pass
- trial MCP server (java -jar, port 18090) | duration_s=n/a | mode=background | result=pass
- mcp-inspector CLI, 11 tool and list calls | duration_s=n/a | mode=foreground | result=pass
- claude -p --model haiku against the trial server | duration_s=n/a | mode=foreground | result=pass
