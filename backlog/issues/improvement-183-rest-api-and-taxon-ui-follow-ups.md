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

## 6. REST `version`/id fields must be server-managed, not caller-writable

**Found (real bug, confirmed via code, not just design smell):** `AdvertisementApiController.create()`/
`ProviderProfileApiController.create()` passed the raw client-supplied `AdvertisementSaveDto`/
`ProviderProfileSaveDto` straight into `AdvertisementSaveService.save()`/`ProviderProfileSaveService.save()`,
which decide create-vs-update purely via `dto.id() == null`. A `POST` (semantically "create") body
that happened to include a non-null `id` silently became an `UPDATE` of that existing row instead
(blocked only by ownership/authorization, not by "this is a create" semantics) — a real REST
contract violation, not just a documentation gap.

**Approach (decided 2026-09-05, not yet implemented) — full If-Match/ETag redesign (chosen over a
Swagger-only annotation fix), applied consistently across all three REST-exposed domains:**
- `AdvertisementApiController`/`ProviderProfileApiController` gain dedicated nested request records
  (`AdvertisementCreateRequest`/`AdvertisementUpdateRequest`, `ProviderProfileCreateRequest`/
  `ProviderProfileUpdateRequest`) with **no `id` or `version` field at all** — mirroring the pattern
  `TaxonApiController.TaxonCreateRequest`/`TaxonUpdateRequest` already established; the controller
  builds the actual `AdvertisementSaveDto`/`ProviderProfileSaveDto` passed to the orchestrator
  service itself (`id` from the path variable on update, `null` on create; `version` from the
  `If-Match` header, `null` on create).
- `TaxonApiController.TaxonUpdateRequest` loses its own `version` field for the same reason.
- Every `GET .../{id}` response gains an `ETag` header carrying the resource's current version.
- Every `PUT`/`DELETE` reads the expected version from an `If-Match` request header instead of a
  body field (`PUT`) or `?version=` query param (`DELETE`) — same nullable-version semantics as
  before (an absent `If-Match` behaves exactly like the previously-absent body/query value did),
  just relocated to where HTTP already has a standard mechanism for this exact concern.
- `platform-commons`'s `AdvertisementSaveDto`/`ProviderProfileSaveDto` themselves are unchanged
  (still carry `id`/`version` fields) — the Vaadin UI's own form-save path uses these same DTOs
  directly with no HTTP layer involved, so those fields stay needed there; only the REST-facing
  request shape changed.
- **Follow-up caught during review:** the read DTOs (`AdvertisementInfoDto`, `ProviderProfileDto`,
  `TaxonDto`) still carried `version` as a plain JSON field, so a REST response duplicated the same
  fact in two places at once (the field and the new `ETag` header) — exactly the kind of
  one-fact-two-homes problem this whole change was meant to clean up. Fixed: `version` on all three
  gets `@Getter(onMethod_ = @JsonIgnore)` — excluded from JSON output (`ETag` is now the sole
  wire-visible source), while the plain Java getter stays intact for the Vaadin UI's own direct
  field access (`ad.getVersion()`, used for delete/update calls in ~10 UI call sites, confirmed via
  grep — none of them serialize these DTOs to JSON, so nothing breaks).

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

## 11. Run module-doc-standards/module-readme-standards audit; find out why /sync-docs output doesn't match them

**Current state:** the `module-doc-standards`/`module-readme-standards` skills exist and are
already referenced from `.claude/commands/sync-docs.md` (confirmed by direct read — `sync-docs.md`
explicitly cites both skills for Javadoc and module-README rules). Whether recent `/sync-docs` runs
actually followed every rule in those skills — not just the most obvious one — has not been
audited.

**Ask:** run the two doc-standards skills across the repo's READMEs and code comments, and
separately find out why `/sync-docs` runs haven't produced fully rule-compliant output where they
touched those files (or confirm it does invoke them correctly and any drift comes from edits made
outside `/sync-docs`). Work this in fixed phases, each presented for approval before executing.

**Approach:**
- **Phase 1 — audit:** systematic compliance pass over a representative sample of module
  `README.md`/Javadoc, checked against each skill's *full* rule set (per `.claude/rules.md`'s
  "Apply the full standard, not just the most obvious rule in it"), not just the most obvious rule.
- **Phase 2 — root cause:** determine, for each drift found, whether the skill wasn't invoked, was
  invoked but a rule was missed, or the file was edited outside `/sync-docs` entirely.
- **Phase 3 — fix:** address identified gaps module-by-module, each step presented for approval
  before executing.

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

- [improvement-073](../completed/issues/improvement-073-rest-endpoint-infrastructure-test-seeding.md) —
  REST API infrastructure (API-key auth, Swagger, apikey/rest-api modules) this whole batch follows
  up on.
- improvement-182 (REST API filter/sort/pagination parity with UI) — same family as items 1, 3, 6.
- improvement-111 (service-boundary authorization in marketplace-orchestrator) — the authorization
  seam item 1's role check should plug into.
- `taxon-spring-boot-starter/DECISIONS.md` — existing locale-fallback-to-`defaultLocale` decision
  relevant to item 4.
- `attachment-spring-boot-starter/DECISIONS.md` — existing upload/storage design relevant to item 9.
