# improvement-183: REST API access/tooling/docs follow-ups, taxon i18n UI rework, and view-parity polish

**Type:** improvement (bundle of independently-schedulable follow-ups surfaced during REST API review)
**Module:** marketplace-rest-api, marketplace-orchestrator, user-spring-boot-starter, taxon-spring-boot-starter, provider-profile-spring-boot-starter, advertisement-spring-boot-starter, attachment-spring-boot-starter, marketplace-app, integration-tests
**Priority:** high
**When:** independent, no blockers — each of the 11 items below is independently schedulable; track completion per item

## Overview

Eleven follow-up items surfaced in one review pass over the REST API rollout
([improvement-073](improvement-073-add-rest-api-support.md)-family work), the taxon multi-language
model, and view/card parity between the Advertisement and Provider Profile domains. Each item below
is independently actionable — pick up whichever first, no shared blocker across them.

## 1. Admin/moderator read access to users via REST — ✅ Done (2026-09-04)

**Implemented:** `UserRegistrationController` renamed to `UserApiController` (full symmetry with
the other 3 domain controllers — one class per REST resource). Added `GET /api/users` (paginated/
filtered/sorted, mirroring `AdvertisementApiController`) and `GET /api/users/{id}`, both gated by
`AuthorizationService.requireIsPrivileged(actorId)` — the same admin-or-moderator check
`TaxonCatalogService` already uses, resolved via a real DB lookup since the bearer-key principal
carries only a raw user id, no role. No `ApiSecurityConfig` change needed — both endpoints already
fell under `.anyRequest().authenticated()`; the privilege check happens inside the controller,
same pattern as Taxon writes. `UserRegistrationControllerTest` renamed to `UserApiControllerTest`,
12 tests (register + list + getById, positive/negative/403/404).

## 2. Multi-step REST pipeline/scenario tooling — ✅ Done (2026-09-04)

**Current state:** no scenario-chaining tool exists in this repo. Testing today is per-endpoint
(unit tests, see item 8) or manual, one call at a time via curl/Swagger UI.

**Ask:** a way to script/run a chained scenario — e.g. create a user, obtain an API key for them,
create a provider profile, then post several advertisements — and see the whole chain execute.

**Approach (decided 2026-09-04):** the pipeline lives as a real scenario test — see item 8. Its
home is `integration-tests`, whose scope is deliberately widened beyond pure starter-repository
tests into a level-based structure (supersedes the "sole home for repository tests" framing
currently in `.claude/rules/integration-tests.md` — that file's scope description needs updating
to match, and the widening itself recorded via `/record-decision`):

- **Level 1** (moved, unchanged behavior) — `org.ost.integrationtests.level1.<domain>` — starter
  repository tests against a real Postgres.
- **Level 2** (new, scaffolded only once a concrete need arises — not built speculatively now) —
  `org.ost.integrationtests.level2.orchestrator` — `marketplace-orchestrator`-level integration
  tests.
- **Level 3** (new, needed now for item 8) — `org.ost.integrationtests.level3.restapi` —
  HTTP-level scenario tests via real `MockMvc` bound to the real `WebApplicationContext`
  (`TestRestTemplate` doesn't exist in this Spring Framework version — replaced by
  `MockMvcBuilders.webAppContextSetup(...)` with the real security filter chain attached), reusing
  this module's existing `AbstractPostgresIntegrationTest` singleton container. The pipeline this
  item asks for (create user → issue key → create several ads) is simply this level's own scenario
  test — running it via `mvn test` (or a dedicated `run.sh` target) *is* running the pipeline, no
  separate scripting layer needed. One top-level package per level, so the package itself signals
  which layer a test exercises.

Requires `integration-tests/pom.xml` to gain `marketplace-orchestrator` and `marketplace-rest-api`
as new dependencies.

## 3. REST API page size must reuse Settings' page-size options, not an arbitrary literal — ✅ Done (2026-09-05)

**Implemented:** `AdvertisementApiController`/`UserApiController` resolve `size` from the caller's
saved `UserSettingsDto` (`adsPageSize`/`usersPageSize`) via `UserProfileService.resolveAdsPageSize`/
`resolveUsersPageSize` — the `size` request parameter was removed from both endpoints entirely, so a
caller cannot override it via the URL. `PATCH /api/users/me/settings` lets a caller change those
settings under the same validation limits (`PageSizeLimits`) the Settings UI form already enforces.
`ProviderProfileApiController`/`TaxonApiController` deliberately left unchanged (no saved-settings
concept for those two lists) — `TaxonApiController`'s own `page`/`size` were later removed entirely
(see item 12/`getAll()`), not resolved from settings.

## 4. Taxon translation validation — keep current behavior, add test coverage + Swagger docs — ✅ Done (2026-09-05)

**Implemented:** kept the current all-languages-required behavior unchanged (no UI/validation
rework). Added `TaxonServiceTest` coverage for `TaxonService.validateTranslations()` — missing
locale, blank name, blank description, all-locales-present success — in
`taxon-spring-boot-starter`. Added a REST-level test (`TaxonApiControllerTest.create_incompleteTranslations_returns400`)
confirming the same rule surfaces as 400 through the REST layer. Added `@Operation`/
`@io.swagger.v3.oas.annotations.parameters.RequestBody`+`@ExampleObject` to `TaxonApiController`'s
create/update operations, documenting the all-supported-locales requirement with a two-locale
example payload.

## 5. Align taxon list styles — category indentation vs. flat city list

**Current state (verified):** neither `TaxonManagementView.java` nor `CityManagementView.java`
(183 lines each) contains any indentation/level/depth rendering logic, and `taxon-view.css` has no
indent-related rule — the category tab's visible indentation was not located via static analysis
(possibly baked into seed data name strings rather than rendering code). The city tab renders flat.

**Ask:** make the two tabs' styling consistent — categories currently look indented/nested, cities
don't.

**Approach:** locate where the category indentation actually originates during implementation
(audit seed data and any shared renderer), then apply the same hierarchy-aware rendering
consistently to both tabs — or confirm cities genuinely have no hierarchy and document why no
indent applies there.

## 6. REST `version`/id fields must be server-managed, not caller-writable — ✅ Done (core), `@JsonIgnore` sub-item deliberately deferred (2026-09-08)

**Found (real bug, confirmed via code, not just design smell):** `AdvertisementApiController.create()`/
`ProviderProfileApiController.create()` passed the raw client-supplied `AdvertisementSaveDto`/
`ProviderProfileSaveDto` straight into `AdvertisementSaveService.save()`/`ProviderProfileSaveService.save()`,
which decide create-vs-update purely via `dto.id() == null`. A `POST` (semantically "create") body
that happened to include a non-null `id` silently became an `UPDATE` of that existing row instead
(blocked only by ownership/authorization, not by "this is a create" semantics) — a real REST
contract violation, not just a documentation gap.

**Implemented (verified directly against code 2026-09-08) — full If-Match/ETag redesign, applied
consistently across all three REST-exposed domains:**
- `AdvertisementApiController`/`ProviderProfileApiController` gained dedicated nested request
  records (`AdvertisementWriteRequest`, `ProviderProfileWriteRequest`, one shared shape for both
  create and update) with **no `id` or `version` field at all** — same pattern
  `TaxonApiController.TaxonCreateRequest`/`TaxonUpdateRequest` already established. The controller
  builds the actual `AdvertisementSaveDto`/`ProviderProfileSaveDto` itself (`id` from the path
  variable on update, `null` on create; `version` from the `If-Match` header via `ETagUtil.parseIfMatch`,
  `null` on create).
- `TaxonApiController.TaxonUpdateRequest` has no `version` field either, for the same reason.
- Every `GET .../{id}` response carries an `ETag` header with the resource's current version
  (`ETagUtil.withVersion`).
- Every `PUT`/`DELETE` reads the expected version from an `If-Match` request header (`ETagUtil.parseIfMatch`)
  instead of a body field or query param.
- `platform-commons`'s `AdvertisementSaveDto`/`ProviderProfileSaveDto` themselves are unchanged
  (still carry `id`/`version` fields) — the Vaadin UI's own form-save path uses these same DTOs
  directly with no HTTP layer involved, so those fields stay needed there; only the REST-facing
  request shape changed.

**Deferred by explicit decision (2026-09-08) — the "Follow-up caught during review" sub-item:** the
read DTOs (`AdvertisementInfoDto`, `ProviderProfileDto`, `TaxonDto`) still carry `version` as a
plain JSON field (confirmed via grep — no `@JsonIgnore` anywhere in `platform-commons`), so a REST
response duplicates the same fact in two places (the field and the `ETag` header). Not applying
`@Getter(onMethod_ = @JsonIgnore)` for now — deliberate, not forgotten; revisit if the duplication
becomes an actual problem.

## 7. Swagger: per-operation descriptions + examples, including cross-endpoint value sources — ✅ Done (2026-09-08)

**Implemented:** `@Operation(summary=..., description=...)` on every operation across
`AdvertisementApiController`, `ProviderProfileApiController`, `UserApiController`, `ApiKeyController`
(`TaxonApiController` already had this from item 4), plus `HealthController`/`SitemapController`
(the two top-level, unauthenticated endpoints, initially missed from the plan and added after being
flagged). Every write operation gets an `@io.swagger.v3.oas.annotations.parameters.RequestBody`
`@ExampleObject` payload. Cross-referenced fields (`categoryIds`/`cityTaxonId`) name their source
endpoint directly in the operation description. Every `GET .../{id}` and the new
`GET /api/users/me/settings` document their `ETag` response header via `@ApiResponse`/`@Header`;
every `PUT`/`PATCH`/`DELETE` documents its `If-Match` request header via `@Parameter`.

## 8. REST unit tests exist but don't capture request/response as reviewable scenarios — ✅ Done (2026-09-04)

**Implemented:** all 5 controller test classes rewritten to `MockMvc` (52 tests, curl-style
request/response logging via `RestApiMockMvcTestSupport`'s `alwaysDo`), plus 6 Level 3 real-DB
scenario tests (`org.ost.integrationtests.level3.restapi`): happy-path pipeline, cross-user
authorization, optimistic-locking conflict, provider-profile pipeline, taxon admin+privilege+
locale-fallback, real-data-volume pagination, duplicate-email conflict. Along the way, fixed two
real bugs this work surfaced: `ApiExceptionHandler` had no mapping for `DuplicateKeyException`/
`IllegalStateException` (now 409/429); `AdvertisementApiController`/`ProviderProfileApiController`
never called `AdvertisementDisplayEnrichmentService`/`ProviderProfileDisplayEnrichmentService`, so
every REST response had `categoryNames`/`cityName`/`createdByUserName`/media fields always null —
now enriched the same way the Vaadin UI already does, with a `?locale=` param added to the
Advertisement/ProviderProfile GET endpoints matching Taxon's existing pattern.

**Current state (verified):** unit tests already exist — `AdvertisementApiControllerTest`,
`ProviderProfileApiControllerTest`, `TaxonApiControllerTest`, `UserRegistrationControllerTest`,
`ApiKeyControllerTest`, plus paging/error-handler tests — but they call the `@RestController` Java
methods directly through Mockito mocks (e.g. `controller.create(ACTOR_ID, dto)`), not real HTTP
requests. No test captures a literal request URL/body or response as a reviewable string, and none
spans a multi-endpoint scenario.

**Ask:** real HTTP-level tests recording the literal request (method, URL, JSON body) and response
as explicit strings per scenario, plus a scenario spanning "create user → issue API key → create
several ads for that user" — ideally with some kind of per-scenario report (request in, response
out).

**Approach (decided 2026-09-04) — both layers, split by module, maximizing positive+negative
coverage on each:**

**Reporting:** start with plain assertions inside each test (no separate generated report
artifact) — revisit a generated per-scenario report file later if plain assertions prove hard to
review.

### Fast contract tests (MockMvc, mocked orchestrator services, no DB)

Rewrite `AdvertisementApiControllerTest`, `ProviderProfileApiControllerTest`,
`TaxonApiControllerTest`, `UserRegistrationControllerTest`, `ApiKeyControllerTest`
(`marketplace-rest-api/src/test/java/org/ost/restapi/api`) from today's direct Mockito method
calls (`controller.create(ACTOR_ID, dto)`) to `MockMvc`-based HTTP calls — captures literal JSON
request/response strings, still fast, no Docker. Every `@ExceptionHandler` in `ApiExceptionHandler`
(403 `AccessDeniedException`, 409 `OptimisticLockingFailureException`, 400
`MethodArgumentNotValidException`, 404 `NoSuchElementException`, 400 `IllegalArgumentException`)
gets at least one test per controller that can trigger it, not just the happy path:

- **UserRegistrationController** (`POST /api/users`): positive — valid signup → 201 + id/name/email.
  Negative — blank `name`/invalid `email` format/`password` under `PASSWORD_MIN_LENGTH` → 400 with
  the specific field named; **verified gap:** `UserService.register()` throws
  `DuplicateKeyException` on a duplicate email and `IllegalStateException` after
  `MAX_REGISTER_ATTEMPTS` — neither is mapped in `ApiExceptionHandler`, so both currently surface as
  an unhandled 500. Write the negative test asserting today's real (undesirable) 500 first, then
  fix `ApiExceptionHandler` to map both properly (409 for duplicate email, 429 or 400 for rate
  limit) and update the test to assert the fixed status — a real bug this test work surfaces, not
  just a coverage gap.
- **ApiKeyController**: positive — `POST /api/api-keys` with Basic auth (with and without a
  `label`) → key returned once; `GET`/`DELETE` with a valid bearer key. Negative — `POST` with no/
  bad Basic credentials → 401; `GET`/`DELETE` with no/invalid/garbage bearer token → 401; `DELETE`
  of another user's key or a nonexistent id → assert the real current behavior of
  `ApiKeyManagementService.revoke(userId, keyId)` (ownership-scoped, likely a silent no-op) —
  confirm that's the intended semantic, not an accidental gap.
- **AdvertisementApiController**: positive — create/update/delete/get/list with filters+sort+
  pagination. Negative — blank `title`, `description` over `DESCRIPTION_RAW_MAX_LENGTH`, missing
  `adKind`, `categoryIds` over `CATEGORY_MAX_COUNT` (10) → 400; unknown `sort` field →
  `IllegalArgumentException` → 400; `GET /{id}` unknown id → 404; `PUT`/`DELETE` with a stale
  `version` → 409; `PUT`/`DELETE` by a non-owner actor → 403; no bearer token on any write → 401.
- **ProviderProfileApiController**: same shape as Advertisement — positive CRUD + list; negative —
  missing `kind`, `about` over `ABOUT_RAW_MAX_LENGTH`, `categoryIds` over 10 → 400; not-found → 404;
  stale version → 409; non-owner write → 403; unauthenticated write → 401.
- **TaxonApiController**: positive — create/update with valid translations (all supported
  locales), `GET` with `?locale=uk`/`?locale=en` resolving the right translation. Negative — write
  by a non-admin/non-moderator actor → 403 (per its own Javadoc: "writes require the caller to be
  privileged... enforced inside `TaxonCatalogService`"); incomplete translations (missing a
  supported locale) → `IllegalArgumentException` from `validateTranslations()` → 400 (same
  assertion item 4 already added at the service level — this is the REST-layer confirmation of the
  same rule); not-found → 404; stale version → 409.
- **Security-chain-level tests** (no specific controller, exercises `ApiSecurityConfig` itself):
  every public `GET` (`/api/advertisements`, `/api/provider-profiles`, `/api/taxons`) succeeds with
  zero `Authorization` header; every write endpoint rejects a request with no `Authorization`
  header, and separately one with a syntactically-plausible but unresolvable bearer token, both
  with 401.

### True end-to-end scenario tests (Level 3, real Postgres, `org.ost.integrationtests.level3.restapi`)

Real `MockMvc` bound to the real `WebApplicationContext` (`MockMvcBuilders.webAppContextSetup(...)`,
real security filter chain attached) against a running context, backed by the module's existing
Testcontainers Postgres container (see item 2's decided approach). Reserved for scenarios that
genuinely need real persistence/composition across domains — not a retest of what the fast
contract tests above already cover in isolation:

1. **Happy-path pipeline** (the item's original ask): create user → issue API key → create several
   advertisements for that user → list/filter/paginate them back → verify `createdBy`/`createdAt`
   are real DB-populated values, not stubbed.
2. **Cross-user authorization, real ownership data:** user A creates an advertisement; user B (a
   second real user + real API key) attempts `PUT`/`DELETE` on it → real 403 from
   `AuthorizationService`, driven by real `created_by` ownership in the row, not a mocked check.
3. **Real optimistic-locking conflict:** create an ad, read back its `version`, issue two
   concurrent `PUT`s carrying that same stale version — one succeeds, the second gets a real 409
   from the actual row-version mismatch (`OptimisticLockingFailureException` from the real
   repository, not simulated).
4. **Provider profile pipeline:** create user → create a provider profile (one of MASTER/SHOP/
   SUPPORT) referencing real taxon category/city ids → verify `categoryNames`/`cityName` resolve
   from real taxon data, not fixture stand-ins.
5. **Taxon admin pipeline + real privilege check:** create an ADMIN-role user + key → create a
   taxon category and a city with translations → verify locale fallback end-to-end (query
   `?locale=` a locale with no translation, get the English fallback back) → attempt the same write
   with a non-admin user's key → real 403 from the real role check.
6. **Pagination/sort against real data volume:** create enough advertisements that page/size/sort
   behavior can only be meaningfully verified against a real result set (something a mocked service
   returning a canned list can't actually prove). **Expanded scope (2026-09-05, in progress):** the
   original version of this scenario only covered page/size/sort-by-title with 25 ads — user
   flagged this as insufficient, asked for the full spectrum of filter+sort+pagination requests,
   across all 4 REST-exposed domains, not just Advertisement:
   - `AdvertisementPaginationScenarioTest` (expand existing) — filter individually by `title`,
     `adKind`, `createdAtStart`/`createdAtEnd`, `updatedAtStart`/`updatedAtEnd`, `categoryIds`,
     `cityTaxonId`, plus at least one combined-filter case; sort by each of `title`/`createdAt`/
     `updatedAt`, both `asc`/`desc`; pagination edge cases (first/middle/last partial/beyond-last
     page).
   - `ProviderProfilePaginationScenarioTest` (new) — filter by `kinds`, date ranges, `categoryIds`,
     `cityTaxonId`; sort by `createdAt`/`updatedAt` (the only two sortable fields); same pagination
     edge cases.
   - `UserPaginationScenarioTest` (new) — filter by `name`, `email`, `roles`, date ranges,
     `startId`/`endId`; sort by `id`/`name`/`email`/`role`/`createdAt`/`updatedAt`; same pagination
     edge cases; requires an ADMIN-role actor (the first-registered user in a clean DB).
   - Taxon dropped from this scope: `TaxonManagementView`/`CityManagementView` call
     `listAllByType(...)` directly with no `TaxonFilterDto`, no sort, no page/size at all —
     `TaxonApiController.list()`'s filter/sort/pagination has no UI-side behavior to verify parity
     against, unlike the other three domains.

   Sort-mechanism design question raised alongside this (fold `sortField`/`sortDirection` into the
   Filter DTO itself vs. keep the separate `?sort=field,dir` query param): **resolved — keep the
   current separate `?sort=field,dir` mechanism unchanged**, no DTO/controller change needed for
   this part.
7. **Duplicate-email registration, real unique constraint:** register the same email twice via
   `POST /api/users` — asserts the real `DuplicateKeyException`-driven behavior end-to-end (ties to
   the same gap flagged above, confirms the fix once made, against the real unique index).

## 9. Media/photo upload for advertisements via REST — not currently possible

**Current state (verified):** `AdvertisementApiController`'s own class Javadoc states: "No photo
upload via this API (no in-progress gallery to commit, unlike the Vaadin form)." `AdvertisementSaveDto`
has no media field; `AdvertisementInfoDto` exposes `mediaUrl`/`mediaContentType`/`mediaCount` as
read-only output only.

**Ask:** is there a way to attach media to an advertisement via REST? If not, design one.

**Approach:** needs its own scoped design pass, not a quick add — `attachment-spring-boot-starter`'s
existing upload flow is built around Vaadin's in-progress-gallery/temp-attachment UX
(`TempAttachmentDto`). The REST equivalent would likely be a separate `multipart/form-data`
endpoint (e.g. `POST /api/advertisements/{id}/media`) delegating to `AttachmentPort`, decoupled
from the create/update JSON body.

## 10. Advertisement/Provider view-vs-card metadata parity, refactored symmetrically

**Current state (verified):** `AdvertisementInfoDto`/`ProviderProfileDto` (the shared REST+UI DTOs)
already carry `categoryNames`/`cityName`/`createdAt`/`updatedAt` (Advertisement also `createdBy`;
Provider has no `createdBy` field — matches `actorName` already shown as the card title). On the
UI side: `AdvertisementCardView` shows categories/city plus one collapsed "date" line (created vs.
updated, whichever is later); `AdvertisementViewOverlayModeHandler` (the detail overlay) shows
categories/city as chip rows plus `OverlayAdvertisementMetaPanel` (author + createdAt + updatedAt
as two separate lines). `ProviderProfileCardView` shows categories/city only — no dates anywhere.
`ProviderProfileCatalogViewModeHandler` (Provider's overlay-view equivalent) shows category/city
chips but has no equivalent of `OverlayAdvertisementMetaPanel` at all — no createdAt/updatedAt
display exists for providers today.

**Ask:** bring Advertisement and Provider Profile surfaces to the same shape. Provider tabs should
gain createdAt/updatedAt analogous to what Advertisement already shows (author/"who created" likely
skippable there since the card title already names the actor); refactor the two domains' view
classes symmetrically; update unit + Playwright tests so this metadata is actually covered, not
just visually present.

**Approach:** add a Provider-side meta panel mirroring `OverlayAdvertisementMetaPanel`
(createdAt/updatedAt only, no author line) for `ProviderProfileCatalogViewModeHandler`; confirm
with the requester the exact remaining gap on the Advertisement side (its overlay-view already
shows categories/city/author/createdAt/updatedAt — the raw request's Advertisement-side ask wasn't
fully unambiguous against what's already verified present) before changing anything there; extend
Playwright specs to assert the new Provider fields render.

## 11. Run module-doc-standards/module-readme-standards audit; find out why /sync-docs output doesn't match them — ✅ Done for `marketplace-rest-api` (2026-09-08); repo-wide rollout still open

**Current state:** the `module-doc-standards`/`module-readme-standards` skills exist and are
already referenced from `.claude/commands/sync-docs.md` (confirmed by direct read — `sync-docs.md`
explicitly cites both skills for Javadoc and module-README rules). Whether recent `/sync-docs` runs
actually followed every rule in those skills — not just the most obvious one — has not been
audited.

**Ask:** run the two doc-standards skills across the repo's READMEs and code comments, and
separately find out why `/sync-docs` runs haven't produced fully rule-compliant output where they
touched those files (or confirm it does invoke them correctly and any drift comes from edits made
outside `/sync-docs`). Work this in fixed phases, each presented for approval before executing.

**Done, scoped to `marketplace-rest-api` only (2026-09-08) — all three phases:**
- **Phase 1 — audit:** compliance pass over `marketplace-rest-api/README.md`, every Javadoc/comment
  under `marketplace-rest-api/src/main/java`, and its `pom.xml`, checked against each skill's *full*
  rule set. Found: README on the deprecated "Key classes" table shape plus stale content (missing
  admin/moderator user listing, ETag/If-Match, settings endpoints); `pom.xml` missing its mandatory
  file-level header; 3 classes (`RestApiAutoConfiguration`, `HealthController`, `SitemapController`)
  missing class-level Javadoc; 9 test classes missing class-level Javadoc.
- **Phase 2 — root cause:** README staleness is a `/sync-docs` **design gap** — the module README
  was never a diff-mode target at all (full-audit-only), so no amount of running the default mode
  could have caught it; confirmed repo-wide (7 of 8 module READMEs still on the deprecated format).
  Missing Javadoc on `HealthController`/`SitemapController` is an **execution gap** — `/sync-docs`
  was demonstrably not run after the relevant commits (`.claude/rules/marketplace-rest-api.md` went
  4 commits without a factual update). `pom.xml`'s missing header is a second **design gap** — the
  command's own Step 2 mapping table had no row for a `pom.xml`'s own header check at all, in either
  mode.
- **Fix — both the tool and the code:**
  - `.claude/commands/sync-docs.md`: added a `pom.xml` file-header row to Step 2 (closing that
    design gap); made a module's own `README.md` a real diff-mode target for **accuracy only** (not
    a full regenerate); added `--module <name>` and `--package <name>` scoped Full-Audit-Mode
    entry points, plus a general "whatever structure is named, check it exhaustively" scoping
    principle. `module-doc-standards`/`module-readme-standards` SKILL.md files each got a one-line
    pointer to `/sync-docs --module <name>` as the mechanical entry point for their existing
    "run the skill over a module" sections.
  - Verified the new rows actually work: ran `/sync-docs 37629629` (diff-mode) — the new README-
    accuracy row caught and fixed the exact staleness Phase 1 found by hand. Ran
    `/sync-docs --module marketplace-rest-api` (the new full-module mode) — fixed everything Phase 1
    found: `pom.xml` header, all 3 missing class Javadocs, all 9 missing test-class Javadocs
    (previously left as an open question — resolved in favor of the rule, no exception, once asked
    to run the full module), and `.claude/rules/marketplace-rest-api.md`'s own missing
    `concurrency`/`ETagUtil` package and new `*WriteRequest` nested records.
- **Repo-wide rollout** (the other 7 starter modules' own README "Key classes" → "Data flow"
  migration, plus a full `--module` pass on each) — still open, not scheduled.

## 12. `TaxonPort.getPageByType`/`DefaultTaxonPort` naming no longer matches Taxon's REST contract

**Current state (2026-09-05):** `GET /api/taxons` no longer takes `page`/`size` at all — confirmed
neither `TaxonManagementView`/`CityManagementView` (no `PaginationBar`, `listAllByType` loads
everything) nor any other UI has ever paged Taxon, unlike Advertisement/ProviderProfile/User (all
three use `PaginationBar`). `TaxonCatalogService.getAll(type, locale, filter, sort)` is now the
only method `TaxonApiController` calls for listing — it always returns the full matching set.
Internally, `getAll()` still calls `TaxonPort.getPageByType(type, locale, filter, 0,
Integer.MAX_VALUE, sort)` unchanged, reusing the existing paginated repository/port plumbing
(`DefaultTaxonPort` → `TaxonService.listByType` → `TaxonRepository.findAllByType`, all still take an
explicit `Pageable`) rather than touching those lower layers.

**Ask:** sort out whether `getPageByType` used this way (real page/size machinery, called with an
effectively-unbounded size) is acceptable as an implementation-reuse detail, or whether the naming
is misleading now that no real caller ever passes a bounded page — either rename it to something
accurate, or split into a dedicated unpaged method, or otherwise resolve the mismatch between the
method's name and its only remaining real use.

## 13. City becomes list-based, assignment-backed (`taxon_assignment`), symmetric across Advertisement and ProviderProfile

**Current state (2026-09-05):** `provider_profile.city_taxon_id` is a plain nullable scalar column
(not a `taxon_assignment` row), a deliberate ADR decision (`platform-commons/DECISIONS.md`) on the
grounds that "a provider has exactly one city." `advertisement`'s city, by contrast, already goes
through `taxon_assignment` (same mechanism as categories) — `AdvertisementDisplayEnrichmentService`
takes the first `TaxonType.CITY` entry found in the assigned-taxon list. Both shapes represent the
same real-world fact (exactly one city per row today) via two different mechanisms — found while
fixing a real NPE bug (`ProviderProfileDisplayEnrichmentService.enrichWithCategoriesAndCity`
crashed on a null-key map lookup for any profile with no city — the null-key lookup only exists
*because* city needed its own separate batch-resolution path distinct from the assignment-based
category lookup Advertisement already reuses for its own city).

**Decision:** unify on the assignment-based model for both domains, and expose city as a **list**
(`cityTaxonIds`/`cityNames`, mirroring `categoryIds`/`categoryNames`) rather than a single
scalar everywhere it's read or written — even though exactly one city is written in practice today
— so that scaling to more than one city later needs no schema/DTO shape change, only a UI/validation
change. Since the app has no production data yet, drop `provider_profile.city_taxon_id` by editing
the original `01-provider-profile-schema.xml` changeset directly (no new migration changeset).

**Scope (26 main-source files across 6 modules):**
- **Schema:** `provider-profile-spring-boot-starter/src/main/resources/db/provider-profile-changelog/changes/01-provider-profile-schema.xml`
  — remove the `city_taxon_id` column entirely.
- **provider-profile-spring-boot-starter:** `ProviderProfile` entity (drop field), `ProviderProfileRepository`
  (drop city from SQL/row-mapper/filter, query-time city filter becomes an id-set resolved via
  `TaxonPort` the same way `resolveCategoryFilter` already does for categories), `ProviderProfileService`
  (drop city from `buildEntity`; city assignment writing moves to `marketplace-orchestrator`'s
  `ProviderProfileSaveService`, matching how category assignment writing already lives there, not
  in this starter).
- **platform-commons:** `ProviderProfileDto`/`ProviderProfileSaveDto`/`ProviderProfileFilterDto`/
  `ProviderProfileSnapshotDto` and, symmetrically, `AdvertisementInfoDto`/`AdvertisementSaveDto`/
  `AdvertisementFilterDto`/`AdvertisementSnapshotDto` — `Long cityTaxonId`/`String cityName` →
  `Set<Long> cityTaxonIds`/`List<String> cityNames` in every one of the 8 DTOs.
- **marketplace-orchestrator:** `ProviderProfileDisplayEnrichmentService` rewritten to the same
  assignment-scan pattern `AdvertisementDisplayEnrichmentService` already uses (collecting every
  `TaxonType.CITY` entry into a list instead of a single null-prone lookup — this also removes the
  NPE's root cause entirely, no separate null-guard needed); `ProviderProfileSaveService` writes
  city via `TaxonAssignmentWriteService`, same call already used for categories;
  `AdvertisementDisplayEnrichmentService`/`AdvertisementSaveService`/`AdvertisementAuditEnrichService`
  updated from "first city found" to "every city found."
- **marketplace-rest-api:** `AdvertisementApiController`/`ProviderProfileApiController` — query
  param `cityTaxonId` → `cityTaxonIds`.
- **marketplace-app:** `AdvertisementEditDto`/`ProviderProfileEditDto` (city field becomes a set),
  both `*FormOverlayModeHandler`s (city `ComboBox` → `MultiSelectComboBox`, mirroring the existing
  category field), both `*CardView`s (city rendered as a chip list, mirroring categories),
  both `*FilterMeta`s (query-bar city filter becomes multi-select), `ProviderProfileViewModeHandler`,
  `AuditTimelineRowRenderer` (city audit-diff label/rendering for a list instead of a scalar).

**Not yet started** — large enough in scope (26 files, 6 modules) to warrant its own focused
implementation pass with tests (unit + integration + Playwright) rather than folding into an
unrelated bug-fix; pick up as its own scheduled unit of work.

## 14. `OrderByBuilder.build()` never appends a stable tiebreaker — paginated results non-deterministic on ties

**Found (real, reproduced via test flakiness, not a hypothetical):** `query-lib`'s
`OrderByBuilder.build(sort, aliasToExpression)` emits `ORDER BY` using only the caller-supplied
sort field(s), with no secondary, always-unique tiebreaker (e.g. `id`) appended. All 5 current
callers (`AdvertisementRepository`, `ProviderProfileRepository`, `TaxonRepository`,
`UserRepository`, `AuditLogRepository`) inherit this gap. Confirmed real, not theoretical:
`ProviderProfilePaginationScenarioTest.realDataVolume_pagesAndSorts` (sorting by `createdAt`, the
only sortable field ProviderProfile exposes with no unique alternative) intermittently returned a
different row at a page boundary across two runs — rows created within the same timestamp tick tie
on the sort key, and Postgres does not guarantee stable ordering for ties without an explicit
secondary key. Any real caller paging through `createdAt`/`updatedAt`-sorted results (bulk import,
high write throughput) can see the same row twice or skip one entirely across two page requests.

**Ask:** decide whether `OrderByBuilder.build()` should always append a stable tiebreaker (e.g. the
entity's own id column, via a small addition to each caller's alias map or a new parameter) and
implement it once, fixing all 5 callers together — a query-lib-level fix, not a per-repository one.

**Not yet started.**

## Related

## 15. `ProviderProfileApiController` should resolve `size` from settings too, mirroring the UI

**Found (2026-09-08):** `ProviderProfileApiController.list()` still takes a caller-supplied
`@RequestParam(defaultValue = "20") int size` — deliberately left this way in item 3 on the
assumption that ProviderProfile has no saved-settings concept for page size. That assumption is
wrong: `ProvidersView.java` (the Vaadin UI's own Providers catalog) already reuses
`UserSettingsDto::getAdsPageSize` for its own pagination (`settingsPaginationBinding.register(paginationBar,
UserSettingsDto::getAdsPageSize, this::refresh)`) — there is no separate
`providerProfilesPageSize` field even on the UI side. REST should mirror this exact behavior
instead of accepting a caller-supplied `size`.

**Approach:** `ProviderProfileApiController.list()` drops the `size` request parameter and calls
`userProfileService.resolveAdsPageSize(actorId)` — the same method `AdvertisementApiController`
already uses — instead of adding a new settings field. `ProviderProfileApiController` currently has
no `@AuthenticationPrincipal Long actorId` parameter on `list()` at all (the endpoint is public,
unauthenticated reads); needs one added (nullable, same anonymous-caller-gets-default-size pattern
`AdvertisementApiController`/`UserApiController` already use).

**Not yet started.**

## 16. `PATCH /api/users/me/settings` should use If-Match too, not a body `version` field — ✅ Done (2026-09-08)

**Implemented:** added `GET /api/users/me/settings` returning `UserSettingsDto` with an `ETag`
response header. `PATCH` now takes a dedicated `UserSettingsWriteRequest` record (adsPageSize/
usersPageSize/timelinePageSize only, no `version`/`schemaVersion`) mirroring
`AdvertisementWriteRequest`/`ProviderProfileWriteRequest`, and requires the expected version via a
mandatory `If-Match` header instead of the body (mandatory, unlike the optional If-Match on
create/update elsewhere, since a settings row always exists by the time a caller can reach this
endpoint — created eagerly at registration, unlike Advertisement/ProviderProfile/Taxon rows which
don't exist yet on create). `platform-commons`'s `UserSettingsDto` itself stays untouched (same
reasoning as items 6/15: it's shared with the Vaadin Settings UI form, which has no HTTP layer).

## 17. `ci.sh --foreground` should be wrappable by `activity-monitor.sh` exactly like the other 7 scripts — ✅ Done (2026-09-08)

**Current state:** `scripts/activity-monitor.sh` wraps 7 scripts (`deploy-and-run.sh`,
`build-and-test.sh`, `playwright.sh`, `sonar.sh`, `run-all-tests.sh`, `reset.sh`, and one
non-agentic-output-contract command) by redirecting the wrapped command's own stdout/stderr into
`/tmp/activity-monitor/<script-basename>/raw.log`, polling that file for new bytes, and parsing
`AGENTIC_SUCCESS_BLOCK`/`AGENTIC_ERROR_BLOCK` JSON marker lines the wrapped script prints directly,
itself, at each real step (`scripts/utils/agentic-output.sh`'s `emit_agentic_success_block`/
`emit_agentic_error_block`) — this is what lets `activity-monitor.sh` render the `✅/⏳/⬜/⚠️/❌`
`tree.txt` a human sees. `ci.sh` cannot use this today: `bash scripts/ci.sh` triggers an async Dagu
DAG run and its own process returns almost immediately (or, with `--foreground`, blocks on a plain
`docker exec ... dagu start ...` call that streams no per-step markers at all — only one marker at
the very end, `ci-run`) — the real per-step work (unit/integration/e2e/sonar/...) happens inside
Dagu, in its own per-step log files, never reaching `ci.sh`'s own stdout. `scripts/ci/watch-run.py`
exists to bridge this gap by polling Dagu's own REST API directly (`GET /api/v1/dag-runs/ci/<id>`)
and printing one plain line per step-status transition.

**First attempt (implemented, then reverted same day):** made `watch-run.py` write its own
`✅/⏳/⬜/⚠️/❌` `tree.txt` and its own `docker inspect`-based container-state check, independent of
`activity-monitor.sh`. Verified working against a real triggered run, but rejected on review — it
duplicates a mechanism (`tree.txt` rendering, container-state checking) that already exists,
correctly, in `scripts/activity-monitor/run.sh`'s own bash code (`render_step_line()`,
`container_state_warning()`, `STEP_IS_CONTAINER`) — two independent implementations of "how do we
draw a CI status tree" is exactly the duplication this project's own standards forbid. Reverted.

**Revised plan — reuse the one existing bash mechanism instead of a second Python one:**
1. Rename `scripts/ci/watch-run.py` → `scripts/ci/dagu-rest-run-monitor.py` (name states what it
   actually does: the one thing in this repo that knows how to poll Dagu's REST API for a run's
   step statuses — JSON-over-HTTP is a Python job, not a bash one, which is why this file stays
   separate rather than being folded into `run.sh`).
2. Its Dagu-polling loop is unchanged. Its *output* changes: instead of a plain
   `print(f"{name}: {status}")` line per transition, print the same
   `AGENTIC_SUCCESS_BLOCK: {...}` / `AGENTIC_ERROR_BLOCK: {...}` JSON-line format
   `emit_agentic_success_block`/`emit_agentic_error_block` already produce (exact shape confirmed
   from `scripts/utils/agentic-output.sh`) — this becomes its only step-reporting format. Remove
   the tree.txt-writing/container-check code added in the reverted first attempt (`render_tree`,
   `flush_tree`, `STEP_ICONS`, `TREE_DIR`/`TREE_FILE`, `container_state_warning()`,
   `STEP_CONTAINERS`, the `calendar`/`subprocess` imports they needed).
3. `scripts/ci/run.sh`'s `--foreground` branch: replace the blocking
   `docker exec "$CONTAINER" dagu start "$DAG_FILE" -- ...` call with triggering in the background
   (`docker exec -d ...`) followed by `python3 -u scripts/ci/dagu-rest-run-monitor.py` — its exit
   code becomes `run.sh`'s own real exit code. `run.sh`'s own stdout now carries real per-step
   markers, the same way `deploy-and-run.sh`'s own stdout already does.
4. `scripts/activity-monitor/run.sh` (the one existing bash mechanism, not a new one): add
   `STEP_LABELS`/`STEP_DESCRIPTIONS` entries for `ci.sh:unit`/`integration`/`e2e`/`sonar`/
   `archunit_metrics`/`pipeline_metrics`/`docs`; add `STEP_IS_CONTAINER` entries for the steps
   backed by one well-known container (`ci.sh:unit`→`advertisement-build-only-unit`,
   `ci.sh:integration`→`advertisement-build-only-integration`, `ci.sh:e2e`→`ci-marketplace-app`,
   `ci.sh:archunit_metrics`→`advertisement-build-only-archunit` — real names from
   `scripts/ci/dagu/ci.yaml`'s own `BUILD_CONTAINER_NAME`/`APP_CONTAINER` overrides; `sonar`
   deliberately excluded, multi-container stack, doesn't fit the single-container check); extend
   `SCRIPT_STEP_SEQUENCE["ci.sh"]` with the new step ids after `start-ci-runner`.
5. Update every reference to the old `watch-run.py` name: `.claude/rules/scripts.md`'s "Local CI
   Runner" section, `scripts/ci/README.md`, this item.

**Implemented, all 5 steps above, plus one design gap found and fixed along the way:**
`unit`/`integration`/`e2e`/`sonar`/`archunit_metrics` genuinely run in parallel in Dagu (all depend
only on `build`, not on each other) — a plain `SCRIPT_STEP_SEQUENCE` entry would only ever show the
*first* of the five as "running" and the rest as "pending" until it finished, misrepresenting real
concurrent progress. Fixed by teaching `render_tree()` itself a new `SCRIPT_STEP_PARALLEL_GROUPS`
concept: a declared group of step ids rendered as one unit — every still-incomplete member shows
`⏳ running` simultaneously, the group only counts as done (and the sequence advances) once every
member has completed. Verified directly (not assumed) via a standalone harness sourcing
`scripts/activity-monitor/run.sh` and calling `render_tree()` with simulated `STEP_COMPLETED_AT`
state for three scenarios: group not started (all 5 show running), group mid-flight (only the
still-running members show running, finished ones show their real duration), group fully done
(sequence correctly advances to `pipeline_metrics`).

**Result:** `bash scripts/activity-monitor.sh -- bash scripts/ci.sh --foreground` works identically
to `bash scripts/activity-monitor.sh -- bash scripts/deploy-and-run.sh` — one command, one
mechanism, real per-step `tree.txt` (including genuine parallel-step rendering), no duplicate
tree-drawing code. `bash scripts/ci.sh` (default, no `--foreground`) and
`bash scripts/ci.sh --foreground` run directly (unwrapped) both keep working exactly as before —
this only changed what `--foreground` prints while it blocks, it didn't remove either invocation
path. `scripts/activity-monitor/README.md`'s own usage examples updated to include `ci.sh` as an
8th covered script (was previously, correctly at the time, excluded).

## 18. SonarQube quality gate is persistently failing, not a one-off — needs a real fix, not just `improvement-114`

**Found (2026-09-08, while investigating this session's own CI run's `sonar` step failure):**
Checked real scan history via SonarQube's API (`GET /api/project_analyses/search?project=advertisement`)
instead of assuming — the quality gate has failed repeatedly, confirmed by real `QUALITY_GATE`
events on **2026-08-17** ("New Issues > 0") and **2026-08-29** ("New Issues > 0, Coverage on New
Code < 80"), and again on today's run. Scans themselves run frequently (roughly every 1-4 days:
08-14, 08-15, 08-17, 08-18, 08-20, 08-21, 08-26, 08-28, 08-29, 09-02, 09-03, 09-04, 09-08) — an
earlier claim in this same conversation that Sonar "hadn't run in 3+ weeks" was wrong, based on
misreading the quality gate's `period.date` field (the New Code Period baseline, i.e. "previous
version" reference point fixed at 2026-08-14) as if it were the last scan date. Corrected here so
the record is accurate.

**Root cause — structural, not incidental:** `new_coverage` reads **0%** against an ≥80% threshold
on every single scan (`improvement-114` — JaCoCo never wired into the scan) — this condition
mechanically cannot pass regardless of what code changes, so the gate is effectively guaranteed to
fail on every run until JaCoCo is actually wired in. `new_violations` (currently 10-18 real
findings, `java:S5663`/`S7467`/`S1192`/`S1450`/`S8491` across `marketplace-rest-api`/
`marketplace-app`/`apikey-spring-boot-starter`) is a secondary, independent failing condition — even
a `new_violations = 0` run would still fail the gate on `new_coverage` alone.

**Ask:** treat this as a real fix, not just the existing `improvement-114` coverage-wiring item in
isolation — `improvement-114` already covers the `new_coverage`/JaCoCo half; this item's scope is
the other half (`new_violations`, the 10-18 real findings) plus confirming, once both are addressed,
that the gate can actually reach a passing state at least once (not yet observed in the real scan
history checked above).

**Not yet started.**

- [improvement-073](../completed/issues/improvement-073-rest-endpoint-infrastructure-test-seeding.md) —
  REST API infrastructure (API-key auth, Swagger, apikey/rest-api modules) this whole batch follows
  up on.
- improvement-182 (REST API filter/sort/pagination parity with UI) — same family as items 1, 3, 6.
- improvement-111 (service-boundary authorization in marketplace-orchestrator) — the authorization
  seam item 1's role check should plug into.
- `taxon-spring-boot-starter/DECISIONS.md` — existing locale-fallback-to-`defaultLocale` decision
  relevant to item 4.
- `attachment-spring-boot-starter/DECISIONS.md` — existing upload/storage design relevant to item 9.
