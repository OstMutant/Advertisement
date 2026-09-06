# improvement-073: Add REST API infrastructure — dev-gated Playwright seeding endpoints + a real external/public API (not the first REST controller — see correction)

**Type:** improvement — new infrastructure capability, prerequisite for improvement-035 and any
future REST-dependent work. Filed 2026-07-16 after deciding a browser-driven Playwright spec
(06-seed-filter-sort-pagination) needs a faster, audit-trail-correct seeding path than either raw
SQL (breaks the spec's own timeline assertions — see improvement-035's correction) or full UI
automation (slow — the actual thing being optimized away). Scope widened (2026-09-01, explicit
user request) to also cover a genuinely public-facing, prod-reachable REST API for external
consumers — a different audience and security posture than the dev-gated seeding endpoints, but
sharing the same base routing/controller infrastructure, so tracked in one issue rather than two.
**Module:** `marketplace-rest-api` (REST controllers, `ApiSecurityConfig`, `OpenApiConfig`),
`apikey-spring-boot-starter` (`api_key` table/entity/repository/service, its own Maven starter
module), `platform-commons` (`ApiKeyPort`/`ApiKeySummaryDto`, own `apikey.spi`/`apikey.dto`
packages).
**Priority:** 🟡 high — no longer a "nice to have": real external API is a stated product goal.
**When:** Both the test-seeding portion and the external/public API portion are done —
`improvement-111` (authorization at the service boundary), the hard gate the external-facing
portion needed, landed and is already relied on throughout this issue's Phase 3+ work (every
orchestrator save/delete service's `AuthorizationService.require*` calls).

## External API scope (added 2026-09-01)

Distinct from the test-seeding endpoints below in every way that matters for security:

- **Audience:** real external consumers (not Playwright test code), reachable in `prod`.
- **Security posture:** the opposite of the seeding endpoints' `@Profile("!prod")` gate — this must
  work correctly *in* `prod`, which is exactly why `improvement-111`'s service-boundary
  authorization gap cannot stay open once this ships. `HealthController`'s `permitAll()` precedent
  does not apply here; this needs real per-request authentication/authorization, not a public rule.
- **Known future consumer, from the private roadmap:** `private/features/F-07-phone-verification.md`
  already plans a Telegram Bot API webhook (`@RestController`) and external SMS-provider REST calls
  — this issue's infrastructure (routing conventions, `SecurityConfig` patterns, controller
  package structure) is what that future feature will build on, so shape it generically rather than
  narrowly for one caller.
- **Exact endpoint shape/business scope:** ~~not yet decided~~ **resolved 2026-09-03** — see
  "Implementation Plan (approved 2026-09-03)" below. `improvement-111` shipped 2026-09-03, so this
  gate is now clear.

## Correction (2026-07-16): this app already has one REST controller — `HealthController`

The original wording claimed "no REST controllers at all today." **Confirmed wrong**:
`org.ost.marketplace.rest.HealthController` (`GET /health`) already exists, already documented in
`marketplace-app/CLAUDE.md`'s Security section as "intentionally public (load balancer probe)."
It even already follows the exact precedent this issue needs — its own explicit
`requestMatchers("/health").permitAll()` rule in `SecurityConfig`, ahead of the
`anyRequest().permitAll()` catch-all (confirmed directly in `SecurityConfig.java`). What this app
does **not** have is any REST endpoint that (a) is gated to non-prod environments, or (b) invokes
real business/service-layer logic rather than returning a static string — `HealthController` does
neither. The `marketplace-app/CLAUDE.md` correction landed alongside this one (see that file's
Security section).

## Problem

Playwright specs are Node.js and cannot call Java service methods directly; the only way to seed
test data through the *real* application service layer (`UserService.register()`,
`AdvertisementSaveService.save()`, so `audit_log` rows and all other side effects are produced
correctly, unlike raw SQL) from a Playwright spec is via an HTTP call into the running app — which
requires an endpoint that does more than `HealthController`'s static response, and one that must
never be reachable in a production deployment (unlike `/health`, which is meant to stay public
everywhere).

## Suggested fix

- Add a dedicated REST controller package (e.g. `org.ost.marketplace.web` or `.api`, sibling to the
  existing `rest/` package — naming TBD, may just extend `rest/`), active **only** outside the
  `prod` profile (`@Profile("!prod")` or equivalent) — this must never be reachable in a production
  deployment, unlike `/health`.
- Explicit `SecurityConfig` handling for this new path prefix, following the exact precedent
  `HealthController` already set (`requestMatchers("/test-seed/**").permitAll()` or equivalent,
  ahead of the catch-all) rather than relying on the pre-existing `anyRequest().permitAll()`
  catch-all to silently cover it.
- First endpoint(s): whatever improvement-035 needs to seed users/advertisements through the real
  service layer (e.g. `POST /test-seed/users`, `POST /test-seed/advertisements`) — exact shape is
  improvement-035's concern, this issue only needs to land the general capability (routing,
  profile-gating, security rule) that any REST endpoint in this app — test-seeding or otherwise —
  would need going forward.

## Implementation Plan (approved 2026-09-03)

Scope widened again during planning (explicit user request): a real, external-facing REST API
with API-key authentication and full CRUD for Users/Advertisements/ProviderProfiles/Taxons —
testable via Postman — not just the narrow dev-gated seeding endpoints originally scoped. One API
now serves both audiences (external consumers AND `improvement-035`'s Playwright seeding), so the
`@Profile("!prod")`-gated seeding-only controller from the original "Suggested fix" above is
**superseded** — the real API works in `prod` and Playwright calls the same endpoints.

Planned via Claude Code's Plan Mode (2 rounds of research agents + 1 design agent, user
clarification on GET-auth and key-management scope), approved in full. Full plan text (context,
every decision + reasoning, file-by-file list, verification steps) is preserved verbatim in
`/root/.claude/plans/rustling-honking-puppy.md` on the machine that planned it — summarized here so
it survives independently of that local file:

### Decisions
- **Auth:** per-user API key (bearer token). Issued via `POST /api/api-keys` gated by HTTP Basic
  (reuses the existing `AuthenticationManager` bean, no new password infra). Every other
  authenticated `/api/**` call uses `Authorization: Bearer <key>`.
- **Key hashing:** SHA-256 fixed hash of a server-generated 256-bit random key — deliberately NOT
  `PasswordEncoder`/bcrypt (bcrypt's slowness defends a low-entropy human password; it only adds
  ~100ms+ per request for an already-random token, with zero security benefit).
- **Reads public, writes need a key:** `GET` under `/api/advertisements`, `/api/provider-profiles`,
  `/api/taxons` stay unauthenticated (matches today's public Vaadin browsing, e.g. the public
  Providers catalog). `POST`/`PUT`/`DELETE` require a valid bearer key. `POST /api/users`
  (registration) is public too, matching today's self-service signup.
- **Key management ships in v1:** `GET /api/api-keys` (list caller's own keys — id/prefix/label/
  timestamps, never the raw key or hash) and `DELETE /api/api-keys/{id}` (revoke, owner-scoped)
  ship alongside `POST /api/api-keys`.
- **Two `SecurityFilterChain` beans coexist**, per `marketplace-app/DECISIONS.md` ADR-025's already-
  established discipline: existing `SecurityConfig` bean stays completely untouched; a new
  `@Order(1)` chain matched to `securityMatcher("/api/**")` (stateless, Basic Auth for key-issuance,
  a new `ApiKeyAuthenticationFilter` for bearer requests) handles everything under `/api/**`.
- **Principal on the bearer path is a plain `Long userId`** (`@AuthenticationPrincipal Long
  actorId` in controllers) — every orchestrator method this API calls only ever needs the id, no
  full `UserPrincipal`/DTO needed.
- **Provider-profile v1 is self-service only** (`targetUserId == actorId` always) — no
  "admin-edits-someone-else's-profile-via-API" path yet; explicit known limitation.
- **DTO reuse:** REST bodies reuse `platform-commons` DTOs directly where the shape is already
  JSON-friendly (`SignUpDto`, `AdvertisementSaveDto`, `ProviderProfileSaveDto`); thin REST-only
  records only where a genuine mismatch exists (Taxon's `Map<Locale,...>` can't bind from JSON) or
  for response shaping.
- **No changes needed to `AuthorizationService`/`AccessDeniedException`** (from `improvement-111`)
  — they already activate correctly once a real `actorId` flows through from any caller, REST
  included; this issue only needs to resolve that `actorId` per request.

### New pieces
- **`user_api_key` table** (`user-spring-boot-starter`) — new `changeSet id="01-user-api-key-schema"`
  added inside the existing `db/user-changelog/changes/01-user-schema.xml` (that file already holds
  two changeSets, `01-user-schema` and `01-user-preferences-schema` — a third follows the same
  precedent; no new `02-...` file, since this schema has never shipped to a real environment yet, so
  there's no prior deployed state a same-file addition could conflict with). Columns: `id`, `user_id`
  (FK to `user_information.id`, `ON DELETE CASCADE` — a key's lifecycle is genuinely owned by its
  user, unlike the no-FK actor-reference convention used for audit columns), `key_hash` (unique),
  `key_prefix`, `label`, `created_at`, `last_used_at`, `revoked_at`.
- **`ApiKeyPort`** — new 5th `user.spi` platform-commons port (`create`/`resolveUserId`/
  `listForUser`/`revoke`) + `ApiKeySummaryDto`. Implementation chain:
  `ApiKeyPortImpl` (pure delegation) → `ApiKeyService` (`user-spring-boot-starter`, single-domain
  credential logic, same shape as `UserService.register()` — not orchestrator-level) →
  `ApiKeyRepository`/`ApiKeyCrudRepository` → new `ApiKey` entity. New `ApiKeyHasher` utility
  (`org.ost.user.security`) for `generate()`/`hash()`.
- **`ApiSecurityConfig`** + **`ApiKeyAuthenticationFilter`** (`marketplace-app/config/`) — the new
  `/api/**` security chain described above.
- **5 new REST controllers** (`org.ost.marketplace.rest.api`, sub-package under the existing
  `rest/`): `ApiKeyController`, `UserRegistrationController`, `AdvertisementApiController`,
  `ProviderProfileApiController`, `TaxonApiController` — each a thin wrapper over the matching
  `marketplace-orchestrator` service(s) already used by the Vaadin UI today.
- **`ApiExceptionHandler`** (`@RestControllerAdvice(basePackages = "org.ost.marketplace.rest.api")`)
  — maps `AccessDeniedException`→403, `OptimisticLockingFailureException`→409,
  `MethodArgumentNotValidException`→400, `NoSuchElementException`→404.

### Playwright reuse (improvement-035)
Only spec 06's seeding tests (Test 1: 60 users, Test 2: 60 ads + categories/cities) move to calling
the new API directly via Playwright's `request` fixture — Tests 3-6 (filter/sort/pagination/
settings/timeline UI verification) stay full browser automation, since that UI behavior is exactly
what they test. `UserService.register()`'s rate limiter confirmed safe (increments only on
`DuplicateKeyException`, never on success — 60 distinct successful registrations from one IP won't
trip it, same reasoning as ADR-026). Existing UI-based Playwright helpers (`signUpBulkParallel`,
`createAdvertisementBulk`, category/city flows) stay unchanged — still used by specs 03/05, which
are legitimately testing those UI flows themselves.

### File-by-file
**New (17):** Liquibase changeSet; `ApiKey` entity; `ApiKeyCrudRepository`; `ApiKeyRepository`;
`ApiKeyHasher`; `ApiKeyService`; `ApiKeyPort` + `ApiKeySummaryDto` (platform-commons);
`ApiKeyPortImpl`; `ApiSecurityConfig`; `ApiKeyAuthenticationFilter`; `ApiKeyController`;
`UserRegistrationController`; `AdvertisementApiController`; `ProviderProfileApiController`;
`TaxonApiController`; `ApiExceptionHandler`; REST-local request/response records.
**Modified (4):** `user-changelog-master.xml`; `UserAutoConfiguration` (new `ComponentFactory<ApiKeyPort>`
bean); `playwright/e2e/_flows/seed.flow.js`; `playwright/e2e/06-seed-filter-sort-pagination.spec.js`.
**Unchanged (confirmed):** `SecurityConfig.java`; `AuthorizationService`/`AccessDeniedException`;
other Playwright flow helpers.

### Test coverage split
- **Integration tests** (`integration-tests` module, real Postgres via Testcontainers):
  `ApiKeyRepository` (SQL correctness — `findActiveByKeyHash`, `touchLastUsed`, `findByUserId`,
  `revoke`) and `ApiKeyService` (delegation + real-DB round trip). This alone is not sufficient —
  it never exercises `ApiKeyHasher` or `ApiExceptionHandler`, neither of which touches the database.
- **Unit tests** (no DB): `ApiKeyHasher` (`generate()` uniqueness/entropy, `hash()` determinism),
  `ApiExceptionHandler` (each exception → correct HTTP status mapping).
- No dedicated Playwright test for the API-key mechanism itself — spec 06 only *consumes* the API
  for seeding, it doesn't need to re-verify the auth mechanism as a UI-level test.

### Verification
`bash scripts/build-and-test.sh --unit --integration`; manual Postman pass (register → issue key →
CRUD with the key → cross-owner write attempt expects 403 → stale-version update expects 409 →
revoke → next call rejected); `bash scripts/playwright.sh e2e --full --ux` (spec 06 must still
pass, full-suite runtime should measurably drop); `bash scripts/ci.sh` before considering this done.

### Architecture revision (2026-09-03, mid-Phase-2): two new modules

Discovered mid-implementation (Phase 1 built, Phase 2 built then reworked) that folding API-key
persistence into `user-spring-boot-starter` and REST controllers into `marketplace-app` breaks two
already-enforced invariants: `marketplace-orchestrator`'s `orchestrator_has_no_persistence_access`
ArchUnit rule (confirms persistence stays inside starters only) and `marketplace-app`'s
`marketplace_app_must_not_depend_on_platform_commons_spi_directly` rule (confirms `*Port`
access routes through the orchestrator, never directly). API keys are used exclusively by the REST
layer (confirmed: no Vaadin flow touches them) — this triggered reconsidering the "New pieces"
section above; the file *locations* below supersede it, decisions/behavior stay the same.
Standards check (web search) confirmed a durable table is still required — a cache alone would
lose all issued keys on restart and can't support the planned `GET`/`DELETE /api/api-keys`;
SHA-256 (not bcrypt) for hashing was independently reconfirmed as correct for high-entropy keys.

**New module 1: `apikey-spring-boot-starter`** — thinner than every existing starter (one table,
CRUD + hash), but a real starter is still the only shape that adds zero exceptions to the
persistence-stays-in-starters invariant. Package root `org.ost.apikey`. Owns: `user_api_key` table
(own changelog, `db/apikey-changelog/`, not folded into `user-changelog`), `ApiKey` entity,
`ApiKeyCrudRepository`/`ApiKeyRepository`, `ApiKeyHasher` (`org.ost.apikey.security`),
`ApiKeyService` (`org.ost.apikey.services`), `ApiKeyPortImpl` (`org.ost.apikey.spi`),
`ApiKeyAutoConfiguration` (`@ComponentScan` + the `ComponentFactory<ApiKeyPort>` bean, moved out
of `UserAutoConfiguration`). `ApiKeyPort`/`ApiKeySummaryDto` (platform-commons) and
`ApiKeyManagementService` (marketplace-orchestrator) are unaffected — they already only depended
on the `platform-commons` contract, never on `user-spring-boot-starter` directly.

**New module 2: `marketplace-rest-api`** — not a starter (no persistence, no Vaadin — same shape as
`marketplace-orchestrator`), sibling to `marketplace-app`, added as a mandatory compile-scope
dependency of it (same pattern as the existing `marketplace-orchestrator` dependency) —
`marketplace-app` stays the sole `@SpringBootApplication` entry point, one deployable jar, no
runtime/deployment split. Package root `org.ost.restapi`. Owns everything currently mixed into
`marketplace-app`'s `rest/`/`config/` packages for REST concerns, moved out for a clean Vaadin/REST
separation, done in this same pass rather than left half-migrated: `HealthController`,
`SitemapController` (top-level `org.ost.restapi`), `ApiKeyController` + `ApiKeyCreateRequest`/
`ApiKeyCreatedResponse` (`org.ost.restapi.api`, mirrors the sub-package split the original plan
already called for), `ApiSecurityConfig` + `ApiKeyAuthenticationFilter` (`org.ost.restapi.config`),
`RestApiAutoConfiguration` (`@ComponentScan("org.ost.restapi")`, mirrors
`OrchestratorAutoConfiguration`'s shape). `ArchitectureRulesTest` (stays in `marketplace-app`, can
still scan `org.ost.restapi..` classes via the classpath once the dependency exists) gets its
`marketplace_app_must_not_depend_on_platform_commons_spi_directly`-equivalent coverage extended to
this module in the same pass, so the new module is governed by the same constraint from day one
rather than silently ungoverned.

**Root `pom.xml`:** two new `<module>` entries (`apikey-spring-boot-starter` after the existing
starters, `marketplace-rest-api` after `marketplace-orchestrator`, before `marketplace-app`) +
`dependencyManagement` entries. `marketplace-orchestrator/pom.xml` gains a compile-scope dependency
on `apikey-spring-boot-starter` (7th starter dependency). `marketplace-app/pom.xml` gains a
compile-scope dependency on `marketplace-rest-api`. `integration-tests/pom.xml` gains a dependency on
`apikey-spring-boot-starter`; its `ApiKeyRepositoryTest`/`ApiKeyServiceTest`/`ApiKeyHasherTest`
move from `org.ost.integrationtests.user` to a new `org.ost.integrationtests.apikey` package,
`@SpringBootTest(classes=...)` gains `ApiKeyAutoConfiguration` alongside `UserAutoConfiguration`.
`ApiKeyAuthenticationFilterTest`/`ApiKeyControllerTest` move from `marketplace-app/src/test` into
the new `marketplace-rest-api` module's own `src/test/java/org/ost/restapi/...` — fixing, in the same move, the
Mockito field-initialization-order bug found in the failed Phase 2 rebuild (construct the class
under test in `@BeforeEach`, not an inline field initializer, matching
`AdvertisementSaveServiceTest`'s established pattern).

**Status: done 2026-09-03.** Both new modules built, all Phase 1/Phase 2 files relocated into
their final shape, `ArchitectureRulesTest` coverage extended, `scripts/build-and-test/build.sh`'s
hardcoded unit-test module list fixed to include `marketplace-rest-api` (the gap that let its two
test classes compile without ever running is now closed). Full verification:
`bash scripts/build-and-test.sh --unit --integration` — unit tests including
`ApiKeyManagementServiceTest` (5/5), `ApiKeyAuthenticationFilterTest` (5/5),
`ApiKeyControllerTest` (4/4), and the broadened `ArchitectureRulesTest` (19/19) all passed;
integration tests 181/181 passed, including the relocated `org.ost.integrationtests.apikey.*`
suite (18 tests).

### Phased execution (approved 2026-09-03)

Implemented in four gated phases, each verified before the next starts — mirrors the stepwise
discipline used for `improvement-111`:

1. **Data + port layer** — ✅ **done 2026-09-03**, relocated ✅ **2026-09-03** into the new
   `apikey-spring-boot-starter` module per the Architecture revision above (`user_api_key`
   changeSet now in `db/apikey-changelog/`; `ApiKey` entity, `ApiKeyCrudRepository`/
   `ApiKeyRepository`, `ApiKeyHasher`, `ApiKeyService`, `ApiKeyPortImpl` all in `org.ost.apikey.*`;
   `ApiKeyPort`/`ApiKeySummaryDto` stay in platform-commons; the `ComponentFactory<ApiKeyPort>`
   bean moved from `UserAutoConfiguration` to the new `ApiKeyAutoConfiguration`). Tests:
   `ApiKeyHasherTest` (5, pure), `ApiKeyRepositoryTest` (8, Testcontainers), `ApiKeyServiceTest` (5,
   Testcontainers) — moved to `integration-tests/src/test/java/org/ost/integrationtests/apikey/`.
2. **Auth wiring** — ✅ **done 2026-09-03**, relocated ✅ **2026-09-03** into the new
   `marketplace-rest-api` module (`org.ost.restapi.config.ApiSecurityConfig`/
   `ApiKeyAuthenticationFilter`, `org.ost.restapi.api.ApiKeyController` —
   `POST`/`GET`/`DELETE /api/api-keys`; a new `ApiKeyManagementService` in `marketplace-orchestrator`
   sits between them and `ApiKeyPort`, since `marketplace-rest-api` may not hold a `*Port` directly,
   same rule `marketplace-app` follows). A key can be issued via Basic Auth and used to authenticate
   a bearer request. Tests: `ApiKeyAuthenticationFilterTest` (5), `ApiKeyControllerTest` (4), both
   in `marketplace-rest-api/src/test/java/org/ost/restapi/...`; `ApiKeyManagementServiceTest` (5)
   in `marketplace-orchestrator`.
3. **Resource controllers** — ✅ **done 2026-09-03**. `UserRegistrationController`,
   `AdvertisementApiController`, `ProviderProfileApiController`, `TaxonApiController`,
   `ApiExceptionHandler` all in `marketplace-rest-api`'s `org.ost.restapi.api` package (17
   files total: 4 controllers, `ApiExceptionHandler`, 6 request/response records
   (`UserCreatedResponse`, `TaxonTranslationRequest`/`TaxonCreateRequest`/`TaxonUpdateRequest`,
   `ErrorResponse`/`ValidationErrorResponse`), 6 test classes). `marketplace-rest-api/pom.xml`
   gained `spring-boot-starter-validation` (needed for `@Valid` on the reused
   `SignUpDto`/`AdvertisementSaveDto`/`ProviderProfileSaveDto` bodies to actually enforce their
   Jakarta Bean Validation annotations). Tests: 27/27 passed in `marketplace-rest-api`
   (`AdvertisementApiControllerTest` 6, `ProviderProfileApiControllerTest` 3,
   `TaxonApiControllerTest` 4, `UserRegistrationControllerTest` 1, `ApiExceptionHandlerTest` 4,
   plus the Phase 2 API-key tests); `ArchitectureRulesTest` still 19/19 (confirms the new
   controllers comply with the module-boundary rules extended in the Architecture revision).
4. **Playwright migration + full verification** — in progress. See "Phase 4 detailed plan" below.

**Status: Phases 1-3 done 2026-09-03** (including the module restructuring) — Phase 4 in progress.

### Phase 4 detailed plan (2026-09-03) — Playwright REST-based seeding

Scope: only spec 06's Test 1 (60 users) and Test 2 (60 ads + 5 categories + 3 cities) move to
REST-based seeding. Explicit user direction: design the new helpers generically enough that other
bulk-seeding scenarios (a known near-future one: provider-profile seeding for a future pagination
test; a second real candidate found while surveying existing specs: spec 03's "adminEn seeds 10
boundary categories" test, pure data-seeding for spec 05's max-category-selection scenario, not
verifying the category-creation UI flow itself) can reuse the same building blocks later —
without implementing either of those two extensions in this pass.

**New helpers in `playwright/e2e/_flows/seed.flow.js`** (shared file — its own charter is already
"bulk data-seeding flow helpers", and a second real future consumer is already known, not
speculative, so this doesn't violate the "extract only when 2+ callers need it" org rule in
`.claude/rules/playwright.md`):
- `issueApiKeyViaApi(request, { email, password })` — `POST /api/api-keys` via HTTP Basic, returns
  the raw key. Domain-agnostic, reusable by any future API-seeding caller.
- `postViaApi(request, apiKey, path, data)` — generic authenticated-POST building block (sets
  `Authorization: Bearer` when `apiKey` is given, throws with status+body on a non-OK response).
  Every domain-specific seeder below (and any future one) builds on this instead of repeating the
  header/error-check boilerplate.
- `seedUsersViaApi(request, users)` — parallel `POST /api/users` (public, no key).
- `seedTaxonsViaApi(request, apiKey, type, taxons)` — sequential `POST /api/taxons` (privileged),
  returns taxon ids in input order.
- `seedAdvertisementsViaApi(request, apiKey, ads)` — parallel `POST /api/advertisements`.

**`06-seed-filter-sort-pagination.spec.js` changes:**
- Test 1 (`seed ${SEED_COUNT} users`): `async ({ browser }) => ...signUpBulkParallel...` →
  `async ({ request }) => ...seedUsersViaApi...`. No browser/page needed at all.
- Test 2 (`adminEn seeds ${SEED_COUNT} advertisements`): `async ({ request }) => {...}` — no
  longer touches the shared `page`. `issueApiKeyViaApi(request, TEST_USERS.adminEn)` once, then
  `seedTaxonsViaApi` for `SEED_CATEGORIES`/`SEED_CITIES` (types `CATEGORY`/`CITY`), then build 60
  ad payloads cycling the returned taxon ids (`adKind` mapped to `OFFER`/`REQUEST`/`PRODUCT`) and
  call `seedAdvertisementsViaApi`. No longer calls `runCreateCategoryFlow`/`runCreateCityFlow`/
  `createAdvertisementBulk` — those UI helpers stay unchanged, still used by specs 03/05.
- `ensureAdminEn` stays UI-based (a real account must exist before Basic-Auth key issuance).
- Tests 3-6 unchanged — they only read state, they don't care how it was created. The Vaadin UI
  form and the REST controllers both call the exact same `marketplace-orchestrator` services
  (`TaxonCatalogService.create`, `AdvertisementSaveService.save`), so audit-log/timeline capture
  (which Test 6 verifies) happens identically regardless of which channel created the data.

**Verification:** `bash scripts/build-and-test.sh --unit --integration`, then
`bash scripts/playwright.sh e2e --full --ux` (spec 06 must still pass end-to-end, full-suite
runtime should measurably drop vs. the ~9-10 min baseline), then `bash scripts/ci.sh` before
considering this issue fully done.

**Bug found and fixed during first `e2e --full --ux` verification run (2026-09-03):**
`POST /api/advertisements` failed with a 500 (`DataIntegrityViolationException`: `null value in
column "created_by"`). Root cause: `ApiKeyAuthenticationFilter` sets a plain `Long` as the
`PreAuthenticatedAuthenticationToken` principal for bearer-token requests, but
`AuthContextService.currentPrincipal()` (`marketplace-app`) — the single choke point both
`JdbcAuditingConfig`'s `@CreatedBy`/`@LastModifiedBy` auditing and the audit subsystem's
`SessionActorHook`/`CurrentActorHook` actor resolution depend on — only recognized principals of
type `AuthenticatedPrincipal` (Vaadin session logins). Every REST write via a bearer key silently
resolved no actor at all. Fixed by adding `AuthContextService.getCurrentActorId(): Optional<Long>`,
which recognizes both principal shapes (`AuthenticatedPrincipal` → `toUserDto().id()`, plain
`Long` → itself), and switching `JdbcAuditingConfig.getCurrentAuditor()` and
`SessionActorHookImpl.getCurrentActorId()` to call it instead of the previous
`getCurrentUser().map(UserDto::id)` indirection. One shared fix, no duplication — same
single-source-of-truth shape as the rest of this phase's own findings.

**Open design question — deferred, revisit later:** the fix above lives inside `AuthContextService`,
whose other two methods (`getCurrentUser`, `getCurrentUserLocale`) are semantically about the
Vaadin *session* user. Bolting a bearer-token-aware branch onto that same class conflates two
distinct auth mechanisms (session login vs. stateless API-key auth) under one "current user"-named
abstraction, even though the new method never re-resolves anything by key — it only reads whichever
principal shape `ApiKeyAuthenticationFilter` already placed in `SecurityContextHolder`. Worth
revisiting once this phase settles: possibly extract a small, auth-mechanism-agnostic
"resolve the acting actor id from the security context" helper that `JdbcAuditingConfig` and
`SessionActorHookImpl` both call directly, decoupled from `AuthContextService`'s session-user
semantics — not done now, flagged for a later refactor pass.

**Second bug found during rerun after the fix above (2026-09-04):** with the actor-resolution fix
in place, `POST /api/advertisements` succeeded, but the next test in spec 06 (sort-column
verification) failed — expected `"Seed Advertisement 60"` first when sorted by date descending,
got `"Seed Advertisement 40"`. Confirmed directly against the DB: `created_at` timestamps for the
60 seeded ads did **not** increase in the same order as the input array's indices. Root cause:
`seedAdvertisementsViaApi` (`playwright/e2e/_flows/seed.flow.js`) issued all 60
`POST /api/advertisements` calls concurrently via `Promise.all()`, so real completion order (and
therefore `created_at` order) is scheduling-dependent, not equal to input order — unlike the old
serial UI-based seeding, which the sort test's assumption was written against. Fixed by making
`seedAdvertisementsViaApi` sequential (a plain `for...of` loop, `await`ed one at a time), matching
`seedTaxonsViaApi`'s already-sequential design in the same file (chosen there for the same
ordering reason). Still far faster than the old browser-automation seeding despite losing the
parallel-POST speedup.

**Third bug, same root cause, found in the same rerun (2026-09-04):** the users sort-column test
failed identically — expected `"Seed User 60"`, got `"Seed User 13"`, confirmed against the DB the
same way. `seedUsersViaApi` had the same `Promise.all()` parallel-POST shape. Fixed the same way:
sequential `for...of` loop instead of `Promise.all()`. With this fix, every seeding helper in
`seed.flow.js` that other tests depend on for creation-order (`seedUsersViaApi`,
`seedAdvertisementsViaApi`, `seedTaxonsViaApi`) is now sequential; only `postViaApi`'s callers with
no ordering dependency would still be safe to parallelize, and none currently are.

### Phase 6 (2026-09-04) — pre-existing login-flow client/server race, unrelated to REST work

**Not a Phase 4/5 regression** — surfaced twice during Phase 4 rerun verification
(`moderatorUk — first login...`, then `userEn — locale persists...` on an earlier rerun), always
the exact same symptom: `Timeout 15000ms` waiting for `.header-settings-button` after submitting
the login form (`auth.flow.js:48`), with the accessibility snapshot at failure time showing the
anonymous "Not signed in" landing page. Investigated with real evidence before concluding
anything (never assumed "just a flake"):

- App logs confirmed the login **did succeed server-side, fast** — for the `moderatorUk` occurrence,
  `Login success: email=moderator.uk@example.com` was logged ~1 second after the prior test's
  logout, with zero exceptions anywhere in the surrounding window.
- Every other failure in both flaky runs (spec 04/05 tests, a timeline actor-picker test) was
  confirmed to be a downstream cascade of this one root failure — spec 03's category/city/
  boundary-user seeding tests never ran because the earlier login failure aborted the rest of that
  describe block, so later specs failed on missing preconditions, not independent bugs.

**Root cause:** `runSubmitLoginFlow` (`playwright/e2e/_flows/auth.flow.js`) clicks the submit
button, *then* calls `page.waitForLoadState('networkidle').catch(() => {})` — since Vaadin keeps a
live push/heartbeat connection open, `networkidle` rarely fires cleanly, so this wait was already
best-effort/swallowed. Calling `waitForLoadState` *after* the click (not paired with it) is exactly
the race Playwright's own docs warn about for actions that trigger a full navigation: whether the
wait's promise gets correctly associated with the navigation the click just triggered, versus the
page state immediately after the click but before the browser has started that navigation, is not
guaranteed — matching the observed shape here (server done fast, client-side wait never resolves).

**Fix:** pair the click and the load-state wait in a single `Promise.all([...])`, the
Playwright-recommended pattern for "this action triggers navigation" — registers the wait before
the click fires, so it's unambiguously tied to the navigation that click causes. Switched from
`networkidle` (unreliable here, hence the swallowed `.catch()`) to `load` (fires once when the
post-login page's `load` event fires, independent of Vaadin's background push traffic).

```js
async function runSubmitLoginFlow(page, expect, user, locale = user.locale) {
  await Promise.all([
    page.waitForLoadState('load'),
    page.locator('vaadin-button').filter({ hasText: /log in|увійти/i }).last().click(),
  ]);
  await expect(page.locator('.header-settings-button')).toBeVisible({ timeout: 15000 });
  ...
```

**Verification:** `bash scripts/playwright.sh e2e --full --ux`, full clean run, 0 failures.

### Phase 3 detailed plan (2026-09-03) — resource controllers

All new files land in the `marketplace-rest-api` module, package `org.ost.restapi.api`, wrapping
existing `marketplace-orchestrator` services — none of the underlying service methods change.
Confirmed by direct research against current method bodies (not assumed):

- **Every write path already enforces authorization internally** — `AdvertisementSaveService.save/delete`,
  `ProviderProfileSaveService.save/delete`, and `TaxonCatalogService.create/update/softDelete/restore`
  all call `AuthorizationService.requireCanOperate`/`requireIsPrivileged` themselves and throw
  `AccessDeniedException` (`org.ost.orchestrator.services`). The new controllers never duplicate
  this check — they only resolve `actorId` and let the exception propagate to `ApiExceptionHandler`.
  One caveat: `AdvertisementSaveService.save()` skips the check entirely on **create** (`dto.id() ==
  null`) — any authenticated actor may create an ad under their own id; there is no separate
  "may this user create ads" rule to enforce.
- **`UserProfileService.register(dto, clientIp)` returns `void`** — the controller must call
  `findByEmail(dto.getEmail())` afterward to resolve the new user's id for the response body.
- **`ProviderProfileSaveService.save(dto, targetUserId, actorId)` takes 3 params, not 4** — the
  `actorIsPrivileged` flag from the plan's original "Decisions" section is resolved internally via
  `authorizationService.isPrivileged(actorId)`; the controller always passes `targetUserId ==
  actorId` (self-service only, per that same Decision).
- **Taxon's delete method is `softDelete`, not `delete`.**
- **`TaxonTranslationDto` is `@Value @Builder`, not a record**, and carries its own `String locale`
  field alongside the `Map<Locale, TaxonTranslationDto>` key — both must be populated consistently.
- **`OptimisticLockingFailureException`** (`org.springframework.dao`, Spring's own) is thrown on a
  stale `version` for Advertisement/ProviderProfile/Taxon writes — maps to 409.

**New files:**
- `UserRegistrationController` — `POST /api/users`, public. `@RequestBody @Valid SignUpDto dto`
  (reused directly, already `@NotBlank`/`@Email`-annotated) + `HttpServletRequest` for
  `request.getRemoteAddr()` (same client-IP convention `marketplace-app` already uses). Calls
  `UserProfileService.register(dto, ip)` then `findByEmail(dto.getEmail())`, returns
  `UserCreatedResponse(Long id, String name, String email)`.
- `AdvertisementApiController` — `/api/advertisements`. `POST`/`PUT {id}` take
  `AdvertisementSaveDto` directly (reused, already `@NotBlank`/`@Size`/`@NotNull`-annotated);
  `PUT` rebuilds the record with the path `{id}` (path wins over any body `id`). `saveService.save(dto,
  actorId, ref -> null)` — no gallery commit in v1 (matches the original plan's `commitGallery`
  decision). `GET`/`GET {id}` wrap `AdvertisementReadService`; `GET` (list) takes only
  `page`/`size`/`sort` query params in v1, no rich filter query — deferred, `AdvertisementFilterDto`'s
  full field set is a larger scoping question than this phase needs to answer. `DELETE {id}` takes
  an optional `version` query param.
- `ProviderProfileApiController` — same CRUD shape over `ProviderProfileSaveService`/
  `ProviderProfileReadService`, `targetUserId == actorId` always.
- `TaxonApiController` — `/api/taxons`. New thin request records (`Map<Locale,...>` can't bind from
  JSON): `TaxonTranslationRequest(String locale, String name, String description)`,
  `TaxonCreateRequest(TaxonType type, List<TaxonTranslationRequest> translations)`,
  `TaxonUpdateRequest(List<TaxonTranslationRequest> translations, Long version)`. Controller converts
  to `Map<Locale, TaxonTranslationDto>` before calling `TaxonCatalogService`. `GET` takes `type`
  (required) and `locale` (defaults to `"en"`) query params, wraps `getAllByType`. `DELETE {id}` calls
  `softDelete`, not `delete`.
- `ApiExceptionHandler` (`@RestControllerAdvice(basePackages = "org.ost.restapi.api")`) — maps
  `AccessDeniedException`→403, `OptimisticLockingFailureException`→409,
  `MethodArgumentNotValidException`→400 (field errors), `NoSuchElementException`→404 (from
  `.orElseThrow()` in the controllers above). Local `ErrorResponse(String message)`/
  `ValidationErrorResponse(Map<String,String> fieldErrors)` records, same package.

**Tests** (plain JUnit+Mockito, `marketplace-rest-api/src/test/java/org/ost/restapi/api/`,
construct-in-`@BeforeEach` per the established pattern): one test class per controller mocking its
orchestrator service(s) directly, plus `ApiExceptionHandlerTest` for the four mapping cases.

**Verification:** `bash scripts/build-and-test.sh --unit --integration`, then move to Phase 4.

### Phase 5 detailed plan (2026-09-03) — UI/REST validation parity, single source of truth, pagination

Triggered directly by user concern: does the REST API duplicate/diverge from the Vaadin UI's own
validation, should shared rules move to one canonical place, and does REST paginate the way the UI
does rather than dumping everything. Researched against real code (not assumed) before drafting
this plan — findings below, each grounded in a specific file/line.

**Finding 1 — Taxon translation validation: a real, total gap.**
`TaxonTranslationRequest`/`TaxonCreateRequest`/`TaxonUpdateRequest`
(`marketplace-rest-api/src/main/java/org/ost/restapi/api/`) carry zero Jakarta annotations.
`TaxonCatalogService.create/update` (`marketplace-orchestrator`) pass the translations straight to
`TaxonPort` with no validation of their own — only an authorization check. Meanwhile the UI's
`TaxonFormOverlayModeHandler.buildBinder()` (`marketplace-app`, lines 257-275) enforces "name
required, 1-255 chars" / "description required, 1-2000 chars" via hand-rolled
`StringLengthValidator` calls with **hardcoded literals**, not derived from any shared constant —
`TaxonTranslationDto`/`TaxonDto` (`platform-commons`) carry no Jakarta annotations either. Today a
REST caller can submit a blank name or a 10,000-character description and it reaches the database
unchanged.

**Finding 2 — two service-layer rules already exist as a single source of truth, but REST leaks
them as unmapped 500s instead of clean 4xxs.** Confirmed both by reading the real service code:
- `HtmlSanitizer.sanitize()` (`html-sanitizer-lib`) calls `validateVisibleTextLength()`, which
  throws `IllegalArgumentException` when visible text exceeds the cap. This runs inside
  `AdvertisementService.save()`/`ProviderProfileService.save()` (lines 130/104) — i.e. the
  2000-visible-char rule the UI's binder also enforces is **already enforced once, service-side,
  for every caller including REST** — it just isn't mapped to a REST-friendly error today.
- `UserService.register()`'s duplicate-email rejection is a DB unique-constraint violation
  (`DuplicateKeyException`, `org.springframework.dao`) — already the single real source of truth
  for email uniqueness (the UI's own `.withValidator(...)` pre-check in `SignUpDialog` is just a
  UX nicety on top of it). `ApiExceptionHandler` has no handler for either exception type, so both
  currently fall through to Spring's default 500.

**Finding 3 — one literal drift risk (cosmetic, not a functional gap).**
`AdvertisementFormOverlayModeHandler.buildBinder()` line ~330 hardcodes the title-length validator
as `new StringLengthValidator(msg, 1, 255)` instead of referencing
`AdvertisementSaveDto.TITLE_MAX_LENGTH` — currently equal (255) but not derived from it, so a
future change to the DTO's constant would silently desync the UI.

**Finding 4 — REST list endpoints accept an unbounded `size`, unlike the UI's own bounded page
sizes.** `AdvertisementApiController.list()`/`ProviderProfileApiController.list()` take a plain
`@RequestParam(defaultValue = "20") int size` with no upper bound — a caller can request
`size=100000` and get everything in one response, unlike the UI grid, whose page size is always
bounded by `UserSettingsDto`'s existing `@Min(5) @Max(100)` constraint. The `page=0`/`size=20`
*defaults* already match the UI's own default (`PaginationDefaults.DEFAULT_PAGE_SIZE = 20`,
`UserSettingsDto.defaultSettings().adsPageSize(20)`) — confirmed identical today, just duplicated
independently in three places (a separate, minor drift risk, not fixed here since nothing is
currently wrong).

**Non-finding, confirmed — Taxon's REST list is unpaginated, but so is the UI's.**
`TaxonApiController.list()` returns the full category/city list for a type in one call. Checked
`TaxonManagementView.refresh()` (`marketplace-app`): it also calls `taxonCatalogService
.listAllByType(...)` and renders everything at once — no `PaginationBar` anywhere in that view.
Category/city catalogs are a small, bounded reference-data set by design; REST already matches the
UI here. No change proposed for this endpoint.

**Proposed fixes:**
1. Add Jakarta constraints to the canonical `TaxonTranslationDto` (`platform-commons`) —
   `@NotBlank @Size(min = 1, max = 255)` on `name`, `@NotBlank @Size(min = 1, max = 2000)` on
   `description` — exposed as public constants (`NAME_MAX_LENGTH`, `DESCRIPTION_MAX_LENGTH`) so
   both `TaxonTranslationRequest` (REST) and `TaxonFormOverlayModeHandler` (UI) reference the same
   numbers instead of independently duplicating them. `TaxonTranslationRequest` gets matching
   `@NotBlank @Size(...)` annotations; `TaxonCreateRequest`/`TaxonUpdateRequest` get
   `@NotEmpty List<@Valid TaxonTranslationRequest> translations`; `TaxonApiController.create/update`
   get `@Valid @RequestBody`.
2. `ApiExceptionHandler`: add `IllegalArgumentException` → 400 and `DuplicateKeyException` → 409,
   alongside the existing four mappings — surfaces the two already-enforced service-layer rules
   properly instead of leaking a 500. No new validation logic, no duplication — only REST error
   shaping.
3. `AdvertisementFormOverlayModeHandler`: replace the hardcoded `255` literal with
   `AdvertisementSaveDto.TITLE_MAX_LENGTH`.
4. `AdvertisementApiController.list()`/`ProviderProfileApiController.list()`: bound `size` (e.g.
   `@Min(1) @Max(100)` on the parameter, `@Validated` on the controller class so Spring enforces
   method-parameter constraints) so REST can no longer request an unbounded page, matching the
   UI's own bounded settings.

**Tests (new/updated, per "all these constraints must be tested"):**
- `TaxonApiControllerTest`: blank/oversized name and description rejected (400); empty
  `translations` list rejected (400).
- `ApiExceptionHandlerTest`: `IllegalArgumentException` → 400, `DuplicateKeyException` → 409.
- `UserRegistrationControllerTest`: duplicate-email registration → 409, not an unmapped 500.
- `AdvertisementApiControllerTest`/`ProviderProfileApiControllerTest`: `size` above the max
  rejected (400).
- Playwright: one lightweight `request`-fixture check (no browser, same pattern as spec 06's REST
  seeding) confirming a duplicate-email `POST /api/users` returns 409 through the live API — the
  only one of these fixes with externally observable behavior distinct from what's already
  covered by unit tests.

**Verification:** `bash scripts/build-and-test.sh --unit --integration`, then
`bash scripts/playwright.sh e2e --full --ux`.

### Phase 7 (proposed, 2026-09-04) — Swagger/OpenAPI + a Postman collection with automatic token injection

**Not yet approved for implementation** — captured here from chat discussion so the plan and its
reasoning aren't lost, per the standing rule that a multi-step plan always lives in the issue file
first. Auth-mechanism reasoning (API-key vs. OAuth2) is recorded in `marketplace-app/DECISIONS.md`
ADR-078, referenced here rather than restated.

**Swagger/OpenAPI:** add `springdoc-openapi-starter-webmvc-ui` to `marketplace-rest-api/pom.xml`.
Generates a live OpenAPI 3 spec from the existing controllers/DTOs' Jakarta annotations with no
hand-written endpoint descriptions, and serves Swagger UI. Needs a `@SecurityScheme` declaration
(HTTP bearer + HTTP basic, matching `ApiSecurityConfig`'s real dual-auth shape) so Swagger UI's own
"Authorize" button works against the real auth model.

**Postman collection:** generated from the live OpenAPI spec via Postman's "linked"/sync import
(not a plain one-time Import), so the collection tracks the real API automatically as it changes —
avoiding a hand-maintained collection silently drifting from reality (two sources of truth). A
collection-level pre-request script (survives sync updates, since sync only regenerates the
generated requests themselves, not collection-level scripts) calls `POST /api/api-keys` once via
HTTP Basic, caches the returned key in a collection variable, and every other request references it
as `Authorization: Bearer {{apiKey}}` — automatic token injection with no manual per-session step.

**Governing invariant:** the collection's request definitions are never hand-edited directly in
Postman — any change to reflect a real API change happens only via re-sync from the live OpenAPI
spec, never manual editing inside Postman. The pre-request script is the one deliberate exception,
since OpenAPI has no way to express "call this endpoint first, then use its response as a bearer
token for everything else" (a real, structural limitation of the spec, not a gap in this plan).

**Verification (once approved and implemented):** Swagger UI reachable and its "Authorize" flow
works against a real account; the synced Postman collection's every request runs green against a
freshly reset DB with zero manual token setup.

### Phase 8 (approved, 2026-09-04) — API-key architecture cleanup + a new review lens to catch this class of gap

Surfaced through direct user questioning of the API-key design (why `user_id`, why no `actor_id`,
why does the SPI live under `user.*`), each confirmed against real code/ADRs before being accepted
as a real finding — not assumed.

**1. `user_id` → `actor_id` rename, FK removed (per ADR-005 + ADR-064 precedent).**
`user-spring-boot-starter/.../01-user-schema.xml`, `provider-profile-spring-boot-starter/.../01-provider-profile-schema.xml`,
and `audit-spring-boot-starter/.../01-audit-schema.xml` all use `actor_id` with **no DB-level FK**
— confirmed directly, every existing usage's own `remarks=` says so
(`"No FK -- matches this codebase's actor-reference-column convention"`). `audit-spring-boot-starter/DECISIONS.md`
ADR-005 ("Actor-centric SPI vocabulary") is the original decision behind this: `user_id` → `actor_id`,
because "actor" applies to bots/service accounts/workflows equally, "user" is a marketplace-specific
term. `marketplace-app/DECISIONS.md` ADR-064 separately established "no hard FK between starter
schemas" (a starter's own Liquibase changelog must be runnable against a DB that has never seen
another starter's schema). `apikey-spring-boot-starter`'s `user_api_key` table currently violates
both: column named `user_id`, and a real `<addForeignKeyConstraint>` to `user_information.id` with
`ON DELETE CASCADE` (plus `ApiKeyAutoConfiguration`'s `apikeyLiquibase` bean is
`@DependsOn("userLiquibase")`, the same coupling symptom ADR-064's own consequences section
describes). Since this table has never been released (no deployed changelog history to preserve),
edited the existing `01-apikey-schema.xml` changeset in place, matching ADR-064's own approach,
rather than a new incremental changeset:
- `user_id` column → `actor_id` (also `fk_user_api_key_user_id`/`idx_user_api_key_user_id` renamed
  to their `actor_id` equivalents, FK constraint removed, plain index kept).
- `ApiKey` entity field `userId` → `actorId`; `ApiKeyRepository`/`ApiKeyCrudRepository`/`ApiKeyService`
  updated to match.
- `ApiKeyAutoConfiguration`'s `@DependsOn("userLiquibase")` removed.
- Cascade-delete replaced with application-level logic: a new `ApiKeyPort.deleteAllForActor(Long actorId)`
  method, called from `UserDeleteService` (which already cascades a user's other dependent data —
  advertisements, provider profile — the same way, per `marketplace-orchestrator/README.md`).

**2. `ApiKeyPort`/`ApiKeySummaryDto` moved to their own `platform-commons` package.**
Every other domain with its own Maven starter module (`advertisement`, `taxon`, `providerprofile`,
`audit`, `attachment`) also has its own top-level package in `platform-commons`
(`advertisement.spi`/`.dto`/`.model`, etc. — see root `CLAUDE.md`'s Module Layout section).
`apikey-spring-boot-starter` was the one exception: its own Maven module, but `ApiKeyPort`/
`ApiKeySummaryDto` lived inside `user.spi`/`user.dto` — inherited from the original plan's framing
of API-key as "a 5th narrow port of the user domain," written before API-key became its own
separate starter module. Moved to `platform-commons/src/main/java/org/ost/platform/apikey/spi/ApiKeyPort.java`
and `.../apikey/dto/ApiKeySummaryDto.java`, matching every sibling domain's shape. `docs/architecture/scripts/generate-architecture-model.sh`'s
`SPI_SUBSYSTEM_ORDER` array gains `apikey`, so the SPI Map diagram gets its own tab for it (previously
correct-as-written only because the port lived under `user`, not because `apikey` genuinely lacked
a subsystem of its own).

**3. New review lens: `precedent-reviewer`.** Neither of `deep-review-orchestrator`'s two existing
lenses (`dry-kiss-yagni-reviewer`, `solid-reviewer`) would have caught any of the three findings
above — confirmed by reading both agent definitions directly: `DECISIONS.md` is currently consulted
only defensively (to *suppress* a finding if a deliberate documented exception exists), never
proactively (to *flag* a new pattern that contradicts an already-`Accepted` ADR). New agent
`.claude/agents/review/precedent-reviewer.md`, same JSON output shape as the two existing lenses,
checking a diff against: (a) explicit rules in `.claude/rules.md`/root `CLAUDE.md`/relevant
`.claude/rules/<module>.md` files, and (b) established precedent in relevant `DECISIONS.md` files
(the changed module's own, plus `platform-commons`/`marketplace-app` when the change touches
SPI/DTO/schema conventions). Wired into `deep-review-orchestrator.md` step 3 as a third parallel
dispatch alongside the existing two; step 8's `ReportFindings` category mapping gains `"precedent"`.

**Verification:** `bash scripts/build-and-test.sh --unit --integration`, full reactor compile
(package move touches every module that imports `ApiKeyPort`/`ApiKeySummaryDto`), then
`bash scripts/playwright.sh e2e --full --ux`. All green — 61/61 Playwright (58 baseline + 3 new
`09-rest-api-swagger.spec.js` checks), full unit+integration suite green.

**4. Follow-up vocabulary sweep (2026-09-04), same session, same reasoning as #1 above.** Two more
"user" leftovers surfaced by direct user review, after #1-3 already shipped:
- `ApiKeyPort.resolveUserId`/`ApiKeyService.resolveUserId`/`ApiKeyPortImpl.resolveUserId` →
  renamed to `resolveActorId` (and every caller: `ApiKeyManagementService`,
  `ApiKeyAuthenticationFilter`, both test files) — the method itself never needed to know it was
  resolving a "user," only an actor id.
- `ApiKeyPort.listForUser`/`ApiKeyService.listForUser`/`ApiKeyPortImpl.listForUser` → renamed to
  `listForActor`, same reasoning, same caller sweep.
- The table itself, `user_api_key` → renamed to `api_key` (edited the existing `01-apikey-schema.xml`
  changeset in place, same pre-release-so-no-deployed-history reasoning as the earlier `actor_id`
  rename) — entity `@Table`, every `ApiKeyRepository` SQL string, `TestDataCleaner`'s table list,
  and this module's own `README.md`/`.claude/rules/apikey-spring-boot-starter.md` all updated to
  match. The table name carrying "user" was the same class of leftover as the column had been —
  once the column stopped saying "user," the table's own name became the next-most-visible one.

**Caught and fixed in the same pass:** the `<!-- Description: ... -->` XML comment headers added to
`pom.xml`/the master changelog (module-doc-standards's own "file-level header, no exception" rule)
used a literal `--` as a clause separator, copying the pattern this project's `#`/`//` comments use
elsewhere — invalid XML (a comment body may never contain `--` except at its own delimiters),
confirmed by a real `xml.etree.ElementTree.ParseError` on both files. Fixed by using a comma
instead; `module-doc-standards/SKILL.md`'s own example template had the identical mistake baked
into it, fixed there too so the next application of the skill doesn't repeat it.

**5. `module-readme-standards`'s "Key classes" table replaced with a "Data flow" section
(2026-09-04).** Raised by direct user review of the just-regenerated
`apikey-spring-boot-starter/README.md`: a `Key classes` table's `Role` column and each class's own
Javadoc both end up describing the same class, which is the exact "one fact, two homes"
duplication `.claude/rules.md` forbids — confirmed as real drift, not theoretical, since several
rows already read close to a Javadoc restatement. Replaced the shape (in
`.claude/skills/module-readme-standards/SKILL.md`) with a `Data flow` section — prose (or a Mermaid
`flowchart` for a module whose real flow genuinely branches) describing what comes in, which
classes touch it in what order, and what goes out — grounded in the same principle
`infra-readme-standards`'s own `Flow` section already applies to script-group directories.
Structurally this can't degrade into a per-class Javadoc restatement the way a table could, since a
sequence is inherently a multi-class fact. Also narrowed the `Dependencies` section's own template
wording: a module's README may name *which module* it depends on/is depended on by, never a class
living inside that other module — `apikey-spring-boot-starter/README.md`'s own `Dependencies`
section had been naming `UserDeleteService`/`ApiKeyController`/`ApiKeyAuthenticationFilter`
(internal classes of `marketplace-orchestrator`/`marketplace-rest-api`), also flagged by direct
user review before this change. Six sections of the skill file were updated to keep it internally
consistent with the new shape (default template, dedicated explainer section, "Aggregated
cross-file facts," "After deleting a class, sweep...," "⛔ Applying this standard," "Independent
review"). Applied immediately to `apikey-spring-boot-starter/README.md` itself: `Key classes` table
replaced with a `Data flow` narrative (issuance/resolution/listing-revocation-cleanup, each traced
through `ApiKeyPortImpl` → `ApiKeyService` → the matching `ApiKeyRepository` call), and
`Dependencies` reworded to name `marketplace-rest-api`/`marketplace-orchestrator` only, dropping
the internal class names.

**6. Database ERD's conceptual (no-FK) relationships: mechanical derivation instead of a growing
hand-curated list (2026-09-04).** Raised by direct user review after the `api_key` addition (step
1 above) surfaced that `USER_INFORMATION → API_KEY` was missing from the diagram — root-caused to
`docs/architecture/scripts/generate-architecture-model.sh`'s `db_erd_conceptual_relationships_json()`
being a hand-maintained array, never updated when `apikey-spring-boot-starter` was added. A full
sweep of every `*_id`/`*_by`-shaped column across every changelog (advertisement, api_key,
attachment, attachment_snapshot, audit_log, provider_profile, taxon, user_preferences) found the
hand-curated list was already missing several more real relationships even before `api_key`
(`taxon.created_by/updated_by/deleted_by`, `attachment_snapshot.changed_by_actor_id`,
`audit_log.actor_id`, `user_preferences.actor_id`) — 9 entries existed, 13 point-relationships
actually exist in the schema, plus a 4th generic entity_type/entity_id pair
(`ADVERTISEMENT → TAXON_ASSIGNMENT`) also missing. Column-name pattern-matching alone (`actor_id`,
`_by`, `_actor_id`) was considered and rejected — column naming is inconsistent across starters
(`created_by` vs `deleted_by_actor_id` vs `changed_by_actor_id`) and some `remarks=` never mention
"no FK" at all (`audit_log.actor_id`), so a name/prose heuristic would keep silently missing cases;
it also cannot express *which* table is the real target (assumed "always `user_information`" would
have been wrong for `provider_profile.city_taxon_id → taxon`).

Adopted instead: a new, fixed, machine-parseable `remarks=` marker convention — every no-FK
reference column's `remarks=` now carries the literal substring `References <table>(<column>), no
FK` (mirrors the real Liquibase FK syntax `references="table(id)"` as prose). Applied to all 13
existing point-relationship columns across 7 changelog files (advertisement, api_key, attachment
×2, audit_log, provider_profile ×2, taxon ×3, user_preferences), each also gaining
`<validCheckSum>ANY</validCheckSum>` where missing (advertisement, audit, provider-profile, taxon)
so a future non-`--reset` deploy doesn't choke on the checksum change. `db_erd_json()`
(`generate-architecture-model.sh`) now derives the point-relationship half of
`conceptualRelationships` by regex-scanning every column's `remarks=` for this marker (`from` =
table named in the marker, `to` = the table the column lives on, `label` = column name) via a
`run_node -e` post-processing step, merged with a much-shrunk hand-curated list that now holds only
the 3 genuine generic entity_type/entity_id pairs (whose real target is a runtime data value, not a
schema fact, so no marker can express it). Verified: regenerated JSON now shows all 16 real
relationships (13 derived + 3 curated), including the two new ones this sweep found
(`USER_INFORMATION → API_KEY`, `ADVERTISEMENT → TAXON_ASSIGNMENT`) and the four pre-existing gaps
listed above. Documented as a new mechanically-required convention in
`.claude/skills/module-doc-standards/SKILL.md` ("No-FK reference columns" section + a pre-write
checklist line), so the next new no-FK column carries the marker from the start instead of being
another silent gap. Recorded as `docs/architecture/scripts/DECISIONS.md` ADR-034 (partially
supersedes ADR-017's decision #3 for the point-relationship portion only — the entity_type/entity_id
generic portion of ADR-017's original design is unchanged).

**7. `starters_must_route_taxon_assignment_writes_through_orchestrator` ArchUnit rule; "Cross-Starter
Exceptions" diagram tab removed (2026-09-04).** Direct user review of the Bounded Contexts diagram's
"Cross-Starter Exceptions" tab raised whether that situation (a starter bypassing the orchestrator
for a cross-domain write) can genuinely recur, given starters are supposedly independent.
Investigation confirmed yes: `starters_must_not_import_sibling_starters` only blocks a starter
importing another starter's own package — it does not block injecting `ComponentFactory<TaxonPort>`
(a `platform-commons` type) and calling a write method on it directly, since that's not a
"sibling-starter import" at all. New `@ArchTest` in `ArchitectureRulesTest.java`:
`starters_must_route_taxon_assignment_writes_through_orchestrator` — build-fails if any class
outside `org.ost.orchestrator..`/`org.ost.marketplace..`/`org.ost.restapi..`/`org.ost.taxon..` calls
`TaxonPort.replaceAssignments()` directly (verified: 20/20 `ArchitectureRulesTest`, was 19/19).
Since this rule's scope is identical to the diagram's own "Cross-Starter Exceptions" category (both
keyed on the same one method), and the category's membership is now permanently guaranteed empty by
a failing build rather than merely "currently empty," the category was removed outright
(`BC_CATEGORY_ORDER`/`LABEL`/`DESC`/`BC_LABEL_CATEGORY`'s `exceptions` entries, the
`"category assignment via"` label, its regex-extraction loop, and the client-side JS mirrors) —
Bounded Contexts now has 3 tabs instead of 4. Recorded as `marketplace-app/DECISIONS.md` ADR-079
(also affects `docs/architecture/scripts`).

**8. `marketplace-rest-api`'s bounded-context misclassification fixed (2026-09-04), found while
investigating the same "Service Calls (BFF)" tab per user suspicion ("мені здається що тут чогось
бракує").** Root cause: `db_orch_mod` (bash scalar, `generate-architecture-model.sh`) is set from
whichever module declares `<architecture.boundedContext>orchestrator</architecture.boundedContext>`
in its own `pom.xml` — but both `marketplace-orchestrator` *and* `marketplace-rest-api` declared
this same value, and since `marketplace-rest-api` is later in root `pom.xml`'s `<modules>` order, it
silently won, overwriting the real orchestrator module. Confirmed by direct count:
`marketplace-orchestrator` has 16 files injecting `ComponentFactory<XPort>` (real domain
composition); `marketplace-rest-api` has 0 (it only ever calls `marketplace-orchestrator`'s own
plain `@Service` beans directly — the same shape `marketplace-app`'s UI layer already uses). Fixed:
`marketplace-rest-api/pom.xml`'s `boundedContext` value changed to `rest-api` (new, distinct value);
generator gained a new `RestApi` bounded-context domain (mirroring `UI`'s treatment: its own node,
a `RestApi -> Orchestrator: calls` edge via the same `import org.ost.orchestrator.*` signal UI
already uses) — deliberately *not* given a `RestApi -> starter: calls` direct-injection loop the way
UI has, since `marketplace_app_must_not_depend_on_platform_commons_spi_directly` (scoped to also
cover `org.ost.restapi..`, see `.claude/rules/marketplace-rest-api.md`) already forbids
`marketplace-rest-api` from ever injecting a `*Port` directly — that loop would always find nothing
by construction, not just by current coincidence. Verified directly: regenerated model now shows
`Orchestrator -> <domain>: calls` edges to all 7 real domains (was near-empty), plus the new
`RestApi -> Orchestrator: calls` edge. Treated as an ordinary bug fix, not ADR-worthy on its own
(self-evident from the diff, no future-constraining decision beyond "a new bounded-context kind
`rest-api` now exists, treated like `ui`").

**Final verification (2026-09-04), covering items 1-8 above as one batch:**
- `bash scripts/build-and-test.sh --unit --integration`: unit 233/233 (query-lib 29, orchestrator
  107, marketplace-rest-api 27, marketplace-app 70 incl. `ArchitectureRulesTest` 20/20 — was 19/19
  before the new rule), integration 183/183. Both `BUILD SUCCESS`.
- `bash scripts/deploy-and-run.sh --reset`: clean start, all 7 edited Liquibase changesets applied
  with no checksum/SQL errors.
- `bash scripts/playwright.sh e2e --full --ux`: 61/61 passed (8.7m).

## Operational notes
- token_cost_review: n/a
- token_cost_research: n/a
- token_cost_verification: n/a
- review_signal_ratio: n/a (no `/code-review` ran during this issue's work — findings surfaced via
  direct user questioning + grep/code investigation, not the review skill)
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: n/a
- flows_chosen: n/a
- flows_matched: n/a

## Related

- [improvement-035](improvement-035-sql-seeding-for-playwright-spec-05.md) — the concrete consumer
  of this infrastructure; blocked on this issue.
- `.claude/rules/marketplace-app.md` "Security: @PreAuthorize and Vaadin" — the existing
  security-config context this issue extends.
- `marketplace-app/DECISIONS.md` ADR-025 — the `anyRequest().permitAll()` decision this issue's
  new endpoint(s) would sit under.
