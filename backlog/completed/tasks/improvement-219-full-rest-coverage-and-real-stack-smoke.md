# improvement-219: Full REST coverage — an operation coverage check, edge cases in the integration scenarios, and a smoke set through the real stack

**Type:** improvement — test coverage of the external API
**Module:** `integration-tests` (`level3/restapi`), `playwright/e2e`, `marketplace-rest-api`
**Priority:** high — Top, right after improvement-213; a live walk of the API on 2026-10-21 showed the integration scenarios miss a whole class of failures
**When:** independent; best done right after the final verification of [improvement-213](improvement-213-rest-api-revision-parity-with-ui.md)

## Current state

Read on 2026-10-21 against the running application. The live OpenAPI spec has 68 operations, 43 of them with an explicit `operationId` (the other 25 are named by item 1). Coverage today: 17 Level 3 scenario classes in `integration-tests/.../level3/restapi` (real security chain and real Postgres, driven by `MockMvc`, with the UI-only hooks replaced by stubs by `RestApiTestSupport`), 10 `MockMvc` controller test classes with mocked services, and one Playwright file, `09-rest-api-swagger`, with three tests (health, the two auth schemes in the spec, Swagger UI reachable). Nothing checks that every operation is exercised at all.

A live walk of the redeployed application found what the scenarios cannot: `GET /api/advertisements/{id}/history` and `POST /api/provider-profiles` answered `500` with `No VaadinSession bound to current thread`, because in the real application the forwarder hooks (locale, labels) are backed by Vaadin session state, while the Level 3 context replaces them with stubs on the assumption that REST never reaches them.

## Why change

A request is only proven to work when it runs through the same wiring as production. The integration scenarios are fast and precise for rules and edge cases, but they hide wiring faults; nothing guarantees that a new operation gets a test.

## Expected benefit

Every operation is known to be covered (a build fails otherwise), the edge cases live where they are cheap to write and fast to run, and a small set of requests runs through the real deployed stack so wiring faults like the one above surface in CI.

## Approach

1. **Operation coverage check (Level 3):** one test reads the operation list from `/v3/api-docs` and fails when an `operationId` is not named, or its path and method not exercised, by any scenario; a short allow-list with a reason covers operations that cannot be driven in-process.
2. **Close the gaps it reports with Level 3 scenarios,** including the edge cases per operation: authorization (visitor, other user, owner, administrator, moderator), validation (blank, too long, wrong enum), `If-Match` (missing, stale), not found, conflict, and the daily and per-address limits. Make the Level 3 context closer to production for the hooks where it can be done without Vaadin (so the faults of item 3 are caught here too). The Level 3 context stubs the forwarder hooks (`RestApiTestSupport`), which is why a UI-scoped locale provider reached from REST was invisible to every scenario; replace the stubs with the real wiring where it can be done without Vaadin, or cover the real hooks in a context test.
3. **Done 2026-10-22.** **Playwright smoke through the real stack (new section next to `09`):** about one scenario per domain using the `request` fixture against the e2e stack — register, key, advertisement with a real file in SeaweedFS, profile, contact reveal, review, history, restore, moderation. Their job is wiring, real HTTP headers and storage, not edge cases.
4. **Fix the faults found:** the session-bound hooks (see "Current state") are fixed first, under improvement-213, before this task starts.
5. **Done 2026-10-22.** **Missing `If-Match` must be a 400, never reach the save:** `PUT /api/users/{id}` declares `If-Match` as optional while its description calls it required; a request without it returns `500` (the null version makes Spring Data attempt an `INSERT`, which the database rejects on the `email` NOT NULL constraint, seen live). The same `parseIfMatch` call sits in the advertisement, provider profile and taxon `PUT`/`DELETE` and the settings `PATCH`. Use `requireIfMatch` wherever the description demands the version, and add a Level 3 case per operation (missing header, stale header). Also decide how a non-privileged user obtains their own version: `GET /api/users/{id}` is administrator and moderator only, yet the description lets a user change their own name.
6. **Done 2026-10-22.** **Moderation transitions must be atomic:** `FeedbackAccessService` checks the entry's status before its transaction, and the feedback starter's `UPDATE feedback_content SET moderation_status = :status WHERE id = :contentId` has no status guard or row-count check, so two moderators deciding on the same entry both pass the check, the last write wins and the history holds two contradictory entries (an approval racing a rejection also records an audit entry for a change that touched no row). The feedback starter's own `approve*` and `confirm*Violation` methods read the entry and then write unconditionally too, so the guard belongs in `FeedbackRepository`, not only in the orchestrator. Move the allowed source statuses into the `UPDATE` (a single transition method on the port) and turn zero updated rows into `FeedbackNotModeratableException` inside the transaction that records the audit entry; cover it with a test that decides twice on the same stale status. The guard is `FeedbackRepository.transitionModerationStatus` (and a guarded `flagContent`); a decision on a stale status throws `FeedbackNotModeratableException` or `FeedbackNotFlaggableException` before any audit entry is written, covered by repository, service (two-thread race) and orchestrator tests.
7. **Done 2026-10-21 (`/review` found nothing; `/sync-docs` fixed 15 files).** **Run `/review` and `/sync-docs` over phase 10 of [improvement-213](improvement-213-rest-api-revision-parity-with-ui.md)** (language resolution, the moderation and timeline guards, the Sonar fixes, the application's own jar copy, the Telegram stub in the end-to-end spec) first thing in this task: they were not run before that phase was saved into history.

## Plan for item 1 (operation identity and coverage check) — drafted 2026-10-21; step 1 approved and in progress, steps 2-9 each need their own plan and approval

Research (2026-10-21): per-request OpenAPI validators are stateless and do not track what was exercised, so coverage needs its own record-then-compare step (the same two-stage shape `swagger-coverage` uses, a tool built around OpenAPI 2 and a separate report, so a ~40-line recorder fits better); guides separate operation coverage (method + path hit) from response-code and contract coverage. The Atlassian `swagger-request-validator-mockmvc` validates each response against the spec (`openApi().isValid(...)`) but its Spring 7 / Boot 4 support is unconfirmed. REST Assured and `RestTestClient` change the client, not the coverage problem. Schemathesis is a Python tool against the real stack: a possible later input to item 3, not part of this item. springdoc silently renames a duplicate `operationId` (`save_1`), so uniqueness must be checked on the generated spec.

Facts read from code on 2026-10-21: 68 `@Operation` annotations, 43 with an explicit `operationId` (all unique, verb + entity camelCase), 25 without. The 25 are the original operations: `HealthController`, `SitemapController`, advertisement create/list/get/update/delete, provider-profile create/list/get/update/delete, taxon create/list/get/update/delete, API-key issue/list/revoke, user register/list/get, and `GET`/`PATCH /api/users/me/settings`. The convention is already specified in improvement-211 item 2.

Order (each step leaves the build green):
1. **Done 2026-10-21 (68 of 68 operations carry a unique explicit `operationId`; unit run green).** **Name the 25 operations** (approved 2026-10-21, dispatched to a Haiku agent): `getHealth`, `getSitemap`; `create`/`list`/`get`/`update`/`delete` + `Advertisement`/`ProviderProfile`/`Taxon` (list is plural: `listAdvertisements`, `listProviderProfiles`, `listTaxons`); `issueApiKey`, `listApiKeys`, `revokeApiKey`; `registerUser`, `listUsers`, `getUser`, `getMySettings`, `updateMySettings`. Afterwards improvement-211 item 2 is marked done for the `operationId` part and gains the Postman-collection binding sub-task (done in the same operation).
2. **Done 2026-10-21 (ArchUnit rule green on the current code, red with the method name when one `operationId` is removed); uniqueness is still checked in step 5.** **Guard it twice:** an ArchUnit rule in the existing `ArchitectureRulesTest` (every request-mapped controller method carries `@Operation` with a non-blank `operationId`) and, in the coverage test below, uniqueness on the generated spec.
2b. **Done 2026-10-21 (green on the current code, red naming both methods when two share an id).** **Uniqueness in ArchUnit (approved 2026-10-21 as a step of its own):** a second rule in `ArchitectureRulesTest` collects every explicit `operationId` across `org.ost.restapi..` and fails on a repeat, naming both methods, so a duplicate fails in the unit stage without Docker (springdoc renames a clash silently, it never errors).
3. **Spike dropped 2026-10-21:** the Level 3 context uses an explicit auto-configuration allow-list without springdoc, and outside `/api/**` default Spring Security would answer 401; instead the operation list comes from `RequestMappingHandlerMapping.getHandlerMethods()` in the same context (HTTP method, path template, `@Operation.operationId`), so no springdoc wiring is needed and the templates are the same Spring strings the recorder stores. The real stack still serves the spec, checked by Playwright `09-rest-api-swagger`.
4-7. **Done 2026-10-21; the allow-list is empty, so all 68 operations are reached by Level 3 scenarios.** Recorder (`ExercisedOperations`, `.alwaysDo` in `AbstractRestApiScenarioTest`), `OperationCoverageTest` with `@Order(Integer.MAX_VALUE)` and the built-in `ClassOrderer$OrderAnnotation` (no custom orderer). The check first found 15 operations without a scenario (the allow-list was briefly seeded with them, which the requirements do not allow); they are now covered by 54 new Level 3 tests (`TaxonCatalogScenarioTest`, `UserSettingsScenarioTest`, `PublicEndpointsScenarioTest`, plus additions to `ContactScenarioTest`, `ModerationScenarioTest`, `ProviderProfileAuthorizationScenarioTest`, `SnapshotRestoreScenarioTest`) with authorization, validation, stale and missing `If-Match`, not-found and conflict cases per operation. Verified with `build-and-test.sh --no-unit --integration --skip-vaadin`: 512 tests green; a removed allow-list entry fails naming only `updateTaxon`; a covered and an unknown entry fail as stale; run together with one scenario class it is skipped. Still open in item 2: the same edge-case matrix for the other 53 operations, which were already reached by earlier scenarios. **Step 4 detail:** `AbstractRestApiScenarioTest` registers `.alwaysDo(...)` on `MockMvc`, storing `(method, best-matching pattern, status)` in a thread-safe static `ExercisedOperations` (paths such as `feedbackPath(id)` or `mediaUrl + "/" + id` make any source-text scan unreliable).
5. **Compare:** `OperationCoverageTest` takes the operation list from `RequestMappingHandlerMapping` in the same context and fails listing each `operationId` (method + path) with no recorded request. Recorded statuses against the response codes declared on the handler methods are printed only (item 2 owns edge cases).
6. **Run last, never falsely red:** `src/test/resources/junit-platform.properties` sets `junit.jupiter.testclass.order.default` to a small `ClassOrderer` that puts the coverage test last. `build-and-test.sh --integration-test X` passes `-Dtest=X`, so the skip cannot rely on the filter text: the base class records every scenario class that ran, the coverage test scans the classpath for every `@Level3ScenarioTest` class and is skipped (`Assumptions`) unless all of them ran. An operation counts as covered when a request reached its handler (security rejections before the handler do not count).
7. **Allow-list as a file:** `level3/operation-coverage-allowlist.txt`, one `METHOD /path # reason` per line, a stale entry also fails so the list only shrinks; seeded with every current gap ("gap, closed by item 2") so the build is green and item 2 empties it.
8. **Optional, decide later:** `swagger-request-validator-mockmvc` as `.alwaysExpect(openApi().isValid(...))` to check every scenario response against the spec; it needs springdoc wired into the Level 3 context and a confirmed Spring 7 / Boot 4 build, so it is skipped unless step 5 shows a gap only it can close.
9. **Verify:** `bash scripts/build-and-test.sh --unit --integration --sandbox` green; a deliberately removed scenario call turns it red with the right `operationId`; a single-class `-Dtest` run skips the coverage test.

## Plan for item 2 (edge-case matrix for the 53 operations reached earlier) — approved 2026-10-21, stage A in progress

Matrix per operation (requirement text of item 2): C1 anonymous 401 on a secured operation; C2 forbidden role or foreign resource 403; C3 every allowed role succeeds (owner, administrator, moderator); C4 validation 400 (blank, too long, wrong enum); C5 unknown id 404 (400 where the code defines it so); C6 stale `If-Match` 412; C7 missing `If-Match` 400; C8 conflict 409; C9 limits 429. Expected statuses come from the code (`ApiExceptionHandler`, services), never adjusted to fit a run; a mismatch is reported as a finding.

Item 5 is taken together with the matrix, not deferred: for the seven operations whose `If-Match` is optional (`updateAdvertisement`, `deleteAdvertisement`, `updateProviderProfile`, `deleteProviderProfile`, `updateTaxon`, `deleteTaxon`, `updateUser`) the current response to a request without the header is checked first, then the controllers switch to `requireIfMatch` with a required header, and the missing-header case (400) is written in the same batch.

Order, each stage needs its own approval and ends with a full `build-and-test.sh --no-unit --integration --skip-vaadin` run:
A. Measure: `OperationCoverageTest` prints, per operation, the statuses seen (`ExercisedOperations` records the status, `AbstractRestApiScenarioTest` passes it); the table shows which matrix cases already exist.
B1 (done 2026-10-22: 55 tests in `AdvertisementMatrixScenarioTest` and `AdvertisementMediaMatrixScenarioTest`, `updateAdvertisement` and `deleteAdvertisement` now require `If-Match`, 567 tests green). B2 (spec for approval 2026-10-22: `ProviderProfileMatrixScenarioTest` and `ContactMatrixScenarioTest`; then the `requireIfMatch` change for `updateProviderProfile` and `deleteProviderProfile`). B2 done 2026-10-22 (68 tests in `ProviderProfileMatrixScenarioTest` and `ContactMatrixScenarioTest`, `updateProviderProfile` and `deleteProviderProfile` now require `If-Match`, 635 tests green). B3 done 2026-10-22 (77 tests, 712 green, no mismatch). B4 done 2026-10-22 (48 tests, 8 validation defects fixed, `updateTaxon` and `deleteTaxon` now require `If-Match`, 760 green). B5 (spec for approval 2026-10-22: `UserMatrixScenarioTest`, `MyAccountMatrixScenarioTest`; `page=-1` on `listUsers` included; then `requireIfMatch` for `updateUser`; `page=-1` for the other list operations is a separate small batch). B4 spec was ( `TaxonMatrixScenarioTest`, `ApiKeyMatrixScenarioTest`, `AuditTimelineMatrixScenarioTest`; then `requireIfMatch` for `updateTaxon` and `deleteTaxon`). B3 spec was ( `FeedbackMatrixScenarioTest` and `ModerationMatrixScenarioTest`; the 409 "edit window closed" case needs a 48 hour clock jump and is not reachable through REST without a clock hook). B1. Advertisement (8) and advertisement media (4). B2. Provider profile (7) and contact (4). B3. Feedback (9) and moderation (4). B4. Taxon (4), API key (3), audit (1). B5. User (9).
For each batch: read the services for roles and limits, write the table operation x missing case x expected status from the code, give Haiku the exact tests, run, report every mismatch with the server response.
Open leftovers of items 2 and 5 found 2026-10-22 (plan for approval): (a) item 2 last sentence, the five forwarder hooks are still stubbed in `RestApiTestSupport`; the real ones live in `marketplace-app`, which `integration-tests` cannot import, and `AuthContextService` and `UiLocaleHookImpl` already have REST-thread unit tests, so the plan adds `I18nServiceImplTest` and `SettingsPaginationServiceTest` (REST caller, no UI) and makes the Playwright smoke of item 3 repeat the two calls that failed in the live walk (provider profile create, advertisement history), which is the wiring-level check; (b) item 5 last sentence, `GET /api/users/{id}` lets a user read their own record with its `ETag` (administrator, moderator or the user themselves), because the description of `PUT /api/users/{id}` lets a user change their own name and no other call returns the version.
Done 2026-10-22: both leftovers above (`GET /api/users/{id}` open to the user themselves, `I18nServiceImplTest` and `SettingsPaginationServiceTest`; unit 213/226/91 and integration 841 green) and item 3: `playwright/e2e/10-rest-api-flow.spec.js`, 9 serial tests through the real stack (register and key, reference data, provider profile with history and restore, advertisement with a real file stored in SeaweedFS and fetched back, contact reveal, review and reply, moderation, audit and settings restore, account deletion); after `deploy-and-run.sh --reset-only-db` the full `e2e --ux` run was 62 passed, 13 skipped (the seed spec), none failed, 11m27s.
Known gap, skipped on purpose (decided 2026-10-22): the 409 "edit window closed" case (48 hours, `FeedbackEditPolicy.MAX_EDIT_AGE`) of `updateFeedback`, `updateFeedbackReply` and `deleteFeedbackReply` has no Level 3 test, because a REST scenario cannot move the clock. Closing it needs a clock hook in the feedback starter, or a Level 1 service test with an aged record.
Done 2026-10-22: B5 (76 tests, `updateUser` now requires `If-Match`, 836 green) and the `page=-1` cases for the other four list operations (4 tests, all already 400, 840 green). The edge-case matrix of item 2 and item 5 are complete apart from the gap above.
Finish: coverage test green, notes in this task, a row in the integration-tests rules file; commit only on request.

## Related

- [improvement-213](improvement-213-rest-api-revision-parity-with-ui.md) — the REST work being verified
- [improvement-211](improvement-211-rest-api-testing-ui-and-mcp-readiness-investigation.md) and [improvement-212](../../tasks/improvement-212-api-flow-runner-page.md) — Postman collection and flow runner, which share the request set
- `playwright/e2e/09-rest-api-swagger.spec.js` — the existing REST Playwright file

## Operational notes
- token_cost_review: 57448
- token_cost_research: 140433
- token_cost_verification: n/a
- review_signal_ratio: 0 / 0
- context_loading_task_type: n/a
- context_loading_consulted: yes
- context_loading_matched: n/a
- flows_situation: review and doc-sync of one already-saved commit (item 7)
- flows_chosen: /review then /sync-docs
- flows_matched: yes
- haiku_decomposition_used: yes
- haiku_decomposition_dispatch_count: 22
- haiku_decomposition_rework_needed: 5 / 22
- adrs_referenced: none

### Agent calls
- code review of commit 85215c28 | subagent_type=deep-review-orchestrator | tokens=57448 | tool_uses=8 | duration_s=92 | mode=background | batch=solo
- docs drift check of 85215c28^..HEAD (Javadoc, READMEs, run.sh header) | subagent_type=general-purpose (model haiku) | tokens=140433 | tool_uses=31 | duration_s=243 | mode=background | batch=solo
- add explicit operationId to 25 REST operations | subagent_type=general-purpose (model haiku) | tokens=75845 | tool_uses=5 | duration_s=44 | mode=background | batch=solo
- ArchUnit rule: explicit operationId on every operation | subagent_type=general-purpose (model haiku) | tokens=81281 | tool_uses=6 | duration_s=39 | mode=background | batch=solo
- ArchUnit rule: unique operationIds | subagent_type=general-purpose (model haiku) | tokens=85035 | tool_uses=6 | duration_s=42 | mode=background | batch=solo
- operation coverage check files | subagent_type=general-purpose (model haiku) | tokens=90719 | tool_uses=13 | duration_s=72 | mode=background | batch=solo
- Level 3 scenarios for the 15 uncovered operations | subagent_type=general-purpose (model haiku) | tokens=183592 | tool_uses=24 | duration_s=400 | mode=background | batch=solo
- record and print statuses per operation | subagent_type=general-purpose (model haiku) | tokens=78107 | tool_uses=10 | duration_s=38 | mode=background | batch=solo
- matrix tests, advertisement and media | subagent_type=general-purpose (model haiku) | tokens=125497 | tool_uses=11 | duration_s=125 | mode=background | batch=solo
- fix video URL and require If-Match on advertisement update/delete | subagent_type=general-purpose (model haiku) | tokens=86632 | tool_uses=7 | duration_s=45 | mode=background | batch=solo
- matrix tests, provider profile and contact | subagent_type=general-purpose (model haiku) | tokens=145607 | tool_uses=14 | duration_s=145 | mode=background | batch=solo
- fix profile matrix tests and require If-Match on profile update/delete | subagent_type=general-purpose (model haiku) | tokens=95608 | tool_uses=8 | duration_s=43 | mode=background | batch=solo
- matrix tests, feedback and moderation | subagent_type=general-purpose (model haiku) | tokens=155752 | tool_uses=13 | duration_s=164 | mode=background | batch=solo
- matrix tests, taxon, API key and audit timeline | subagent_type=general-purpose (model haiku) | tokens=135384 | tool_uses=12 | duration_s=103 | mode=background | batch=solo
- fix taxon, API key and audit validation defects | subagent_type=general-purpose (model haiku) | tokens=97277 | tool_uses=17 | duration_s=53 | mode=background | batch=solo
- matrix tests, users and own account | subagent_type=general-purpose (model haiku) | tokens=156075 | tool_uses=13 | duration_s=167 | mode=background | batch=solo
- require If-Match on user update | subagent_type=general-purpose (model haiku) | tokens=69564 | tool_uses=4 | duration_s=24 | mode=background | batch=solo
- negative page cases for four list operations | subagent_type=general-purpose (model haiku) | tokens=85100 | tool_uses=11 | duration_s=50 | mode=background | batch=solo
- operational notes and rules row for the task | subagent_type=general-purpose (model haiku) | tokens=83638 | tool_uses=5 | duration_s=30 | mode=background | batch=solo
- own-record read on GET /api/users/{id} and two hook unit tests | subagent_type=general-purpose (model haiku) | tokens=109588 | tool_uses=22 | duration_s=99 | mode=background | batch=solo
- Playwright spec for the REST flow through the real stack | subagent_type=general-purpose (model haiku) | tokens=155203 | tool_uses=21 | duration_s=140 | mode=background | batch=solo
- Playwright spec settings value fix | subagent_type=general-purpose (model haiku) | tokens=69839 | tool_uses=4 | duration_s=22 | mode=background | batch=solo
- atomic moderation transitions | subagent_type=general-purpose (model haiku) | tokens=173280 | tool_uses=32 | duration_s=156 | mode=background | batch=solo
- concurrency test helper type fix | subagent_type=general-purpose (model haiku) | tokens=87084 | tool_uses=5 | duration_s=23 | mode=background | batch=solo

### Script/command runs
- docs/architecture/scripts/generate-architecture-model.sh | duration_s=n/a | mode=foreground | result=pass
- scripts/build-and-test.sh --no-unit --integration --skip-vaadin | duration_s=271 | mode=background | result=pass
- scripts/build-and-test.sh --no-unit --integration --skip-vaadin | duration_s=270 | mode=background | result=fail
- scripts/build-and-test.sh --no-unit --integration --skip-vaadin | duration_s=214 | mode=background | result=fail
- scripts/build-and-test.sh --no-unit --integration --skip-vaadin | duration_s=229 | mode=background | result=pass
- scripts/build-and-test.sh --no-unit --integration --skip-vaadin | duration_s=233 | mode=background | result=fail
- scripts/build-and-test.sh --no-unit --integration --skip-vaadin | duration_s=228 | mode=background | result=pass
- scripts/build-and-test.sh --no-unit --integration --skip-vaadin | duration_s=267 | mode=background | result=pass
- scripts/build-and-test.sh --no-unit --integration --skip-vaadin | duration_s=257 | mode=background | result=fail
- scripts/build-and-test.sh --no-unit --integration --skip-vaadin | duration_s=280 | mode=background | result=pass
- scripts/build-and-test.sh --no-unit --integration --skip-vaadin | duration_s=287 | mode=background | result=fail
- scripts/build-and-test.sh --no-unit --integration --skip-vaadin | duration_s=300 | mode=background | result=pass
- scripts/build-and-test.sh --no-unit --integration --skip-vaadin | duration_s=300 | mode=background | result=pass
- scripts/build-and-test.sh --unit --integration --skip-vaadin | duration_s=338 | mode=background | result=pass
- scripts/deploy-and-run.sh --reset-only-db | duration_s=171 | mode=background | result=pass
- scripts/playwright.sh e2e --ux | duration_s=687 | mode=background | result=pass
- scripts/build-and-test.sh --unit --integration --skip-vaadin | duration_s=53 | mode=background | result=fail
- scripts/build-and-test.sh --unit --integration --skip-vaadin | duration_s=348 | mode=background | result=pass

### Review angle yield
- dry-kiss-yagni | survived=0 | total_candidates=0 | tokens=66464
- solid | survived=0 | total_candidates=0 | tokens=67776
- precedent | survived=0 | total_candidates=0 | tokens=n/a
