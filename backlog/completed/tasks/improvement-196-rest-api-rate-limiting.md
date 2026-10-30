# improvement-196: Add per-account rate limiting to marketplace-rest-api write endpoints

**Type:** improvement — security/robustness
**Module:** `marketplace-rest-api`, `integration-tests`, `marketplace-app` (dev profile), `scripts/deploy-and-run`
**Priority:** 🟡 High/medium ROI — real, unaddressed abuse surface; proportionate effort (reuse of an
existing class, one filter, config)
**When:** independent, no blockers; plan researched and approved, execution waits for its own approval

## Current state

Verified directly against current code. `ApiSecurityConfig` (`marketplace-rest-api`) makes `GET` on
`/api/advertisements`, `/api/provider-profiles`, `/api/taxons` public, and `POST /api/users`
(registration) public; every other `/api/**` request needs either HTTP Basic (key issuance only) or a
bearer API key, resolved by `ApiKeyAuthenticationFilter`, which does authentication only.
`ApiKeyController.create()` (`POST /api/api-keys`) lets any caller who can authenticate with email and
password self-issue a bearer key, with no approval step. Once a caller holds a key, every
create/update/delete endpoint accepts unlimited request volume.

Already limited and not part of this task: registration (`UserService.register()` limiter), failed
Basic logins on key issuance (`BasicLoginRateLimitFilter`), contact reveals (`ContactRevealService`,
30 per 15 minutes per address and viewer), feedback writes (`FeedbackService`).

## Why change

An account holder who self-issues an API key faces zero limit on write-endpoint request volume. Each
write costs a database write plus an audit row; media uploads also cost storage. OWASP API Security
Top 10 (API4:2023) lists a missing per-client/per-key limit as a vulnerability, and the host (Render)
leaves application-level (L7) abuse to the owner.

## Expected benefit

Bounded write-request volume per account, using the 429 + `Retry-After` contract the API already has,
with no second Caffeine counter and no new dependency.

## Plan (for people)

**Decision (all seven proposals of the research accepted):** add one small in-app limit.

- A filter in the REST API's bearer-key security chain counts each account's POST/PUT/PATCH/DELETE
  requests and answers `429` with `Retry-After` once the budget (60 per minute in production) is used.
  It reuses `FailureRateLimiter` and gets its numbers from `marketplace.rest-api.write-limit.*`.
- Not counted: public requests without a key, all reads, key issuance (its own chain).
- The `dev` profile and the local Docker stack raise the number, so e2e seeding (Playwright spec 06
  sends 60+ writes with one key under the `prod` profile) and the API flow runner (103 writes) never
  provoke the limiter.
- Swagger shows a 429 on each of the 34 counted write endpoints.

Decisions taken: (1) in-app limit, not a proxy and not nothing; (2) writes only; (3) no new limit on
public reveals (already limited); (4) 60 per minute per account in production, raised in test setups;
(5) no stricter upload budget now, a storage cap would fit better later; (6) key issuance not counted
(a per-user key cap was deferred by an earlier decision); (7) one shared 429 helper for both filters.

Why not a proxy: Render has no route-level limit; Cloudflare Free/Pro rules count only by client IP,
per-key counting is Enterprise-only. Rejected libraries: Bucket4j (new dependency, second mechanism),
Resilience4j (not per key). Rejected: a `HandlerInterceptor` (Spring warns against it as a security
layer; multipart is parsed before `preHandle`).

Cost: about 2.5 h and ~300k tokens (sonnet ~180k, haiku ~75k, main thread ~40k).

Risks and known gaps:
- The counter is in memory: with N instances the real limit is N times the setting. One instance is
  assumed; nothing in the repo sets the instance count (unverified).
- Check-then-count is not atomic and the window is fixed, so bursts and window edges can let up to
  about 2x through. Fine for a volume quota.
- Unlimited successful registration lets someone get around a per-account limit with many accounts.
  Separate gap, not part of this task.
- A signed-in contact reveal with a bearer key also counts toward the write budget.

Where this differs from the project's practice (recorded in the decision at step 7):
- Limits are configuration here, while the six existing limiters use constants (needed so test setups
  can raise the number).
- `marketplace-app` ADR-026 reads as "count only failures"; this is a per-account volume quota, the
  kind `ContactRevealService` and `FeedbackService` already use, so no conflict in substance.
- Only `Retry-After` is sent, not the draft `RateLimit` headers.
- The 429 is documented per endpoint (project style), not by one global springdoc customizer.
- Noticed, not fixed here: the `FailureRateLimiter` Javadoc says "sliding window" but the Caffeine
  setting gives a fixed window.

## Constraints

- Do not touch `AuthService.login()`/`UserService.register()` — their rate limiting is a separate,
  already-correct mechanism.
- Do not weaken `FailureRateLimiter`'s behavior for its current callers.
- Stays out of `platform-commons`'s `*Port`/`*Hook` SPI surface.

## Related

- `platform-commons/src/main/java/org/ost/platform/core/FailureRateLimiter.java`
- `marketplace-rest-api/src/main/java/org/ost/restapi/api/error/ApiExceptionHandler.java`
- `marketplace-app` ADR-026, ADR-027, ADR-087; `docs/api-collections` ADR-002 (see
  `.claude/nav/adr-index.md`)

## Autopilot log

- Unit build green (5 new filter tests, 8 existing `BasicLoginRateLimitFilterTest` unchanged); `/deep-review` returned no findings.
- Full `scripts/ci.sh`: unit, integration, Sonar, ArchUnit, lint, shellcheck and docs all passed; e2e failed in `global.setup.js` before any write: the sign-up button did not render within 10 s while four other stages ran in parallel (app start 111 s). The app log shows no error and no 429. Decision: rerun e2e alone on a reset stack (`--e2e --reset-e2e-stack --no-docs`) instead of changing the spec timeouts.

## Execution plan

Applies the proposals in `plan-human.md` (decisions 1a, 2 writes only, 3 no new limit, 4a 60 per
minute plus test overrides, 5 no upload budget, 6 key issuance not counted, 7 shared helper). If
the person picks another option, adjust the steps it names before executing. Every step follows the
project rules: Lombok first, `lombok.NonNull` on public parameters, one-line comments or none, no
task numbers in code, English only.

### Steps

#### Step 1: settings record, write-limit filter, shared 429 helper, wiring

- **Depends on:** none
- **Executor:** sonnet
- **Files:**
  - new `marketplace-rest-api/src/main/java/org/ost/restapi/config/ApiWriteLimitProperties.java`
  - new `marketplace-rest-api/src/main/java/org/ost/restapi/config/ApiWriteRateLimitFilter.java`
  - new `marketplace-rest-api/src/main/java/org/ost/restapi/config/TooManyRequestsResponse.java`
  - edit `marketplace-rest-api/src/main/java/org/ost/restapi/config/BasicLoginRateLimitFilter.java`
  - edit `marketplace-rest-api/src/main/java/org/ost/restapi/config/ApiSecurityConfig.java`
  - edit `marketplace-rest-api/src/main/java/org/ost/restapi/config/RestApiAutoConfiguration.java`
- **Input:** read first: the six files above (where they exist),
  `platform-commons/src/main/java/org/ost/platform/core/FailureRateLimiter.java`,
  `.../TooManyAttemptsException.java`, `taxon-spring-boot-starter/src/main/java/org/ost/taxon/config/TaxonProperties.java`
  (pattern for the record), `.claude/rules/marketplace-rest-api.md`.
- **Exact change (behavior and constraints):**
  1. `ApiWriteLimitProperties`: a `public record` annotated
     `@ConfigurationProperties(prefix = "marketplace.rest-api.write-limit")`, components
     `@DefaultValue("60") int maxRequests` and `@DefaultValue("1m") Duration window`, one-line
     Javadoc in the shape of `TaxonProperties`'s ("Binds the `marketplace.rest-api.write-limit.*`
     prefix: how many write requests one account may send per window").
  2. `RestApiAutoConfiguration`: add `@EnableConfigurationProperties(ApiWriteLimitProperties.class)`
     (the same way `TaxonAutoConfiguration` enables `TaxonProperties`).
  3. `TooManyRequestsResponse`: a package-private class with Lombok `@UtilityClass`, one static
     method `write(HttpServletResponse response, TooManyAttemptsException ex) throws IOException`.
     It sets status 429, sets `Retry-After` to `ex.retryAfter()` in whole seconds when present, sets
     content type `application/json`, and writes `{"message":"<ex.getMessage()>"}`. That is exactly
     what `BasicLoginRateLimitFilter.reject` produces today, moved here. One-line Javadoc.
  4. `BasicLoginRateLimitFilter`: replace the private `reject(...)` method and its call with
     `TooManyRequestsResponse.write(response, ex)` inside the existing `catch`. Delete `reject`.
     The limiter is built with `Duration.ofMinutes(WINDOW_MINUTES)`, so `ex.retryAfter()` is 900 s
     and the observable response is unchanged (`BasicLoginRateLimitFilterTest` asserts the exact
     header and body and must stay green unmodified). Remove imports that become unused.
  5. `ApiWriteRateLimitFilter extends OncePerRequestFilter`, **not** a Spring bean (no `@Component`;
     Spring Boot would otherwise also register it as a global servlet filter in front of Vaadin).
     It has a constructor taking `@NonNull ApiWriteLimitProperties`, which builds one
     `FailureRateLimiter(properties.maxRequests(), properties.window())`. In `doFilterInternal`:
     - When the request method is not POST, PUT, PATCH or DELETE, or the current
       `SecurityContextHolder` authentication is null, or its principal is not a `Long`, pass the
       request through untouched. That is anonymous/public traffic, and HTTP Basic on key issuance
       never reaches this chain.
     - Otherwise the key is the actor id as a string. Call `limiter.checkAllowed(key, "Too many
       write requests, try again later")`. On `TooManyAttemptsException`, call
       `TooManyRequestsResponse.write(response, ex)` and return without calling the chain.
       Otherwise call `limiter.recordAttempt(key)` and then `filterChain.doFilter(...)`. Count before
       the handler runs, whatever the outcome: a volume quota, the same order as
       `ContactRevealService.reveal`.
     - Use a Java pattern match (`instanceof Long actorId`), no `orElse(null)`-style null juggling.
     - One-line class Javadoc: "Answers 429 once one account has sent `maxRequests`
       POST/PUT/PATCH/DELETE requests within `window`; anonymous requests and reads pass untouched."
  6. `ApiSecurityConfig`: add a `private final ApiWriteLimitProperties writeLimitProperties;` field
     (the class is already `@RequiredArgsConstructor`). In `apiSecurityFilterChain` (the `@Order(2)`
     chain only) add
     `.addFilterAfter(new ApiWriteRateLimitFilter(writeLimitProperties), ApiKeyAuthenticationFilter.class)`
     right after the existing `.addFilterAfter(apiKeyAuthenticationFilter, BasicAuthenticationFilter.class)`.
     If Spring Security rejects `ApiKeyAuthenticationFilter.class` as an anchor ("does not have a
     registered order"), anchor on `BasicAuthenticationFilter.class` instead, added after the
     key filter (insertion order keeps it behind). Step 3's test proves the order either way.
     Extend the class Javadoc by one clause: "writes with a bearer key are limited per account by
     `ApiWriteRateLimitFilter`".
- **Verification:** `bash scripts/build-and-test.sh --unit --no-integration --sandbox` compiles and
  `BasicLoginRateLimitFilterTest` stays green without edits. Run through
  `scripts/activity-monitor.sh` per `.claude/rules.md` "Scripts".
- **Sources:** [OWASP API4:2023](https://api-security.owasp.org/editions/2023/en/0xa4-unrestricted-resource-consumption),
  [RFC 6585 §4](https://www.rfc-editor.org/rfc/rfc6585#section-4),
  [Spring interceptor warning](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/handlermapping-interceptor.html),
  [filter exceptions bypass advice](https://codejava.net/frameworks/spring/handle-exceptions-in-a-filter),
  [Spring Boot external config](https://docs.spring.io/spring-boot/reference/features/external-config.html),
  [Caffeine expiry](https://github.com/ben-manes/caffeine/wiki/Eviction)

#### Step 2: unit test of the filter

- **Depends on:** 1
- **Executor:** sonnet
- **Files:** new `marketplace-rest-api/src/test/java/org/ost/restapi/config/ApiWriteRateLimitFilterTest.java`
- **Input:** `marketplace-rest-api/src/test/java/org/ost/restapi/config/BasicLoginRateLimitFilterTest.java`
  (copy its shape: plain JUnit 5 + AssertJ, `MockHttpServletRequest`/`MockHttpServletResponse`, a
  small `run(...)` helper, a one-line class Javadoc).
- **Exact change:** build the filter with `new ApiWriteLimitProperties(2, Duration.ofMinutes(1))`.
  Set and clear the security context per test (`SecurityContextHolder` with a
  `PreAuthenticatedAuthenticationToken(42L, null, List.of())`; clear it in `@AfterEach`). Cases:
  - the third POST by actor 42 gets 429, `Retry-After` `60`, and body
    `{"message":"Too many write requests, try again later"}`, and the chain is not called (assert
    with a counting lambda `FilterChain`);
  - PUT, PATCH and DELETE share the same counter as POST;
  - GET by the blocked actor passes;
  - another actor (43) passes while 42 is blocked;
  - a POST with no authentication passes untouched however many times it is sent (e.g. 10).
- **Verification:** `bash scripts/build-and-test.sh --unit-test ApiWriteRateLimitFilterTest --no-integration --sandbox` is green.
- **Sources:** [RFC 6585 §4](https://www.rfc-editor.org/rfc/rfc6585#section-4)

#### Step 3: Level 3 scenario over the real security chain

- **Depends on:** 1
- **Executor:** sonnet
- **Files:** new `integration-tests/src/test/java/org/ost/integrationtests/level3/restapi/ApiWriteRateLimitScenarioTest.java`
- **Input:** `integration-tests/src/test/java/org/ost/integrationtests/level3/restapi/ApiAuthenticationChainScenarioTest.java`
  (shape: `@Level3ScenarioTest`, `extends AbstractRestApiScenarioTest`, `registerUserAndIssueApiKey`),
  `.claude/rules/integration-tests.md` (Level 3, operation coverage).
- **Exact change:** do **not** add `@TestPropertySource` or any per-class configuration.
  `@Level3ScenarioTest` must stay identical so the shared context is reused, which means the
  production default of 60 per minute applies. One test:
  `sixtyFirstWriteInAMinute_isTooManyRequests_whileReadsAndOtherAccountsPass`.
  1. Register a fresh user with a key.
  2. Send 60 `DELETE /api/advertisements/{unknown id}` with the bearer key and an `If-Match`
     header, and assert each status is not 429.
  3. The 61st gets 429 with `Retry-After` `60` and a body containing `Too many write requests`.
  4. `GET /api/api-keys` with the same key is 200.
  5. A second fresh user's write is not 429.
  Fresh users per test keep every other Level 3 class far below 60 writes per actor (the largest
  per-actor loop today is 30, in the contact and feedback tests).
- **Verification:** `bash scripts/build-and-test.sh --no-unit --integration-test ApiWriteRateLimitScenarioTest --sandbox`
  is green, then the full `--no-unit --integration` run is green, including `OperationCoverageTest`.
- **Sources:** [OWASP API4:2023](https://api-security.owasp.org/editions/2023/en/0xa4-unrestricted-resource-consumption)

#### Step 4: document the 429 on every counted write endpoint

- **Depends on:** 1
- **Executor:** haiku
- **Files (all under `marketplace-rest-api/src/main/java/org/ost/restapi/api/`):**
  `AdvertisementApiController.java`, `AdvertisementMediaApiController.java`, `ApiKeyController.java`,
  `FeedbackApiController.java`, `ModerationApiController.java`, `ProviderProfileApiController.java`,
  `TaxonApiController.java`, `UserApiController.java`
- **Input:** the files as they are.
- **Exact change:** for each method listed below, insert this exact line directly above its
  `@PostMapping`/`@PutMapping`/`@PatchMapping`/`@DeleteMapping` line, with the same indentation:
  `    @ApiResponse(responseCode = "429", description = "Write limit reached")`.
  Identify each method by the `operationId` in its `@Operation`:
  - `AdvertisementApiController`: createAdvertisement, updateAdvertisement, patchAdvertisement,
    restoreAdvertisementFromSnapshot, deleteAdvertisement
  - `AdvertisementMediaApiController`: uploadAdvertisementMedia, addAdvertisementVideo, deleteAdvertisementMedia
  - `ApiKeyController`: revokeApiKey (NOT issueApiKey)
  - `FeedbackApiController`: updateFeedback, updateFeedbackReply, deleteFeedbackReply, flagFeedback,
    flagFeedbackReply (NOT createProviderProfileFeedback or createFeedbackReply, which already carry a 429)
  - `ModerationApiController`: approveFeedback, confirmFeedbackViolation, approveFeedbackReply,
    confirmFeedbackReplyViolation
  - `ProviderProfileApiController`: createProviderProfile, updateProviderProfile, patchProviderProfile,
    restoreProviderProfileFromSnapshot, deleteProviderProfile
  - `TaxonApiController`: createTaxon, updateTaxon, deleteTaxon, restoreTaxon, restoreTaxonFromSnapshot
  - `UserApiController`: updateUser, restoreUserFromSnapshot, deleteUser, changeMyLocale,
    updateMySettings, restoreMySettingsFromSnapshot (NOT registerUser)

  That is 34 lines. `AdvertisementMediaApiController.java` and `ModerationApiController.java` lack
  the import: add `import io.swagger.v3.oas.annotations.responses.ApiResponse;` in alphabetical
  position among the existing `io.swagger` imports. Do not touch `ContactApiController.java`.
- **Verification:** `git diff --stat` shows only these 8 files.
  `git grep -c 'description = "Write limit reached"' -- marketplace-rest-api` totals 34. The unit
  build compiles.
- **Sources:** [GitHub REST rate limits](https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api)

#### Step 5: raise the budget in the developer and local-test setups

- **Depends on:** 1
- **Executor:** haiku
- **Files:** `marketplace-app/src/main/resources/application-dev.yml`, `scripts/deploy-and-run/run.sh`
- **Input:** the two files.
- **Exact change:**
  - `application-dev.yml`. Old text:
    ```
    # Developer-only pages (API flow runner) served by marketplace-rest-api
    marketplace:
      dev-tools:
        enabled: true
    ```
    New text:
    ```
    # Developer-only pages (API flow runner) served by marketplace-rest-api
    marketplace:
      dev-tools:
        enabled: true
      # Seed scripts and API flows send hundreds of writes a minute with one account.
      rest-api:
        write-limit:
          max-requests: 100000
    ```
  - `scripts/deploy-and-run/run.sh`, in `start_app_container()`. Old text:
    `    -e APP_PUBLIC_URL="http://localhost:$APP_PORT"`
    New text (two lines):
    ```
        -e APP_PUBLIC_URL="http://localhost:$APP_PORT"
        -e MARKETPLACE_RESTAPI_WRITELIMIT_MAXREQUESTS=100000
    ```
    In the header's `# Outputs:` paragraph, after the words `running marketplace-app container on APP_PORT`, insert
    ` (its REST write limit raised to 100000 per minute, since e2e seeding and API flows exceed the production default)`.
- **Verification:** `bash -n scripts/deploy-and-run/run.sh` passes. `shellcheck --severity=error`
  is clean if available.
- **Sources:** [Spring Boot external config](https://docs.spring.io/spring-boot/reference/features/external-config.html)

#### Step 6: module rules doc

- **Depends on:** 1
- **Executor:** haiku
- **Files:** `.claude/rules/marketplace-rest-api.md`
- **Exact change:** in the `org.ost.restapi.config` bullet, old text:
  `  address and email with 429 after 5 failed Basic logins in 15 minutes, on the key-issuance chain),`
  new text (two lines):
  ```
    address and email with 429 after 5 failed Basic logins in 15 minutes, on the key-issuance chain),
    `ApiWriteRateLimitFilter` (on the bearer chain, answers 429 once one account sends more POST/PUT/PATCH/DELETE requests than `ApiWriteLimitProperties` allows, `marketplace.rest-api.write-limit.*`, 60 per minute by default), `TooManyRequestsResponse` (writes the 429 for both filters, since a filter's exceptions never reach `ApiExceptionHandler`),
  ```
- **Verification:** `git diff .claude/rules/marketplace-rest-api.md` shows the one added line.
  `paths:` is untouched, so the glob checker is not needed.

#### Step 7: record the decision

- **Depends on:** 1-6
- **Executor:** main thread through `/record-decision marketplace-app — REST API limits write requests per account`
  (the REST API's ADRs live in `marketplace-app/DECISIONS.md` with `**Also affects:** marketplace-rest-api`,
  like ADR-087). Do not hand-write it.
- **Content to carry:**
  - Context: self-issued keys with no write limit; Render leaves L7 limiting to the app.
  - Decision: per-actor fixed-window write quota in a bearer-chain filter on `FailureRateLimiter`,
    429 + `Retry-After`, configurable, raised in dev and the local Docker stack.
  - Scope versus ADR-026: a volume quota per account, not an auth-failure limiter.
  - Rejected: a proxy or Cloudflare rule (IP-only on Free/Pro, no per-key counting), Bucket4j (new
    dependency, second mechanism), Resilience4j (not per key), a `HandlerInterceptor` (Spring warns
    against interceptors as a security layer; multipart is parsed before `preHandle`).
  - Consequences: per instance; registration volume and a per-user key/storage cap remain open.
  - Then regenerate the ADR index as that command does.
- **Verification:** `.claude/nav/adr-index.md` shows the new row.

### Verification of the whole

1. `bash scripts/build-and-test.sh --unit --integration --sandbox` is green: unit counts include the
   new `ApiWriteRateLimitFilterTest`, integration includes `ApiWriteRateLimitScenarioTest` and
   `OperationCoverageTest`.
2. `bash scripts/deploy-and-run.sh --dev-tools`, then the full Playwright `e2e --full --ux`. Spec
   `06-seed-filter-sort-pagination` must pass, which proves the local-stack override works.
3. The headless API flows (`docs/api-collections/api-collection-tests.sh`, per its header) all pass
   with no 429 (docs/api-collections ADR-002).
4. Manual check of the production default: start the app without the override and send 61 writes
   with one key. Expect 429 and `Retry-After: 60` on the 61st, and Swagger UI shows the 429 on a
   write endpoint.
5. Run `/check-architecture-map` (new classes in `org.ost.restapi.config`), then the task lifecycle
   per `.claude/rules.md` "Task Lifecycle".

## Operational notes
- token_cost_review: 59384
- token_cost_research: 180018
- token_cost_verification: n/a
- review_signal_ratio: 0 / 0
- context_loading_task_type: Resuming a research or a planning run
- context_loading_consulted: yes
- context_loading_matched: yes
- flows_situation: deep researched plan for a task before approving it
- flows_chosen: /deep-research
- flows_matched: yes
- haiku_decomposition_used: yes
- haiku_decomposition_dispatch_count: 2
- haiku_decomposition_rework_needed: 0 / 2
- adrs_referenced: ADR-026 (marketplace-app), ADR-087 (marketplace-app), ADR-002 (docs/api-collections), ADR-091 (marketplace-app)

### Agent calls
- deep research of the write-limit design | subagent_type=deep-researcher | tokens=180018 | tool_uses=67 | duration_s=746 | mode=background | batch=solo
- 429 annotations on 34 endpoints | subagent_type=general-purpose (model haiku) | tokens=78771 | tool_uses=5 | duration_s=45 | mode=background | batch=parallel-1
- dev/local-stack overrides and rules line | subagent_type=general-purpose (model haiku) | tokens=103714 | tool_uses=11 | duration_s=63 | mode=background | batch=parallel-1
- code review of the diff | subagent_type=deep-review-orchestrator | tokens=59384 | tool_uses=9 | duration_s=76 | mode=background | batch=solo

### Script/command runs
- scripts/build-and-test.sh --unit --no-integration | duration_s=57 | mode=background | result=fail (test import typo)
- scripts/build-and-test.sh --unit --no-integration | duration_s=304 | mode=background | result=pass
- scripts/ci.sh --foreground | duration_s=1423 | mode=background | result=fail (e2e setup timed out while four stages ran in parallel; every other stage passed)
- scripts/ci.sh --foreground --e2e --reset-e2e-stack --no-docs | duration_s=1685 | mode=background | result=pass (65 e2e tests)

### Review angle yield
- dry-kiss-yagni-reviewer | survived=0 | total_candidates=0 | tokens=n/a
- solid-reviewer | survived=0 | total_candidates=0 | tokens=48025
- precedent-reviewer | survived=0 | total_candidates=0 | tokens=n/a
