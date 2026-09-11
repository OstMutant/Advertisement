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

## 5. Align taxon list styles — category indentation vs. flat city list — ✅ Done (2026-09-09)

**Current state (verified):** neither `TaxonManagementView.java` nor `CityManagementView.java`
(183 lines each) contains any indentation/level/depth rendering logic — there is no category
hierarchy anywhere (confirmed via the `taxon`/`taxon_translation` Liquibase schema, no `parent_id`
or equivalent column at all). Both tabs render an equally flat list of cards.

**Real root cause found (not seed data):** `taxon-view.css` defines `.taxon-management-view {
width: 100%; padding: 1rem; box-sizing: border-box; }`, giving the Categories tab's root `Div`
(which carries that class) a 1rem padding on every side — top gap from the tab underline, left/right
gaps from the viewport edge. `CityManagementView.init()` puts the class `city-management-view` on
its own root `Div` instead, and **no CSS rule for `.city-management-view` existed at all** — so the
Cities tab rendered with zero padding, flush against the tab line and the left edge. Confirmed
visually via real Playwright screenshots (`taxon-02-two-categories-in-list` vs.
`city-01-two-cities-in-list`, both from a real CI e2e run) before fixing.

**Fix:** `marketplace-app/src/main/frontend/themes/my-app/taxon-view.css` — extended the existing
selector to `.taxon-management-view, .city-management-view { ... }`, one line, no other changes
needed (`.taxon-list-container`/`.taxon-row-wrapper`/etc. were already shared by both tabs).

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

### Decisions — Provider catalog card, `ProviderProfileCardView` (2026-09-10)

Advertisement side confirmed out of scope — its card and overlay already show the full metadata
set; item 10 is Provider-only. Card-level decisions, fixed:

1. **Add a meta panel to the Provider card** — a date line only, **no author line** (the card
   title already is `actorName`).
2. **Show createdAt/updatedAt** on the Provider card using the same collapsed rule
   `AdvertisementCardView` uses: one line, `Created …` when `updatedAt == createdAt`, otherwise
   `Updated …`.
3. **Move the kind badge below the categories/city lines** so the Provider card's element order
   matches `AdvertisementCardView` (title → about → spacer → categories → city → kind badge →
   bottom row).
4. Nothing else changes on either card.

### Decisions — detail-view category/city labels + Provider catalog-view missing city (2026-09-10)

Separate from the meta-panel (createdAt/updatedAt) work above — this batch is category/city
label parity between the cards and the detail overlays, plus one real bug it surfaced.

**Bug — city never renders in `ProviderProfileCatalogViewModeHandler`.** `buildPrimaryContent()`
pulls taxons via `taxonLookupService.getForEntity(EntityType.PROVIDER_PROFILE, id, locale)` (reads
`taxon_assignment` rows) then filters `TaxonType.CITY`. A provider profile's city is the scalar
`provider_profile.city_taxon_id` column, **not** a `taxon_assignment` row (categories are
assignments, city is not — the same asymmetry item 13 exists to remove wholesale), so the CITY
filter is always empty and the city row never appears. `ProviderProfileDto.cityName` is already
correctly enriched (`ProviderProfileDisplayEnrichmentService`, via `taxonLookupService.findById`)
and reaches this handler on both open paths (card click + deep-link, both `enrichSingle`), just
unused for the city row. `ProviderProfileViewModeHandler` (account-tab) already renders city the
right way — from `profile.getCityName()`. Targeted fix here, no schema change; full assignment-based
unification stays item 13.

**Plan — 3 Java files + 1 CSS rule:**

1. `AdvertisementViewOverlayModeHandler.buildChipRow(...)` — add a visible label as the row's first
   child: `Span label = new Span(ariaLabel + ":"); label.addClassName("overlay-chips-label");
   row.add(label);`. `ariaLabel` is the already-resolved value of
   `ADVERTISEMENT_OVERLAY_FIELD_CATEGORIES`/`_CITY`; the `":"` suffix in code matches the card's
   `Categories:` style — no `.properties` change. `role="list"` + `aria-label` on the row unchanged.
2. `ProviderProfileCatalogViewModeHandler` — same visible label in its `buildChipRow(...)`; **plus**
   drop the `buildChipRow(textCard, taxons, TaxonType.CITY, …)` call and instead render the city
   row from `params.getProfile().getCityName()` when non-null (small dedicated method, mirroring
   `ProviderProfileViewModeHandler.buildProfileCard`). Categories stay on the `getForEntity` path
   (richer `TaxonDto`, carries `isDeleted()`).
3. `ProviderProfileViewModeHandler.buildChipRow(...)` — same visible label (city already renders
   correctly here).
4. CSS — one rule `.overlay-chips-label { font-size: 0.78rem; font-weight: 500; color:
   var(--app-text-muted); align-self: center; }`. `advertisement-card.css` and
   `provider-profile-overlay.css` both carry the chip-row selectors and both are `@import`ed from
   `styles.css`; put the rule in whichever is the natural shared home (likely a shared/global
   block) so both overlays pick it up.

Empty taxon list — `buildChipRow` still returns early, so no bare label is shown.

### Implemented — the decided parts (2026-09-10)

Both decision blocks above are now in code; the still-open questions below are untouched.

- **Provider card** (`ProviderProfileCardView`) — `createMetaLine(profile)` added: one `Span`
  `.provider-profile-card-meta`, `Created:`/`Updated:` + `TimeZoneUtil.formatInstantHuman`, same
  collapsed rule as `AdvertisementCardView`, **no author**. Bottom row is now
  `HorizontalLayout(meta, actions)` with `Alignment.END` + `JustifyContentMode.BETWEEN` (was
  actions-only, `END`). Kind badge moved below the categories/city lines.
- **Detail-view labels** — `AdvertisementViewOverlayModeHandler`,
  `ProviderProfileCatalogViewModeHandler`, `ProviderProfileViewModeHandler`: each `buildChipRow`
  prepends a `Span` `.overlay-chips-label` = `ariaLabel + ":"`.
- **City bug fix** — `ProviderProfileCatalogViewModeHandler` no longer asks `getForEntity` for
  `TaxonType.CITY`; a new `buildCityRow(textCard, cityName)` renders the city chip from the
  already-enriched `profile.getCityName()`. Categories still use `getForEntity` (richer `TaxonDto`).
- **i18n** — new `providers.card.created` / `providers.card.updated` (`PROVIDERS_CARD_CREATED` /
  `_UPDATED`) in `I18nKey` + `messages_{en,uk}.properties`. Detail-view labels reuse the existing
  `*_OVERLAY_FIELD_CATEGORIES`/`_CITY` keys, ":" appended in code.
- **CSS** — one `.overlay-chips-label` rule in `styles.css` (shared by both overlay domains).

**Verified (2026-09-10)** — `deploy-and-run.sh --reset-only-db` then `playwright.sh e2e --ux`:
48 passed, 13 skipped, 0 failed. Screenshots confirm each decided change:
- `provider-catalog-list` — meta line reads `Created:` / `Updated:` + date, **no author**; kind
  badge sits below the categories/city lines.
- `provider-catalog-deep-link-opened` — `Categories:` / `City:` label prefixes present; the
  **`City: Lviv` row now renders** (was absent before — the scalar-vs-assignment bug).
- `provider-profile-view-after-create` — same `Categories:` / `City:` label prefixes on the
  account-tab view.
- `adv-deep-link-opened` — `Categories:` label prefix on the Advertisement detail overlay.
- `trunc-card-collapsed` — Advertisement card unchanged (author + one date, badge above meta).

### Decision — cards render categories/city as chips like the detail views (2026-09-10, recorded, not started)

On the cards (`AdvertisementCardView`, `ProviderProfileCardView`) categories/city are plain text
lines (`Categories: cat1, cat2` / `City: Lviv`). Make them chip rows identical to the detail
views' pills, and keep the vertical rhythm tight (no big gaps).

- `AdvertisementCardView.createCategoriesLine` / `createCityLine` — stop calling
  `createInfoLine(...)`; build a chip row instead: a `Span.overlay-chips-label` prefix
  (`Categories:` / `City:`) + one `Span.advertisement-category-chip` / `.advertisement-city-chip`
  per name (from `getCategoryNames()` / `getCityName()` — plain strings, no `TaxonDto`, so no
  `--deleted` state). `createInfoLine` becomes unused → remove it. Return type `Span` → `Div`;
  update the call sites in `createContent`.
- `ProviderProfileCardView.createCategoriesLine` / `createCityLine` — same, chips
  `.provider-profile-category-chip` / `.provider-profile-city-chip`; `createInfoLine` removed.
- `advertisement-card.css` — remove the now-unused `.advertisement-categories` / `.advertisement-city`
  text rules; add `.advertisement-card-chip-row { display:flex; flex-wrap:wrap; align-items:center;
  gap:6px; }` with **no `margin-top`** (the existing `.advertisement-content { gap:6px }` already
  spaces every child). Chip and label classes reused as-is.
- `provider-profile-card.css` — replace the text `.provider-profile-card-categories,
  .provider-profile-card-city` rules with a `.provider-profile-card-chip-row` container of the
  same shape; `.provider-profile-card-content { gap:6px }` gives the uniform rhythm.
- Vertical spacing: every card child (title → about → categories → city → kind badge → meta)
  separated by the container's single `gap:6px`; chips `gap:6px` internally; the meta line stays
  pinned to the bottom via its existing `margin-top:auto`. No stacked `margin-top:8px`.

### Decision — kind badge to the bottom in the Provider detail views (2026-09-10, recorded, not started)

Flagged from the `provider-profile-view-after-create` screenshot (the `MASTER` badge sits at the
top, right under the card header). Move the `MASTER`/`SHOP`/`SUPPORT` kind badge from the top to
the **bottom** — after the categories/city rows — in both Provider detail views, symmetric with
the card change already done (item 3 of the card-decisions block):
- `ProviderProfileViewModeHandler.buildProfileCard` — `new Div(cardHeader, about)`, then the
  category/city chip rows, then `card.add(kindBadge)` last (currently `new Div(cardHeader,
  kindBadge, about)`).
- `ProviderProfileCatalogViewModeHandler.buildPrimaryContent` — same move: `new Div(cardHeader,
  about)`, chip rows, then `textCard.add(kindBadge)` last.

### Decision — per-field presence/absence + placement test coverage (2026-09-10, recorded, not started)

Once the UI decisions above are implemented, extend the Playwright specs so every field on each
card/detail surface is verified for **both** its rendering position **and** — for optional fields
— its absence:

- **Surfaces:** `AdvertisementCardView`, `AdvertisementViewOverlayModeHandler`,
  `ProviderProfileCardView`, `ProviderProfileCatalogViewModeHandler`, `ProviderProfileViewModeHandler`.
- **Optional fields** — assert absence (element not in the DOM at all when the value is empty)
  **and** placement (correct vertical order vs the sibling fields when present):
  - Advertisement: description, categories, city, media/thumbnail.
  - Provider: categories, city (about has an empty-state fallback, so it's effectively required —
    assert placement only).
- **Required fields** — assert placement (vertical order) only:
  - Advertisement: title, ad-kind badge, meta line (author + created/updated date).
  - Provider: title/actor name, kind badge, meta line (created/updated date).
- **Placement = actual order**, not "each is visible somewhere": read the ordered list of the
  content container's child class names (or use `locator(...).nth(i)`) and assert the exact
  sequence (e.g. Provider card: title → about → categories → city → kind badge → meta; each
  surface's own expected order spelled out in the spec).
- **Fixtures needed:** a provider profile with categories+city, one with no categories, one with
  no city; the same three shapes for advertisements. Add via `test.step` inside the existing
  create/edit flows where possible (per `.claude/rules/playwright.md`'s "extend an existing test"
  rule); a dedicated `test(...)` only for the "empty" shapes that need their own setup.
- Specs to touch: `04-provider-profile-flow.spec.js`, `05-marketplace-advertisement-flow.spec.js`.

### Resolutions (2026-09-10) — the last three open questions, now decided

1. **createdAt/updatedAt on the Provider detail views — YES, add to both.**
   `ProviderProfileCatalogViewModeHandler` (catalog overlay) and `ProviderProfileViewModeHandler`
   (account-tab) both gain a created/updated line, date only, no author (same as the card). Tests
   cover its presence + placement.
2. **Shared meta-panel component — YES, extract one.** Keeping `AdvertisementCardMetaPanel` +
   `OverlayAdvertisementMetaPanel` + Provider's inline `createMetaLine` + the new Provider
   detail-view meta as separate implementations is duplication. Extract a single shared
   `EntityMetaPanel` (`ui/views/components/`), `Configurable` prototype bean, with:
   `authorName`/`authorEmail` nullable (Advertisement passes them, Provider passes null → the
   author span is omitted) and a variant for card (compact `Span`, one collapsed date line) vs
   overlay (`HorizontalLayout`, `Created:` + `Updated:` when edited). All four call sites migrate
   to it; the two `Advertisement*MetaPanel` classes are deleted.
3. **Item 13 — NOT bundled (assessed 2026-09-10).** Item 13 is genuinely 26 files / 6 modules:
   a schema change (drop `city_taxon_id`, needs a `--reset` deploy), 8 public `platform-commons`
   DTOs (`*InfoDto`/`SaveDto`/`FilterDto`/`SnapshotDto` for both domains), the
   `provider-profile-spring-boot-starter` entity/repo/service, four `marketplace-orchestrator`
   enrichment/save/audit services, two REST controllers, and a wide `marketplace-app` sweep
   (`MultiSelectComboBox`, `*FilterMeta`, `AuditTimelineRowRenderer`), plus a unit+integration+e2e
   matrix. Folding that into item 10 turns a contained `marketplace-app` visual pass into a
   cross-contract refactor — rejected. **Instead:** item 10 builds the city chip row and the
   meta panel to take a `List<String>` from the start (fed `List.of(cityName)` today), so when
   item 13 flips the DTO the view code just passes `getCityNames()` — a ~5-line delta per surface,
   no rework of the rendering machinery.

**Item 10 remaining work — all decided, ready to implement (2026-09-10):**
- **A. Shared `EntityMetaPanel`** (`ui/views/components/`, `Configurable` prototype): params
  `authorName`/`authorEmail` (nullable → author span omitted when null), `createdAt`, `updatedAt`,
  `variant` (CARD = compact `Span`, one collapsed date; OVERLAY = `HorizontalLayout`, `Created:` +
  `Updated:` when edited). Replaces `AdvertisementCardMetaPanel` + `OverlayAdvertisementMetaPanel`
  (both deleted) + Provider's inline `createMetaLine`; also added to the two Provider detail views.
- **B. Card categories/city → chip rows** (per the card-chips decision block above).
- **C. Kind badge → bottom in the two Provider detail views** (per the badge-position block above).
- **D. createdAt/updatedAt on both Provider detail views** — `EntityMetaPanel` OVERLAY variant,
  no author, after the kind badge.
- **E. Tests** — per the per-field presence/absence + placement block above, plus the new
  Provider detail-view meta line.
- **F. Verify** — compile → `deploy-and-run.sh --reset-only-db` → `playwright.sh e2e --ux` →
  screenshots.

### ✅ Implemented in full + verified (2026-09-10)

**A-D done:**
- `EntityMetaPanel` (`ui/views/components/`) — one `Configurable` prototype, CARD (compact `Div`,
  one collapsed date) / OVERLAY (`Created:` + `Updated:` when edited) variants, author span
  omitted when `authorName == null`. New shared i18n keys `ENTITY_META_AUTHOR/CREATED/UPDATED`.
  All five call sites migrated (`AdvertisementCardView`, `AdvertisementViewOverlayModeHandler`,
  `AdvertisementFormOverlayModeHandler`, `ProviderProfileCardView`, plus the two Provider detail
  handlers now show it too). `AdvertisementCardMetaPanel` + `OverlayAdvertisementMetaPanel`
  deleted, their factory bean + 7 now-dead i18n keys removed.
- Card categories/city render as chip rows (`.advertisement-card-chip-row` /
  `.provider-profile-card-chip-row`, `aria-label` = `Categories:` / `City:`), reusing the
  detail-view chip pills; `createInfoLine` removed from both card views. City chip row is fed a
  `List<String>` — ready for item 13's `cityNames` with a one-line delta.
- Kind badge moved below the categories/city rows in both Provider detail views.
- Both Provider detail views gained the `EntityMetaPanel` (OVERLAY, no author) after the badge.

**Spacing pass** (flagged from a screenshot — the gap after the kind badge looked bigger than the
others): removed `margin-bottom: 4px` from `.advertisement-ad-kind-badge` /
`.provider-profile-kind-badge` and `margin-top: 8px` from the four overlay chip-row classes — those
fought the containers' flex `gap` (6px on cards, 12px on overlays). Vertical rhythm is now uniform.
CSS: `.entity-meta*` + `.overlay-chips-label` live in `styles.css` (shared); the dead
`.advertisement-meta*`, `.advertisement-categories`/`-city` text rules and
`.overlay__meta-container` block removed.

**E (tests) done:**
- `_helpers.js` — new `assertAbsent(expect, container, selector)` and
  `assertVerticalOrder(page, expect, container, selectors[], screenshotName)` (bounding-box
  top-to-bottom order; missing selectors skipped).
- `04-provider-profile-flow.spec.js` — field-order steps added to `userEn creates provider
  profile` (account view) and the anonymous-catalog test (catalog card + catalog overlay); new
  `moderatorEn creates a minimal provider profile` test (no category / no city → both chip rows
  `assertAbsent`, reduced order header→about→badge→meta, then deleted so it leaves no state).
- `05-marketplace-advertisement-flow.spec.js` — field-order steps added to `userEn creates
  advertisement` (card + overlay); new `userEn creates a minimal advertisement` test (no
  category / no city → `assertAbsent`, reduced order, then deleted).
- Broken helpers fixed: `category.flow.js:assertCardHasCategories` and `city.flow.js:assertCardHasCity`
  now target the chip rows, not the removed `.advertisement-categories`/`.advertisement-city` text.

**F verified:** `bash scripts/build-and-test.sh --no-unit --no-integration --skip-vaadin` →
`BUILD SUCCESS`. `deploy-and-run.sh --reset-only-db` → `playwright.sh e2e --ux`: **50 passed,
0 failed**. Screenshots confirm chips on all card/detail surfaces, `Categories:`/`City:` labels,
kind badge below categories/city everywhere, `EntityMetaPanel` on every surface, uniform vertical
spacing, and the optional-field-absent layout.

**Follow-up fix (2026-09-10):** the commit above failed the Sonar `new_duplicated_lines_density`
gate (6.28% > 3%) — CPD flagged the four copy-pasted `buildMetaPanel(...)` methods (2 Advertisement
+ 2 Provider handlers) and the near-identical private `chipRow(...)` in the two card views. Fixed:
`EntityMetaPanel.Parameters` gained static factories `card(...)` / `card(created, updated)` /
`overlay(...)` / `overlay(created, updated)` (author overloads for Advertisement, no-author for
Provider), collapsing every `buildMetaPanel` to a one-line call at the use site; the card chip row
moved to a new `ui/views/utils/ChipRowUtil.labelled(label, names, rowCss, chipCss)`. Also tightened
two comments to the "one line or none" rule (the 2-line city-scalar note, `EntityMetaPanel`'s
3-sentence class Javadoc). Verified via full `ci.sh` — all stages green, **QUALITY GATE STATUS:
PASSED**.

**Still open — not yet decided:** shared-vs-separate is resolved (extracted); item 13 sequencing
(kept separate, item 10's chip/meta code is list-shaped for a cheap later delta).

## 11. Run module-doc-standards/module-readme-standards audit; find out why /sync-docs output doesn't match them — ✅ Done, all 8 modules (2026-09-09)

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
- **Repo-wide rollout — done (2026-09-09), 6 parallel agents, one per module** (`apikey-spring-boot-
  starter` was already compliant, needed none): `advertisement-` (4 classes), `user-` (15 classes),
  `taxon-` (14 classes), `audit-` (7, incl. 2 nested records + one 4-paragraph Javadoc trimmed to
  one line), `attachment-` (18, incl. 3 nested records), `provider-profile-spring-boot-starter` (3
  classes) — every missing class-level Javadoc added, every `pom.xml` got its mandatory header,
  every `README.md` regenerated to the current `What it provides`/`Data flow`/`Dependencies` shape.
  Each module's README also went through the skill's required independent fresh-context review
  pass, which caught and fixed real Javadoc-restated-in-README duplications and factual errors in
  several modules (worst: `user-spring-boot-starter`'s README wrongly claimed `user_preferences`
  rows are created lazily — they're created unconditionally at registration).

**Cross-cutting findings surfaced along the way, out of this item's own scope (Java/pom.xml/README
only) — proposed for `improvement-133`'s deferred-findings bucket, not yet added:**
- `.claude/rules/{advertisement,audit,user,attachment}-spring-boot-starter.md` all still point
  readers at their README's old "Key classes" table, now gone after the regenerate — stale
  cross-references in 4 files.
- 2 Liquibase changelogs missing their mandatory file-level header: `taxon-changelog/master.xml`,
  `audit-changelog-master.xml` (+ `audit-spring-boot-starter/changes/01-audit-schema.xml`).
- `provider-profile-spring-boot-starter`'s Liquibase `remarks=` on `provider_profile` states
  category assignments are written by this starter's own service — factually wrong, actually
  written by `marketplace-orchestrator`'s `TaxonAssignmentWriteService`.
- `attachment-spring-boot-starter/pom.xml` declares `query-lib` and `jackson-datatype-jsr310` as
  dependencies with zero references anywhere in that module's source — likely dead.
- Trimmed rationale with no existing `DECISIONS.md` entry to route to: DB-commit-before-S3-delete
  ordering and array-bind-vs-`IN(:list)` in `attachment-spring-boot-starter`'s `AttachmentRepository`/
  `AttachmentCleanupService` (comments trimmed per the ticket-number ban, original rationale not
  yet preserved anywhere).

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

## 14. `OrderByBuilder.build()` never appends a stable tiebreaker — paginated results non-deterministic on ties — ✅ Done (2026-09-09)

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

**Decided design (2026-09-09), after iterating through several rejected alternatives** (a
generic-tiebreaker overload applied to every field regardless of type — rejected as broader than
needed initially; a per-repository hardcoded string check for date fields, duplicated 5x —
rejected as duplication; concatenating the tiebreaker directly into an alias-map string value —
rejected, a real bug: applies the sort direction only to the last column in a comma list, silently
breaking `DESC` sorts; reflection-based `Instant`-type detection — rejected, adds complexity and
breaks from this codebase's established `Fields.*`-constant convention):

- New `query-lib` type `org.ost.query.sort.SortField` — a record
  `(String property, String expression, Sort.Direction direction, List<SortField> tiebreakers)`,
  recursive by design so a tiebreaker can itself carry its own tiebreakers if ever needed (no
  concrete case for that today — deliberate flexibility, not filling an existing need). Three
  static factories: `of(property, expression)` (leaf, no tiebreakers); `of(property, expression,
  SortField... tiebreakers)` (tiebreakers with the default `DESC` direction); `of(property,
  expression, Sort.Direction direction, SortField... tiebreakers)` (explicit direction override).
  `direction` is only read when the `SortField` is acting as a nested tiebreaker (via
  `directionOrDefault()`) — ignored when it's a top-level entry matched against the caller's own
  `Sort.Order`, whose direction always wins there instead.
- New `OrderByBuilder.build(Sort sort, List<SortField> fields)` overload (existing `build(Sort,
  Map<String,String>)` untouched, still used wherever a plain repository has no tiebreaker need).
  Builds one `List<String>` of clauses and joins once at the end (no string-concatenation
  chaining). Recursively walks each matched field's `tiebreakers()`, skipping any tiebreaker whose
  `property` is already present anywhere in the caller's requested `Sort` (duplicate-avoidance,
  checked by property name, not by SQL-expression string).
- All 5 repositories switch their `Map.ofEntries(...)` sort-alias literal to a `private static
  final List<SortField> SORT_FIELDS` — only `createdAt`/`updatedAt` entries carry a tiebreaker
  (`SortField.of(Fields.id, "<alias>.id")`), matching this issue's original `createdAt`/`updatedAt`
  scope; other fields (`title`, `name`, `kind`, etc.) stay tiebreaker-free leaves.
- **Refined further (still 2026-09-09):** `AuditLogRepository`'s hand-written `if
  (orderBy.isBlank()) orderBy = " ORDER BY al.created_at DESC"` fallback string is gone entirely —
  `OrderByBuilder.build(Sort, List<SortField>)` itself now falls back to the **first field in the
  list**, using that field's own `direction`, whenever the caller's `Sort` is empty **and** that
  first field declares an explicit `direction`. Safe for the other 4 repositories: their top-level
  `SortField` entries never set `direction` (always the 2-arg `of(property, expression)` factory),
  so `direction == null` and the fallback never activates for them — confirmed empty `Sort` is a
  real, reachable case there too (`SortQueryParser.parse(null, ...)` in `marketplace-rest-api`
  returns `Sort.unsorted()` whenever a caller omits `?sort=`), so changing this only for the one
  repository that opts in (via an explicit `direction`) avoids silently changing pagination order
  for the other 3 REST-exposed domains. `AuditLogRepository.SORT_FIELDS` is now the single
  declarative source of both its normal sort and its default: `SortField.of(Fields.createdAt,
  "al.created_at", Sort.Direction.DESC, SortField.of(Fields.snapshotId, "al.id"))`.

**Implemented and verified (2026-09-09):**
- `OrderByBuilderTest` (`query-lib`) — 14 tests (11 original + 3 for the empty-sort default
  fallback), all passing, run via `bash scripts/build-and-test.sh --unit --no-integration
  --skip-vaadin` (0 failures, 0 errors).
- Full reactor compile (all 5 changed repositories, via the same `build-and-test.sh` run's `-am`)
  — `BUILD SUCCESS`.
- `ProviderProfilePaginationScenarioTest.sortByEachField_bothDirections` (the real failure that
  surfaced this issue) — re-run via `bash integration-tests/run.sh --sandbox --no-check
  ProviderProfilePaginationScenarioTest` against a real Postgres: 6/6 tests passing, 0 failures.

**Coverage closed (2026-09-09):** added 8 new integration tests — one pair per repository
(`AdvertisementRepositoryTest`, `ProviderProfileRepositoryTest`, `TaxonRepositoryTest`,
`UserRepositoryTest`; `AuditLogRepositoryTest` already had equivalent coverage) — each forces a
`createdAt`/`updatedAt` tie via a direct `UPDATE`, sorts by that field, and asserts `id DESC` as the
stable tiebreaker. All 42 tests in the 4 classes passing (`bash integration-tests/run.sh --sandbox
"AdvertisementRepositoryTest,ProviderProfileRepositoryTest,TaxonRepositoryTest,UserRepositoryTest"`).
This closed the real Sonar `new_coverage` gap the tiebreaker change itself created — see item 20 for
why the first attempt to verify that didn't show any improvement.

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

## 18. SonarQube quality gate is persistently failing, not a one-off — needs a real fix, not just `improvement-114` — ✅ Done (both halves), 2026-09-09

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

**`new_coverage`/JaCoCo half — ✅ Done via `improvement-114` (2026-09-08):** JaCoCo wired
reactor-wide, `bash scripts/sonar.sh` run in blocking mode (no `--no-gate`) reached
`QUALITY GATE STATUS: PASSED` end to end for the first time — the "gate can actually reach a
passing state at least once" confirmation this item asked for is now satisfied. See
`completed/issues/improvement-114-sonar-jacoco-coverage-not-wired.md`.

**`new_violations` half — ✅ Done, verified 2026-09-09 against a real completed CI scan** (Dagu run
`034Lh059uIy2ylVQs8ACOU`, `sonar` step finished 07:14:49Z). Checked directly via SonarQube's API
(`GET /api/qualitygates/project_status`, `GET /api/issues/search`), not assumed:
- Quality gate: `OK` — all 3 conditions green, `new_violations` actual value `0`.
- Project-wide: `GET /api/issues/search?componentKeys=advertisement&resolved=false` returns
  `total: 0` — no open issues anywhere in the project.
- Per named rule (any status, all time): `java:S5663` — 9 findings, all `CLOSED` (the
  `marketplace-rest-api` controllers named in the original finding); `java:S7467`/`java:S1192`/
  `java:S1450`/`java:S8491` — 0 findings, no trace of any of them.

**Both halves of item 18 confirmed done — the quality gate has now genuinely passed end to end**
(see `improvement-114`'s own verification for the `new_coverage` half, 2026-09-08; this
`new_violations` check, 2026-09-09).

## 19. `ci-runner`'s CI-stage artifacts never reach the host disk — only 4 of ~8 output kinds are synced back — ✅ Done (2026-09-09)

**Found (2026-09-09), while investigating why `playwright/pw-report/` stayed dated 2026-09-05 despite
a same-day CI run:** `ci-runner` (`scripts/ci/Dockerfile`) is built via `COPY . .` — a frozen
snapshot of the source tree at image-build time, not a live bind mount of the host working
directory (confirmed: host-path bind mounts don't work reliably when the caller invoking `docker
run`/`docker build` is itself running inside a container, same root cause already documented for
`scripts/build-and-test/run.sh` — see `scripts/ci/DECISIONS.md`). This is a deliberate, already-
accepted design (ADR-001, `scripts/ci`), not itself a bug.

The actual gap: every wrapped script (`build-and-test.sh`, `playwright/run.sh`, `sonar/run.sh`)
already does its own internal `docker cp` to pull results out of its own test container — but when
that script runs *inside* `ci-runner` (as every `unit`/`integration`/`e2e`/`sonar` Dagu step does),
its own `$ROOT`-relative `docker cp` destination resolves to `ci-runner`'s own internal filesystem
copy, not the real host disk. `scripts/ci/run.sh`'s own `sync_artifacts()` function does perform
the second hop (`ci-runner` → real host disk) — but only for 4 files:
`architecture-metrics.json`, `pipeline-metrics.json`, `architecture-model.json`,
`architecture-map.html` (the `archunit_metrics`/`docs` steps' outputs).

**Confirmed missing from that second hop** (verified directly, `docker exec ci-runner ls`/`docker
inspect` timestamps, not assumed):
- `unit` step — Surefire reports (`scripts/build-and-test/reports/surefire/<module>/`), JaCoCo XML
  (`.../jacoco/*.xml`), run log (`scripts/logs/build-and-test/`)
- `integration` step — Surefire mirror (`integration-tests/reports/`), run log
- `e2e` step — Playwright HTML report + screenshots (`playwright/pw-report/`), run log
  (`scripts/logs/playwright/`)
- `sonar` step — HTML report (`scripts/sonar/report/report.html`), run log (`scripts/logs/sonar/`)

**Decided approach (2026-09-09), two mechanisms depending on where the artifact actually lives:**
1. **Volume-based (preferred, more robust)** — for artifacts that already land in the shared
   `test-reports` named Docker volume before any per-script `docker cp` moves them further (this is
   true for Playwright's report/screenshots and the unit/integration Surefire+log output — all
   confirmed to originate in `/reports/...` inside that volume, written by the build container /
   `pw-runner` regardless of which host process later reads them out). `sync_artifacts()` gains a
   step that mounts `test-reports` via a throwaway container (`docker run --rm -v
   test-reports:/reports -v "$ROOT/...":/dest alpine cp -r ...`) and copies the relevant subtrees
   straight to the host — this does not depend on `ci-runner` still holding a copy, and survives
   even if `ci-runner` itself were removed and recreated between the run and the sync.
2. **Direct `docker cp` from `ci-runner`'s own filesystem (fallback)** — for the one artifact that
   does not pass through `test-reports`: Sonar's final `report.html` (written directly to `$ROOT`
   inside the scanner-invoking script, never staged in the shared volume). Same pattern as the
   existing 4 `sync_artifacts()` entries: `docker cp "$CONTAINER:/app/scripts/sonar/report/report.html" ...`.

**Verification plan, once implemented:** run a real `bash scripts/ci.sh --foreground` end to end,
confirm all ~8 artifact kinds land on the real host disk with fresh timestamps matching the run,
not stale copies from an earlier session. Once verified working for real, record the design via
`/record-decision` in `scripts/ci/DECISIONS.md` and reflect the new sync coverage in
`scripts/ci/README.md` (both — the decision *why* two different mechanisms are used belongs in
`DECISIONS.md`, the current *what gets synced* fact belongs in the README per this project's
one-fact-one-home rule).

**Implemented (2026-09-09):** `sync_artifacts()` extended exactly per the decided approach above —
volume-based copy (throwaway `alpine` container mounting `test-reports`) for unit/integration/sonar
Surefire+JaCoCo+logs and Playwright's report+screenshots+log, plus the `docker cp` fallback for
Sonar's `report.html`. Already committed (`scripts/ci/run.sh`).

**Verified (2026-09-09), real host files, not assumed:** confirmed fresh, non-empty output for
every one of the ~8 artifact kinds after a real `ci` DAG run (18:49-19:11 UTC) — `playwright/
pw-report/` (mtime 19:11, matching), `scripts/build-and-test/reports/advertisement-build-only-
{unit,integration,sonar}/jacoco/*.xml` + `surefire`/`it-mirror`, `scripts/logs/build-and-test/
advertisement-build-only-*/`, `integration-tests/reports/surefire/*.txt`,
`scripts/sonar/report/report.html` + `scripts/logs/sonar/run.log` (mtime 15:48, matching that
run's own sonar step) — all present.

**Doc-sync step closed too:** `run.sh`'s own header (the canonical home, per the atomic-unit-first
rule) already lists every synced artifact kind in full since the implementation commit —
`README.md`'s "Live status" section pointed at it but undersold it ("metrics files"); corrected the
wording there to name the real scope (Surefire/JaCoCo/Playwright/Sonar output, not just
architecture-metrics) rather than duplicating the list itself.

## 20. `jacoco:report-aggregate` silently produced an empty coverage report for every `*-spring-boot-starter` module — ✅ Done (2026-09-09)

**Found while verifying item 14's new tests actually closed the `new_coverage` gap:** after adding
8 new integration tests (item 14), `new_coverage` stayed exactly 54.2% — unchanged. Direct Sonar API
check showed every `*-spring-boot-starter` repository class at 0% coverage for its **entire** file
(not just new lines), including files untouched that day (`AttachmentRepository`,
`ApiKeyRepository`). `query-lib` (a plain library, covered by its own unit tests) showed correct
real coverage (93.6%) — the gap was isolated to modules whose only tests live in `integration-tests`
(this codebase's own architecture: domain starters carry no test code of their own).

**Root cause:** `integration-tests/run.sh` and `scripts/build-and-test/build.sh`'s
`run_integration_tests()` both invoke `./mvnw -pl integration-tests test` (no `-am`) — a deliberate
speed optimization (a separate staleness-check step installs starter JARs first). `jacoco:report-
aggregate` (the Maven goal meant to attribute `integration-tests`' exec data back to the starter
classes it exercises) only resolves dependency modules present in the *same reactor session* — with
scoped `-pl` alone, that session contains only `integration-tests` itself, so the goal silently
produced a report with 0 packages (confirmed: no "Analyzed bundle" log line for that goal, unlike
the plain `jacoco:report` goal immediately above it in the same log; the copied XML was a bare
250-byte `<report>` skeleton).

**Rejected fix:** adding `-am` to the mvn invocation — confirmed working (report-aggregate then sees
all 10 dependency modules) but reintroduces real side effects (`marketplace-orchestrator`/
`marketplace-rest-api`'s own unit tests re-run a second time, recompilation of all 10 modules) that
defeat the point of the narrow `-pl` optimization — rejected after discussion.

**Actual fix:** the standalone JaCoCo CLI jar (`org.jacoco:org.jacoco.cli:0.8.14:nodeps`, same
version already pinned for the Maven plugin) has no reactor-session requirement — its `report`
command reads the exec file plus explicit `--classfiles`/`--sourcefiles` directory paths straight
off disk. Both scripts now generate `integration-tests-aggregate.xml` via this CLI instead of
copying `jacoco:report-aggregate`'s (broken) output, listing the same 10 modules
`integration-tests/pom.xml` itself declares as `<dependency>` entries (`platform-commons` + 7
starters + `marketplace-orchestrator` + `marketplace-rest-api`). No extra mvn invocation, no
recompilation, no repeated test runs — one `java -jar` call after the exec file already exists;
the CLI jar itself is fetched once via `mvnw dependency:get` into the shared `~/.m2` if missing.

**Verified (2026-09-09):**
- Manual CLI run against the existing `jacoco.exec`: 159 classes analyzed (was 0 via
  `report-aggregate`), `AdvertisementRepository.java` — LINE 61 covered / 18 missed (was 0/67).
- End-to-end via `integration-tests/run.sh` (with the fix wired in): same real numbers reproduced
  automatically, no manual CLI invocation needed.
- Full `bash scripts/sonar.sh` re-run: `new_coverage` 54.2% → **88.8%** (threshold 80%), quality
  gate `OK`, all 3 conditions green — verified directly via SonarQube's API
  (`GET /api/qualitygates/project_status`), not assumed.

## 21. `check-*-freshness.sh`'s restore trap can zero out the committed file it's meant to protect — ✅ Done (2026-09-10)

**Found (2026-09-10, investigating two consecutive CI `docs`-stage failures — Dagu runs
`034M0xpg8nVyoIZpd2iF0H` 2026-09-09 20:33 and `034MCQrzuvTFjPzz66x33D` 2026-09-10 04:20).** Both
failed only on the `docs` step, only on `check-adr-index-freshness.sh`, with
`ERROR: .claude/nav/adr-index.md is stale`. The first was a genuine stale-index commit, fixed by
`c6cb4df0`. The second was not: `.claude/nav/adr-index.md` extracted straight from that run's own
CI image (`docker create` + `docker cp`, no entrypoint) had md5 `d41d8cd98f00b204e9800998ecf8427e`
— the md5 of an empty file. `ci.sh` builds the runner image via `COPY . .` from the working tree
at invocation time, so the working-tree copy of `adr-index.md` was 0 bytes when that run started.

**Root cause — the freshness-check scripts' own "safe restore" is not safe:**
```bash
BACKUP="$(mktemp)"                         # 1. empty temp file
trap 'mv "$BACKUP" "$COMMITTED"' EXIT      # 2. on ANY exit, move temp over the real file
cp "$COMMITTED" "$BACKUP"                  # 3. fill the temp with real content
bash generate-adr-index.sh > /dev/null     # 4. (truncates + rewrites COMMITTED in place)
```
Interrupted (SIGINT / tool timeout) between steps 1 and 3, the EXIT trap still fires and runs
`mv <empty temp> <adr-index.md>`, replacing the real ~40 KB file with an empty one. Compounding
it, `generate-adr-index.sh` (and `generate-architecture-model.sh`) truncate their output with
`> "$OUTPUT"` as the first write, so a hard kill mid-generation leaves the committed file partial
regardless of the trap. An earlier interrupted freshness-check run in a dev session left
`adr-index.md` at 0 bytes; the next `ci.sh` snapshotted it and CI went red.

**Fix — 4 files:**
- `.claude/nav/scripts/generate-adr-index.sh` — takes an optional `OUTPUT_PATH` arg (default the
  committed path); builds into a `mktemp` sibling and `mv`s into place only on full success, with
  an EXIT trap that `rm -f`s the temp. An interrupted run now leaves the target untouched.
- `.claude/nav/scripts/check-adr-index-freshness.sh` — rewritten to call
  `generate-adr-index.sh "$GENERATED"` (a throwaway temp) and `diff` that against the committed
  file. The committed file is never opened for writing at all — no backup, no restore trap, no
  possible clobber.
- `docs/architecture/scripts/generate-architecture-model.sh` — same atomic pattern for its
  `.json` and `.html` outputs (temp siblings + `mv` at the end + cleanup trap).
- `docs/architecture/scripts/check-architecture-model-freshness.sh` — still backs up/regenerates/
  restores (its generator has no output-path arg), but populates both backups *before* arming the
  trap and guards each restore `mv` with `[ -s "$BACKUP" ]`, so a truncated/empty backup can
  never overwrite an intact committed file. Header brought to the full 7-field shape.

Also: `.gitignore` gains patterns for the three atomic-write temp siblings (only ever present
after a hard kill). `docs/architecture/data/architecture-model.json` + `architecture-map.html`
regenerated to re-embed the two changed script headers (the generator parses every script's
header into the model) — word-diff confirms those two header blocks are the only change.

**Verified (2026-09-10):**
- `generate-adr-index.sh /tmp/…` writes to the given path, leaves `.claude/nav/adr-index.md`
  git-clean; content byte-identical to `HEAD`.
- `generate-adr-index.sh` (no arg) and `check-adr-index-freshness.sh` both leave the committed
  file git-clean; freshness check reports "up to date"; no leftover `*.tmp`.
- The three CI `docs`-stage checks (`check-adr-index-freshness.sh`,
  `check-flows-completeness.sh`, `check-hardcoded-counts.sh`) all pass.
- `generate-architecture-model.sh` re-run end to end (GEN_EXIT=0): committed files survive, no
  leftover `*.tmp`; the earlier trap bug (EXIT trap deleting the just-moved final file after the
  var was reassigned) found and fixed before this.
- `check-architecture-model-freshness.sh`: committed files always present and restored after a
  run, even when the generator's own trap misfired mid-development — the `[ -s ]`-guarded restore
  is what saved them.
- `bash -n` clean on all four scripts.

**Note on the CI failures themselves:** no code was ever broken — `HEAD` passed the freshness
check throughout, and a fresh `docker build` of the ci-runner image from the current tree passes
all three `docs`-stage checks. The trap fix above stops the *working tree* from ever holding an
empty `adr-index.md`. A third `docs` failure (2026-09-10 06:39) proved a second, independent
mechanism is also in play — see item 22.

## 22. `ci.sh` can run against a stale Docker `COPY . .` layer — the ci-runner must always see the live working tree — ✅ Done (2026-09-10)

**Found (2026-09-10, third consecutive `docs`-stage CI failure, Dagu run `034MFq3hlntYSebmASSdvA`).**
Same `adr-index.md is stale` error. `.claude/nav/adr-index.md` extracted straight from that run's
ci-runner image (and from the still-running container) was **0 bytes**, while the host working-tree
file was a correct 39888 bytes, git-clean, untouched since well before the run. A fresh
`docker build -f scripts/ci/Dockerfile -t testci "$ROOT"` (byte-identical to what `scripts/ci/run.sh`
does) produced an image with the correct 39888-byte file and all three `docs` checks green
(`adr rc=0`, `flows rc=0`, `counts rc=0`).

**Root cause:** `scripts/ci/run.sh` builds the ci-runner image with a plain `docker build "$ROOT"`
(no cache control). The `COPY . .` layer got served from Docker's build cache — a layer baked
around the time item 21's trap bug had left `adr-index.md` at 0 bytes on the host. Docker's cache
key for that `COPY` layer did not reflect the file's later 0 → 39888-byte change, so `ci.sh`
snapshotted an empty file into the image even though the host tree was correct. `docker system df`
shows ~15.6 GB of ci-runner build cache. The ci-runner image has no dependency-download layer
worth caching past the `apt-get` step (the Dockerfile itself says so).

**Decided design (2026-09-10) — sync live source into the running container, don't rebuild:**
- Dagu run history is safe either way — it lives in the `ci-dagu-home` **named volume**
  (`run.sh:245` `-v ci-dagu-home:/root/.dagu`), independent of the image and the container
  filesystem; `docker rm -f` + recreate does not touch it.
- **`run.sh` — new default flow:**
  1. `NEED_BUILD` — build the image only when it is missing, when `scripts/ci/Dockerfile` /
     `scripts/ci/docker-entrypoint.sh` is newer than the image's own creation timestamp, or when
     `--rebuild` is passed. Otherwise skip `docker build` entirely.
  2. `NEED_CONTAINER` — (re)create the container + proxy only when a build just happened, or when
     `ci-runner` / `ci-runner-dagu-proxy` isn't running. Otherwise keep the running container.
  3. **Every run, before triggering:** overlay `/app` inside the running container with the current
     working tree — `git -C "$ROOT" ls-files -z --cached --others --exclude-standard | tar -C
     "$ROOT" --null --no-recursion --ignore-failed-read -T - -cf - | docker exec -i "$CONTAINER"
     tar -C /app -xf -`. The file set is `git ls-files` (tracked + untracked-not-`.gitignored`),
     **not** a tar tree-walk — so `.git`, every `*/target`, `node_modules`, and report/log dirs are
     excluded for free, and it never trips over an IDE-locked build artifact (the real `tar=2` a
     first attempt hit on Windows) or a socket/FIFO. `*.md` is kept (unlike `.dockerignore`). Same
     host↔container transfer pattern `sync_artifacts()` uses in reverse (`run.sh:114-150`).
  4. Trigger the Dagu run unchanged (`docker exec -d "$CONTAINER" dagu start ...`).
- **Flags:** `--no-rebuild` **removed** (the smart default replaces its "reuse as-is" behavior; the
  pair `--rebuild`/`--no-rebuild` read as contradictory). `--rebuild` kept as the one manual
  override — force an image rebuild + container recreation even when the Dockerfile is unchanged.
- **Sync failure handling:** `git ls-files` failure (empty set), fatal `tar` (exit 2), or a failed
  extract side ends the run with a `sync-source` `AGENTIC_ERROR_BLOCK`; a non-fatal `tar` exit 1
  (a listed file changed/vanished mid-read on a live tree) is tolerated.
- **Not doing:** a host bind mount (`-v "$ROOT:/app"`) — documented broken under nested Docker,
  the reason `docker cp` is used throughout this repo; a `CACHEBUST` build-arg — superseded by
  not rebuilding at all in the common case.
- **One-time:** `docker builder prune -f` to drop the stale ~15.6 GB cache.
- **Docs updated in the same change:** `run.sh` header; `.claude/rules/scripts.md` "Local CI
  Runner" section; `scripts/ci/README.md` (Flow diagram + the UI-path caveat); new
  `scripts/ci/DECISIONS.md` ADR via `/record-decision` (+ adr-index regen).

**Implemented (2026-09-10):**
- `scripts/ci/run.sh` — `NEED_BUILD`/`NEED_CONTAINER` detection, `--rebuild` flag (`--no-rebuild`
  removed), a `sync-source` step that pipes `git ls-files` through `tar` into `ci-runner:/app`
  before every trigger, with a `sync-source` `AGENTIC_ERROR_BLOCK` on a real failure. Header +
  Usage updated.
- `scripts/activity-monitor/run.sh` — `sync-source` added to `SCRIPT_STEP_SEQUENCE["ci.sh"]`,
  `STEP_LABELS`, `STEP_DESCRIPTIONS`.
- `.claude/rules/scripts.md`, `scripts/ci/README.md` — updated for the new behavior; README Flow
  diagram redrawn.
- `docker builder prune -f` — ran, ~10.75 GB reclaimed.
- First attempt used a raw `tar` tree-walk with `--exclude`s; it failed with `tar=2` on a real
  Windows run (an IDE-locked jar under `target/`). Replaced with `git ls-files | tar` — git's own
  file set never touches `target/`, `node_modules`, `.git`, sockets.
- **Follow-up fix (2026-09-10):** the `git ls-files | tar -x` overlay left behind files *deleted*
  from the working tree since the last sync — a CI run compiled a stale copy of a
  `.java` removed in item 10 while its `I18nKey` constants were already gone, so `build` failed
  with `cannot find symbol` and every downstream stage cascaded. Fixed: the `docker exec` side is
  now `sh -c 'find /app -mindepth 1 -delete; exec tar -C /app -xf -'` — `/app` is wiped then
  re-extracted, so it always equals the working-tree `git ls-files` set exactly. Safe: everything
  under `/app` is build-regenerated; the durable caches are in the `/root/.m2` / `/root/.ci-tools`
  / `/root/.dagu` volumes. `scripts/ci/DECISIONS.md` ADR-012, `scripts/ci/README.md` (flow +
  prose), `.claude/rules/scripts.md` updated to match.

**Verified (2026-09-10):**
- `bash -n scripts/ci/run.sh` / `scripts/activity-monitor/run.sh` clean.
- The `NEED_BUILD`/`NEED_CONTAINER` expressions, run standalone against the live state: both
  empty (image newer than Dockerfile, both containers running) → fast path, no rebuild.
- The exact `git ls-files -z --cached --others --exclude-standard | tar --null --no-recursion
  --ignore-failed-read -T - -cf - | docker exec -i ci-runner tar -C /app -xf -` pipeline run
  against the poisoned running `ci-runner` (`adr-index.md` was 0 bytes): pipe status `0 0 0`,
  file became 40072 bytes, no `/app/.git`, no `/app/marketplace-app/target` in the container.
- A fresh `docker build -f scripts/ci/Dockerfile -t testci "$ROOT"` (identical to `run.sh`'s own
  build) produced an image with the correct `adr-index.md` and all three `docs`-stage checks green.

## 23. CI `docs` stage should regenerate the ADR index and hand it back, not gate on drift — ✅ Done (2026-09-10)

**Found (2026-09-10, item 10 dedup follow-up run):** two consecutive CI runs (`034MPDm5` 13:01,
`034MRjxH` 14:44) failed the `docs` step on `check-adr-index-freshness.sh` (`adr-index.md is
stale`) while the committed index was in fact byte-identical to a fresh regeneration on the host.
Root cause: `generate-adr-index.sh` builds via `mktemp` + `mv` and never `chmod`s, so every local
run left the committed `.claude/nav/adr-index.md` at mode `0600` (owner-only). When `run.sh`'s
`sync-source` `tar` ran as a different uid it could not read the file, `--ignore-failed-read`
silently dropped it, and the container's `/app` had no `adr-index.md` at all — the freshness diff
then compared a fresh regeneration against a missing file and reported "stale".

**Decided (2026-09-10):** stop gating on drift. The `docs` stage now *regenerates* the index and
the run hands the fresh file back:
- `generate-adr-index.sh` — `chmod 644` after the atomic `mv` so the committed file is always
  world-readable regardless of who ran it.
- `scripts/ci/run.sh` `sync-source` — dropped `--ignore-failed-read`; any tar read failure now
  fails the sync loudly instead of silently dropping a file.
- `docs/architecture/scripts/generate-architecture-model.sh` — regenerates `.claude/nav/adr-index.md`
  in place first (it then reads it to fold ADRs into each module's intent list), so one
  "regenerate the architecture docs" run refreshes the index too.
- `scripts/ci/dagu/ci.yaml` `docs` stage — `check-adr-index-freshness.sh` removed.
- `scripts/ci/run.sh` `sync_artifacts()` — copies `ci-runner:/app/.claude/nav/adr-index.md` back
  to the host; `docker cp`'s own exit code is the check, no extra size comparison (an earlier
  `docker exec stat` size check was dropped — it added a flaky daemon round-trip for coverage
  `docker cp` already gives).
- `.claude/nav/scripts/check-adr-index-freshness.sh` deleted; `.claude/nav/README.md`,
  `.claude/nav/scripts/README.md` (prose + mermaid), `docs/architecture/data/runtime-notes.md`,
  and `generate-architecture-model.sh`'s script-order map updated to match.
- The standing `.claude/rules.md` rule (regenerate + commit the index in the same operation as any
  `DECISIONS.md` edit) is unchanged and stays the primary defense.

**Also fixed here (2026-09-10):** `ProviderProfilePaginationScenarioTest.sortByEachField_bothDirections`
was flaky — it asserted exact `createdAt,asc` positions, but the real `ORDER BY pp.created_at ASC,
pp.id DESC` reverses same-tick rows via the id-DESC tiebreaker (deliberate `OrderByBuilder`
default). Loosened to assert only what the impl guarantees: full set + non-decreasing `createdAt`;
kept the exact order on `createdAt,desc` (fully deterministic there). Verified via
`build-and-test.sh --integration --integration-test ProviderProfilePaginationScenarioTest` — 6/6
pass — and a full `ci.sh` run (`034MUhJy`: integration/sonar/docs all green; e2e failed only on
`05-...:399` YouTube-lightbox `ECONNREFUSED`, an external-network flake unrelated to any change).

**ADR status (checked 2026-09-11):** `scripts/ci/DECISIONS.md` ADR-013 already records this exact
reversal in full — including that it "realigns with `docs/architecture/scripts` ADR-001's stated
preference" (ADR-001 itself never established a freshness gate; it explicitly preferred manual
regeneration "rather than inventing a separate drift-detection mechanism," and the freshness-gate
script this item removes was an undocumented later addition that briefly contradicted it). No
second/duplicate ADR needed — this "Open" note was stale, superseded by ADR-013 once item 24's
work landed it the same day.

## 24. `ci.sh --foreground` monitors the wrong Dagu run when another is in flight; concurrent runs collide; failures are misreported — ✅ Done (2026-09-10)

**Found (2026-09-10, item 10/23 verification runs):** while a `ci` DAG run was still in its long
e2e stage, launching a second `ci.sh --foreground` produced a nonsense step tree — early stages
shown `✅` with `(0s)` durations while the user's Dagu tab showed them still running. Also seen: 5
unrelated Playwright specs failing with `.header-settings-button` not found. Three separate defects:

1. **Monitor attaches to the wrong run.** `dagu-rest-run-monitor.py`'s `await_fresh_run_id()` starts
   *after* `run.sh`'s detached `docker exec -d dagu start`, then picks "the newest run that isn't
   terminal". If the new run hasn't registered with Dagu's API yet (a few-second lag) and a
   *previous* run is still in flight (early stages done, e2e still going), "newest non-terminal" is
   that previous run -- so the monitor watches it. Its `build/unit/integration/archunit` are already
   `succeeded`; the monitor emits all their done-markers in one poll, and
   `scripts/activity-monitor/run.sh`'s `mark_step` timestamps a step's completion with `date +%s`
   *when it sees the marker*, not Dagu's real finish time -- several "done" in one instant →
   `(0s)` each. Benign display artifact on top of the real "wrong run" bug.
2. **Concurrent runs collide on the shared e2e stack.** The e2e stage's `ci-advertisement-db` /
   `ci-marketplace-app` / ... containers have fixed, non-per-run names. A second run's e2e stage
   redeploys/`--reset-only-db`s that stack out from under the first run's Playwright tests -- the
   app vanishes mid-test, `.header-settings-button` never appears, specs cascade-fail. Dagu's
   `maxActiveRuns` does not gate a manual `dagu start`.
3. **Misleading failure line.** When every DAG stage passed but `run.sh`'s post-run
   `sync_artifacts()` (host-side `docker cp` of the regenerated docs/adr-index) failed, the tree
   still said "CI DAG run failed -- one or more stages did not pass" -- the opposite of what
   happened.

**Fix:**
- `run.sh` assigns the run id itself: `DAGU_RUN_ID="ci-<UTC>-<pid>-<rnd>"`, passed to both
  `dagu start -r "$DAGU_RUN_ID"` and (as env) `dagu-rest-run-monitor.py`. The monitor's new
  `wait_for_run()` waits for *that exact id* to register, then watches it -- no guessing.
  `await_fresh_run_id()` kept only as the fallback for a hand-run monitor against a UI-triggered run.
- `run.sh` refuses to start while a run is genuinely alive -- checked via `dagu ps -d ci` (the live
  process store), not the REST `statusLabel` (a run killed with its container lingers as
  `running` in persisted history until Dagu reconciles it; `dagu ps` never shows that zombie).
- `run.sh --foreground` now reports a real DAG-stage failure and an artifact-sync-only failure
  distinctly ("A ci DAG stage failed" vs "Every ci DAG stage passed, but ... sync ... failed --
  re-run ... --sync-artifacts").
- The run id is surfaced as `tree.txt`'s first line on every state via a new generic
  `AGENTIC_CONTEXT:` marker (`dagu-rest-run-monitor.py` emits it; `scripts/activity-monitor/profiles/agentic.sh`
  sets `run.sh`'s `CONTEXT_LINE`; `render_tree()` prepends it), plus in the `Dagu run id:` line and
  the PASSED/FAILED line.
- `scripts/ci/Dockerfile` `DAGU_VERSION` 2.16.2 → 2.16.3.

**Files:** `scripts/ci/run.sh`, `scripts/ci/dagu-rest-run-monitor.py`, `scripts/ci/Dockerfile`,
`scripts/activity-monitor/run.sh`, `scripts/activity-monitor/profiles/agentic.sh`,
`scripts/ci/DECISIONS.md` (ADR-012/013 touched), `scripts/ci/README.md`.

**Resolved (2026-09-11):** the "one ci run at a time + self-assigned run-id contract between
`run.sh` and the monitor" is now recorded in `scripts/ci/DECISIONS.md` (see
`.claude/nav/adr-index.md` for the entry) — no code change, this only closed the missing paper
trail for the fix already shipped above. `.claude/nav/adr-index.md` regenerated in the same
operation.

**Follow-up (2026-09-11) -- the real recurring cause of point 3's sync failure, found after point 3's
own fix started reporting it honestly:** `sync_artifacts()`'s host-side write of
`architecture-model.json`/`architecture-map.html`/`adr-index.md` failed consistently (not
transient) on a checkout under a WSL2 Windows-drive mount (`/mnt/c`, `/mnt/d`, ...). `docker cp`'s
own unlink-then-recreate extraction, and a plain shell `cp -f`/`mv` fallback tried after it, both
hit DrvFs enforcing the real Windows ACL underneath -- confirmed down to `cp: cannot create regular
file ...: Permission denied` on a brand-new file in the destination directory, so no client-side
cp/mv/rename trick can route around it (this is a real, sourced WSL2/DrvFs limitation, not a bug in
any of those tools -- see the fix commit for citations). Fixed by doing the final write from
*inside* a throwaway `alpine` container that bind-mounts the destination directory instead of the
calling shell touching it directly -- Docker Desktop's own WSL2 file-sharing layer for a bind mount
goes through a different path than a WSL shell's direct DrvFs access, and does succeed. Diagnostics
(per-file copy outcome, with the real stderr) are relayed into `$CONTAINER` itself via `docker exec`
(`/tmp/ci-sync-diag.log`), not just to `$ROOT`, since `$ROOT` is the caller's own host filesystem --
invisible to whoever else needs to debug a run they didn't personally trigger. Verified end to end
on the actual affected WSL2 checkout: `rc=0` for all three files, run `succeeded` with no trailing
sync failure. Also fixed alongside: `scripts/activity-monitor/run.sh`'s exit-code fallback no longer
overwrites an already-`skipped` step to `error` just because the wrapped script failed elsewhere
(hit via the new `--docs-only` flag, which skips unit/integration/e2e/sonar/archunit_metrics
entirely for a fast path to test the docs stage/sync alone); `ci-run` (the post-DAG artifact sync)
added to `ci.sh`'s own step sequence so it renders as its own visible "running" step instead of
silently appearing pass/fail only once finished. Files: `scripts/ci/run.sh`,
`scripts/activity-monitor/run.sh`. Committed `ea3627b0`.

## 25. Generalize the WSL2/DrvFs-safe container-to-host copy beyond `ci.sh` -- not started, deferred

**Ask (2026-09-11):** item 24's follow-up fix (bind-mount-container copy instead of a direct
`docker cp`/`cp`/`mv` write, to survive WSL2 Windows-drive checkouts) currently lives inline in
`scripts/ci/run.sh`'s own `docker_cp_diag()`. Other scripts have the same container-to-host `docker
cp` shape and would hit the identical DrvFs wall on the same kind of checkout:

- `scripts/sonar/run.sh:335` -- `docker cp "$SCANNER_CONTAINER":/tmp/sonar-report.html
  "$REPORT_FILE"` -- a single file, **not** suppressed (no `|| true`), the direct analog of what
  item 24 just fixed; `sonar.sh` would hard-fail the same way `ci.sh` did.
- `scripts/sonar/run.sh:344` and `scripts/build-and-test/run.sh:293,294,302` -- directory copies
  (`docker cp CONTAINER:/reports/.../. HOST/`), already wrapped in `2>/dev/null || true`
  (best-effort, non-fatal today even if DrvFs blocks them) -- would need a directory-copy variant
  of the fix (`mktemp -d` + a recursive merge inside the throwaway container), not just the
  single-file one.

**Planned approach:** extract the single-file bind-mount-copy helper out of `scripts/ci/run.sh`
into a shared `scripts/utils/docker-cp-to-host.sh` (this repo's established home for logic shared
across script-groups, see `scripts/utils/README.md`), source it from both `scripts/ci/run.sh` and
`scripts/sonar/run.sh`, and use it for the `sonar/run.sh:335` call site. The directory-copy variant
for the three best-effort call sites is separate, lower-priority work (not blocking anything today)
-- deferred further, picked up only if one of those best-effort copies is confirmed actually
failing on a real WSL2 checkout, not built speculatively ahead of that evidence.

- [improvement-073](../completed/issues/improvement-073-rest-endpoint-infrastructure-test-seeding.md) —
  REST API infrastructure (API-key auth, Swagger, apikey/rest-api modules) this whole batch follows
  up on.
- improvement-182 (REST API filter/sort/pagination parity with UI) — same family as items 1, 3, 6.
- improvement-111 (service-boundary authorization in marketplace-orchestrator) — the authorization
  seam item 1's role check should plug into.
- `taxon-spring-boot-starter/DECISIONS.md` — existing locale-fallback-to-`defaultLocale` decision
  relevant to item 4.
- `attachment-spring-boot-starter/DECISIONS.md` — existing upload/storage design relevant to item 9.
