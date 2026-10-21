# improvement-219: Full REST coverage — an operation coverage check, edge cases in the integration scenarios, and a smoke set through the real stack

**Type:** improvement — test coverage of the external API
**Module:** `integration-tests` (`level3/restapi`), `playwright/e2e`, `marketplace-rest-api`
**Priority:** high — Top, right after improvement-213; a live walk of the API on 2026-10-21 showed the integration scenarios miss a whole class of failures
**When:** independent; best done right after the final verification of [improvement-213](../completed/tasks/improvement-213-rest-api-revision-parity-with-ui.md)

## Current state

Read on 2026-10-21 against the running application. The live OpenAPI spec has 68 operations, every one with an explicit `operationId`. Coverage today: 17 Level 3 scenario classes in `integration-tests/.../level3/restapi` (real security chain and real Postgres, driven by `MockMvc`, with the UI-only hooks replaced by stubs by `RestApiTestSupport`), 10 `MockMvc` controller test classes with mocked services, and one Playwright file, `09-rest-api-swagger`, with three tests (health, the two auth schemes in the spec, Swagger UI reachable). Nothing checks that every operation is exercised at all.

A live walk of the redeployed application found what the scenarios cannot: `GET /api/advertisements/{id}/history` and `POST /api/provider-profiles` answered `500` with `No VaadinSession bound to current thread`, because in the real application the forwarder hooks (locale, labels) are backed by Vaadin session state, while the Level 3 context replaces them with stubs on the assumption that REST never reaches them.

## Why change

A request is only proven to work when it runs through the same wiring as production. The integration scenarios are fast and precise for rules and edge cases, but they hide wiring faults; nothing guarantees that a new operation gets a test.

## Expected benefit

Every operation is known to be covered (a build fails otherwise), the edge cases live where they are cheap to write and fast to run, and a small set of requests runs through the real deployed stack so wiring faults like the one above surface in CI.

## Approach

1. **Operation coverage check (Level 3):** one test reads the operation list from `/v3/api-docs` and fails when an `operationId` is not named, or its path and method not exercised, by any scenario; a short allow-list with a reason covers operations that cannot be driven in-process.
2. **Close the gaps it reports with Level 3 scenarios,** including the edge cases per operation: authorization (visitor, other user, owner, administrator, moderator), validation (blank, too long, wrong enum), `If-Match` (missing, stale), not found, conflict, and the daily and per-address limits. Make the Level 3 context closer to production for the hooks where it can be done without Vaadin (so the faults of item 3 are caught here too). The Level 3 context stubs the forwarder hooks (`RestApiTestSupport`), which is why a UI-scoped locale provider reached from REST was invisible to every scenario; replace the stubs with the real wiring where it can be done without Vaadin, or cover the real hooks in a context test.
3. **Playwright smoke through the real stack (new section next to `09`):** about one scenario per domain using the `request` fixture against the e2e stack — register, key, advertisement with a real file in SeaweedFS, profile, contact reveal, review, history, restore, moderation. Their job is wiring, real HTTP headers and storage, not edge cases.
4. **Fix the faults found:** the session-bound hooks (see "Current state") are fixed first, under improvement-213, before this task starts.
5. **Missing `If-Match` must be a 400, never reach the save:** `PUT /api/users/{id}` declares `If-Match` as optional while its description calls it required; a request without it returns `500` (the null version makes Spring Data attempt an `INSERT`, which the database rejects on the `email` NOT NULL constraint, seen live). The same `parseIfMatch` call sits in the advertisement, provider profile and taxon `PUT`/`DELETE` and the settings `PATCH`. Use `requireIfMatch` wherever the description demands the version, and add a Level 3 case per operation (missing header, stale header). Also decide how a non-privileged user obtains their own version: `GET /api/users/{id}` is administrator and moderator only, yet the description lets a user change their own name.
6. **Moderation transitions must be atomic:** `FeedbackAccessService` checks the entry's status before its transaction, and the feedback starter's `UPDATE feedback_content SET moderation_status = :status WHERE id = :contentId` has no status guard or row-count check, so two moderators deciding on the same entry both pass the check, the last write wins and the history holds two contradictory entries (an approval racing a rejection also records an audit entry for a change that touched no row). The feedback starter's own `approve*` and `confirm*Violation` methods read the entry and then write unconditionally too, so the guard belongs in `FeedbackRepository`, not only in the orchestrator. Move the allowed source statuses into the `UPDATE` (a single transition method on the port) and turn zero updated rows into `FeedbackNotModeratableException` inside the transaction that records the audit entry; cover it with a test that decides twice on the same stale status.
7. **Run `/review` and `/sync-docs` over phase 10 of [improvement-213](../completed/tasks/improvement-213-rest-api-revision-parity-with-ui.md)** (language resolution, the moderation and timeline guards, the Sonar fixes, the application's own jar copy, the Telegram stub in the end-to-end spec) first thing in this task: they were not run before that phase was saved into history.

## Related

- [improvement-213](../completed/tasks/improvement-213-rest-api-revision-parity-with-ui.md) — the REST work being verified
- [improvement-211](improvement-211-rest-api-testing-ui-and-mcp-readiness-investigation.md) and [improvement-212](improvement-212-api-flow-runner-page.md) — Postman collection and flow runner, which share the request set
- `playwright/e2e/09-rest-api-swagger.spec.js` — the existing REST Playwright file

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
