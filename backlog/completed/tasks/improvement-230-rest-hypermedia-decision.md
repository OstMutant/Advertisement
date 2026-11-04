# improvement-230: REST hypermedia decision — do agents and clients need `_links` and `GET /api/me`

**Type:** improvement — decision first, implementation only if the decision is yes
**Module:** `marketplace-rest-api`, `marketplace-orchestrator`, `platform-commons` (only if implemented)
**Priority:** medium — Top, right after [improvement-221](improvement-221-mcp-server-trial-run.md); a decision, not yet work
**When:** blocked on [improvement-221](improvement-221-mcp-server-trial-run.md) — its trial shows how an agent actually uses the API, which is the evidence this decision needs; do not start before it

## Current state

Carved out of [improvement-186](improvement-186-rest-hypermedia-media-subresource-and-patch-support.md) item 3 on 2026-10-28; that task now covers `PATCH` only. Facts:

- No response carries links or action hints. Every single-resource response is a flat DTO, so a caller must already know from Swagger which operations apply to a given resource for the current actor.
- `GET /api/users/me` (operationId `getMe`, the caller's own account with its id) was added by improvement-221; what is still open is a list of the caller's available actions and links to actions on resources.
- The `marketplace-app` architecture decision on list pagination chose RFC 8288 `Link` + `X-Total-Count` headers and rejected Spring HATEOAS and an envelope body. It decided pagination only; it did not rule on action discovery for a single resource (see `.claude/nav/adr-index.md`, title "External REST API list endpoints").
- The design drafted in improvement-186 item 3: hand-built HAL-shaped `_links` on single-resource responses, present per actor (`self` always; `edit`/`delete`/`media` only when `AuthorizationService.canOperate` is true), a new `GET /api/me` listing the actor's top-level actions by role, and the `swagger-hal-navigator` add-on for Swagger UI. Pagination links inside list bodies were explicitly left out because they would reverse the pagination decision.
- The root `README.md` lists "REST hypermedia (HATEOAS)" under planned directions.

## Why change

Nothing yet proves that a client needs this. The known consumers are Swagger UI, the API flow runner and the planned MCP server; the MCP server takes tool names from `operationId`, so it may not need links at all. Building `_links` for every resource DTO and keeping them in step with the authorization rules is real, permanent upkeep, so it should be decided from evidence, not assumed.

## Expected benefit

A recorded yes or no with reasons. If no: a decision record that stops the topic from coming back, and the README direction removed. If yes: an implementation scoped to what the observed clients actually use instead of the full HAL design.

## Approach

1. After improvement-221 is done, read its recorded finding on whether an agent lacked a hint about the actions available on a resource, and list any other client that would read links.
2. Decide among: (a) no hypermedia — record it with `/record-decision` and remove the README direction; (b) only `GET /api/me`, without HAL; (c) the full design from improvement-186 item 3. Revisiting list pagination inside bodies stays out of scope and needs its own separate go-ahead.
3. If (b) or (c): write the implementation plan into this file, with the verification (an API check per role: owner, other user, admin) and the Swagger UI step.

## Decision and execution plan (2026-11-03)

Research: `backlog/research/rest-hypermedia-decision/` (`plan-human.md`, `plan-machine.md`, `notes.md`, `sources.md`). The user chose **(a): no hypermedia** and answered: fix the three advertisement descriptions — yes; remove the whole README bullet — yes; `totalCount` sentence in the MCP list tools — yes; record in a new ADR in `marketplace-app/DECISIONS.md` (also affects `marketplace-rest-api`, `marketplace-mcp`) — yes; task for the broken `md-to-decisions-json.js --extract` — no.

Each item is approved on its own before it runs; mark it done here with its date.

- [x] (done 2026-11-03) 1. `/record-decision marketplace-app` — new ADR "External REST API carries no hypermedia"; regenerate `.claude/nav/adr-index.md`. Executor: sonnet, main thread.
- [x] (done 2026-11-03) 2. `README.md` "Planned directions": remove the whole bullet "REST hypermedia (HATEOAS); broader `PATCH` support ..." (two lines). Executor: haiku.
- [x] (done 2026-11-03) 3. `marketplace-rest-api/.../AdvertisementApiController.java`: in the `@Operation` descriptions of `updateAdvertisement`, `patchAdvertisement`, `deleteAdvertisement` replace `the caller must own the advertisement.` with `the owner of the advertisement, an administrator or a moderator may call it, with their API key.` Executor: haiku.
- [x] (done 2026-11-03; the feedback list tool got a different sentence — its totalCount counts only visible reviews while the list also returns hidden ones, a REST pagination mismatch left as is) 4. `marketplace-mcp/.../tools/` (`AdvertisementTools`, `AuditTools`, `FeedbackTools`, `ModerationTools`, `ProviderProfileTools`, `UserTools`): before `The result has items, totalCount` add one sentence that `totalCount` is the number of all items matching the filter across all pages, after confirming that from each REST controller. Executor: sonnet.
- [x] (done 2026-11-03: unit+integration 1817 tests, 0 failed; REST flows by role user/moderator/administrator passed; MCP flows by role passed; live checks: stranger PUT/DELETE → 403 over REST and `HTTP 403` tool error over MCP, owner PUT/update → 200, `getMe` returns `role`) 5. `bash scripts/activity-monitor.sh -- scripts/build-and-test.sh --unit --integration --sandbox --skip-vaadin`; per-role check (owner, other user, administrator/moderator) from the named level-3 tests; `run-flows.sh --each-role` and the MCP collection. Executor: sonnet.
- [x] (done 2026-11-03: `getMe` response schema `UserDto` carries `id` and `role`; the three advertisement descriptions carry the new text and "must own" is gone from the whole spec; no `_links` anywhere in `/v3/api-docs`) 6. Swagger UI check: `getMe` in `/v3/api-docs`, corrected descriptions, no `_links`. Executor: haiku.
- [x] (done 2026-11-04; reopened 2026-11-03 for item 8, earlier: after a code review and a docs sync: the REST description of the feedback list now says what X-Total-Count counts; the owner/administrator/moderator sentence is one constant in the advertisement controller; the shared totalCount sentence is one constant in the MCP tools package) 7. Close the task (only when the user asks): move to `backlog/completed/tasks/`, remove the `BACKLOG.md` row, `✅ Done` line in `BACKLOG-ARCHIVE.md`, `Applied` +1 on the ADRs it followed, regenerate the ADR index.

### Item 8 — the feedback list total (found by the code review, 2026-11-03)

`listProviderProfileFeedback` (REST and MCP) and the Vaadin `FeedbackPanel` pager take the total from the rating aggregate (`reviewCount`, visible reviews only), while the list returns every entry including hidden ones as censored rows. With hidden reviews the total is too low, so `hasMore`/`nextPage` and the page count stop early. `FeedbackPort.count` already counts every entry of the entity; only the orchestrator does not expose it. Fix the behaviour, then drop the special wording written for the old behaviour.

- [x] (done 2026-11-04) 8a. `marketplace-orchestrator` `FeedbackAccessService`: add `count(EntityType, Long)` next to `findForEntity`, same shape (`feedbackPortFactory.findIfAvailable().map(port -> port.count(entityType, entityId)).orElse(0)`).
- [x] (done 2026-11-04) 8b. `FeedbackApiController.listProviderProfileFeedback`: `int total = feedbackAccessService.count(EntityType.PROVIDER_PROFILE, id);` instead of the aggregate's `reviewCount()`; remove the sentence about `X-Total-Count` counting only visible reviews from the operation description.
- [x] (done 2026-11-04) 8c. `FeedbackTools` (MCP): replace the special sentence with `ToolDescriptions.TOTAL_COUNT_NOTE`, so all six list tools say the same.
- [x] (done 2026-11-04) 8d. `marketplace-app` `FeedbackPanel.refreshHeader`: the pager total (`paginationBar.setTotalCount`) comes from `feedbackAccessService.count(...)`; the header's rating, average and "N reviews" label stay on the aggregate.
- [x] (done 2026-11-04) 8e. Tests: in `FeedbackScenarioTest` (level 3, REST) flag a review so it becomes hidden and assert `X-Total-Count` equals the number of returned entries; a unit test for `FeedbackAccessService.count` in `marketplace-orchestrator` in the style of its sibling tests. No other test is changed.
- [x] (done 2026-11-04) 8f. Verify: `build-and-test.sh --unit --integration --sandbox --skip-vaadin`; redeploy with `deploy-and-run.sh` and run `run-flows.sh --each-role` (REST and MCP config); a live check with a hidden review (flag as a second user, list as a visitor, compare `X-Total-Count` with the entries).
- [x] (done 2026-11-04) 8g. Playwright, extending the existing `04-provider-profile-flow.spec.js` test "moderator works a 7-item moderation queue ..." (no new spec file): add two unflagged reviewers so the profile has 6 feedback entries of which 3 are hidden; set the moderator's feedback page size to the minimum (5) through the settings API and restore it afterwards; open the provider overlay and assert the feedback pager reads "1–5 of 6", the next page shows 1 entry and previous/first/last work; the queue counts (7 items) must stay unchanged. Screenshot per pager state. With the old total (3 visible) the pager would show one page and the sixth entry would be unreachable, so this step fails on the old behaviour.
- [x] (done 2026-11-04) 8h. Run `playwright.sh e2e --full --ux` through the activity monitor after the redeploy (Definition of Done for a UI-visible change), and check the screenshots of the new pager states.
- [x] (done 2026-11-04) 8i. Found by the Playwright pager step: `PaginationBar` buttons changed `currentPage` and called the listener without refreshing the bar's own label, page indicator and button state, so a consumer that does not call `setTotalCount` in its callback (the feedback panel) showed stale text after a click. Fix at the root: `triggerCallback()` calls `updateUI()` first; every other view is unaffected because its `refresh()` calls `setTotalCount` afterwards anyway.
- [x] (done 2026-11-04) 8j. Found by the same screenshots: the header "(3 feedback entries)" (rating basis, visible reviews only) contradicted the pager's "6 records". The header label is now the bare count "(3)" next to the stars with a `title` of "Ratings: 3" / "Оцінок: 3" (new key `feedback.aggregate.count.title`, both locales); the three affected assertions in the provider-profile spec follow, and the pager screenshot of page 1 scrolls the pager into view.
- [x] (done 2026-11-04) 8k. `FeedbackApiControllerTest` (unit, `marketplace-rest-api`) stubbed `getAggregate` for the list total and failed once the controller used `count`; three tests updated, the hidden-entry one now also asserts `X-Total-Count`.

## Related

- [improvement-221](improvement-221-mcp-server-trial-run.md) — supplies the evidence
- [improvement-186](improvement-186-rest-hypermedia-media-subresource-and-patch-support.md) — the original design, now `PATCH` only
- `.claude/nav/adr-index.md` — the pagination decision and its rejected Spring HATEOAS alternative

## Operational notes
- token_cost_review: 81148
- token_cost_research: 169113
- token_cost_verification: 915122
- review_signal_ratio: 3 / 4
- context_loading_task_type: n/a
- context_loading_consulted: yes
- context_loading_matched: n/a
- flows_situation: a deep, researched plan for a decision before approving it
- flows_chosen: /deep-research
- flows_matched: yes
- haiku_decomposition_used: yes
- haiku_decomposition_dispatch_count: 20
- haiku_decomposition_rework_needed: 1 / 20
- adrs_referenced: ADR-093 (marketplace-app), ADR-080 (marketplace-app), ADR-092 (marketplace-app)

### Agent calls
- deep research of the hypermedia decision | subagent_type=deep-researcher | tokens=169113 | tool_uses=71 | duration_s=6376 | mode=background | batch=solo
- remove the README bullet | subagent_type=general-purpose (haiku) | tokens=70496 | tool_uses=4 | duration_s=19 | mode=background | batch=solo
- correct three advertisement permission descriptions | subagent_type=general-purpose (haiku) | tokens=87085 | tool_uses=7 | duration_s=37 | mode=background | batch=solo
- add the totalCount sentence to five MCP list tools | subagent_type=general-purpose (sonnet) | tokens=93486 | tool_uses=11 | duration_s=90 | mode=background | batch=solo
- add the totalCount sentence to the feedback list tool | subagent_type=general-purpose (haiku) | tokens=74163 | tool_uses=4 | duration_s=43 | mode=background | batch=solo
- code review of the staged change (default effort, targeted at the totalCount and role claims) | subagent_type=code-review | tokens=81148 | tool_uses=5 | duration_s=59 | mode=background | batch=solo
- align the REST feedback description | subagent_type=general-purpose (haiku) | tokens=77175 | tool_uses=4 | duration_s=32 | mode=background | batch=parallel-1
- extract the write-permission constant | subagent_type=general-purpose (haiku) | tokens=87583 | tool_uses=7 | duration_s=45 | mode=background | batch=parallel-1
- extract the shared totalCount constant | subagent_type=general-purpose (haiku) | tokens=88467 | tool_uses=15 | duration_s=83 | mode=background | batch=parallel-1
- fix the feedback list total across layers (orchestrator, REST, MCP, Vaadin, two tests) | subagent_type=general-purpose (sonnet) | tokens=127225 | tool_uses=20 | duration_s=135 | mode=background | batch=solo
- extend the provider-profile spec with the feedback pager step | subagent_type=general-purpose (sonnet) | tokens=97359 | tool_uses=7 | duration_s=77 | mode=background | batch=solo
- update three FeedbackApiControllerTest stubs | subagent_type=general-purpose (haiku) | tokens=84633 | tool_uses=8 | duration_s=38 | mode=background | batch=solo
- make PaginationBar refresh its own state on a click | subagent_type=general-purpose (haiku) | tokens=78147 | tool_uses=5 | duration_s=31 | mode=background | batch=solo
- rework the rating count label and the page 1 screenshot | subagent_type=general-purpose (haiku) | tokens=186847 | tool_uses=19 | duration_s=95 | mode=background | batch=solo
- verification runs, eleven haiku agents running a script and reporting from its live log | subagent_type=general-purpose (haiku) | tokens=915122 | tool_uses=n/a | duration_s=n/a | mode=background | batch=solo

### Script/command runs
- scripts/build-and-test.sh --unit --integration --sandbox --skip-vaadin | duration_s=481 | mode=background | result=pass
- scripts/build-and-test.sh (after the review fixes) | duration_s=488 | mode=background | result=pass
- scripts/build-and-test.sh (after the feedback total fix) | duration_s=361 | mode=background | result=fail
- scripts/build-and-test.sh (after the unit test update) | duration_s=428 | mode=background | result=pass
- scripts/build-and-test.sh (after the PaginationBar fix) | duration_s=441 | mode=background | result=pass
- scripts/build-and-test.sh (after the label rework) | duration_s=429 | mode=background | result=pass
- scripts/deploy-and-run.sh, four runs | duration_s=153 | mode=background | result=pass
- docs/api-collections/scripts/run-flows.sh --each-role (REST and MCP config), twice, and the hidden-review live check | duration_s=n/a | mode=foreground | result=pass
- docs/architecture/scripts/generate-architecture-model.sh | duration_s=304 | mode=foreground | result=pass
- scripts/playwright.sh e2e --full --ux (before the PaginationBar fix) | duration_s=731 | mode=background | result=fail
- scripts/playwright.sh e2e --full --ux (after the PaginationBar fix) | duration_s=742 | mode=background | result=pass
- scripts/playwright.sh e2e --full --ux (after the label rework) | duration_s=737 | mode=background | result=pass

### Review angle yield
- targeted check of the totalCount and role claims (phase 1 only) | survived=3 | total_candidates=4 | tokens=81148
