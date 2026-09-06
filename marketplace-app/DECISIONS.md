# Architecture & Technical Decisions — marketplace-app

---

## ADR-080: External REST API list endpoints — filter/sort bind onto the existing domain DTOs, pagination uses RFC 8288 `Link` + `X-Total-Count` headers, never an envelope or Spring HATEOAS

**Status:** Accepted

**Also affects:** marketplace-rest-api, taxon-spring-boot-starter

**Context:** `GET /api/advertisements`/`GET /api/provider-profiles`/`GET /api/taxons` (shipped as part
of the REST API infrastructure work) hardcoded an empty filter and unsorted results — no filter or
sort query parameter was exposed at all, despite the exact same capability already working in the
Vaadin UI and the orchestrator service layer underneath (`AdvertisementReadService.getFiltered`/
`.count`, same for `ProviderProfileReadService`). `TaxonPort` had no pagination/count capability at
any layer at all (`getAllByType`/`listAllByType` always returned the full unpaginated list) — the
repository (`TaxonRepository.findAllByType`) already had filter (`TaxonFilter`, matched by name)
and sort infrastructure (`SqlFilterBuilder`/`OrderByBuilder`), just no `LIMIT`/`OFFSET` or count.

Three real design questions needed resolving, each grounded in this codebase's own existing
precedent and industry practice (researched directly, not assumed):
1. **Where does filter/sort/pagination logic belong?** `query-lib/DECISIONS.md` ADR-003 explicitly
   freezes that library's SQL-DSL scope, rejecting "generic pagination abstractions" beyond the
   already-narrow `PaginationSqlBuilder.pageLimit`. `marketplace-orchestrator` must stay
   transport-agnostic (root `CLAUDE.md`'s 3-layer principle) — it already exposes `getFiltered`/
   `count`, but HTTP-specific concerns (query-param parsing, response headers) don't belong there
   either. `marketplace-rest-api` cannot import the UI's `AdvertisementFilterMeta`/
   `AdvertisementSortMeta` (wrong dependency direction — `marketplace-app` depends on
   `marketplace-rest-api`, not the reverse), so both sides independently reference the same
   `*Dto.Fields.*` constants instead of one sharing the other's class.
2. **How should filter validation stay in sync with the UI's own validation?** Already solved by
   construction: `AdvertisementFilterDto`/`ProviderProfileFilterDto` are the same DTOs the UI's own
   Binder validates (`@Size`, `@ValidRange` — real Jakarta Bean Validation constraints in
   `platform-commons`, not duplicated logic). Binding REST query parameters directly onto these DTOs
   via `@ModelAttribute @Valid` makes the identical constraints apply to REST for free, through the
   already-existing `ApiExceptionHandler`'s `MethodArgumentNotValidException` → 400 mapping.
3. **How should pagination expose "next page" navigation?** Compared three real options: a
   hand-rolled `PageResponse<T>` envelope (breaks the existing plain-array response shape), Spring
   HATEOAS (`PagedModel`/`PagedResourcesAssembler` — a new dependency, more framework machinery than
   this project's own "explicit over implicit" principle favors), and an RFC 8288 `Link` response
   header (`rel="next"/"prev"/"first"/"last"`) plus `X-Total-Count` — the convention GitHub's and
   GitLab's own REST APIs already use. Chose the `Link`-header approach: no new dependency, no
   breaking change to the existing array response body, genuinely navigable pagination.

**Decision:**
1. Each list endpoint binds `GET` query parameters directly onto its existing `<Domain>FilterDto`
   via `@ModelAttribute @Valid` — no parallel REST-only filter DTO, except for `TaxonFilterDto`
   (new, `platform-commons`), needed because `TaxonPort` never had a filter DTO of its own at any
   layer before this (only an internal `TaxonFilter` inside `taxon-spring-boot-starter`, unreachable
   across the module boundary) — `TaxonFilterDto` mirrors that internal type's own one real field
   (`name`); its internal-only `showDeleted` field is not exposed, staying an admin-only concern
   already handled by the separate `listAllByType(includeDeleted)` method.
2. A `?sort=field,dir` query parameter, parsed by a new shared `SortQueryParser`
   (`marketplace-rest-api`, `org.ost.restapi.api.paging`) and validated against an explicit
   allow-list per controller, built from that domain's own response DTO's `@FieldNameConstants`
   `Fields.*` constants (`AdvertisementInfoDto`, `ProviderProfileDto`, `TaxonDto` — the latter
   gained `@FieldNameConstants` for this) — an unknown field is a 400
   (`ApiExceptionHandler` gained an `IllegalArgumentException` → 400 mapping for this). Taxon's own
   allow-list is deliberately narrower (`id` only) than what the repository's `SORT_ALIASES` map
   technically supports (`id`/`createdAt`/`updatedAt`) — `TaxonDto` itself carries no
   `createdAt`/`updatedAt` field, so exposing them as sort keys would let a caller sort by a field
   it can never see in the response body.
3. Pagination: a new shared `PageLinkHeaderBuilder`/`PagedResponseBuilder` pair
   (`marketplace-rest-api`, same package) builds the RFC 8288 `Link` header and sets
   `X-Total-Count`, reused identically by all three list endpoints. Response bodies stay plain
   `List<T>`, unchanged shape.
4. `TaxonPort` gained `getPageByType(type, locale, filter, page, size, sort)` + `count(type,
   filter)`, mirroring `AdvertisementPort`/`ProviderProfilePort`'s already-established
   `(FilterDto, page, size, Sort)`/`count(FilterDto)` shape exactly — not a new pattern, a third
   application of an already-twice-proven one. `TaxonRepository.findAllByType`/
   `TaxonService.listByType`'s `Sort` parameter became `Pageable` (carries `Sort` via
   `Pageable.getSort()`, plus page/size for `PaginationSqlBuilder.pageLimit`); the three pre-existing
   unpaginated callers (`getAllByType`, `listAllByType`, `getUsageCounts`) now pass
   `Pageable.unpaged(sort)`/`Pageable.unpaged()` — no behavior change for them.

**Rejected alternative — `PageResponse<T>` envelope:** rejected because it would have changed every
list endpoint's response body shape (a breaking change for the array-shaped responses already
shipped), for a benefit the header-based approach already delivers.

**Rejected alternative — Spring HATEOAS:** rejected as more framework machinery than this specific
need justifies — a hand-built RFC 8288 header string is a handful of lines
(`PageLinkHeaderBuilder`), not a new library dependency, matching root `CLAUDE.md`'s "explicit over
implicit" principle.

**Consequences:**
- A future new REST list endpoint follows this same three-part shape: bind query params onto the
  existing domain `FilterDto`, validate sort against that domain's own response-DTO `Fields.*`,
  delegate response assembly to `PagedResponseBuilder`.
- Verified directly: unit 255/255 (`marketplace-rest-api` 49, up from 27 — new `SortQueryParser`/
  `PageLinkHeaderBuilder`/`PagedResponseBuilder` unit tests plus extended controller tests),
  integration 186/186 (3 new `TaxonRepository` pagination/count tests against real Postgres),
  `ArchitectureRulesTest` 20/20 unaffected.
- A `/review` pass (`deep-review-orchestrator`) found and fixed one real DRY violation during this
  same implementation: the three controllers' `list()` methods duplicated the same 7-statement
  response-assembly sequence verbatim — extracted into `PagedResponseBuilder` before this ADR was
  written, so the shape described above already reflects the deduplicated version, not the
  as-first-written one.

---

## ADR-079: Build-enforced ArchUnit rule blocks direct `TaxonPort.replaceAssignments()` calls from starters; the diagram category it superseded is removed

**Status:** Accepted

**Also affects:** docs/architecture/scripts

**Context:** The Bounded Contexts diagram's "Cross-Starter Exceptions" tab existed to document a
specific, real historical bug class: a starter (`provider-profile`/`advertisement`) calling
`TaxonPort.replaceAssignments()` directly instead of routing the write through
`marketplace-orchestrator`'s `TaxonAssignmentWriteService`. Both known instances of this bug were
already fixed by the time this ADR was written — the tab's own regex-based regression guard
(`generate-architecture-model.sh`) had found nothing for a while — but nothing actually *prevented*
the bug from being reintroduced: `starters_must_not_import_sibling_starters`
(`ArchitectureRulesTest`) only blocks a starter importing another starter's own package; it does
not catch a starter injecting `ComponentFactory<TaxonPort>` (a `platform-commons` type, always
importable) and calling a write method on it directly. The diagram category was therefore a
documentation-time detector, not a build gate — a reintroduced bypass would only surface at the
next architecture-doc regeneration, not at PR time.

**Decision:**
1. New `@ArchTest` in `ArchitectureRulesTest.java`:
   `starters_must_route_taxon_assignment_writes_through_orchestrator` — fails the build if any class
   outside `org.ost.orchestrator..`/`org.ost.marketplace..`/`org.ost.restapi..`/`org.ost.taxon..`
   calls `TaxonPort.replaceAssignments()` directly (scanned via `getMethodCallsFromSelf()` +
   `getTarget().getOwner().isAssignableTo(TaxonPort.class)`, same custom-`ArchCondition` style as
   the existing `starters_must_not_import_sibling_starters` rule).
2. The "Cross-Starter Exceptions" diagram category (`docs/architecture/scripts/generate-architecture-model.sh`:
   `BC_CATEGORY_ORDER`/`BC_CATEGORY_LABEL`/`BC_CATEGORY_DESC`/`BC_LABEL_CATEGORY`'s `exceptions`
   entries, the `"category assignment via"` label and its regex-based extraction loop) is removed
   outright, along with its client-side JS mirrors (`BC_LABEL_MEANING`/`BC_CATEGORY_LABEL_JS`). It
   was the one category whose entire membership (a single label) is now guaranteed empty by a
   failing build rather than merely "currently empty" — permanently-empty documentation for
   something a build gate already guarantees is not worth the tab.

**Rejected alternative — keep the diagram category alongside the new rule:** rejected because the
two mechanisms had identical scope (both keyed on the same one method,
`TaxonPort.replaceAssignments()`) — keeping the diagram category bought no additional detection
coverage over the ArchUnit rule, only a strictly weaker, slower one (documentation-regeneration-time
instead of build-time).

**Consequences:**
- Verified directly: `ArchitectureRulesTest` passes (20/20, was 19/19 before this rule) against the
  current, already-clean codebase — the new rule is a regression guard, not a fix for a live
  violation.
- A future genuinely new class of starter-to-starter Port-write bypass (a different Port, a
  different method) is not automatically caught by this rule — it is scoped to the one concrete,
  historically-observed case, matching this file's own established precedent of narrowly-scoped
  custom `ArchCondition` rules rather than one generic "any write bypass" rule.

---

## ADR-078: External REST API authentication — long-lived bearer API-key, not OAuth2

**Status:** Accepted

**Context:** Building an external, Postman/Swagger-testable REST API (`marketplace-rest-api`) needed
a real authentication mechanism beyond the existing Vaadin session login. The API serves
programmatic/script access to a user's own account (external integrations, Playwright test
seeding) — not third-party apps acting on behalf of a user. Considered reshaping the
credential-issuance endpoint into a genuine OAuth2 client-credentials flow, since that is what
Postman natively auto-refreshes without a custom script, and OpenAPI models declaratively via
`securitySchemes: oauth2`.

**Decision:** Kept the simpler, already-implemented pattern: a per-user, long-lived, revocable
bearer API key (`POST /api/api-keys` issues it behind HTTP Basic auth; `Authorization: Bearer
<key>` authenticates every other call, resolved via `ApiKeyAuthenticationFilter` →
`ApiKeyManagementService`). This is the same pattern Stripe, GitHub, OpenAI, and Anthropic's own
API use for exactly this use case (a user scripting against their own account) — a legitimate,
standard pattern in its own right, not a simplified OAuth2. Key hashing is SHA-256 of a 32-byte
`SecureRandom` value, not bcrypt/`PasswordEncoder` — bcrypt's deliberate slowness defends a
low-entropy human password against offline brute-force; it buys nothing for an already-random
256-bit token and would add real latency to every authenticated request.

**Rejected alternatives:**
- **OAuth2 client-credentials flow** — the natural fit for Postman/Swagger's native no-script
  auto-auth, but solves a different problem: delegated third-party authorization, multiple
  registered clients, short-lived token rotation. None apply here (no third-party apps, one
  "client" = the account owner). Doing it properly would need a refresh-token mechanism, a
  client-registration concept, and scopes — solving problems this app doesn't have, purely so
  Postman's UI doesn't need a 5-line pre-request script. Rejected as disproportionate complexity
  (YAGNI) for the actual requirement.
- **bcrypt/`PasswordEncoder` for key hashing** — no benefit for an already-high-entropy random
  token; only adds latency.

---

## ADR-001: All Vaadin UI consolidated in marketplace-app
**Status:** Accepted

**Context:** Two phases — (1) UI extracted from individual starters into a short-lived
`marketplace-ui` module; (2) `marketplace-ui` merged back into `marketplace-app` (no second
consumer existed, Maven module boundary added cost with no benefit) and domain logic moved to
dedicated starters.

**Decision:** All Vaadin UI lives in `marketplace-app` (`org.ost.marketplace.ui.*`). No starter
contains UI code. `marketplace-app` imports starters only via platform-commons contracts
(`UserPort`, `AdvertisementPort`, `AuditPort`, `AttachmentPort`, `UserDto`, etc.).
`*HookImpl` and `*PortImpl` orchestrators live in `marketplace-app`.

**Consequences:** CSS rule: all CSS lives in `marketplace-app/src/main/frontend/themes/my-app/`
— Vaadin 25 Vite build does not include CSS from `@CssImport` in JAR starters.

---

## ADR-002: No JPA / no Hibernate — JdbcClient + inline SQL
**Status:** Accepted

**Context:** JPA/Hibernate introduces hidden lazy-loading bugs, N+1 queries, and magic that
conflicts with the "explicit over implicit" architecture principle.

**Decision:** All database access via `JdbcClient` with SQL inlined directly in repository methods.
`CrudRepository<T, Long>` only for trivial `save`/`findById`/`deleteById` — never for custom queries.

**Consequences:** Rejected: Spring Data JPA — too much hidden magic.

---

## ADR-003: Inline SQL repository style — no descriptor layer
**Status:** Accepted

**Context:** A prior `SqlEntityDescriptor` / `SqlCommand` descriptor layer required jumping to
constants classes to read any query.

**Decision:** SQL inline per method — text block `"""..."""` for multi-line, single-line string
otherwise. No `TABLE`, `ALIAS`, `SOURCE`, or single-use SQL string constants.
- `@SuppressWarnings("java:S1192")` on every repository class.
- `MapSqlParameterSource` constructed inline per method.
- Dynamic SQL assembled with `.formatted()`, never `+` concatenation.
- `SqlFilterBuilder.build(params, filter, prefix)` — prefix is part of the call.
- `OrderByBuilder.build(sort, map)` returns `" ORDER BY ..."` with leading space.

**Consequences:** The Descriptor layer (`SqlEntityDescriptor`, `SqlCommand`, `SqlDescriptorField`,
`SqlEntityProjection`, `SqlFixedQuery`, `RepositoryCustom`) fully removed from the codebase.
When adding a new repository method, write the full SQL inline. Never extract a SQL fragment to
a constant unless shared across 3+ methods and genuinely non-trivial.

---

## ADR-005: Modular storage — contract in attachment-starter, not marketplace
**Status:** Accepted

**Context:** Storage only exists to serve attachments — no use case for storage without attachments.

**Decision:** `StorageService` interface and implementations live in `attachment-spring-boot-starter`
(`org.ost.attachment.services` — moved from the originally accepted `org.ost.attachment.storage`
package at some point, decision itself unaffected). UI components use `ObjectProvider.ifAvailable()`
to degrade gracefully when the attachment starter is absent.

**Consequences:** Rejected: keeping `storage-s3-spring-boot-starter` as a separate module —
two modules with a mandatory one-way dependency and no realistic decoupling scenario.

---

## ADR-006: Audit subsystem in audit-spring-boot-starter; domain calls via AuditPort
**Status:** Accepted

**Context:** Audit is infrastructure, not domain. Domain services calling audit internals directly
would couple business logic to audit infrastructure.

**Decision:** The full audit subsystem (write + read sides) lives in `audit-spring-boot-starter`.
All Vaadin audit UI lives in `marketplace-app`. Domain services call `AuditPort` (contract interface).
The starter contains zero advertisement-specific knowledge — all domain coupling expressed through
SPIs (`AuditDomainHook`, `AuditActivityEnrichHook`), both now implemented in
`marketplace-orchestrator` (`org.ost.orchestrator.spi`) — see `marketplace-orchestrator/CLAUDE.md`'s
"Forwarder SPI pattern" for why the two implementations that still need a UI-shell resource go
through a forwarder SPI instead of living directly in `marketplace-app`.

**Consequences:** Rejected: conditional Spring annotations in `platform-commons` — contracts must
be Spring-free pure Java.

---

## ADR-007: @EnableMethodSecurity active; @PreAuthorize not at class level on services
**Status:** Accepted

**Context:** Vaadin view beans are initialized on the first HTTP request — before the user
authenticates. Class-level `@PreAuthorize` on services breaks this initialization with
`AuthorizationDeniedException`.

**Decision:** `@EnableMethodSecurity` added to `SecurityConfig`. `@PreAuthorize("isAuthenticated()")`
NOT applied at class level on services. `/health` endpoint is intentionally public.
Future non-public REST endpoints use `@PreAuthorize` at method level on the controller.

**Consequences:** Rejected: class-level `@PreAuthorize` on `AdvertisementService`,
`AuditReadService`, `UserSettingsService` — confirmed broken via smoke tests.

---

## ADR-008: Double-click guard on save buttons via setEnabled
**Status:** Accepted, mechanism superseded by ADR-020 (2026-07-01, corrected 2026-07-16)

**Context:** Rapid double-click submits the form twice, causing duplicate saves.

**Decision:** Save buttons are disabled on click to prevent a double submit.

**Consequences (updated 2026-07-16 — verified against current code, original text was stale):**
The guard no longer lives in each handler's `activate()` with a `finally` block re-enable, as
originally described here. It now lives in shared
`AbstractFormOverlayModeHandler.wireSaveGuard()` (`saveBtn.setEnabled(false)` on click, no
`finally` block anywhere in this flow) — re-enabling happens instead via `afterSave(boolean)` →
`updateButtons()` in each concrete handler (e.g. `AdvertisementFormOverlayModeHandler
.afterSave()`), called unconditionally from every branch of `AbstractEntityOverlay.handleSave()`.
This is ADR-020's later, more general lifecycle mechanism — this entry should have been marked
Superseded when ADR-020 landed and wasn't; corrected here instead of duplicating ADR-020's fuller
description.

---

## ADR-009: ValidRange.List without @Constraint; ValidRangeValidator caches field lookups
**Status:** Accepted

**Context:** Jakarta Bean Validation 3.0 spec says container annotations should carry
`@Constraint(validatedBy = {})`, but Hibernate Validator (Spring Boot 4.0.6) does NOT support
this for class-level (`ElementType.TYPE`) constraints — adding it causes `HV000030: No validator
could be found` at runtime.

**Decision:** The inner `@List` annotation on `@ValidRange` intentionally omits
`@Constraint(validatedBy = {})`. `ValidRangeValidator` caches reflected fields in a static
`ConcurrentHashMap` keyed by `FieldKey(class, start, end)`.

**Consequences:** Rejected: adding `@Constraint(validatedBy = {})` to `ValidRange.List` —
confirmed broken at runtime via smoke tests.

---

## ADR-010: Restore semantics — restore TO snapshot, not BEFORE snapshot
**Status:** Accepted

**Context:** The previous behavior restored to the state *before* a change, not *to* that
snapshot — inverting the UX expectation.

**Decision:** Clicking a restore button restores the entity to the state captured in the clicked
entry's snapshot, via `AuditPort.getSnapshotContent(snapshotId, entityType)`. Diff display works
directly from snapshot pairs, with no separate "previous snapshot" lookup. Every restore entry
point — `AdvertisementFormOverlayModeHandler.handleRestoreFromActivity`,
`UserFormOverlayModeHandler`'s equivalent, `SettingsFormModeHandler.handleRestoreFromActivity`,
`TaxonFormOverlayModeHandler`'s own restore flow, and `CityFormOverlayModeHandler`'s — maps the
snapshot directly into its own `*EditDto` and calls `loadRestored(dto)`; the actual persistence
happens only once the user explicitly saves the form.

**Consequences:** There is no dedicated `*.restoreToSnapshot()` server-side method anywhere in the
codebase — restoring is client-side form population only, going through each domain's normal
`save()` path.

---

## ADR-012: No shared UI module — plain-class pattern for future sharing
**Status:** Accepted

**Context:** No actual cross-module UI duplication exists as of 2026-05-26. `PaginationBar` and
`EmptyStateView` exist only in `marketplace-app`. Multiplying modules has real cost.

**Decision:** No new UI module (`advertisement-ui-core` or similar). If cross-module sharing ever
becomes real, move the component to `platform-commons` as a plain class (no `@SpringComponent`,
no `@Scope`). Each consuming module declares its own `@Bean @Scope("prototype")` in a local
`@Configuration`.

**Consequences:** Rejected: separate `advertisement-ui-core` module.
Rejected: making `platform-commons` a starter — commons has no Vaadin dependency.
Prerequisite for any component moved to commons: remove all marketplace-specific imports
(`I18nKey`, `I18nParams`, `PaginationDefaults`) — pass as constructor parameters instead.
`AuditUiPort` (an earlier SPI indirection layer between marketplace UI and the audit snapshot
binder) was removed for the same reason — no second consumer ever existed — leaving
`OverlayFormBinder` used directly by marketplace-app UI components; do not re-introduce it.

---

## ADR-013: AbstractViewOverlayModeHandler — template method for view overlays; tab machinery has since moved to AbstractFormOverlayModeHandler
**Status:** Superseded by current code, corrected 2026-07-27 (see ADR-057, which already removed
this class's secondary/tertiary-tab machinery — this entry hadn't been updated to match)

**Context:** `AdvertisementViewOverlayModeHandler` and `UserViewOverlayModeHandler` had identical
tab-switching and lazy-loading boilerplate (~15 lines each).

**Original decision (no longer current):** all "view mode" overlay handlers extended
`AbstractViewOverlayModeHandler`, which provided the tab-switching machinery described below
(`SecondaryTabDef`/`TertiaryTabDef` records, `buildSecondaryTab()`/`buildTertiaryTab()` hooks).

**Current reality:** `AbstractViewOverlayModeHandler` now has exactly two abstract methods —
`buildPrimaryContent()` and `buildHeaderActions()` — and no tab machinery at all (confirmed by
reading the class directly; this shrinking is ADR-057's doing, "dead since the Timeline-tab
extraction"). The tabbed-content pattern this ADR originally described now lives on
`AbstractFormOverlayModeHandler.buildTabbedContent(Tabs, Tab, Div, Supplier<Component>)` instead —
a different base class, used by *form* handlers (the Edit/Activity tab pair), not view handlers.

**Consequences:** Rejected (still applies): `TabbedOverlayContent` as a Spring `@Prototype`
`Configurable` bean — passing live UI components as `Parameters` violates the convention that
Parameters carry data/config, not pre-built component trees.

---

## ADR-014: Audit writes belong in the service layer, not the UI
**Status:** Accepted

**Context:** `SettingsOverlay.handleSave()` was calling `auditPortFactory.captureUpdate(...)`
directly — the only place in the entire UI layer that fired an audit write. All other entities
correctly placed audit calls inside the service.

**Decision:** `AuditPort.captureUpdate` / `captureCreation` / `captureDeletion` must only be
called from `*Service` classes — never from UI overlays, view components, or `*HookImpl` classes.
`UserPreferencesService.save()` now loads old settings, saves, publishes the domain event, and
captures the audit entry.

**Consequences:** When a save/update/delete operation needs to be audited, put the `AuditPort`
call at the end of the corresponding `*Service.save()` / `*Service.delete()` method. The UI
layer calls the service; the service handles persistence + audit atomically.

---

## ADR-015: *HookImpl — no snapshot inspection, pure delegation
**Status:** Accepted

**Context:** `AuditDomainHookImpl.resolveDisplayName()` was directly accessing `ad.title()` and
`u.name()` from snapshot DTOs and applying a null guard — business logic embedded in a hook.

**Decision:** `*HookImpl` methods must not access fields of snapshot DTOs or apply any
formatting/resolution logic. Each switch branch must contain exactly one service call.
Entity-type routing (a `switch` over `EntityType`) is the only permitted logic in a `*HookImpl`.

**Consequences:** If a `*HookImpl` method needs entity-specific data, add a method to the
corresponding `*Service` and delegate.

---

## ADR-016: Full user decoupling — marketplace no longer imports org.ost.user.* internals
**Status:** Accepted

**Context:** marketplace-app previously imported `User` entity, `UserService`, `UserSettingsService`,
and `UserPrincipal` directly — violating the module import rule.

**Decision:**
- `AuthenticatedPrincipal` SPI added to `platform-commons` (`org.ost.platform.user.spi`).
- `UserPrincipal` (user-starter) implements `AuthenticatedPrincipal.toUserDto()`.
- `AuthContextService` rewritten: reads `AuthenticatedPrincipal` via pattern matching; returns `Optional<UserDto>`.
- `UserPort` gains `save(UserProfileDto, Long actingUserId)` and `refreshCurrentUserInContext(Long userId)`.
- All 22 files now use `UserDto`/`UserPort` from platform-commons exclusively.

Key design — `refreshCurrentUserInContext` in `UserPort`: updating the Spring Security principal
requires `UserPrincipal(User)` — which needs the password hash (not in `UserDto`). Moving this
responsibility into `UserPortImpl` avoids exposing the `User` entity or password hash across the
module boundary.

**Consequences:** `UserSortMeta` and `UserQueryConfig`: `User.Fields.*` replaced with string
literals (field names are stable). `UserMapper` maps `UserDto → UserEditDto`.

---

## ADR-018: Top-level Timeline tab
**Status:** Accepted (done 2026-06-23)

**Context:** Per-overlay timeline queried by `actor_id` only. A top-level tab with proper filters
gives full audit context without navigating into individual overlays.

**Decision:** Added a dedicated **Timeline** navigation tab (alongside Listings and Users) with
filter, sort, and pagination. Inline timeline tabs removed from `UserOverlay` and `SettingsOverlay`.

Components: `TimelineView` (`@UIScope`), `TimelineQueryBlock`, `AuditTimelineListRenderer`,
`AuditTimelineRowRenderer`, `PaginationBar`. Visibility gated by `access.canView()`.
USER role: only own activity (actor filter forced server-side by `AccessEvaluator`).

**Consequences:** Key lesson: `TimelineView` must override `setVisible(boolean)` to call
`refreshFeed()` — tab switching uses `setVisible()`, not component detach/attach, so
`@PostConstruct` alone produces stale data after mutations.

---

---

## ADR-019: Taxon/Category domain extracted as standalone starter
**Status:** Accepted (done 2026-06-26)

**Context:** Advertisement categories (and future tags) are a classification taxonomy that:
- spans multiple entity types (`ADVERTISEMENT`, potentially `USER`, etc.)
- requires soft-delete + restore (same as advertisement/user)
- needs multilingual translations (locale-keyed)
- must be audited (category assignments recorded in audit_log)

Embedding this inside `advertisement-spring-boot-starter` would couple two distinct domains and
prevent reuse across other entity types.

**Decision:** New `taxon-spring-boot-starter` module owns:
- `taxon`, `taxon_translation`, `taxon_assignment` tables (Liquibase changelog at `db/taxon-changelog/`)
- `Taxon`, `TaxonTranslation`, `TaxonAssignment` entities
- `TaxonService`, `TaxonAssignmentService` — business logic
- `DefaultTaxonPort` — coordination layer (not pure delegation: it resolves translations and builds DTOs)
- `TaxonProperties` — configurable `defaultLocale` for translation fallback

SPI contracts in `platform-commons`:
- `TaxonPort` (marketplace → starter) — CRUD, assignment management, batched queries

Marketplace-app additions:
- `TaxonManagementView` + `TaxonOverlay` + `TaxonFormOverlayModeHandler` + `TaxonViewOverlayModeHandler`
- `ReferenceDataView` — tab container for taxon management (nested sub-tabs)
- **Resolved 2026-07-17:** this ADR originally required a dedicated
  `TaxonAuditHook` → `TaxonAuditHookImpl` → `TaxonActivityService` chain recording category
  assignment changes to the audit log independently. That chain was never built (`TaxonAuditHook`
  had zero implementations; `TaxonActivityService` never existed). Investigation found the
  underlying need was already met differently: every taxon assignment change in this codebase
  happens exclusively inside an advertisement save/delete (`AdvertisementSaveService`/
  `AdvertisementService`, both via `TaxonPort.replaceAssignments()`), which already produces its
  own audit snapshot capturing the before/after category set
  (`AdvertisementSnapshotDto.categoryIds`). The actual, narrower bug was that the Timeline tab
  rendered raw taxon ids instead of resolved names in that diff (the Activity tab already resolved
  them) — fixed in `AdvertisementEnrichService`. `TaxonAuditHook` was removed entirely rather than
  implemented, along with the unused `TaxonPort.assign()`/`unassign()`/`findByCode()` methods
  (zero callers).

`TaxonType` enum (in `platform-commons`) — was a closed set of just `CATEGORY` when this ADR was
written; **`CITY` was added since (F-02, ADR-065)**, confirming the "release-level change" this
paragraph warned about did happen, with UI/audit/seed-entry work exactly as anticipated.

Advertisement filtering by category: `AdvertisementRepository` calls
`TaxonPort.findEntityIdsWithAnyTaxon()` to translate taxon ids into entity ids — no direct
join to `taxon_assignment` table from advertisement code.

**Consequences:**
- `EntityType.TAXON` added to `platform-commons` for audit records of taxon entity changes.
- `ReferenceDataView` added as a new top-level navigation tab with sub-tabs per taxon type.
- `DefaultTaxonPort` is permitted to contain coordination logic (translation fallback chain,
  DTO assembly) because the alternative would require exposing TaxonTranslation internals
  to marketplace-app — that would be a worse boundary violation.

---

---

## ADR-020: AbstractEntityOverlay<H> — currentFormHandler and save/cancel lifecycle in base class
**Status:** Accepted (done 2026-07-01)

**Context:** Concrete overlay classes (`AdvertisementOverlay`, `UserOverlay`, `TaxonOverlay`,
`SettingsOverlay`) each duplicated the same `currentFormHandler` field, `handleSave()` try/catch
block, `doCancel()` dispatch, and `hasUnsavedChanges()` check. Any fix in one overlay had to be
applied to all others.

**Decision:** `AbstractEntityOverlay<H extends AbstractFormOverlayModeHandler<?>>` now owns:
- `protected H currentFormHandler` — typed field for the active form handler
- `protected final handleSave()` — shared save lifecycle (try/catch, `afterSave(bool)`, `proceed()`)
- `protected final doCancel()` — calls `currentFormHandler.discardChanges()`, then `afterDiscard()`
- `protected final hasUnsavedChanges()` — null-safe check via `currentFormHandler.hasChanges()`

Concrete overlays implement abstract hooks: `saveConfig()`, `proceed()`, `afterDiscard()`.

**Consequences:**
- `switchTo()` must reset `currentFormHandler = null` before the switch expression — without this,
  after `VIEW → EDIT → VIEW` the handler remains non-null and `hasUnsavedChanges()` returns `true`.
- `SaveConfig` record `(I18nKey success, I18nKey validFailed, I18nKey saveError, I18nKey conflict)`
  declares error key mapping per overlay type — `conflict` added by ADR-029 (optimistic-locking
  UI) after this ADR was written; noted here 2026-07-13 since this ADR's body was never amended.

---

## ADR-021: QuillEditor — custom Vaadin web component for rich-text editing
**Status:** Accepted (done 2026-07-01)

**Context:** Advertisement descriptions require rich-text formatting (bold, italic, links, paragraph
blocks). Vaadin's built-in `TextArea` is plain-text only; embedding Quill as a web component
gives native Vaadin field integration with value binding.

**Decision:** `QuillEditor` extends `AbstractSinglePropertyField<QuillEditor, String>`, registered
via `@Tag("quill-editor")`, `@NpmPackage(value="quill", version="2.0.3")`, and `@JsModule("./quill-editor.js")`.
Implements `HasSize` and `HasLabel` for standard Vaadin field behavior. The custom element handles
Quill initialization and bidirectional value sync.

**Consequences:**
- `quill-editor.js` lives in `marketplace-app/src/main/frontend/` (Vaadin Vite entry).
- HTML output is sanitized server-side in `AdvertisementService` using OWASP HTML Sanitizer
  (`Sanitizers.FORMATTING.and(LINKS).and(BLOCKS)`) — server never trusts raw HTML from the client.
- Used in `AdvertisementFormOverlayModeHandler` and `TaxonFormOverlayModeHandler` for description fields.

**Update (2026-07-04) — false-dirty-state bug and its proper fix:** editing an advertisement
whose description contained rich HTML made Save/Discard appear active immediately on opening
the form, with no actual edit. Root cause: `quill-editor.js` loaded/restored content via direct
`this.__quill.root.innerHTML = ...` assignment, bypassing Quill's API entirely. Quill's own
MutationObserver-based change detection treats any DOM mutation it didn't originate through its
own API as an external, `'user'`-sourced change — so loading a value re-triggered Quill's HTML
serializer (`getSemanticHTML()`), which normalizes rich HTML (quotes/attributes/tag structure)
differently byte-for-byte from what was stored, and dispatched a spurious `value-changed` event
that Vaadin's `Binder` picked up as a real edit (`Binder` has no `fromClient`-aware guard against
this — confirmed against decompiled `flow-data` bytecode).

An earlier, undocumented attempt to fix this (`awaitingNormalization` flag + `setPresentationValue`/
`setModelValue` overrides in `QuillEditor.java`, added 2026-07-03 in commit `94647d9e` under an
unrelated "docs sync" commit message) did not work, because it assumed `Binder` inspects
`fromClient` on the changed binding — it doesn't.

**Proper fix:** use Quill's own native `source` parameter instead of custom bookkeeping. All
programmatic content loads (`connectedCallback()` initial load, `set value()`) now go through
`quill.setContents(delta, 'silent')` — Quill's `'silent'` source never fires `text-change` at
all, so there is no echo to filter. The `text-change` listener needs no source check: since our
own writes never reach it, anything that does fire there is a genuine change (real typing,
toolbar clicks, or an external API call like `quill.setContents(delta)` without an explicit
source — which Quill tags `'api'`, not `'user'`; an earlier draft of this fix filtered on
`source === 'user'` and broke Playwright's rich-text test helper, which calls `setContents`
directly without a source argument). The dead `awaitingNormalization` mechanism in
`QuillEditor.java` was removed as part of this fix — see the corresponding commit for both files.
Confirmed this is also how Vaadin's own (commercial, CVALv3-licensed) `RichTextEditor` add-on
solves the identical problem internally (its Quill-wrapping mixin uses the same `source`
tagging), validating the approach against a production-proven precedent without needing the
paid component itself.

---

## ADR-022: isCurrentState — criteria for "Current state" badge vs Restore button
**Status:** Accepted

**Context:** The audit activity panel shows a "Current state" badge on historical versions that
match the current entity state, and a "Restore" button on versions that differ. The criteria for
equality must cover all user-visible fields so that the badge only appears when restoring would
have no effect.

**Decision:** A historical snapshot is considered "current state" when its full
`AdvertisementSnapshotDto` record equals the current one via `Objects.equals` — covering `title`,
`description`, `categoryIds`, and `attachmentSnapshotId` (a soft reference to the attachment
gallery's current snapshot id). Media is compared as part of the same single record-equality
check as every other field, since `attachmentSnapshotId` lives directly on the record — no
separate hook call. `AuditActivityRowRenderer` computes the badge with exactly one line:
`Objects.equals(h.snapshotData(), cfg.currentSnapshot())`. If any field differs, the "Restore"
button is shown; if all are identical, the badge is shown.

`AdvertisementSnapshotDto` stores `categoryIds` as a `List<Long>` of numeric category ids, sorted
at construction time, compared and diffed via `Objects.equals`/`FieldChange` — the diff display
formats ids to a comma-separated string only for the UI, the stored/compared value itself is
numeric ids. `CategoryChangeSnapshotDto` and the `AuditableSnapshot.isVisible()` mechanism do not
exist — category-change information rides in the main `AdvertisementSnapshotDto` diff; there is no
row-hiding machinery.

---

## ADR-023: TransactionTemplate orchestrator — preferred pattern for multi-step backend operations
**Status:** Accepted

**Context:** Some save flows require calling multiple ports in a specific order within one
transaction. The canonical example: `advertisementPort.save()` → `captureSnapshot` →
`taxonPort.replaceAssignments()`. With `@Transactional` on service methods, the order is
controlled by implementation details buried inside each starter's service — invisible to the
caller. Self-invocation tricks (inner `@Component`, self-injection) work but obscure intent.

**Decision:** Use `TransactionTemplate` in a dedicated `*SaveService` (or `*Orchestrator`) class
in `marketplace-app` whenever a save flow requires:
- calling two or more ports in a controlled order, AND/OR
- mixing non-transactional work (S3, external calls) with transactional DB writes

Pattern:
```java
@Service
@RequiredArgsConstructor
class FooSaveService {
    private final TransactionTemplate tx;

    public Long save(FooSaveDto dto, Long actorId,
                     Runnable preCommitExternal,  // S3, external — outside TX
                     Runnable captureSnapshot) {  // DB only — inside TX
        preCommitExternal.run();                  // outside transaction

        return tx.execute(status -> {
            Long savedId = fooPort.get().save(dto, actorId);
            captureSnapshot.run();                // before side-effects that shift audit window
            barPort.ifAvailable(p -> p.doSideEffect(savedId, dto));
            return savedId;
        });
    }
}
```

`TransactionTemplate` is auto-configured by Spring Boot from `PlatformTransactionManager` —
inject directly, no `@Bean` declaration needed. Inner port `@Transactional` methods join the
existing TX via default `REQUIRED` propagation.

**Consequences:**
- Transaction boundary is explicit and visible in code — not hidden behind `@Transactional` on a
  deeply nested service method.
- Non-transactional work (S3) is clearly separated from DB work by position in the method.
- Order of operations is enforced structurally, not by convention.
- Rejected: inner `@Component` static class — workaround that obscures why the extra class exists.
- Rejected: self-injection (`@Lazy @Autowired FooService self`) — same workaround category.
- Rejected: two separate `@Transactional` service classes — atomicity not guaranteed across calls.

---

## ADR-024: Jsoup-based, defense-in-depth description length validation
**Status:** Accepted (done 2026-07-04)

**Also affects:** advertisement-spring-boot-starter

**Context:** `AdvertisementSaveDto.DESCRIPTION_MAX_LENGTH = 2000` existed as a constant but was
enforced only by a client-reachable, regex-based binder validator
(`html.replaceAll("<[^>]+>", "")` + length check) that tag-spam could bypass — formatting tags
like `<b></b>` survive the OWASP sanitizer (it preserves allowed tags), so thousands of empty
ones pass the stripped-text check while still bloating the stored HTML
(`backlog/completed/issues/issue-description-length-tag-spam.md`). No server-side length
guard existed at all — a direct port call bypassing the UI could persist an unbounded
description.

**Decision:** three-layer validation, none of them removable without reopening a gap:
1. **Raw-size cap** — `@Size(max = DESCRIPTION_RAW_MAX_LENGTH = 20_000)` on
   `AdvertisementSaveDto.description` (`platform-commons`) — plain Bean Validation, no new
   dependency, bounds worst-case payload size before any parsing.
2. **UI content check** — `AdvertisementFormOverlayModeHandler`'s binder validator replaced
   the regex with `Jsoup.parse(html).text().length() <= DESCRIPTION_MAX_LENGTH`.
3. **Service-level guard** — `AdvertisementService.sanitizeHtml()` calls a new
   `validateDescriptionLength()` using the same Jsoup check on the *sanitized* HTML, throwing
   `IllegalArgumentException` on overflow. No new exception-handling plumbing was needed:
   `AbstractEntityOverlay.handleSave()` already has a generic `catch (Exception e)` that shows
   `e.getMessage()` via `NotificationService`.

`org.jsoup:jsoup:${jsoup.version}` (`1.22.1`, new root-pom property, same pattern as
`aws-s3-sdk.version`) was added directly to `marketplace-app` and
`advertisement-spring-boot-starter` — **not** to `platform-commons`, keeping the shared kernel
dependency-free per its governance rules. The one-line Jsoup check is intentionally duplicated
in both layers (UI for fast feedback, service for real enforcement) rather than factored into
a shared utility — a single line of logic does not justify a cross-module abstraction, and the
two layers have no natural common module to live in without violating the starter/UI
boundary.

**Consequences:**
- The Quill UI character counter and `advertisement.description` DB column limit were unblocked
  by this ADR and have since been added.
- Any future field with the same "rich HTML but bounded visible text" shape should follow the
  same three-layer pattern rather than reaching for `@Size` directly on the HTML string.

---

## ADR-025: Deny-by-default at the Spring Security URL layer is incompatible with this app's Vaadin routing model

**Status:** Accepted

**Context:** Hardening `SecurityConfig` from `anyRequest().permitAll()`
to `anyRequest().denyAll()` (permitting only `vaadinInternalRequestMatcher` and `/health`), to
guard against future public REST endpoints being accidentally exposed, was proposed and tried.
Deployed and tested via
the full Playwright e2e suite: **0/46 tests passed** — the root page itself never rendered.

Root cause, confirmed via `HandlerHelper.isFrameworkInternalRequest`'s actual implementation:
it matches only Vaadin's internal AJAX/RPC traffic (UIDL, heartbeat, push, upload, dynamic
resource requests) — **not** the root page bootstrap (`GET /`) that serves the application shell.
Since `MainView` is the sole `@Route("")` and the entire app is one Vaadin SPA behind that single
route, `anyRequest().denyAll()` blocks the application shell itself, not just hypothetical future
REST endpoints.

This is consistent with ADR-007: access control for Vaadin views in this app is enforced *inside*
the view (`AccessEvaluator`, hidden tabs), not via Spring Security URL matching — a direct
consequence of Vaadin initializing view beans before authentication completes.

**Decision:** Reverted to `anyRequest().permitAll()`. Deny-by-default at the URL layer does not
apply to a monolithic Vaadin SPA; instead, each future non-Vaadin REST controller (webhooks,
sitemap, etc.) must declare its own explicit `requestMatchers(...)` rule ahead of the Vaadin
catch-all, as a discipline/process rule rather than a global switch.

**Consequences:**
- `SecurityConfig` keeps `anyRequest().permitAll()`; only `/health` and the Vaadin internal
  matcher get explicit rules.
- Any new REST controller added later must add its own security rule *before* the catch-all —
  reviewers should treat a missing explicit rule on a new controller as a blocker.

---

## ADR-026: Rate limiting counts only real failures, never successful attempts

**Status:** Accepted

**Context:** Caffeine-based rate limiting was added to `AuthService.login()`
(`org.ost.marketplace.services.auth` — corrected 2026-07-13; `AuthService` lives in
marketplace-app, not `org.ost.user.services`) and `UserService.register()` (`org.ost.user.services`,
correct as originally written). The first version incremented the attempt counter on *every* call
regardless of outcome. This broke the Playwright e2e suite:
all signups run from the same client IP (the test-runner container), and the 6th successful
signup within the 15-minute window was rejected as "too many attempts" — the generic
`catch (Exception ex)` in `SignUpDialog` then misreported it as "email already registered",
masking the real cause.

**Decision:** Both limiters now increment only on an actual failure:
- `AuthService.login()` — increments on `BadCredentialsException`, invalidates the cache entry
  on success (unchanged from the original design — this one was correct from the start).
- `UserService.register()` — increments only on `DuplicateKeyException` from `repository.save()`
  (a TOCTOU race past the client-side email-uniqueness check); the client-side binder validator
  already rejects duplicate emails before `register()` is ever called, so this path is rare by
  design. Successful registrations never count toward the limit.

Both dialogs (`LoginDialog`, `SignUpDialog`) catch `IllegalStateException` (thrown when the
limit is exceeded) separately from the generic exception handler, showing a dedicated
"too many attempts, try again later" notification (`LOGIN_ERROR_TOO_MANY_ATTEMPTS`,
`SIGNUP_ERROR_TOO_MANY_ATTEMPTS`) instead of a misleading generic error.

**Consequences:**
- e2e coverage: `02-marketplace-authentication-flow.spec.js` — `rateLimitUser exceeds login
  attempts` test signs up a dedicated throwaway user and drives 5 wrong-password attempts + a
  6th blocked attempt, to avoid locking out shared `TEST_USERS` accounts used by later specs.
- No equivalent e2e test exists for the registration limiter's `DuplicateKeyException` path —
  it is not reachable through the normal UI flow (client-side check intercepts first) and would
  require bypassing that check to force a real race.

---

## ADR-027: forward-headers-strategy so registration rate limiting sees the real client IP

**Status:** Accepted

**Context:** An external audit pass (round 7) found that `UserService.register()`'s rate
limiter (ADR-026) keys solely on `clientIp` (`request.getRemoteAddr()` from
`SignUpDialog`), with no email component (unlike the login limiter, which keys on
`remoteAddr + "|" + email` and stays scoped to one target account even if `remoteAddr`
collapses). The project's README documents the deployed target as Render, which — like
essentially all PaaS providers — terminates the connection at its own edge and forwards to the
app instance over its internal network. `server.forward-headers-strategy` was not configured
anywhere, so `request.getRemoteAddr()` returned Render's internal proxy address for every
user, not the real client IP. This collapsed the registration limiter into one shared bucket
for the entire platform: 5 failed registrations from anyone (even organic
`DuplicateKeyException` races) would lock out registration for everyone for 15 minutes.

**Decision:** `application-prod.yml` — added `server.forward-headers-strategy: framework`, so
Spring's `ForwardedHeaderFilter` translates `X-Forwarded-For` into the request's apparent
remote address before the app sees it. This assumes Render forwards this header (standard
PaaS behavior) — not independently verifiable from this dev environment; needs confirming
once actually deployed.

A coarser global (IP-independent) backstop limiter was considered and rejected: registration
failures don't have a natural key to count against the way login does (a login failure is
always a guess against one specific existing account; a registration failure is a random
collision with whatever email happened to already be taken, essentially never the same email
retried), so a second counter added complexity without a clean justification for this
codebase's actual threat model. Fixing the IP resolution itself is the real fix.

**Consequences:**
- The login limiter (`AuthService`) was not touched — its compound key already scopes lockout
  to one target account even under a fully collapsed `remoteAddr`.

---

## ADR-028: SettingsPaginationService — per-user ownership on bindings, detach-based cleanup

**Status:** Accepted

**Context:** `SettingsPaginationService` is a singleton `@Component` holding a
`CopyOnWriteArrayList<BindingEntry>` shared across every user's UI session. Two problems found
via external audit + internal verification:
1. **Cross-session bleed (bug):** `onSettingsChanged(userId, settings)` only checked whether
   the *current thread's* user matched `userId`, then pushed the new page size to **every**
   registered `PaginationBar` across **all** sessions — so when user X changed their page size,
   users Y and Z had their live grids silently resized to X's value until they next reloaded
   the view. Invisible in single-user e2e (only one logged-in user drives each test).
2. **UI-reference leak risk:** the singleton held strong references to `PaginationBar` (→ the
   whole UI subtree) with cleanup relying entirely on views calling `unregister()` from
   `@PreDestroy`; any path that skips that callback pins a dead UI tree in the service forever.

**Decision:** `BindingEntry` now carries the owning `userId` (captured from
`AuthContextService` at `register()` time). `onSettingsChanged` filters `entries` by
`entry.userId().equals(userId)` instead of gating on the current thread's user — this fixes the
bleed regardless of which thread fires the hook, and drops the now-redundant
`authContextService.getCurrentUser()` pre-filter entirely. `register()` also adds
`bar.addDetachListener(_ -> unregister(bar))`, so cleanup no longer depends solely on
`@PreDestroy` being called correctly on every path; `unregister()` remains for the explicit
call.

**Consequences:**
- `SettingsPaginationBinding` and the three call sites (`AdvertisementsView`, `TimelineView`,
  `UserView`) are unchanged — same `register`/`unregister` signatures.
- e2e coverage: extended `05-seed-filter-sort-pagination.spec.js` — `adminEn changes page
  sizes...` test now opens a second browser context logged in as `userEn` right after `adminEn`
  changes their own page size, and asserts `userEn`'s Advertisements grid still shows the
  default page size (unaffected).

---

## ADR-029: Optimistic locking via a stored version column — no auto-reload on conflict

**Status:** Accepted

**Context:** No entity carried a version field; two concurrent edits of the same advertisement,
user, or taxon resulted in silent last-write-wins — the second save overwrote the first with no
error, audit anomaly, or warning to either editor. With the planned community
migration (24k members, moderators/owners editing shared listings) this stops being a rare
accident. Several alternatives were discussed and rejected before settling on this design:
- Computing a "version" on the fly from `audit_log` (the way the audit timeline already does via
  a `ROW_NUMBER()`/`COUNT(*)` window function, see audit-spring-boot-starter ADR-020) — rejected:
  audit is optional (`ComponentFactory<AuditPort>.ifAvailable()`), so core save correctness can't
  depend on it, and not every bespoke UPDATE writes an audit row.
- Comparing `updated_at` instead of adding a column — rejected: equality on a high-precision
  timestamp across a DB → Java `Instant` → JSON/DTO round trip is fragile (rounding, truncation);
  an integer counter never has that class of bug, which is why every mainstream ORM (Rails
  `lock_version`, Hibernate/JPA `@Version`, EF Core `rowversion`) uses one.
- A full-row compare (`WHERE title = :old AND description = :old ...`) — avoids a new column and
  isn't precision-fragile, but flags a conflict on *any* field touched by someone else, even one
  the current editor never looked at. Rejected in favor of the simpler, standard column.

**Decision:** `version BIGINT NOT NULL DEFAULT 0` added directly to the existing
`01-advertisement-schema.xml`, `01-user-schema.xml`, and `001-taxon.xml` changesets (DB not yet
in production — no new migration file, per the same rationale as the `taxon.deleted_by` column;
requires `deploy-and-run.sh --reset` locally). `@Version private Long version;` added to `Advertisement`,
`User`, `Taxon`.

For `Advertisement` and `Taxon`, saves go through `CrudRepository.save()`, so Spring Data JDBC's
`@Version` handling applies natively — it appends `WHERE version = ?` and throws
`OptimisticLockingFailureException` on a mismatch. Two places rebuild the entity via `Builder`
before saving and were missed on the first pass because they don't automatically carry the field
forward: `AdvertisementService.buildEntity()` and `TaxonService.update()` — both now explicitly
forward the version from the incoming DTO / port parameter (the value the caller last read),
**not** a version re-fetched inside the same save method (which would just match itself and
detect nothing).

`User`'s real edit path (`UserService.save()` → `UserRepository.updateProfile()`) originally
bypassed `CrudRepository` entirely via hand-written SQL, so `@Version` alone did nothing there.
`updateProfile()` did the check by hand: `SET ..., version = version + 1 WHERE id = :id AND
version = :version`, throwing `OptimisticLockingFailureException` manually when zero rows match.

**Update (2026-07-14) — `User` moved onto native `CrudRepository.save()` too:** a second, narrower
entity, `UserProfileUpdate` (later renamed `UserEditableFields`, and its repository
`UserProfileCrudRepository` renamed `UserEditableFieldsCrudRepository` — see
`user-spring-boot-starter/CLAUDE.md`; `id`, `name`, `role`, `updatedAt`, `version` — deliberately
excludes `email`/`passwordHash`), mapped to the same `user_information` table via its own
dedicated repository, replaces the hand-written SQL in `UserRepository.updateProfile()`.
This was chosen over mirroring `AdvertisementService.buildEntity()`/`TaxonService.update()`
(rebuild the full entity via `Builder`, forwarding every unedited field from `before`) precisely
because that pattern's known failure mode — forgetting to forward a field — is far more dangerous
for `User` than for `Advertisement`/`Taxon`: dropping `passwordHash` or `email` silently breaks
login or notifications, not just a lock-check regression. Since `passwordHash`/`email` are not
mapped properties on `UserProfileUpdate`, the generated `UPDATE` cannot reference them regardless
of builder mistakes — the risk is closed at the type level, not by discipline. See
`user-spring-boot-starter/CLAUDE.md`.

`softDelete` on `Advertisement` and `Taxon` also got the same manual guard (an admin/owner
deleting a listing while someone else is mid-edit should not silently win); `updateMedia`,
`updateLocale`, and `TaxonRepository.restore()` were left unguarded — none of them represent a
user-authored edit that competes with a live form.

The version travels end-to-end: `AdvertisementInfoDto`/`UserDto`/`TaxonDto` (read side, RowMapper
+ SELECT column added where hand-rolled) → `AdvertisementEditDto`/`UserEditDto` (MapStruct maps
the field by name automatically) → `AdvertisementSaveDto`/`UserProfileDto` (write side) →
`TaxonPort.update()`/`softDelete()` and `AdvertisementPort.delete()` gained a trailing `version`
parameter since they had no DTO to carry it (see platform-commons ADR-019).

`AbstractEntityOverlay.handleSave()` gained a `catch (OptimisticLockingFailureException)` before
the generic `catch (Exception)`, showing a dedicated conflict notification
(`*_NOTIFICATION_CONFLICT` i18n keys, one per domain) instead of the generic save-error message.
`SaveConfig` record gained a fourth `conflict` component; `SettingsOverlay` (which has no version
field) passes `null` for it, same as its existing `null` validation/save-error keys.

**Deliberately not done:** no automatic form reload on conflict. Silently replacing the editor's
in-progress form with fresh server data would destroy their unsaved changes without them
noticing — a different flavor of the same "silent data loss" bug this feature exists to fix. The
user sees the conflict notification and must manually cancel/reopen to get a fresh version and
retry — safer than a clever auto-merge for a first version of this feature.

**Consequences:**
- e2e coverage: new test in `04-marketplace-advertisement-flow.spec.js` — two browser contexts
  (`userEn`, `moderatorEn`) open the same advertisement for edit before either saves; the first
  save succeeds, the second (stale) save shows the conflict notification instead of overwriting.

---

## ADR-032: Request correlation id via SLF4J MDC, plus closing silent-service logging gaps

**Status:** Accepted

**Context:** Log lines from a single HTTP request/UI action carried no shared identifier —
reconstructing "everything that happened for one save click" across threads meant matching on
timestamps and guessing. `Advertisement.version`/`User.version`/`Taxon.version`
were considered and rejected as a substitute: a version is unique only within
one row, many requests touch zero or several entities, and a failed/conflicting request has no
version to log against at all.

Separately, a review of `log.` coverage across all services found several completely silent
classes — mutating operations with no log line at all, which would leave the new correlation id
with nothing to correlate: `TaxonService` (create/update/softDelete/restore), `AuthService`
(login/logout — a security-relevant gap), `AttachmentService` (upload/delete/addVideo/
softDeleteAll), `TaxonAssignmentService` (assign/unassign/replaceAssignments),
`AttachmentSnapshotService` (captureAndGetId), `UserSettingsService` (save),
`AdvertisementSaveService` (the marketplace-app-level save transaction, distinct from
`AdvertisementService.save()` which already logged), and both cleanup services
(`AuditCleanupService`/`AdvertisementService.cleanup()`, which discarded the deleted-row count).
`LoginDialog` also lacked the generic catch-all exception log that `SignUpDialog` already had.

**Decision:**
1. `RequestCorrelationFilter` (`marketplace-app/config`, extends `OncePerRequestFilter`) puts a
   fresh `UUID.randomUUID()` in MDC under key `requestId` at the start of every HTTP request,
   removed in a `finally` block. Auto-registered by Spring Boot (any `Filter` bean); no
   `SecurityConfig` changes needed.
2. `application.yml`'s console logging pattern gained `%.8X{requestId}` (first 8 hex chars —
   enough to eyeball-correlate without cluttering the console with a full UUID).
3. Alternative considered: `micrometer-tracing` (auto-configured MDC insertion, full
   distributed-tracing spans) — rejected as heavier than needed for a single-instance monolith
   with no downstream services to trace across; revisit if the app ever splits into multiple
   services.
4. Added `log.info`/`log.warn` to every silent mutating method listed above, following the
   existing convention (`"<Entity> <action>: id={}"`). `deleteOlderThan()` in both
   `AuditLogRepository` and `AdvertisementRepository` changed from `void` to `int` (returns the
   JDBC update count) so cleanup services can log how many rows were actually deleted, not just
   that cleanup ran.

**Vaadin-specific caveat:** a single Vaadin UI session spans many HTTP requests (one per server
round-trip), so `requestId` correlates *one round-trip* (e.g. one save-click), not "the whole
time the user had the form open" — that's expected and matches the actual unit anyone wants to
correlate.

**Consequences:**
- Purely additive: no schema changes, no port/hook signature changes except the two
  `deleteOlderThan()` return-type widenings (both had exactly one caller each, both updated).
- Verified end-to-end: `docker logs` shows a distinct `requestId` per login request during the
  e2e run (e.g. `[d96a341c]`, `[121b21da]`, one per HTTP round-trip).
- Full e2e suite 48/48 green.

---

---

## ADR-033: Optional-port UI components use `ComponentFactory<Port>`, never `@ConditionalOnBean` on the component class

**Status:** Accepted

**Context:** `AttachmentGalleryService`, `AttachmentGallery`, and `AuditActivityPanel` inject
`AttachmentPort`/`AuditPort` directly as hard constructor parameters. In a genuinely optional
future starter (payment-, telegram-, ai-spring-boot-starter per the roadmap), this would crash
Spring bean construction the moment the starter is removed from the classpath. Adding
`@ConditionalOnBean(AttachmentPort.class)` / `@ConditionalOnBean(AuditPort.class)`
directly on these three `@SpringComponent` classes so the bean definition itself would not exist
when the port is unavailable ("Option A/C") was proposed.

This was implemented, deployed, and **empirically broke the app** even with every starter present
and every port genuinely available: the full Playwright e2e suite went from 48/48 to 5 failed / 35
skipped. Root cause, confirmed via container logs and source inspection: `@ConditionalOnBean` on a
`@ComponentScan`-discovered class evaluates during regular bean-definition registration, which
happens *before* `@AutoConfiguration` classes register their beans (`AuditPort`'s real
implementation, `DefaultAuditPort`, is registered by `AuditAutoConfiguration`, an autoconfiguration
class). At the point Spring evaluates the condition for `AuditActivityPanel`, `AuditPort`'s bean
definition does not exist yet — the condition fails, `AuditActivityPanel` is silently never
registered, and `SettingsFormModeHandler.activate()`'s `auditActivityPanelFactory.findIfAvailable()`
guard (line 119) silently omits the Activity tab with no exception. The very first e2e test
(admin sign-up, settings check) failed on that missing tab; because that spec file runs in serial
mode, every later test in the file was skipped, and several e2e users were never created — causing
a cascade of unrelated-looking "Invalid email or password" failures in later spec files that
merely tried to log in as those never-created users. One bug looked like five.

**Decision:** `@ConditionalOnBean` must never be used on a `@ComponentScan`-discovered UI component
class (marketplace-app) that depends on a bean registered by a starter's `@AutoConfiguration`
class — the ordering is not guaranteed and the failure mode is silent (no exception, just an
absent tab/feature). Reverted all three `@ConditionalOnBean` additions. Instead, applied the
already-established pattern from `marketplace-app/CLAUDE.md` ("Use `ComponentFactory<T>` for
optional singleton services/ports"):
- `AuditActivityPanel`: hard field `AuditPort auditPort` → `ComponentFactory<AuditPort>
  auditPortFactory`, resolved via `.get()` inside `configure()`.
- `AttachmentGalleryService` / `AttachmentGallery`: hard field `AttachmentPort attachmentPort` →
  `ComponentFactory<AttachmentPort> attachmentPortFactory`; `AttachmentGallery` resolves it once in
  `@PostConstruct init()` into a cached field (kept the rest of its ~15 call sites unchanged).
  `ObjectProvider<T>` (which `ComponentFactory<T>` wraps) is always injectable regardless of
  whether a `T` bean exists — Spring never fails constructor injection on it, deferring the
  "is it actually there" check to whenever `.get()`/`.ifAvailable()`/`.findIfAvailable()` is
  actually called.
- The *availability gate* moved up one level, from checking the wrapping UI component's factory to
  checking the **port's own** `ComponentFactory` — because once the component classes above no
  longer carry `@ConditionalOnBean`, their own `UiComponentFactory<X>.findIfAvailable()` would
  always resolve non-empty regardless of whether the port truly exists, silently defeating
  graceful degradation. Fixed in every call site that previously gated on the wrapping factory:
  `SettingsFormModeHandler`, `UserFormOverlayModeHandler`, `TaxonFormOverlayModeHandler` (gate
  changed from `auditActivityPanelFactory.findIfAvailable()` to `auditPortFactory.findIfAvailable()`
  — `auditPortFactory` already existed as a field in all three, just unused for this purpose), and
  `AdvertisementFormOverlayModeHandler`, `AdvertisementViewOverlayModeHandler`,
  `AdvertisementCardView` (gate changed from `galleryServiceFactory.ifAvailable(...)` to a new
  `ComponentFactory<AttachmentPort> attachmentPortFactory` field's `.ifAvailable(...)`/
  `.findIfAvailable()`).

**Consequences:**
- No `@ConditionalOnBean` remains on any marketplace-app UI component class targeting a
  starter-provided port — establishes the pattern for future genuinely-optional starters.
- Two pre-existing instances of the *same* wrong-level gate (`auditActivityPanelFactory
  .findIfAvailable()` instead of `auditPortFactory.findIfAvailable()`) were found and fixed in
  `TaxonFormOverlayModeHandler` and `UserFormOverlayModeHandler` during this pass — they had not
  yet caused a visible failure only because `AuditActivityPanel` had never carried a conditional
  annotation before, so `findIfAvailable()` on it always happened to resolve
  correctly by accident, not by design.
- Full e2e suite verified 48/48 green after the corrected fix (was 8/48 with the
  `@ConditionalOnBean` approach).

---

## ADR-034: No raw cross-starter SQL joins — bulk-lookup port + service-level enrichment; actor-reference columns follow Taxon's naming convention

**Status:** Accepted

**Also affects:** advertisement-spring-boot-starter

**Context:** `AdvertisementRepository.findAdvertisementById()`/`findByFilter()` did
`FROM advertisement a LEFT JOIN user_information u ON a.created_by_user_id = u.id`, hardcoding
`user-spring-boot-starter`'s table and column names (`user_information`, `u.name`, `u.email`)
inside `advertisement-spring-boot-starter`. This is a stronger violation of "starters must not
depend on each other" (`.claude/rules.md`) than a Java import: there is no class-level
dependency for ArchUnit to detect, only a raw SQL string — a rename in
`user-spring-boot-starter` would break `advertisement-spring-boot-starter` at runtime with zero
compile-time warning. Separately, `advertisement`'s actor-reference columns
(`created_by_user_id`/`last_modified_by_user_id`/`deleted_by_user_id`) embedded the word "user"
for no functional reason, unlike `taxon`'s already-established `created_by`/`updated_by`/
`deleted_by` convention (same kind of value — an opaque `User.id` populated via
`AuditorAware<Long>`).

**Decision:** Mirrors the already-completed `TaxonPort.findByIds()` pattern, applied to `UserPort`:
- `UserPort.findByIds(Set<Long>) -> Map<Long, UserDto>` added to `platform-commons`;
  `UserPortImpl.findByIds()` is pure delegation to `UserService.findByIds()`, which calls a new
  `UserRepository.findByIds(Long[])` (`SELECT ... WHERE id = ANY(:ids)`, same shape as the
  existing `findActorNames()`/`findExistingIds()` bulk lookups).
- `AdvertisementRepository` no longer joins `user_information` at all — `findAdvertisementById()`
  and `findByFilter()` select only `advertisement.created_by` (the FK id, still present as a
  plain column). The three dead `ORDER BY` alias entries for `u.id`/`u.name`/`u.email` were
  removed together with the join — verified dead: `AdvertisementSortMeta` only exposes
  `TITLE`/`CREATED_AT`/`UPDATED_AT`, so no UI path ever built a `Sort` reaching those aliases.
- `AdvertisementService.enrichWithActorInfo(ads)` (new at the time, mirroring
  `enrichWithCategories()`) merges `createdByUserName`/`createdByUserEmail` into
  `AdvertisementInfoDto` after the repository call, in `getFiltered()` and `findById()`.
  `UserPortImpl`/`AdvertisementPortImpl` stay pure delegation — the merge logic lives only in the
  service, per `platform-commons/CLAUDE.md`'s `*PortImpl` rule. **Later relocated:** this
  enrichment step now lives in `marketplace-orchestrator`'s
  `AdvertisementDisplayEnrichmentService.enrichWithActorInfo()` — see
  `marketplace-orchestrator/CLAUDE.md`; `advertisement-spring-boot-starter`'s own
  `AdvertisementService` no longer performs display enrichment at all.
- Renamed `advertisement`'s actor-reference columns to match `taxon`: `created_by_user_id` →
  `created_by`, `last_modified_by_user_id` → `updated_by`, `deleted_by_user_id` → `deleted_by`
  (direct edit of `01-advertisement-schema.xml` — DB not yet in production, same practice as
  every prior schema edit — requires `deploy-and-run.sh --reset` for the Liquibase checksum). Matching
  Java field renames: `Advertisement.createdByUserId`→`createdBy` (`@CreatedBy`),
  `.lastModifiedByUserId`→`updatedBy` (`@LastModifiedBy`); `AdvertisementInfoDto.createdByUserId`
  (platform-commons) and `AdvertisementEditDto.createdByUserId`/`.lastModifiedByUserId`
  (marketplace-app) renamed to match — `createdByUserName`/`createdByUserEmail` keep their names
  since they describe enrichment output, not a `_user_id`-suffixed column.

**Sort-by-author, if ever requested:** do not reintroduce a JOIN (recreates this exact violation)
and do not sort in memory after `enrichWithActorInfo()` (pagination `LIMIT`/`OFFSET` runs in SQL
*before* enrichment, so an in-memory sort would only order the current page, silently wrong for
any page beyond the first). The correct fix is the same pattern already used for
`media_url`/`media_content_type`: denormalize `created_by_user_name` onto `advertisement`, synced
via a typed hook fired on user name changes — not a query-time join.

**Explicitly not touched:** the FK constraints in `01-advertisement-schema.xml`
(`referencedTableName="user_information"`) still reference the other starter's table by name —
this is a deeper, separate schema-level coupling (referential integrity inherently requires
knowing the referenced table) that this ADR does not attempt to resolve.

**Consequences:**
- Full e2e suite must stay green — the advertisement card's author-email display
  (`AdvertisementCardView`, `ad.getCreatedByUserEmail()`) and the owner-only edit/delete
  button visibility (`getOwnerUserId()`, used in `AdvertisementCardView`,
  `AdvertisementFormOverlayModeHandler`, `AdvertisementViewOverlayModeHandler`) are the concrete
  regression detectors.

---

## ADR-035: `advertisement` stores no denormalized attachment columns — media summary enriched at read time via a bulk `AttachmentPort` lookup

**Status:** Accepted

**Context:** `advertisement` had three columns — `media_url`, `media_content_type`, `media_count`
— caching a summary of the entity's attachments, written by
`AdvertisementRepository.updateMedia()`, triggered by `MediaChangeHookImpl.onMediaChanged()`
whenever `AttachmentService` fired `AttachmentMediaChangeHook`. An earlier pass on this review
dismissed these columns as "fine as-is" because the sync mechanism (a hook) was clean — that
answered "is the sync mechanism clean?" (yes) instead of "does the coupling exist?" (also yes,
independently of the mechanism): three columns named with attachment-domain vocabulary, living on
`advertisement`'s own row, in a different starter's schema. `backlog/entity-extensions/SPEC.md`
(deleted 2026-07-13) had already named this exact coupling as a motivating problem; its proposed
fix (genericize into a `media JSONB` column) was rejected — re-encoding the same data on the same
row removes type safety without removing the coupling itself.

**Decision:** Same shape as ADR-034 (User) and the completed Taxon equivalent: a bulk
lookup replaces the denormalized cache.
- `AttachmentPort.getMediaSummaries(EntityType, Set<Long>) -> Map<Long, AttachmentMediaSummaryDto>`
  added to `platform-commons`, alongside the existing single-entity `getMediaSummary(EntityRef)`.
  `DefaultAttachmentPort` stays pure delegation to a new `AttachmentService.getMediaSummaries()`,
  which calls a new `AttachmentRepository.loadMediaStats(EntityType, Set<Long>)` — one SQL query
  using `ROW_NUMBER() OVER (PARTITION BY entity_id ORDER BY created_at ASC)` to pick each entity's
  earliest attachment as its "main" one, plus `COUNT(*) OVER (PARTITION BY entity_id)`, matching
  the existing single-entity method's semantics exactly (`loadMediaStats(EntityType, Long)`,
  unchanged, still used by the single-entity path). **Later removed:** the port-level single-entity
  `getMediaSummary(EntityRef)` was dropped once every real caller was confirmed to use the bulk
  variant — `AttachmentRepository.loadMediaStats(EntityType, Long)` itself stays, since it has its
  own direct repository-level test coverage. See `attachment-spring-boot-starter/CLAUDE.md`.
- `AdvertisementService.enrichWithMediaSummary(ads)` (new at the time, same shape as
  `enrichWithCategories()`/`enrichWithActorInfo()`) merges `mediaUrl`/`mediaContentType`/
  `mediaCount` into `AdvertisementInfoDto` at read time in `getFiltered()`/`findById()`, using the
  already-existing `ComponentFactory<AttachmentPort> attachmentPortFactory` field. Entities with
  zero attachments fall back to `AttachmentMediaSummaryDto.empty()`. **Later relocated:** this
  enrichment step now lives in `marketplace-orchestrator`'s
  `AdvertisementDisplayEnrichmentService` — see `marketplace-orchestrator/CLAUDE.md`;
  `advertisement-spring-boot-starter`'s own `AdvertisementService` no longer performs display
  enrichment at all.
- The three columns were removed from `advertisement` (`01-advertisement-schema.xml`, direct
  edit — DB not yet in production, `deploy-and-run.sh --reset` required), along with
  `AdvertisementRepository.updateMedia()` and the three dead `ORDER BY` sort-alias entries for
  them (confirmed unreachable — `AdvertisementSortMeta` never exposed a media-related sort).
- **The write-triggered sync path was deleted entirely, not just emptied**: `MediaChangeHookImpl`
  (the only implementation of `AttachmentMediaChangeHook`), `AdvertisementService
  .onMediaChanged(Long)`, and `AdvertisementPort.onMediaChanged(Long)` (confirmed unused by any
  marketplace-app call site) are all gone — there is nothing left to update once no column caches
  the data. `AttachmentService` still fires `AttachmentMediaChangeHook` on every media change (the
  interface itself stays in `platform-commons` as a generic, still-meaningful extension point for
  any future starter that wants to react to media changes) — it now simply has zero listeners,
  which is the same valid, gracefully-degraded state every other optional SPI in this codebase
  already tolerates.

**Update (2026-07-22):** the "zero listeners is fine" call above did not hold up
against `platform-commons/CLAUDE.md`'s own governance rule ("random abstractions without ≥2
cross-module consumers are not allowed there") — carrying a permanently-unimplemented hook cost
comprehension on every read of `AttachmentService` with no concrete future consumer named. Removed
entirely: `AttachmentMediaChangeHook` (interface), the `ObjectProvider` field and all 7
`notifyMediaChanged()` call sites in `AttachmentService`. Git history preserves the shape if a real
consumer ever appears.

**Tradeoff accepted explicitly:** one more bulk `AttachmentPort` query per advertisement list
render — the same cost class already accepted twice (Taxon categories, User author info) for the
same real decoupling benefit.

**Consequences:**
- Full e2e suite must stay green — `AdvertisementCardView.java`'s media thumbnail/badge rendering
  (reads `ad.getMediaUrl()`/`getMediaContentType()`/`getMediaCount()`) is the concrete regression
  detector; behavior is unchanged since `AdvertisementInfoDto` still carries these fields, just
  populated by enrichment instead of a stored column.

---

## ADR-036: `AdvertisementRepository.buildIdClause()` binds a plain array, not a `Set`, for the category-filter id list

**Status:** Accepted

**Context:** `AdvertisementService.resolveCategoryFilter()` calls
`TaxonPort.findEntityIdsWithAnyTaxon()` to get the set of advertisement ids matching the selected
categories — the bulk-lookup pattern ADR-034 already established. One level down,
`AdvertisementRepository.buildIdClause()` bound that `Set<Long>` directly to
`WHERE a.id IN (:allowedIds)`. Spring's `NamedParameterJdbcTemplate` expands a `Collection`-typed
bind value into one `?` placeholder per element for an `IN` clause — unbounded for a popular
category's advertisement count, and the SQL text itself changes shape (different placeholder
count) for every differently-sized result, defeating Postgres's query-plan cache on top of the
parameter-count risk.

**Decision:** Bind a plain `Long[]` array instead of the `Set<Long>`, and compare with
`= ANY(:allowedIds)` instead of `IN (:allowedIds)`:
```java
params.addValue("allowedIds", ids.toArray(new Long[0]));
return " AND a.id = ANY(:allowedIds)";
```
Spring only expands `Collection`-typed values into multiple placeholders — a native array is
passed through as a single bind parameter, and the PostgreSQL JDBC driver binds it natively as one
`bigint[]` value. Not a new pattern: `AdvertisementRepository.findExistingIds(Long[] ids)` already
does exactly this (`WHERE id = ANY(:ids)`) a few lines below `buildIdClause()` — the fix applies
the class's own existing, already-proven convention consistently.

Rejected alternatives: leaving the `IN` clause as-is (no evidence this app's real category sizes
approach the risk zone today, but not worth carrying the parameter-count risk forward);
defensively capping `allowedIds` size (silently wrong results, doesn't fix the scaling problem);
joining to `taxon_assignment` directly (reverses ADR-034's no-raw-cross-starter-SQL-joins
decision for no compelling reason); `unnest()` (an equally valid single-array-bind alternative,
not chosen only because `= ANY()` was already an established, tested pattern in this class).
`= ANY()` is Postgres-specific syntax, consistent with this codebase's existing commitment to
Postgres-specific features elsewhere (`JSONB` columns, `ROW_NUMBER() OVER (PARTITION BY ...)`,
tuple comparisons) — portability to another RDBMS has never been a goal. Binding a native array
also removes the parameter-count limit entirely; the remaining limit (PostgreSQL's per-value
TOAST cap, ~1 GB) allows tens of millions of `bigint` elements before it would matter.

**Consequences:**
- `AdvertisementRepository.findByFilter()`/`countByFilter()` — the only two callers of
  `buildIdClause()` — are otherwise unchanged.
- New regression coverage: `AdvertisementRepositoryTest
  .findByFilter_allowedIdsRestrictsToMatchingRows` /
  `.countByFilter_allowedIdsRestrictsCount` — the existing test class had zero coverage of the
  non-null `allowedIds` path before this (every prior test passed `null`).

---

## ADR-039: Maven Enforcer — dependency hygiene enforced at build time, starter-to-starter ban activated per-starter not at the root

**Status:** Accepted

**Context:** Several dependency-hygiene rules were convention-only, checked by human review: no
direct starter→starter Maven dependencies (the module import rule from `.claude/rules.md`), no
guard against conflicting transitive dependency versions across the reactor, no enforced minimum
Java/Maven version at build time.

**Decision:**
- Root `pom.xml`: `maven-enforcer-plugin` added to an active `<build><plugins>` block (inherited by
  every module) with `dependencyConvergence`, `requireJavaVersion` (`[25,)`), and
  `requireMavenVersion` (`[3.9,)`).
- The starter→starter ban (`bannedDependencies`) is **not** in the root's active plugins — a
  project-wide ban would also apply to `marketplace-app`/`integration-tests`, which legitimately
  depend on starters. Instead, each of the 5 starter poms
  (`audit-`/`attachment-`/`user-`/`advertisement-`/`taxon-spring-boot-starter`) activates the same
  rule individually in its own `<build><plugins>`, banning the other 4 starter artifacts.

**Found and fixed while turning this on (not just theoretical):**
1. `advertisement-spring-boot-starter/pom.xml` declared `audit-spring-boot-starter` and
   `attachment-spring-boot-starter` as `<optional>true</optional>` Maven dependencies with **zero**
   Java source anywhere in the module importing from either (`org.ost.audit.*`/`org.ost.attachment.*`
   — confirmed via full-module grep before removing). Vestigial cruft, removed; all real
   cross-starter wiring already went through `platform-commons`' SPI types via `ComponentFactory<T>`,
   which needed no Maven dependency at all.
2. `dependencyConvergence` immediately caught a real, previously invisible conflict:
   `liquibase-core:5.0.2` depends on `commons-text:1.15.0` directly, and also on
   `opencsv:5.12.0` → `commons-text:1.13.1` transitively — two versions of the same artifact
   resolving inconsistently depending on classpath order. Pinned to `1.15.0` (matching
   liquibase-core's own direct requirement) in the root `dependencyManagement`.

**Follow-up (2026-07-16):** the same rule caught a second, equivalent
`commons-io` conflict once something actually ran `mvn -pl integration-tests test` for the first
time (`testcontainers`'s transitive `2.20.0` vs. `liquibase-core`'s transitive `2.21.0`) —
`liquibase-core` bumped to `5.0.3` (from `5.0.2` above) and `commons-io` pinned to `2.22.0` to
match. Full rationale in `scripts/ci/DECISIONS.md` ADR-001.

**Consequences:**
- A future starter→starter Maven dependency (optional or not) now fails the build immediately,
  not just a code-review catch.
- Verified via full `deploy.sh --no-cache` (all 5 starters + marketplace-app rebuild from empty
  cache, exercising every enforcer rule) + `bash scripts/playwright.sh e2e --full --ux`, 48/48.

---

## ADR-041: ArchUnit codifies cross-module architecture rules — lives in `marketplace-app/src/test`, not a new module

**Status:** Accepted

**Context:** Every cross-module and intra-module architecture rule in this project (module import
restrictions, no-Vaadin-in-starters, Port/Hook placement, `@PreAuthorize` placement, `Optional`-
parameter ban, `config` vs `configuration` naming, `*PortImpl`/`*HookImpl` delegation-only) lived
as prose in `CLAUDE.md`/`rules.md`, checked by human review only. Two real violations
(an unguarded port injection, and a view deviating from its own
`refresh()` pattern) reached working code before being caught by manual audit rather than a build
failure.

**Decision:** Added ArchUnit (`com.tngtech.archunit:archunit-junit5:1.4.2`, test-scope) directly to
`marketplace-app`'s existing test tree — `marketplace-app/src/test/java/org/ost/marketplace/
architecture/ArchitectureRulesTest.java` — rather than a new dedicated module. `marketplace-app`
depended on every starter + `platform-commons` + `query-lib` at the time (confirmed directly), so
its test classpath already had every compiled class ArchUnit needs to check cross-module rules
(e.g. "no Vaadin in starters" needs starter classes; "UI must not call repositories directly"
needs marketplace-app's own `ui` package) — no new module, no new dependency wiring. Still true
today even though `marketplace-app` no longer declares starter/`query-lib` dependencies directly:
`marketplace-orchestrator` (a mandatory `marketplace-app` dependency) now pulls every starter onto
the classpath instead, and `marketplace-app`'s test classpath still sees those classes
transitively. This also means these checks run automatically as part of the existing
`scripts/build-and-test.sh --unit`/`scripts/ci.sh --unit` stage, with zero new script/CI plumbing.

Seven rules implemented, mapping directly to the seven prose rules identified earlier:
UI-must-not-call-repositories, no-Vaadin-in-starters, Ports/Hooks-live-only-in-platform-commons
(split into two separate rules, one per suffix, rather than one combined `.or()` rule — simpler
and less error-prone than chaining ArchUnit's fluent predicate combinators), no-class-level-
`@PreAuthorize`-on-services, no-`Optional`-method-parameters (a custom `ArchCondition` — ArchUnit
has no built-in predicate for this), no-`configuration`-packages, and `*PortImpl`/`*HookImpl`-
delegation-only (no `java.util.stream`/Jackson dependencies).

**The delegation-only rule needed no explicit exception list for `DefaultTaxonPort`/
`DefaultAuditPort`/`DefaultAttachmentPort`** (all three are documented, deliberate
coordination-layer exceptions — see e.g. `taxon-spring-boot-starter/CLAUDE.md`, and `DefaultTaxonPort`
does genuinely import `java.util.stream.Collectors`) — the rule only targets the `*PortImpl`/
`*HookImpl` simple-name suffix, and none of the three `Default*Port`-named classes match that
suffix. The existing `Default*Port` vs. `*PortImpl` naming convention
(`platform-commons/CLAUDE.md` "Hook and Port Implementation Rules") already draws exactly the
line this rule needs, for free.

**Consequences:**
- All 7 rules (8 `@ArchTest` fields — Port/Hook split into two) pass cleanly against the current
  codebase on first run — the rules describe discipline already actually followed, now enforced by
  a build failure instead of a code-review judgment call. Verified via `bash scripts/unit-tests.sh
  ArchitectureRulesTest` (8/8 passed) and a full `bash scripts/unit-tests.sh` run (all suites still
  green) to confirm the new test-scope dependency didn't disturb anything else.

---

## ADR-042: `UserPickerField` gains an offset-based lookup (`OffsetPageable`) instead of deriving a page number from Vaadin's raw offset

**Status:** Accepted

**Context:** `UserPickerField` is the only place in `marketplace-app` using Vaadin's native lazy
`CallbackDataProvider`/`DataProvider.fromFilteringCallbacks` — every other grid uses the app's own
`PaginationBar` with explicit page-state tracking. Its data provider derived a page number from
Vaadin's raw row offset via `query.getOffset() / query.getLimit()`, which is only correct when the
offset happens to be an exact multiple of the limit — not guaranteed by Vaadin's `Grid`/
`DataCommunicator` under fast/jump scrolling. A non-page-aligned fetch (e.g. `offset=137,
limit=50`) silently computed the wrong page, returning duplicated, skipped, or wrong rows. Went
untriggered by existing Playwright coverage because the seed spec used exactly 50 users, matching
Vaadin `Grid`'s default page size — the entire result set fit in one always-aligned fetch
(`offset=0`). Tracked alongside a related, smaller gap: the picker's inline
search button used a raw `Button` instead of `UiIconButton`, since `UiIconButton` had no variant
for Vaadin's `LUMO_TERTIARY_INLINE` (the suffix/prefix-slot button treatment).

**Decision:**
- Added `OffsetPageable` (`query-lib/src/main/java/org/ost/query/sort/OffsetPageable.java`) — a
  `record` implementing Spring Data's `Pageable` directly, carrying an arbitrary raw `offset`
  instead of deriving `offset = page * size` the way `PageRequest.of(page, size)` always does.
  Chosen over reworking `UserPort.getFiltered()`'s existing page-based contract (used by every
  `PaginationBar`-style caller) — introducing a second, offset-shaped `Pageable` implementation
  is additive and keeps the page-based contract untouched for its existing callers.
- Added `UserPort.getFilteredByOffset(filter, offset, limit, sort)`, delegating through
  `UserPortImpl` → `UserService.getFilteredByOffset()` → `UserRepository.findByFilter(filter, new
  OffsetPageable(offset, limit, sort))`. The repository itself needed **no changes** — its SQL
  already used `pageable.getOffset()` (via `PaginationSqlBuilder.pageLimit()` →
  `LIMIT :limit OFFSET :offset`), so it was already offset-correct; only the `Pageable` it
  received was wrong.
- `UserPickerField`'s data provider now calls `userPort.getFilteredByOffset(filter,
  query.getOffset(), query.getLimit(), sort)` directly — no more `offset/limit` division.
- `UiIconButton.Parameters` gained a `boolean inline` field (default `false`); `configure()` now
  applies `LUMO_TERTIARY_INLINE` instead of `LUMO_TERTIARY` when `inline` is set, moved out of
  `init()` (which previously hardcoded the non-inline variant unconditionally). The picker's
  search button now goes through `UiIconButton` with `inline(true)`.

**Verification:** the existing Playwright seed volume (50 users) could never have exercised the
bug (see Context) — bumped `05-seed-filter-sort-pagination.spec.js`'s `SEED_COUNT` from 50 to 60
specifically so the picker's underlying grid needs a second Vaadin-internal data-provider page,
and retargeted the timeline actor-filter test to pick a name-sorted user past the first page
(`'Seed User 60'` instead of `adminEn`, who previously sorted within the first page and never
exercised the bug either). `fillActorPicker` in `timeline.flow.js` gained a grid-scroll step
(`grid.scrollToIndex(...)`) to actually reach that row. Full `bash scripts/playwright.sh e2e --full
--ux` run: 48/48 passed, confirming both the fix and that the seed-count bump didn't regress any
existing pagination assertion (`verifyPagination()`'s hardcoded 3-page structure, `1-20`/`21-40`/
`41-{total}`, holds for 60 exactly as cleanly as for 50 — 60 divides evenly into three full pages
of 20 rather than 50's partial third page of 10).

**Consequences:**
- `OffsetPageable` is a new, general-purpose `query-lib` type available to any future caller that
  needs to pass a Vaadin-native (or otherwise raw) offset through to a `Pageable`-based repository
  method without page-number derivation.

---

## ADR-044: `UserSettingsRepository` optimistic-locking version lives inside the `settings` JSONB blob, not a new SQL column

**Status:** Superseded by ADR-070 — `locale`/`settings` moved off `user_information` into their own
`user_preferences` table; the repository is now `UserPreferencesRepository`, not
`UserSettingsRepository`. The version-inside-JSONB mechanism this ADR decided is otherwise still
current — see `user-spring-boot-starter/CLAUDE.md`.

**Context:** `UserSettingsRepository.save()` had no conflict detection at all — a bare
`UPDATE user_information SET settings = :settings::jsonb WHERE id = :userId`, unlike every other
mutable entity in this codebase (`Advertisement`, `Taxon`, `user_information` name/role via
`UserEditableFields` — all `@Version`-column optimistic locking per ADR-029). Two browser tabs of
the same user editing settings would silently clobber each other: the second tab's save overwrites
the *entire* JSONB blob with its own stale copy, discarding whatever the first tab's save had just
written, with no error and no conflict notification. Traced the full call chain
(`SettingsOverlay` → `AuthContextService.getCurrentUser()`) and confirmed there is exactly one
write path (the current user's own settings, via the header settings icon) — no admin-edits-
another-user's-settings path, no background writer — so the only realistic conflict is the same
user's own concurrent tabs.

**Decision:**
- `UserSettingsDto` (`platform-commons`) gained a `long version` field. `UserSettingsRepository`
  already serializes/deserializes the *entire* DTO directly into/from the `settings` JSONB column
  (`mapper.writeValueAsString`/`readValue`), so the version travels for free through the same
  round-trip — no new SQL column, no entity restructuring.
- `save()` increments `version` on the DTO being written and adds
  `AND (settings->>'version')::bigint = :expectedVersion` to the `UPDATE`'s `WHERE` clause (Postgres
  JSONB `->>` operator extracts the field as text, cast to `bigint` for the comparison); 0 affected
  rows throws `OptimisticLockingFailureException` — same exception type and UI conflict-handling
  path ADR-029 already standardized on, so no new UI code was needed for the failure case itself.
- UI (`SettingsEditDto`, `SettingsFormModeHandler`) threads `version` through every lifecycle path
  (`activate`, `save`, `discardChanges`, `handleRestoreFromActivity`, `loadRestored`) so it is never
  silently re-derived from a stale read mid-flow — the same discipline ADR-029 requires for
  `Advertisement.buildEntity()`/`Taxon.update()`. `handleRestoreFromActivity()` is the one
  deliberate exception: it fetches the *current* DB version via `userPort.loadSettings()` rather
  than trusting the audit snapshot (which predates this field and carries no version), since a
  restore only stages values into the form — the eventual save still has to check against whatever
  is actually current in the row, not what the snapshot happened to hold.
- Schema default (`01-user-schema.xml`'s `settings` column) updated to
  `{"adsPageSize":20,"usersPageSize":20,"timelinePageSize":20,"version":0}` so freshly-registered
  users start at version 0 rather than a missing JSON key (which would make
  `(settings->>'version')::bigint` never match any expected value on a user's first save).

**Rejected alternatives:**
- **A dedicated new SQL column** (e.g. `settings_version BIGINT`), matching the shape of a real
  `@Version` field. Rejected as unnecessary ceremony: it would require restructuring
  `UserSettingsRepository` to read/write the column separately from the JSONB blob, when the whole
  point of that repository is that it treats `settings` as one opaque serialized DTO.
- **Reusing the row's existing `user_information.version`** (the one `UserProfileUpdate`/
  `UserProfileCrudRepository` already uses for name/role edits). Rejected: it would couple two
  functionally independent parts of the same row — a settings save in one tab and a profile-name
  edit in another tab would spuriously conflict with each other, which is not a real data race
  (they touch disjoint columns) and would surface confusing, unwarranted conflict errors.
- **A formal new Liquibase changeset** (`02-user-settings-default-version`) to fix the live dev
  database's column default, mirroring how every other schema change in this codebase is applied.
  Explicitly skipped per direct instruction, since this application is not yet in production — the
  dev DB's actual column default was fixed directly via a one-off `ALTER TABLE ... ALTER COLUMN
  settings SET DEFAULT ...`. **A proper Liquibase changeset is still required before any real
  deploy** — editing `01-user-schema.xml`'s `defaultValue` (even with `validCheckSum ANY`) has no
  effect on an already-migrated database; Liquibase does not re-execute a changeset once applied,
  it only suppresses the checksum-mismatch error on future runs.

**Consequences:**
- No new SQL column, no new Liquibase changeset (see rejected alternative above — a real gap, not
  an oversight, tracked for whenever this app approaches a production deploy).
- `integration-tests/.../user/UserSettingsRepositoryTest` (new) covers fresh-user version-0 start,
  stale-version save throwing `OptimisticLockingFailureException`, and current-version save
  succeeding + incrementing — verified against real Postgres.

---

## ADR-046: `AbstractFormOverlayModeHandler.buildContentWithActivity()` unifies the Edit/Activity tab choreography across Advertisement/Taxon/User form handlers; fixes `UserFormOverlayModeHandler`'s viewer/subject `userId` bug

**Status:** Accepted

**Context:** `AdvertisementFormOverlayModeHandler`, `TaxonFormOverlayModeHandler`, and
`UserFormOverlayModeHandler` each independently built the same "Edit tab + lazily-loaded Activity
tab wrapping an `AuditActivityPanel`" structure — same `Tabs`/`Tab` construction, same
`ComponentFactory<AuditPort>.findIfAvailable()` degrade-gracefully guard, same
`tabbedSecondaryContent` CSS wiring. Verifying the proposed dedup surfaced a real, independently
traced bug: `UserFormOverlayModeHandler.buildActivityContent()` passed
`.userId(params.getUser().id())` (the profile subject) into `AuditActivityPanel.Parameters`, while
Advertisement and Taxon both correctly pass `.userId(access.getCurrentUserId())` (the acting
viewer). Tracing `AuditPort.getEntityActivity(entityType, entityId, userId, isPrivileged)` →
`AuditLogRepository.findRows()` confirms `userId` becomes `filterActorId`, applied as `WHERE
CAST(:filterActorId AS BIGINT) IS NULL OR actor_id = :filterActorId` for non-privileged viewers —
i.e. it must always be the viewer's id, never the subject's. The bug was masked because
`canOperate` for `User` entities only allows self-view (`owner == viewer` always coincide in the
only reachable path today) or privileged viewers (whose filter short-circuits to `null`
regardless).

**Decision:** Extracted the shared choreography into
`AbstractFormOverlayModeHandler.buildContentWithActivity(ActivityTabParams)` — a new nested
`@Value @lombok.Builder` parameter class (9 fields: `canOperate`, `isCreateMode`, `editTabLabel`,
`activityTabLabel`, `tabsCssClass`, `secondaryContentCssClass`, `editContent`, `auditPortFactory`,
`activityContentLoader`) per the "5+ fields → Builder" convention. `formTabs`/`editTab` moved up
from being redeclared as private fields in each subclass to `protected` fields on the base class
(subclasses still reference them by the same names in `afterSave()`/`loadRestored()`; Taxon's
previously differently-named `topTabs` was renamed to `formTabs` to match). All three call sites
now delegate to this one method; Taxon's own near-identical private `buildContentWithActivity(Div)`
helper (which predates this ADR and only existed in `TaxonFormOverlayModeHandler`) was deleted
outright, superseded by the base-class version. Domain-specific pieces (`AuditActivityPanel`
parameter construction — `entityRef`, `canOperate`, `onRestoreRequested`) stay local to each
subclass's own `buildActivityContent()`, since those genuinely differ per domain; only the
tab-building shell was shared. `UserFormOverlayModeHandler` was fixed to
`.userId(access.getCurrentUserId())`, matching Advertisement/Taxon.

**Consequences:**
- Any future domain form handler adding an Activity tab has one call site to follow, not three
  slightly-different examples to copy from (and potentially re-introduce this class of bug from).
- `TaxonFormOverlayModeHandler`'s `canOperate` is passed as a literal `true` — preserves its
  pre-existing behavior exactly (it never filtered by ownership, unlike Advertisement/User, since
  taxon management is already privileged-only at the view level); not a behavior change.
- Covered by a new `UserFormOverlayModeHandlerTest` (marketplace-app, plain Mockito unit test, no
  Spring context) that constructs the handler with a viewer id and profile-subject id deliberately
  different, invokes the private `buildActivityContent()` via reflection, and asserts the captured
  `AuditActivityPanel.Parameters.userId` equals the viewer's id — fails under the pre-fix code,
  passes under the fix. No Playwright coverage added: the buggy path (subject id ≠ viewer id while
  `canOperate` is true) isn't reachable through any real UI flow today (see masking note above), so
  an e2e test couldn't exercise it either way — matches the originating issue's own "Playwright
  scenario OR unit test" framing. Full e2e suite (specs 01-06, `--ux`, non-`--full`) re-run after
  the change: 35/35 non-skipped tests green, including the User/Advertisement/Taxon activity-tab
  flows this refactor directly touches.

---

## ADR-047: `AdvertisementSaveService.save()` moves the attachment-gallery commit to just before the transaction's own commit; logs loudly if it rolls back after the S3 move anyway

**Status:** Accepted — `AdvertisementSaveService` itself later relocated to
`marketplace-orchestrator` (see ADR-073); this decision's reasoning still applies to that class in
its new location.

**Context:** `save()`'s `tx.execute(...)` block called `commitGallery.apply(...)` (which triggers
`AttachmentService.commitTempUploadsQuiet()`'s non-transactional, physical S3 file move) right
after the initial `advertisementPortFactory.get().save(dto, actorId)`, with category reassignment
(`taxonPortFactory...replaceAssignments()`) and audit capture both still running *after* it,
inside the same transaction. Any failure in either of those (or the transaction's own commit)
rolled the DB back while the S3 files stayed physically moved — an orphan invisible to every
existing cleanup pass (`attachment-spring-boot-starter`'s own `AttachmentCleanupService` closes the
gap on the other side, with a DB-cross-checked orphan sweep that finds exactly these).

**Decision:** Two changes, both defense-in-depth on top of that orphan sweep (which alone is
sufficient to eventually clean up any orphan, but only on its next nightly run):
1. Reordered `save()` so `replaceAssignments()` runs *before* `commitGallery.apply()` — the S3
   move is now the last mutation before this transaction's own commit, so it only has to survive
   the commit itself, not also category reassignment and audit capture. `commitGallery`'s return
   value (`gallerySnapshotId`) is still needed immediately after for `attachmentSnapshotId`/`after`
   snapshot construction, so those stay in their original relative order right after the call.
2. `registerOrphanWarningOnRollback(entityRef, gallerySnapshotId)` — guarded by both
   `gallerySnapshotId != null` (skip if the gallery wasn't actually touched this save) and
   `TransactionSynchronizationManager.isSynchronizationActive()` (skip outside a real Spring
   -managed transaction — required, not optional: `registerSynchronization()` itself throws
   `IllegalStateException` otherwise, which is exactly the shape `AdvertisementSaveServiceTest`'s
   mocked `TransactionTemplate` exercises, since `tx.execute()` there just invokes the callback
   directly with no real transaction ever starting). Registers a `TransactionSynchronization`
   whose `afterCompletion()` logs `ERROR` if `status == STATUS_ROLLED_BACK`, naming the
   `EntityRef` — turns a silent orphan into an immediately discoverable one via logs/alerting,
   without waiting for ADR-011's nightly sweep.

**Consequences:**
- Does not eliminate the gap entirely — a failure between the S3 move and the transaction's own
  commit is still structurally possible, just a much smaller window than before (previously had to
  survive two more steps). ADR-011's sweep is what actually guarantees eventual cleanup regardless
  of this window's size.
- No behavior change to the four existing `AdvertisementSaveServiceTest` cases (none assert
  ordering between `taxonPortFactory` and the `commitGallery` lambda) — reordering is safe against
  them as-is; the `isSynchronizationActive()` guard is what keeps the new rollback-logging code
  from breaking them (confirmed: without the guard, `save_galleryTouched_...` throws
  `IllegalStateException` since its mocked `tx` never activates real transaction synchronization).
- Verified via `bash scripts/build-and-test.sh --unit` (`AdvertisementSaveServiceTest` 5/5)
  and a full Playwright e2e pass (specs 01-06, `--ux`): 35/35 non-skipped green, including every
  advertisement create/edit/restore flow that exercises the gallery commit path.

---

## ADR-049: `I18nKey` stays one consolidated enum — domain-split considered and rejected

**Status:** Accepted (rejection of a proposed change)

**Context:** An external review (2026-07-19) suggested splitting the ~300-key, ~400-line
`I18nKey` enum into per-domain enums (`AuditI18nKey`, `AdI18nKey`, `TaxonI18nKey`, ...) to ease
navigation and reduce Git merge conflicts.

**Decision:** Keep the single consolidated `I18nKey` enum (`services/i18n/I18nKey.java`), as
already codified in `marketplace-app/CLAUDE.md` ("Translation keys — single consolidated enum").

**Why the split loses on inspection:**
- A split requires a common type for `I18nService.get(...)` — a `sealed interface I18nKey
  permits AuditI18nKey, ...` — which trades the enum's flat simplicity for a new failure mode
  that does not exist today: two enums can silently declare the same message-properties key, so
  key uniqueness would need a runtime startup check instead of being structurally impossible.
- `I18nKey.forAction(ActionType)` and similar exhaustive mappings are simplest against one enum.
- The Git-conflict argument assumes parallel team edits; this is a single-developer repo.
- Navigation inside one sectioned enum is an IDE structure-view away; 400 lines is large but
  cohesive (same conclusion as `docs/architecture/08-scorecard.md`'s cohesion note).

**Revisit trigger:** a new large domain landing (which would push the enum well past its current
size), or i18n infrastructure appearing in starters — currently explicitly excluded ("Starters
have no i18n infrastructure of their own", `marketplace-app/CLAUDE.md`). If revisited, the
sealed-interface design above is the starting point, with a startup-time uniqueness check over
all member enums' keys as a hard requirement.

## ADR-051: User deletion — soft-delete, cascade to the user's own ads, retention purge, actor-name annotation

**Status:** Accepted — `UserDeleteService`, `UserActorNameService`, and `AuditDomainHookImpl`
(described below as living in `marketplace-app`) all later relocated to `marketplace-orchestrator`
(see ADR-073 and `marketplace-orchestrator/CLAUDE.md`); this decision's reasoning still applies to
those classes in their new location.

**Context:** `UserService.delete()` was a hard `DELETE`, the only hard-delete lifecycle mutation in the system
and the only one with no audit capture. User picked Option A: soft-delete, aligning with
advertisement/taxon.

**Decision:**
- `user_information` gets `deleted_at`/`deleted_by` columns, added directly to the existing
  `01-user-schema` Liquibase changeset (not a new one — this app has no production deployment
  yet, so in-place edits to an unreleased changeset are acceptable; `<validCheckSum>ANY</validCheckSum>`
  already present on it).
- `UserService.delete(userId, actingUserId)` soft-deletes and calls `captureDeletion`. `findByEmail`
  (the login lookup) and the user list (`findByFilter`/`countByFilter`) exclude soft-deleted rows;
  `findById`/`findActorNames`/`findByIds` stay unfiltered, matching `TaxonRepository.findById`'s
  precedent (not `AdvertisementRepository`'s stricter one) — those three are used for internal
  lookups and historical audit-name resolution, where a deleted user must still resolve.
- **Cascade discovered mid-design:** `advertisement.created_by` has `ON DELETE RESTRICT`. A user
  who ever posted a still-active ad would block their own row's eventual retention purge forever
  once that ad's cleanup never fires (advertisement cleanup only purges *already* soft-deleted
  ads). Fix: new marketplace-app `UserDeleteService` cascades — soft-deletes the user's own ads
  first (each via `AdvertisementSaveService.delete()`, so each gets its own audit capture too),
  then soft-deletes the user. Lives in marketplace-app, not in `UserService` (the starter), because
  `AdvertisementSaveService` (where advertisement audit capture lives) isn't reachable from a
  starter.
- **Retention purge job:** `UserService.cleanup(retentionDays)` + a `UserAutoConfiguration`
  `SchedulingConfigurer` bean, same `CleanupProperties`/cron shape as advertisement/attachment/
  audit. Deliberately **not** one bulk `DELETE` (unlike advertisement's `deleteOlderThan`) — loops
  per-id with `try/catch` around `DataIntegrityViolationException`, so a row still FK-blocked by
  some other reference (a race between this job and the ad-cleanup job, or any future domain
  adding its own reference to `user_information`) is skipped and retried the next run instead of
  aborting the whole batch.
- **Actor-name annotation:** historical audit rows must still show a deleted actor's name (not a
  raw unresolved id), but visibly marked. New marketplace-app `UserActorNameService` combines
  `UserPort.findActorNames()` + a new `UserPort.findDeletedIds()`, appending an i18n suffix
  (`I18nKey.AUDIT_ACTOR_DELETED_NAME`, `"{0} (deleted)"`) for deleted actors. `AuditDomainHookImpl
  .resolveNames()` delegates to this service instead of doing the combining itself, keeping the
  `*HookImpl` pure-delegation rule intact.

**Consequences:**
- `UserPort.delete()` signature gained `actingUserId` — the only caller (`UserView`) already had
  it via `AccessEvaluator.getCurrentUserId()`.
- New `AdvertisementPort.findByCreator(userId)` — needed so the cascade can find a user's ads
  without a starter-to-starter dependency.
- No "restore a deleted user" UI was added — out of scope for this issue; the soft-delete only
  exists to preserve the audit trail and support the cascade/cleanup ordering above.

---

## ADR-052: Leaf UI components converted from `@SpringComponent` prototype beans to plain classes

**Status:** Accepted

**Context:** Many leaf UI components (`UiPrimaryButton`, `UiTertiaryButton`, `UiIconButton`,
`DeleteActionButton`, `EditActionButton`, `OverlayBreadcrumbBackButton`, `UiTextField`,
`UiTextArea`, `UiEmailField`, `UiPasswordField`, `UiComboBox<T>`, `UiLabeledField`,
`EmptyStateView`, `DialogLayout`, `OverlayLayout`, `ConfirmActionDialog`, and the entire
`ui/query/elements/*` tree — `SvgIcon`, `SortIcon`, `QueryActionButton`, `QueryActionBlock`,
`QueryInlineRow`, `QueryTextField`, `QueryLongField`, `QueryDateTimeField`,
`QueryMultiSelectComboField<T>`) were `@SpringComponent @Scope("prototype")` beans implementing
`Configurable`/`Initialization`, even though their only real dependency was `I18nService`, used
solely to resolve a label/tooltip string at construction time — exactly the case
`marketplace-app/CLAUDE.md`'s "When NOT to use Configurable" rule already calls out. Every parent
`ModeHandler`/`View`/`QueryBlock` had to constructor-inject a `UiComponentFactory<T>` per widget
just for this, and `MarketplaceUiConfiguration`/`ComponentFactoryConfig` carried a matching
now-pointless `@Bean` for each one.

**Decision:** Converted every leaf component listed above to a plain class with a constructor
taking already-resolved `String`/`Icon`/`Runnable` values; i18n resolution moved to the call site
(`i18n.get(key)` before constructing). Removed every now-empty `UiComponentFactory<T>` `@Bean`
declaration these types previously needed. Two components with a real dependency (`data-testid`
computation, dynamic re-resolution) needed a small variant of the same pattern instead of a bare
value constructor:
- **Fields carrying `data-testid`** (`UiTextField`, `UiTextArea`, `UiEmailField`,
  `UiPasswordField`, `UiComboBox<T>`, `UiLabeledField`) — the test id (`I18nKey.toTestId()`) is
  computed at the call site and passed as an explicit `String testId` constructor parameter,
  keeping every existing Playwright selector byte-identical.
- **`SortIcon`** — the one real exception to the "resolve once, pass a `String`" template: it
  re-resolves its tooltip *dynamically* every time the sort direction cycles
  (NEUTRAL→ASC→DESC), not just once at construction, so a single pre-resolved `String` can't
  represent it. `SortIcon(I18nService i18nService)` keeps `i18nService` as a plain field and
  calls `.get()` internally whenever direction changes — a plain object holding a reference to
  an already-existing singleton bean; nothing requires `SortIcon` itself to remain Spring-managed.

`PaginationBar` was deliberately **kept a `@SpringComponent @Scope("prototype")` bean
permanently, not converted**: it is read from a separately-invoked `refresh()` method in three
different `View` classes (`AdvertisementsView`/`TimelineView`/`UserView`), and `TimelineViewTest`
already mocks it as an injected collaborator through the constructor — converting it would force
every consuming `View` to gain a new `I18nService` constructor parameter purely to build its own
`PaginationBar` internally, and would require rewriting existing tests to swap a mock in via
reflection post-construction, a materially bigger and riskier change than the "leaf without
dependencies" case this decision otherwise covers.

Handlers whose leaf-component fields were previously constructor-injected Spring beans, read
again later from `activate()`/`buildBinder()` (e.g. `saveButton`/`discardButton` pairs,
`titleField`, `nameField`/`roleComboBox`), keep them as plain mutable fields constructed once in
`activate()`. `BaseActionButton.applyConfig(BaseConfig)` (used only by
`DeleteActionButton`/`EditActionButton`) simplified to a plain
`applyConfig(String tooltip, Runnable onClick, String cssClassName)`. `BaseDialog.buildLayout()`
(the niladic lifecycle hook, previously `abstract`) became a no-op default method —
`LoginDialog`/`SignUpDialog` still override and `@PostConstruct`-annotate it (they remain Spring
beans, holding real dependencies like `AuthService`/`UserPort`), but `ConfirmActionDialog` calls
`buildLayout(layout)` inline in its own constructor instead. Dead code found and removed along the
way: `QueryComboField<T>` and `QueryNumberField` had zero real construction sites anywhere in the
app.

**Consequences:**
- `MarketplaceUiConfiguration`/`ComponentFactoryConfig` no longer carry a `@Bean` for any of the
  converted types; `userPickerFieldFactory` stays (`UserPickerField` is a `CustomField` with a
  real dialog/grid dependency, not a leaf widget, and stayed out of scope).
- If the attachment-lightbox family (`AttachmentLightbox`/`CardLightboxViewer`/
  `AttachmentThumbnail`, promoted to full beans specifically to inject
  `UiComponentFactory<UiIconButton>`) is ever revisited, they become candidates for the same
  plain-class conversion once `UiIconButton` itself stopped being a Spring bean.
- Verified after each conversion batch with unit-tests, integration-tests, and a full Playwright
  `e2e --full --ux` run — every batch green, including two pre-existing bugs found and fixed along
  the way and unrelated to the conversion itself: a `LogoutDialog` locale-refresh-after-session-
  invalidation ordering bug, and a `fillActorPicker()` Playwright helper race missing a
  post-search-click Vaadin round-trip wait.

---

## ADR-057: `AbstractViewOverlayModeHandler`'s secondary/tertiary-tab machinery removed — dead since the Timeline-tab extraction

**Status:** Accepted

**Context:** A dead-code cleanup pass found `AbstractViewOverlayModeHandler.buildSecondaryTab()`/`buildTertiaryTab()`
always return `null` — none of its three subclasses (`AdvertisementViewOverlayModeHandler`,
`TaxonViewOverlayModeHandler`, `UserViewOverlayModeHandler`) override either. Tracing `activate()`'s
logic further: since `secondary`/`tertiary` are always `null`, the `if (secondary == null &&
tertiary == null)` branch is unconditionally taken, meaning the `else` branch — the one that
actually builds a `Tabs` component from `buildPrimaryTab()`/`tabsCssClass()` — was **already
unreachable**, not merely unused. `UserViewOverlayModeHandler` did override `buildPrimaryTab()`
(returning a "Profile" tab labeled via `ACTIVITY_PROFILE_TAB`) and `tabsCssClass()`, but neither
override had any rendering effect today — confirmed by the same reachability argument, not
guessed. This matches this project's known history: the tab-per-mode-handler approach was the
pre-Timeline-tab design for showing activity/history data inline inside an overlay, superseded by
the dedicated top-level Timeline tab; this was the leftover scaffolding from that migration that
never got removed.

**Decision:** Collapsed `AbstractViewOverlayModeHandler` to what `activate()` actually executes:
```java
public abstract class AbstractViewOverlayModeHandler implements OverlayModeHandler {
    @Override
    public final void activate(OverlayLayout layout) {
        layout.setContent(buildPrimaryContent());
        layout.setHeaderActions(buildHeaderActions());
    }
    protected abstract Div buildPrimaryContent();
    protected abstract Div buildHeaderActions();
}
```
Removed `SecondaryTabDef`/`TertiaryTabDef` records, `buildSecondaryTab()`/`buildTertiaryTab()`,
`buildPrimaryTab()`/`tabsCssClass()` (both now with zero overriders), and the private
`assembleTabbedContent()` tab-switching-listener logic. Removed `UserViewOverlayModeHandler`'s now-
unreachable `buildPrimaryTab()`/`tabsCssClass()` overrides and the orphaned `ACTIVITY_PROFILE_TAB`
i18n key (both locales).

**Consequences:**
- No behavior change for end users — the "Profile" tab label was never actually rendered, so its
  removal doesn't remove anything currently visible; this is a pure code simplification, not a
  UI change.
- If per-mode secondary/tertiary tabbed content inside a *view* overlay is ever wanted again
  (distinct from the Form-side `buildTabbedContent()` pattern in `AbstractFormOverlayModeHandler`,
  which is unrelated and unaffected), re-add it then against a concrete need rather than restoring
  this dead scaffolding.

## ADR-058: `UiComponentFactory<T>` bounded to `T extends Configurable<T, ?>`; non-`Configurable` consumers migrated to plain `ComponentFactory<T>`

**Status:** Accepted

**Context:** `UiComponentFactory<T>.build(params)` cast `get()` to `Configurable<T, P>` under
`@SuppressWarnings("unchecked")` because `UiComponentFactory<T>` had no compile-time guarantee `T`
was actually `Configurable`. Ten beans in `ComponentFactoryConfig` (`AuditActivityListRenderer`,
`AuditHistoryListRenderer`, `AuditHistoryRowRenderer`, `AuditActivityRowRenderer`,
`AttachmentGalleryService`, `AttachmentGallery`'s own `thumbnailFactory` field, `CardMediaLightbox`'s
`viewerFactory`, `AttachmentThumbnail`, `UserPickerField`, and `AdvertisementCardView`'s/
`AdvertisementFormOverlayModeHandler`'s/`AdvertisementViewOverlayModeHandler`'s `galleryServiceFactory`)
were declared `UiComponentFactory<X>` and used only via the inherited `.get()` — none of those `X`
types implement `Configurable`, contradicting `marketplace-app/CLAUDE.md`'s own rule.

**Decision:**
1. `UiComponentFactory<T extends Configurable<T, ?>> extends ComponentFactory<T>` — the bound is
   now enforced at compile time; `build(P params)` no longer needs `@SuppressWarnings("unchecked")`
   on the cast to `Configurable<T, P>` itself being sound-by-construction for `T`, only the
   `Configurable<T, P>`-vs-caller's-`P` cast remains (unavoidable — `Configurable<T, ?>`'s own `P`
   is existential, same shape as `AuditDomainHook.castIfKnown()` before ADR-023).
2. All ten non-`Configurable` consumers above migrated from `UiComponentFactory<X>` to
   `ComponentFactory<X>` in both their `@Bean` declaration (`ComponentFactoryConfig`/
   `MarketplaceUiConfiguration`) and every injection site — matching the pre-existing documented
   rule exactly, not a new pattern.
3. `OverlayFormBinder<T extends EditDto>` (a genuine `Configurable` implementor, self-bound generic)
   could not keep its single shared raw-typed `overlayFormBinderFactory` bean once the
   `Configurable<T, ?>` bound was added — verified directly that **neither the raw type
   `OverlayFormBinder` nor the wildcard `OverlayFormBinder<?>`** satisfies
   `T extends Configurable<T, ?>` (both fail with "type argument ... is not within bounds of
   type-variable T" — a self-bound recursive generic (`OverlayFormBinder<T> implements
   Configurable<OverlayFormBinder<T>, ...>`) cannot be named as `OverlayFormBinder<?>` and still
   satisfy `Configurable<OverlayFormBinder<?>, ?>`, since `OverlayFormBinder<?> != OverlayFormBinder<CAP#1>`
   from the bound-checker's perspective). Split the one shared bean into four concrete beans in
   `ComponentFactoryConfig` — `advertisementFormBinderFactory`, `userFormBinderFactory`,
   `taxonFormBinderFactory`, `settingsFormBinderFactory` — one per `EditDto` subtype actually
   consumed (`AdvertisementEditDto`, `UserEditDto`, `TaxonEditDto`, `SettingsEditDto`), each properly
   bounded. The four existing consumer fields (`AdvertisementFormOverlayModeHandler`,
   `UserFormOverlayModeHandler`, `TaxonFormOverlayModeHandler`, `SettingsFormModeHandler`) already
   declared their own concrete `UiComponentFactory<OverlayFormBinder<XEditDto>>` type and needed no
   change — Spring resolves each to its matching new bean by exact generic type.

**Consequences:**
- `marketplace-app/CLAUDE.md`'s `UiComponentFactory<T>` vs `ComponentFactory<T>` usage rule is now
  actually enforced by the compiler, not just documentation — a future consumer that mistakenly
  injects `UiComponentFactory<X>` for a non-`Configurable` `X` fails to compile instead of silently
  working via the old unbounded generic.
- A single shared factory bean for a self-bound generic `Configurable` type serving multiple
  concrete type parameters (the `OverlayFormBinder` shape) is no longer possible once
  `UiComponentFactory` is bounded — any *future* domain adding its own `OverlayFormBinder<NewEditDto>`
  consumer needs its own dedicated `@Bean` method in `ComponentFactoryConfig`, following the same
  four-beans precedent, not a fifth raw-typed shared one.

## ADR-059: F-01 deep links + Open Graph meta tags, Share button, sitemap.xml, and browser History sync

**Status:** Accepted

**Context:** The app was a pure single-route Vaadin SPA (`MainView` at `@Route("")`); opening an
advertisement was entirely session-state-driven
(`AdvertisementOverlay.openForView(AdvertisementInfoDto, ...)`, no URL involved). The F-01 feature
needs a stable, shareable URL per advertisement that (a) opens the right overlay for a real
browser user, (b) renders a rich preview card when the same URL is posted into
Facebook/Telegram/Viber (crawlers never execute the SPA's JS, so meta tags must be present in the
server's raw HTML response), (c) is reachable via a "Share" affordance and discoverable by search
engines, and (d) keeps the browser's own URL bar / Back-Forward navigation in sync with the
overlay's open/closed state.

**Decision:**
1. **Real-browser navigation:** `AdvertisementDeepLinkView` (`@Route("ads")`, implements
   `HasUrlParameter<Long>`) — a trivial, dependency-free view that, on `/ads/{id}`, stores a
   `PendingAdvertisementDeepLink(Long adId)` record in `VaadinSession` and calls
   `event.forwardTo("")` (same-navigation-cycle reroute, not a client-side redirect — the
   `AdvertisementDeepLinkView` itself is never actually rendered). `MainView.init()` calls
   `AdvertisementsView.openPendingDeepLinkIfAny()` after building tabs, which reads and clears the
   session attribute and opens the overlay via the existing `AdvertisementPort.findById()` +
   `AdvertisementOverlay.openForView()` path — no new opening mechanism, reuses what card-click
   already does.
2. **Crawler preview:** `OgMetaRequestListener` (`config/seo/`) implements both
   `VaadinServiceInitListener` (registers itself via `event.addIndexHtmlRequestListener(this)` —
   Spring-Vaadin auto-detects `VaadinServiceInitListener` beans, no manual wiring) and
   `IndexHtmlRequestListener` (the standard, Vaadin-documented way to inject server-side `<head>`
   content per request path, since crawlers only ever read the initial HTML). Matches
   `^/ads/(\d+)$` against `VaadinRequest.getPathInfo()`, looks up the ad via
   `ComponentFactory<AdvertisementPort>` (Caffeine-cached 5 min, matching the existing
   `AuthService` rate-limiter's cache style), and appends `og:title`/`og:description`/`og:url`/
   `og:type`/`twitter:card`/`og:image` (when present) directly onto the Jsoup `Document` Vaadin
   already builds for the index page. `twitter:card` uses `attr("name", ...)` via a dedicated
   `addTwitterMeta()` helper, distinct from `addMeta()`'s `attr("property", ...)` used for `og:*`
   tags — Twitter's crawler specifically requires the `name=` attribute, unlike Open Graph's
   `property=`; Twitter's own spec falls back to `og:title`/`og:description`/`og:image` for
   anything not `twitter:`-prefixed, so no other `twitter:*` tags are needed. `og:image` carries a
   cache-busting `?v=<updatedAt-epoch-second>` suffix (`versionedImageUrl(ad)`, appends `&v=...`
   if the URL already has a query string) — editing an ad's photo changes `updatedAt`, changes the
   query string, and FB/Telegram's per-URL preview cache treats it as a new image, with no manual
   cache-buster step needed after an edit. A `<script type="application/ld+json">` `Product` block
   (`@context`, `@type`, `name`, `description`, `url`, `image`) is appended alongside the meta
   tags, built via a local `private static final ObjectMapper JSON = new ObjectMapper()` (not a
   Spring-managed bean — this project's named `auditObjectMapper`/`userSettingsObjectMapper` beans
   would need an explicit `@Qualifier` per this project's "no `@Primary`" convention, unnecessary
   overhead for this narrow, dependency-free use), serialized via `@SneakyThrows` over
   `writeValueAsString(Map<String,Object>)` and written as a raw `org.jsoup.nodes.DataNode` (not
   `.text(...)`, since Jsoup only skips HTML-entity-escaping for `<script>`/`<style>` content via
   `DataNode` — plain `.text()` would corrupt the JSON by escaping its quotes).
3. `HtmlExcerptUtil.plainText(String html)` (`ui/views/utils/`) extracted from
   `AdvertisementCardView.createDescription()`'s inline Jsoup call — a second real consumer (the
   OG listener needs the same HTML→plain-text excerpt for `og:description`), following this
   project's "shared file only once two or more consumers need it" rule.
4. `app.public-base-url` (config key, `application.yml`, defaults to `http://localhost:8080`,
   overridable via `APP_PUBLIC_URL` env var in prod) — the absolute base every `/ads/{id}` URL is
   built from. `deploy-and-run.sh` passes `-e APP_PUBLIC_URL="http://localhost:$APP_PORT"` to the
   app container (mirroring the existing `S3_PUBLIC_URL` pattern), since the app container's
   internal port 8080 is published to host port 8081 by default (`APP_PORT`, 8080 reserved for
   local IntelliJ dev server) — the config's own `http://localhost:8080` default would otherwise
   point at an unreachable port for local runs. Any real deployment (Render, etc.) still needs its
   own `APP_PUBLIC_URL` pointing at the actual public domain.
5. **Share button:** `AppLinkService.advertisementUrl(Long id)` (`ui/views/services/`) is the
   single place that builds the `{app.public-base-url}/ads/{id}` shape. `ShareUtil.share(Component,
   String url, String title, Runnable onCopied)` (`ui/views/utils/`) tries
   `navigator.share({title, url})` first (mobile — native OS share sheet), falls back to
   `navigator.clipboard.writeText(url)` on desktop, and calls `onCopied` (server-side) only once
   the clipboard-copy path resolves, via Vaadin's `PendingJavaScriptResult.then()` callback — no
   `@ClientCallable` needed. `ShareActionButton` (`ui/views/components/buttons/action/`), a
   sibling to `EditActionButton`/`DeleteActionButton`, sits on `AdvertisementCardView`'s action
   row; the view overlay (`AdvertisementViewOverlayModeHandler`) uses a plain `UiIconButton`
   instead (icon-only header action, matching its existing edit/close buttons), calling the same
   `ShareUtil.share()`.
6. **`sitemap.xml`:** `SitemapController` (`rest/`) — `@GetMapping("/sitemap.xml")`, `produces =
   MediaType.APPLICATION_XML_VALUE`. Builds the standard sitemaps.org `<urlset>` XML by paging
   through the existing `AdvertisementPort.getFiltered()` (empty filter, `Sort.by(Fields.id)` —
   registered as a valid sort alias at the repository level even though not exposed in the UI's
   `AdvertisementSortMeta`, giving a stable, gap-free pagination key) until an empty page is
   returned — no new `AdvertisementPort` method needed. Result cached whole (Caffeine, 15 min,
   single entry). `SecurityConfig` gained `requestMatchers("/sitemap.xml").permitAll()` ahead of
   the catch-all, per the `HealthController` precedent — this is a genuine new REST endpoint,
   unlike `OgMetaRequestListener` (a listener decorating an existing Vaadin-served response, which
   needed no `SecurityConfig` change — `/ads/**` is already covered by the existing
   `anyRequest().permitAll()`).
7. **History API sync:** `AdvertisementOverlay.openForView()` calls
   `UI.getCurrent().getPage().getHistory().pushState(null, "ads/" + id)`; `closeToList()` is
   overridden to `pushState(null, "")` before delegating to `BaseOverlay`'s real close logic (this
   correctly intercepts every path that closes the overlay, including the breadcrumb back button,
   which is wired via `this::closeToList` and resolves virtually to the override). Browser
   Back/Forward is handled via a handler registered through the shared `OverlayNavigationRegistry`
   (see ADR-076): if the new location isn't under `ads/` and the overlay is currently open in VIEW
   mode, it closes without re-pushing history (`super.closeToList()` directly).

**Known limitation, resolved by ADR-076:** Vaadin's `History` API is a single-handler slot
(`setHistoryStateChangeHandler`, not an `addListener`-style multi-registration) —
`AdvertisementOverlay` registering its own handler directly would silently overwrite any other
overlay's handler on the same `UI`. Left unaddressed here since no other domain had a deep-link
route yet; once a second one did, ADR-076 introduced the shared per-UI history dispatcher this note
predicted would be needed.

**Consequences:**
- The app is no longer a pure single-route SPA at the Vaadin router level
  (`AdvertisementDeepLinkView` is a second registered route), though it remains one functionally
  for every real user — the deep-link route immediately forwards, never rendering its own content.
  A future domain adding its own deep-link route follows the same shape.
- Verified end-to-end via Playwright: direct navigation to `/ads/:id` opens the correct overlay; a
  raw `page.request.get('/ads/:id')` (no browser JS — the actual crawler-facing HTML) asserts
  `twitter:card` uses `name=` and a `Product` JSON-LD block is present; the Share button's
  clipboard-copy fallback (headless Chromium has no `navigator.share`) shows a "Link copied"
  notification; `GET /sitemap.xml` returns valid XML containing a just-created test
  advertisement's link; a card click followed by `page.goBack()` asserts the URL updates to
  `/ads/:id` and back-navigation closes the overlay and returns to `/`.
- Sharing a real `/ads/:id` link into an actual Facebook post or Telegram chat is the one item
  that was never automatable — it needs a public URL this sandbox doesn't have, and remains a
  manual check for whoever deploys this.

## ADR-063: List stability after edit — splice-in-place instead of full refresh (Advertisement, User, Taxon)

**Status:** Accepted

**Context:** `AdvertisementsView`/`UserView` both sort `updatedAt DESC, createdAt DESC` and paginate with plain
OFFSET. Editing a row on page 2+ changes its `updatedAt`, so the next `refresh()` (fired
immediately on save, while the overlay is often still open) would move that row to page 1 under
the *current* page's OFFSET — the row the user was just looking at silently vanishes from view,
with the page number left unchanged. Verified directly against `AdvertisementQueryConfig`/
`UserQueryConfig`'s sort and `AbstractEntityOverlay.handleSave()`'s refresh-before-close ordering,
not assumed. External research into keyset/cursor pagination and dashboard "live/paused" UX
patterns confirmed keyset pagination is the industry-standard fix for OFFSET instability under
concurrent inserts/deletes generally, but for the narrower "I edited a row and it moved" case a
much cheaper client-side fix exists: don't refetch the whole page at all, just patch the one row
that changed.

**Decision:** Replaced each overlay's single `Runnable onSaved`/`onChanged` callback with three
purpose-specific callbacks, so the caller (the view) can react precisely instead of always doing a
full-list rebuild:
- `Consumer<T> onUpdated` — fired after an EDIT save; splices the fresh entity into its existing
  position in the currently-rendered list/grid, without touching row count or order.
- `Runnable onListChanged` — fired after a CREATE save; row count genuinely changes, so this still
  does a full `refresh()`.
- `Runnable onClosed` — fired on every overlay close (Advertisement/User only, not Taxon — see
  below); triggers a cheap `count()`-only query compared against the count captured at the last
  `refresh()`, surfacing a refresh affordance if they differ (see "Amended" below for its final
  shape). No continuous polling — this is the only place a stale-count check happens.

Per-domain implementation:
- **Advertisement** (`AdvertisementOverlay`, `AdvertisementCardView`, `AdvertisementsView`):
  `updateCardInPlace(AdvertisementInfoDto)` finds the existing card in the `FlexLayout` container
  by a `data-ad-id` element attribute, rebuilds just that one `AdvertisementCardView`, and
  reinserts it at the same index via `com.vaadin.flow.component.HasOrderedComponents.indexOf()`/
  `.addComponentAtIndex()` — note the correct import is `com.vaadin.flow.component
  .HasOrderedComponents`, not `com.vaadin.flow.component.orderedlayout.HasOrderedComponents` (the
  latter package has no such type; `FlexLayout` gets the interface via `FlexComponent`).
  Deprecated-for-removal in this Vaadin version but still fully functional — not addressed here,
  same as the existing `@Theme` deprecation tracked separately in the backlog.
- **User** (`UserOverlay`, `UserView`): a local `List<UserDto> currentItems` field tracks the
  currently-rendered page; `updateRowInPlace(UserDto)` replaces the matching entry by `id()` and
  re-calls `grid.setItems(currentItems)` — no ordering API needed since `Grid`/`ListDataProvider`
  render directly from the list's own order.
- **Taxon** (`TaxonOverlay`, `TaxonManagementView`): converted for consistency of approach only —
  Taxon has no pagination (`listAllByType()` loads the whole list at once), so the "wrong page"
  symptom this issue exists for cannot occur here. `updateRowInPlace(TaxonDto)` finds the row `Div`
  by a `data-taxon-id` attribute and replaces it via `Div`'s own `HasOrderedComponents`
  implementation (unlike `FlexLayout`, `Div` implements the interface directly — no cast needed).
  **No `onClosed`/banner for Taxon** — no count concept to compare against without pagination, so
  the banner would have nothing meaningful to show.
- Delete/restore (all three domains) still call a full `refresh()` directly — row count or
  active/deleted section membership changes there regardless, so splicing doesn't apply.

**Corrected mid-implementation:** the first `TaxonOverlay.proceed()` pass had CREATE call
`closeToList()` immediately after save, mirroring Advertisement's CREATE behavior. This broke 3
Playwright tests (`03-marketplace-promotion-flow.spec.js`'s category-create/discard-after-save
test, plus two tests cascading from the missing "Electronics" category) — Taxon's CREATE overlay
is expected to **stay open** after save (its own `createCategory()` test helper explicitly clicks
the close (X) button afterward, and a separate test step verifies "modify after save → discard
reverts to the saved values, not empty" while the form is still open). Fixed by removing the
auto-close: `proceed()` now only calls `onUpdated`/`onListChanged` and never `closeToList()`,
matching the pre-existing (and correct) behavior of never auto-closing after any Taxon save.

**Consequences:**
- Editing a row on any page no longer moves or hides it — the currently-rendered page is a
  "frozen" working set until the user takes an action that does a real refresh (page/filter
  change, or clicking the new banner's "Refresh" button).
- If other users create/delete rows while someone is browsing, that browser's view goes stale
  silently until the on-close count check fires (Advertisement/User) — deliberately not
  continuously polled; deeper OFFSET-under-concurrent-mutation instability (unrelated rows
  shifting pages due to someone else's insert/delete, not this user's own edit) remains a known,
  unaddressed gap, tracked as a keyset-pagination follow-up in the backlog.
- Verified via `bash scripts/unit-tests.sh marketplace-app` (73/73, including ArchUnit boundary
  checks) and a full Playwright re-run after the Taxon fix above: `e2e --full --ux`, 50/50.

**Amended (2026-07-25):** the "N changes — Refresh" banner (`Div` with a `Span` message + a
`UiPrimaryButton`) was replaced with a single `UiIconButton` (`VaadinIcon.REFRESH`), same
visibility trigger (`count()` mismatch on overlay close), but no message text — the button's
native `title`/`aria-label` (set from `*_TOOLTIP_REFRESH_AVAILABLE`, a static string, no count
param) carries the explanation instead of a persistent banner row. Its click handler now also
calls the new `PaginationBar.resetToFirstPage()` before `refresh()` — since the sort is
`updatedAt DESC, createdAt DESC`, every change (the user's own CREATE, or anyone else's mutation)
surfaces on page 1, so jumping there on refresh is where the changes actually are, not wherever
the user happened to be paginated to. i18n keys `*_BANNER_CHANGES_AVAILABLE`/`*_BUTTON_REFRESH`
removed in favor of a single `*_TOOLTIP_REFRESH_AVAILABLE` key per domain (Advertisement, User;
Taxon still has neither). Re-verified: unit-tests 73/73, Playwright `e2e --full --ux` 50/50.

## ADR-064: `advertisement` → `user_information` hard FK coupling removed — last one between starters

**Status:** Accepted

**Also affects:** advertisement-spring-boot-starter

**Context:** `01-advertisement-schema.xml` had three physical `addForeignKeyConstraint` blocks from
`advertisement` to `user_information` (`created_by` `ON DELETE RESTRICT`, `updated_by`/`deleted_by`
`ON DELETE SET NULL`) — confirmed the sole such constraint anywhere in the codebase; `taxon`,
`audit`, and `attachment` all store actor references as plain `BIGINT` with no DB-level FK, exactly
matching the Java-level decoupling `advertisement-spring-boot-starter/CLAUDE.md` already documents
(`AdvertisementRepository` never joins `user_information`). Found during F-02 planning review, not
originally sought out.

Traced both real consumers before touching anything, since the constraint turned out to be
load-bearing, not dead weight: `UserDeleteService.delete()` (interactive admin delete) already
cascades correctly and never hits the FK. `UserService.cleanup(retentionDays)` (the retention-purge
`@Scheduled` job) does a genuine hard `deleteById()` and was the one path actually relying on the
constraint — catching `DataIntegrityViolationException` to skip a user still referenced by an
`advertisement` row, since `AdvertisementService.cleanup()` is a *separate*, independently-scheduled
job in a different starter with no cross-starter ordering guarantee.

**Decision:**
1. Removed all three FK constraints from `01-advertisement-schema.xml` directly (edited the
   existing changeset in place, not a new incremental one — the DB has never been released, so
   there is no deployed changelog history to preserve). `idx_advertisement_created_by` kept as a
   plain index, matching `taxon`'s existing pattern for its own actor columns.
2. `AdvertisementPort` gained two methods, split by the two constraints' different semantics rather
   than one generic existence check: `findOwnerIds(Set<Long> userIds)` (subset that created at
   least one advertisement, including soft-deleted rows — mirrors the old RESTRICT, blocks purge)
   and `clearActorReferences(Set<Long> userIds)` (nulls `updated_by`/`deleted_by` — mirrors the old
   SET NULL). Both pure-delegation through `AdvertisementService`/`AdvertisementPortImpl`, per the
   project's `*PortImpl` convention.
3. `UserService.cleanup()` now calls `clearActorReferences()` unconditionally first, then skips
   `deleteById()` only for ids still in `findOwnerIds()`'s result — same observable retry-next-cycle
   behavior as before, no longer dependent on a DB constraint to enforce it.
4. **Verified no UI regression, not assumed** — confirmed `AdvertisementInfoDto` has no
   `updatedBy`/`deletedBy` fields at all and `AdvertisementMapper` doesn't map them, so nulling those
   columns is invisible to every screen. Activity/Timeline actor-name resolution
   (`UserActorNameService`) is a completely separate mechanism keyed on `audit_log.actor_id`, which
   never had a DB constraint either — a purged/unresolvable actor id already fell back to a blank
   name before this change, same as after. `created_by` (the only actor reference actually surfaced
   in the UI, via `createdByUserName`) is *more* protected now than before, since `findOwnerIds()`
   checks it unconditionally regardless of soft-delete state.

**Consequences:**
- `user-spring-boot-starter` can now run its own Liquibase changelog against a database that has
  never seen `advertisement-spring-boot-starter` at all — the concrete thing the old FK made
  impossible.
- `integration-tests/support/RepositoryTestSupport.java` gained a third empty
  `ComponentFactory<AdvertisementPort>` bean (alongside the existing `AuditPort`/`AttachmentPort`
  ones), `@ConditionalOnMissingBean`-guarded since `AdvertisementRepositoryTest` itself already
  provides the real one via `AdvertisementAutoConfiguration` — omitting that annotation causes a
  `BeanDefinitionOverrideException` the moment a test combines both, confirmed by hitting it
  directly during verification.
- Local dev DBs that already had the old changeset applied fail Liquibase's checksum validation on
  the next deploy — expected and handled by `deploy.sh`'s new auto-recovery (see
  `.claude/nav/adr-index.md`), not a regression in this change itself.
- Verified: unit-tests 74/74 (cascades through the full reactor since `AdvertisementPort` is a
  `platform-commons` interface change), integration-tests 126/126 (full suite, not just the
  touched classes), Playwright `e2e --full --ux` 50/50 (twice, including a final check after the
  `deploy.sh` auto-recovery fix landed).

---

## ADR-065: F-02 city dictionary + geo filter — `TaxonType.CITY` reusing the existing taxon assignment mechanism, no schema change

**Status:** Accepted

**Context:** Local service listings are geo-first ("плиточник у Луцьку" is a real query shape); without a city
facet the catalog is unfilterable past one city. The issue's own research (see its `## Suggested
fix`) had already ruled out two tempting shortcuts: a `city_taxon_id BIGINT REFERENCES taxon(id)`
FK column on `advertisement` (would recreate the exact starter-to-starter hard coupling ADR-064
just removed, this time within the same session), and a Liquibase `<insert>` seed changeset for
city rows (no such changeset exists anywhere in this project — categories aren't seeded that way
either, only ever created through the running admin UI).

**Decision:**
1. Added `CITY` to `TaxonType` (`platform-commons`, alongside `CATEGORY`) — no schema change
   anywhere. A city assignment is just another `taxon_assignment` row
   (`entity_type='ADVERTISEMENT', taxon_id=<city id>`), the exact same mechanism categories already
   use via `TaxonPort.replaceAssignments()`/`getForEntity(s)`.
2. `AdvertisementInfoDto`/`AdvertisementFilterDto`/`AdvertisementSaveDto`/`AdvertisementSnapshotDto`
   each gained a `cityTaxonId`/`cityName` field, mirroring `categoryIds`'s shape exactly (`diff()`
   and `allFields()` updated the same way).
3. **Critical correctness hazard, caught by reading `TaxonAssignmentService.replaceAssignments()`'s
   actual source before writing any save-path code, not assumed:** it does a full diff-replace
   across *all* taxon types assigned to an entity in one call. `AdvertisementSaveService.save()`
   therefore unions category ids and the city id into one `Set<Long>` before the single
   `replaceAssignments()` call — two separate calls would have silently wiped whichever ran first.
   The same file's `buildCurrentSnapshot()`, `AdvertisementService.enrichWithTaxons()` (renamed from
   `enrichWithCategories()`), and `AdvertisementEnrichService`'s audit-diff resolution all had to
   split `getForEntity(s)`'s *type-unfiltered* result by `TaxonDto.getType()` client-side for the
   same reason — none of these port methods take a `TaxonType` parameter.
4. **Same expandWithChanges() raw-id fallback bug pre-empted for city from the start** (the bug
   this session already root-caused and fixed for categories earlier): `AdvertisementEnrichService
   .resolveCity()` always appends a resolved `cityTaxonId` entry when the enriched snapshot has one
   to show — mirroring `resolveCategories()`'s shape exactly. Caught immediately in unit tests
   (below) that BOTH `resolveCity()` and the pre-existing `resolveCategories()` were appending a
   synthetic empty entry even when that specific field had nothing to show (`nameById` non-empty
   because the *other* field's ids resolved) — fixed by guarding the append on `currId != null ||
   prevId != null` (city) / non-empty `currIds`/`prevIds` (categories), not just "did we already
   attach an entry".
5. **New `City*` classes by analogy, not a parameterized `Taxon*`** — matches the issue's explicit
   rejection of a generalized `TaxonManagementView(TaxonType)`: `TaxonOverlay` is a `@UIScope`
   singleton, so two simultaneous tabs (Categories, Cities) need two distinct bean instances, not
   one shared parameterized instance. Added `CityEditDto`, `CityManagementView`, `CityOverlay`,
   `CityFormOverlayModeHandler`, `CityViewOverlayModeHandler` (same packages as their `Taxon*`
   counterparts), each hardcoded to `TaxonType.CITY`. `ReferenceDataView` gained a second
   `Tab`/page pair ("Cities"). CSS classes deliberately reused where the shape is identical
   (`taxon-locale-content`, `taxon-row-wrapper`, `taxon-list-container`, ...) and deliberately
   distinct where the two screens must render simultaneously in the DOM
   (`city-management-view`/`city-overlay`/`city-add-button` vs. `taxon-management-view`/
   `taxon-overlay`/`taxon-add-button`) — reusing a class name across two concurrently-mounted trees
   would make CSS-scoped Playwright selectors ambiguous.
6. Advertisement UI: single-select `ComboBox<TaxonDto>` (categories use `MultiSelectComboBox` — no
   existing single-select precedent in this codebase before this change) in the query block, form,
   view overlay (chip) and card (badge below the categories line) — mirroring the existing category
   rendering shape exactly, new `advertisement-city-chip`/`advertisement-city` CSS classes.
7. Initial city list entered by hand through `CityManagementView` after deploy, same as categories
   — no Liquibase seed.

**Consequences:**
- Zero schema changes anywhere in this feature — verified no new Liquibase changeset was needed in
  any of the three starters it touches.
- A **real, unrelated bug was found and fixed as a side effect**: `TaxonManagementView`/
  `TaxonOverlay`'s `listAllByType()`/`findById()` calls hardcode `Locale.ENGLISH` rather than the
  current UI locale — pre-existing, not introduced by this change, left as-is (out of scope; not
  yet filed as a separate issue at the time of this ADR).
- Playwright: extended existing tests rather than adding new spec files, per explicit instruction —
  city admin creation/edit folded into spec 03's existing category-admin tests (`city.flow.js` new
  shared flow file, mirroring `category.flow.js`'s shape only where the UI shape actually matches
  single- vs. multi-select), a `city`/`cityToSet` param added to
  `runCreateAdvertisementFlow`/`runEditAdvertisementFlow` (spec 04), and a city filter folded into
  spec 05's existing seed/filter test (`fillCity()` in `filter.flow.js`, `SEED_CITIES`/`CITIES`
  constants, distinct city names from spec 03's Lviv/Kyiv to avoid dupes in full-suite mode).
- **Second real bug found via the Playwright run itself, not by inspection:** `openReferenceDataTab()`/
  the spec-03-local `openRefDataTab()` only clicked the top-level "Reference Data" tab and assumed
  the "Categories" sub-tab was showing — but Vaadin's `Tabs` component retains its last selection
  across visibility toggles, so once a test visited the new "Cities" sub-tab, every later call to
  these helpers left `.taxon-management-view` hidden. Fixed by having both helpers explicitly
  reselect "Categories" every time, making them deterministic regardless of prior sub-tab state.
- Verified: unit-tests 75/75 (including two new tests that pinned down the resolveCity/
  resolveCategories noise bug above), integration-tests 128/128 (two new `AdvertisementSnapshotDto`
  `cityTaxonId` diff tests), Playwright `e2e --full --ux` 50/50 (after fixing the sub-tab-selection
  bug and one activity-version off-by-one caused by the new `cityToSet` step adding an extra save
  to an existing edit-lifecycle test).

**Update (2026-07-25, post-implementation SOLID/DRY review):** A repo-wide review pass (11 parallel
read-only agents, one per module) surfaced two more real bugs in this feature, both fixed and
covered:
1. `AdvertisementActivityFieldsHookImpl.labelFor()` had no `case` for `cityTaxonId` — the Activity
   tab showed the raw field key `"cityTaxonId"` instead of a translated "City" label. Fixed by
   adding the missing case (new `CHANGES_FIELD_CITY` key), with a Playwright assertion
   (`toContainText('City')` on the diff row) that fails against the pre-fix code.
2. `AdvertisementService.findById()` re-implemented its own inline category/city split instead of
   calling the existing `enrichWithTaxons()` — and its inline version never set
   `categoryNames`/`cityName` at all (only `categoryIds`/`cityTaxonId`). Since `findById()` is what
   refreshes a card in place after an edit save (`AdvertisementFormOverlayModeHandler` →
   `savedInfoDto` → `AdvertisementOverlay.proceed()` → `updateCardInPlace()`), every edit save was
   silently dropping the category/city text from that one card until the next full list refresh —
   a real, user-visible regression, not just a DRY nit. Fixed by having `findById()` call
   `enrichWithTaxons(List.of(dto), Locale.ENGLISH)` directly, and added a Playwright assertion
   (`assertCardHasCity` on the card right after an edit-save) that fails against the pre-fix code.
3. Also applied, lower-risk: extracted `AdvertisementEnrichService.resolveField()` (shared by
   `resolveCategories()`/`resolveCity()`, replacing ~30 duplicated lines), `AdvertisementService
   .resolveTaxonIdFilter()` (shared by `resolveCategoryFilter()`/`resolveCityFilter()`),
   `AdvertisementViewOverlayModeHandler.buildChipRow()` (shared category/city chip rendering),
   `AdvertisementCardView.createInfoLine()` (shared category/city card-line rendering), and
   `AdvertisementSnapshotDto.idToString()`/`AdvertisementEnrichService.idToName()` now delegate to
   their list-based siblings instead of duplicating the same null-check-and-format ternary.
4. The same review surfaced a substantial list of **pre-existing** findings across every other
   module (attachment-starter, audit-starter, platform-commons, query-lib, taxon-starter,
   user-starter, and other marketplace-app areas) unrelated to this feature — reported to the user
   for separate triage, not fixed here to keep this change's blast radius scoped to what F-02
   actually touched.
- Re-verified after this update: unit-tests 75/75, integration-tests 128/128, Playwright
  `e2e --full --ux` 50/50 (including the two new regression assertions above).

## ADR-066: F-03 listing types (Offer/Request/Product) — new `ad_kind` column, `RadioButtonGroup` (first use in this codebase), multi-select filter

**Status:** Accepted

**Context:** The catalog had no way to distinguish "I'm offering X" from "I'm looking for X" from "selling a
physical product X", collapsing three different user intents into one undifferentiated list.
Unlike F-02's city facet (ADR-065), this is a **mandatory, always-present** classification with a
small closed set of values — not a natural fit for the taxon/taxon_assignment mechanism (no
translations, no soft-delete, no admin-managed dictionary), so it's modeled as a genuine new
column instead.

**Decision:**
1. New `AdKind` enum (`platform-commons`, `org.ost.platform.advertisement.model`, alongside
   `taxon.model`/`user.model`/`attachment.model`) — `OFFER`, `REQUEST`, `PRODUCT`.
2. New `advertisement.ad_kind VARCHAR(20) NOT NULL DEFAULT 'OFFER'` column, added to the
   *existing* `01-advertisement-schema.xml` changeset (never released, per the project's
   edit-in-place convention — ADR-064's precedent) plus a plain (non-unique) index. Direct
   `EnumType` field on the `Advertisement` entity — Spring Data JDBC maps it to `VARCHAR` natively
   (same as `User.role`), no custom converter.
3. `AdvertisementInfoDto`/`SaveDto`/`FilterDto`/`SnapshotDto` each gained a `adKind` /
   `adKinds` (filter, `Set<AdKind>`) field, mirroring the existing field shapes;
   `SnapshotDto.diff()`/`allFields()` updated the same way as every other field.
4. **UI: `RadioButtonGroup<AdKind>` — first use of this Vaadin component in the codebase**
   (confirmed via grep before use), chosen over `ComboBox`/`MultiSelectComboBox` because this field
   is mandatory, always-visible, and has exactly one value from a small fixed set — the "pick
   exactly one, always shown" shape neither existing pattern fits. Filter uses
   `MultiSelectComboBox<AdKind>` (mirrors `categoryIds`'s shape — filtering can match any of
   several types at once).
5. **Binder default-value hazard, caught by reasoning about `readInitialValues()` before writing
   the code, not discovered by a failing test:** calling `.setValue(AdKind.OFFER)` directly on
   the widget in `activate()` would be silently overwritten the moment `binder.readInitialValues()`
   reads the (null) DTO value back into the field. Fixed by setting the default on
   `AdvertisementEditDto` itself (`.builder().adKind(AdKind.OFFER).build()`) for CREATE
   mode, not on the widget.
6. **Activity diff localization, the one real bug this feature's own Playwright run surfaced:**
   `AdvertisementSnapshotDto.diff()` stores the raw enum name (`"OFFER"`, `"PRODUCT"`) as the
   `FieldChange` value — correct for a stable, diffable representation, but wrong to show a user
   directly. `AdvertisementEnrichService` gained `resolveAdKind()`, mirroring
   `resolveCategories()`/`resolveCity()`'s shape but *not* reusing their shared `resolveField()`
   helper: those two manufacture a synthetic entry whenever the field currently has a value, even
   if `diff()` didn't include one for that specific update (deliberate, since category/city are
   optional — showing them gives full current-classification context). Listing type is *never*
   absent, so the same manufacture-if-missing behavior would inject a "Listing type: Offer" line
   into every single activity/timeline row regardless of what actually changed. `resolveAdKind()`
   instead only relabels an entry `diff()`/`allFields()` already produced (via
   `ChangeEntry.replaceIfField()` directly), leaving the list untouched — same size, same reference
   identity — when no such entry exists.
7. Playwright: extended existing tests rather than adding new spec files, per established
   convention — `adKind`/`adKindToSet` params added to
   `runCreateAdvertisementFlow`/`runEditAdvertisementFlow` (spec 04), a listing-type filter folded
   into spec 05's existing seed/filter test, `selectAdKind()`/`assertCardHasAdKind()`/
   `assertViewOverlayHasAdKind()` added to `advertisement.flow.js`.

**Consequences:**
- **Playwright selector collision, caught by reasoning about existing selector scope before
  running any test:** adding a second `MultiSelectComboBox` (listing type) to
  `AdvertisementQueryBlock` would silently break `fillCategory()`'s pre-existing bare-tag-name
  selector. Fixed proactively with `data-testid` on both combos.
- **Second, related bug the Playwright run itself did surface:** `data-testid` scoping the *input*
  wasn't enough — `fillCategory()`/`fillAdKind()` also waited on a bare
  `vaadin-multi-select-combo-box-overlay` locator's `.first()` for visibility. Once a second
  multi-select combo exists on the same page, its own (by-then-hidden, from an earlier use)
  overlay element can still be the one `.first()` resolves to, so the wait never sees the *actual*
  newly-opened overlay become visible. Fixed by waiting on the specific combo element's own
  `opened` property via `page.waitForFunction()` instead of any shared overlay-tag locator.
- **Third bug the Playwright run surfaced:** the default-listing-type assertion
  (`adKind || 'Offer'`) hardcoded the English label regardless of the logged-in test user's
  UI locale — failed for `userUk` (real per-account persisted Ukrainian locale, switched earlier in
  the suite) whose card correctly rendered "Пропозиція". Fixed with a `locale` param +
  `DEFAULT_AD_KIND_LABEL` map in `advertisement.flow.js`. Note this is distinct from
  `maxUk`/`maxEn` in spec 04's boundary tests — those names describe the *ad content's* language
  only; neither account's UI locale is ever actually switched in that describe block, so both
  correctly assert the English default.
- **Fourth bug (test-only, not production): spec 05's exact per-type filter count.** Unlike
  category/city (optional — a pre-existing test ad with no category/city never pollutes an
  exact-match filter count), every advertisement always has *some* listing type, so non-seed ads
  left behind by earlier specs always land in one of the three buckets and inflate whichever count
  is checked. Fixed by asserting `>= SEED_COUNT / AD_KINDS.length` (same `getTotalCount()` +
  `toBeGreaterThanOrEqual` idiom `verifyDateRangeFilters()` already used for this exact class of
  problem) instead of an exact match.
- Verified: unit-tests 77/77 (including two new `AdvertisementEnrichServiceTest` cases pinning the
  localize-only-if-present behavior above), integration-tests unaffected (schema/repository change
  only — `AdvertisementRepositoryTest`'s shared `save()` fixture needed `.adKind(OFFER)` added
  to satisfy the new `NOT NULL` column), Playwright `e2e --full --ux` 50/50.

**Update (2026-07-27, same-day rename):** renamed `ListingType`→`AdKind` end-to-end (enum, DTO
fields, `listing_type`→`ad_kind` column/index, i18n keys, CSS classes, Playwright identifiers) —
`ListingType listingType` read as an awkward stutter and the name didn't convey the concept well.
Also moved the field to display after City on the card, edit form, and view overlay (was
positioned right after Description). `AdvertisementSnapshotDto.diff()`'s `adKind` comparison now
uses the shared `field()`/`diffField()` helpers (same as `title`/`description`) instead of a
hand-rolled ternary + `Objects.equals` — the null case it still guards against is a historical
snapshot recorded before this column existed, not a live/fresh save. Playwright's
`assertCardHasCity`/`assertViewOverlayHasCity` (`city.flow.js`) and the `AdKind` equivalents
(`advertisement.flow.js`) were unified into shared `assertCardHasText()`/`assertOverlayHasText()`
helpers in `_helpers.js`. Re-verified after the rename: unit-tests 77/77, integration-tests
130/130, Playwright `e2e --full --ux` 50/50.

---

## ADR-067: Activity/restore moved from an "Activity" tab to a stacked nested overlay

**Status:** Accepted, rolled out to all five domains (Settings, Advertisement, Taxon, City, User)

**Context:** Every existing overlay (Advertisement, Taxon, City, User, Settings) pairs its one
content tab with an "Activity" tab (`AbstractFormOverlayModeHandler.buildContentWithActivity()` /
`buildTabbedContent()`) showing snapshot history with per-row Restore. While planning a
unified "My Account" overlay (3 content tabs spanning 2 backing entities) for provider profiles,
this 1-tab-content + 1-tab-activity pairing
was found not to generalize — naively extending it would mean either 6 tabs or awkward asymmetric
pairing. A redesign of this was filed, with Settings chosen as the smallest, self-contained pilot
before rolling the pattern out to the other four overlays and the future Account overlay.

**Decision:** History/restore no longer lives in a tab. It is a second, standard overlay
(`SettingsActivityOverlay`, same `BaseOverlay`/`OverlayLayout`/breadcrumb shape as every other
overlay in the app) that visually stacks on top of the already-open Settings overlay — Settings is
never actually closed underneath, just covered. Consequence: since Activity was the *only* second
tab, Settings' own content also stops being wrapped in a `Tabs` component — it renders directly.

**X always means "go back to whatever screen opened this overlay" — not "exit to Home".** For
every *existing* single-level overlay those two things are the same screen, which is why X there
has always looked like it means "exit to Home" (e.g. `SettingsFormModeHandler`'s own close button
tooltip was already `HEADER_HOME`). The nested history overlay is the first place in the codebase
where the two meanings actually diverge — it was opened from Settings, not Home, so its X goes
back to Settings. (An earlier draft of this ADR had this backwards — X was briefly wired to close
all the way to Home — corrected after direct user testing; see "Update" below.)

**Breadcrumb is a real, multi-segment chain — Home / Settings / Activity — not a single
back-link.** Both "Home" and "Settings" are independently clickable; only "Activity" (the current
page) is plain text. This is genuinely new: every existing overlay's `OverlayLayout` only ever had
one back-link + one current-page label. `OverlayLayout.setBreadcrumbLinks(List<Component>)` (new,
`setBreadcrumbButton(Component)` now delegates to it as the 1-link case) and a matching
`EntityOverlaySupport.createLayout(List<Component>)` overload generalize this without touching any
existing single-link overlay's behavior or call sites. Restoring a snapshot closes back to
Settings (the "Settings" link's target), since the point of restoring is to review/save it there.

**New, small, additive infrastructure — `BaseOverlay.openNested()` / `closeNested()`.** Every
existing overlay assumes exactly one is ever open at a time, and `open()`/`closeToList()` couple
CSS-class visibility toggling with **page-level** scroll-lock + focus-trap JS and a `ui`-level ESC
`Shortcuts` registration. Stacking a second `BaseOverlay` on top and calling the existing
`open()`/`closeToList()` unmodified would let the inner overlay's close release the *page* scroll
lock the outer overlay still needs, and both overlays would independently react to Escape. Fix:
`openNested()`/`closeNested()` toggle only the `overlay--visible` class + their own ESC listener,
skip the `document.body` scroll/focus-trap JS entirely (owned solely by whichever overlay opened
first via the original `open()`). Every existing single-level overlay keeps using `open()`/
`closeToList()` unchanged. CSS: `.settings-activity-overlay.overlay--visible { z-index: 101; }` —
one higher than the shared `.base-overlay`'s `z-index: 100` — so the nested overlay reliably paints
above Settings rather than relying on DOM insertion order alone.

**`SettingsActivityOverlay` is a plain `BaseOverlay`, not an `AbstractEntityOverlay`** — the latter
carries Save/Discard/`OverlaySession`/form-handler machinery this read-only history panel doesn't
need; it just needs the breadcrumb + layout + close-button shell every overlay already shares via
`EntityOverlaySupport`.

**Removed:** `SettingsFormModeHandler`'s `Tabs`/`Tab`/`buildTabbedContent()` usage, the
`SETTINGS_ACTIVITY_TAB` i18n key (replaced with `SETTINGS_ACTIVITY_BUTTON`, since it now labels an
icon button and the nested overlay's breadcrumb-current text, not a tab), and the now-orphaned
`.user-view-tabs` CSS rule (verified zero other consumers before deleting).

**Explicitly out of scope for this pass** (deferred to a later step once this
experiment is validated): Advertisement/Taxon/City/User overlays still use the old tab pairing
unchanged; `AbstractFormOverlayModeHandler`'s shared `buildTabbedContent()`/
`buildContentWithActivity()` methods are untouched.

**Playwright:** `settings.flow.js` gained `openHistory()`/`closeHistory(page, via)`, replacing the
old tab-click helper; `restoreLatestFromActivity()` updated for the overlay-close (not tab-select)
contract. `_helpers.js`'s shared `closeOverlay()` was scoped to `.base-overlay.overlay--visible
.overlay__breadcrumb-back` (previously unscoped) — defensive fix, since a second always-present,
occasionally-initialized overlay in the header now makes the old unscoped selector a latent
multi-match risk for *any* spec that opens Settings' history at least once. `audit.flow.js`'s
`runVerifySettingsAfterSignupFlow` repointed to the new overlay; its sibling
`runVerifySettingsActivityFlow` was dead code (zero callers) and was deleted outright rather than
fixed forward. Spec 05's "verify save from activity tab switches view back to settings form"
sub-test — meaningless under the new design, since the history overlay now covers the Save button
entirely — was replaced with an equivalent-purpose check: an unsaved field edit survives a trip
into history and back out (via breadcrumb) before being saved.

**Verified:** unit-tests 77/77, integration-tests 133/133 (no schema/repository changes — pure UI
refactor), Playwright `e2e --full --ux` 50/50.

**Update (2026-07-28, same day, after direct user review of the running app):** two real bugs
found by manually exercising the feature, not by inspection:
1. **X was wired to "exit to Home" instead of "back to the opening screen"** — corrected as
   described above (`closeToHome()` renamed usage swapped for `closeToParent()` on the X button;
   the breadcrumb's new "Home" link is the only path that actually exits to Home now).
2. **Doubled-up "›" separator, then uneven spacing between segments.** First bug:
   `OverlayLayout.setBreadcrumbLinks()` added a trailing separator after *every* link including
   the last one, which collided with the outer, always-present separator before
   `breadcrumbCurrent` — fixed by only inserting a separator *between* links, not after the last
   one (`if (i < links.size() - 1)`). Second bug, found immediately after via visual review of the
   rendered page: `.overlay__breadcrumb-back-slot` (the container holding the link chain) had no
   `display: flex`, so its children had no consistent layout — the single-link case looked fine
   only because it happened to be one element, but the fix's first pass (adding `display: flex;
   align-items: center;` with no `gap`) still under-spaced the inner separator relative to the
   outer one (which benefits from the parent `.overlay__breadcrumb`'s own `gap: 4px` in addition to
   the separator's own `padding: 0 2px`). Fixed by giving `.overlay__breadcrumb-back-slot` the same
   `gap: 4px` as its parent, so every segment gap in the chain is visually uniform regardless of
   whether it's "inside" or "outside" the link-chain container. Both fixes covered by new Playwright
   assertions on separator count (`toHaveCount(2)`) and absence of a doubled `››` substring, for
   both the multi-link (history overlay) and single-link (Settings itself) cases. Re-verified:
   unit-tests 77/77 (no Java logic changed by the CSS fix), Playwright `e2e --full --ux` 50/50.

**Update (2026-07-28, same day): rolled out to Advertisement, Taxon, City, User — one shared,
generic component, not five near-duplicate classes.** Per this ADR's own pilot-stage question
("does this need a shared helper before rolling out further?") — yes. `SettingsActivityOverlay`
(the Settings-only class from the pilot) was deleted; a single `EntityActivityOverlay`
(`ui/views/components/audit/`, `@SpringComponent @UIScope`, registered once in `HeaderBar` since
it must be reachable regardless of which routed View is currently showing) now backs all five
domains, parameterized via a `Parameters` builder (`entityRef`, `userId`, `isPrivileged`,
`canOperate`, `outerLabelKey`, `parentLabelKey`, `currentLabelKey`, `onCloseToOuter`,
`onRestoreRequested`) instead of Settings-hardcoded values.

**One correction to the pilot's own stated rule, caught by reading each domain's actual
`*Overlay.getBreadcrumbLabelKey()` before generalizing:** the pilot's ADR text said "X always
means exit to Home" — true only by coincidence for Settings, whose own outer breadcrumb link
really is `HEADER_HOME`. Every other domain's own overlay breadcrumb says something else
(`AdvertisementOverlay`/`TaxonOverlay`/`CityOverlay`/`UserOverlay.getBreadcrumbLabelKey()` return
`MAIN_TAB_ADVERTISEMENTS`/`MAIN_TAB_REFERENCE_DATA`/`MAIN_TAB_REFERENCE_DATA`/`MAIN_TAB_USERS`).
`EntityActivityOverlay`'s breadcrumb chain is genuinely two independently-labeled links —
`outerLabelKey` (exits all the way, e.g. back to the Advertisements list) and `parentLabelKey`
(one level up, back to the form overlay this was opened from) — supplied per call site, not a
single hardcoded "Home" concept.

**New, small, additive infrastructure for the multi-link breadcrumb —
`OverlayLayout.setBreadcrumbLinks(List<Component>)`** (existing `setBreadcrumbButton(Component)`
now delegates to it as the 1-link case) and a matching `EntityOverlaySupport.createLayout
(List<Component>)` overload. No existing single-link overlay's call site or behavior changed.

**`AbstractFormOverlayModeHandler` — dead tab machinery deleted once all five callers migrated:**
`buildTabbedContent()`, `buildContentWithActivity()`, `ActivityTabParams`, and the
`tabbedSecondaryContent`/`formTabs`/`editTab` protected fields are gone — verified zero remaining
callers first. Every `*FormOverlayModeHandler` now does `layout.setContent(...)` unconditionally
and adds a history icon button (`.advertisement-history-button` / `.taxon-history-button` /
`.city-history-button` / `.user-history-button` / `.settings-history-button`) to `headerActions`,
guarded by the same `auditPortFactory.findIfAvailable()` + `canOperate`/`!isCreateMode` condition
`ActivityTabParams` used to gate on. Also deleted: the now-orphaned `*_ACTIVITY_TAB`/
`*_TAB_ACTIVITY` i18n keys (renamed to `*_ACTIVITY_BUTTON`, same rename-not-reuse-in-place
reasoning as the pilot), `*_OVERLAY_TAB_EDIT` keys (the "Edit" tab label, meaningless once there's
no tab), and the `.activity-feed-content`/`.entity-activity-content` tab-pane wrapper CSS rules.

**Playwright — extracted `_flows/entity-activity.flow.js`, the shared helper this ADR's pilot
section deferred designing.** `openEntityActivity(page, buttonSelector)` /
`closeEntityActivity(page, via)` (`'parent'`/`'x'`/`'outer'`) / `restoreFromEntityActivity(page,
index)`. `closeEntityActivity` is deliberately idempotent (no-op if the overlay isn't open) —
discovered necessary mid-rollout: several verification helpers (`assertSingleCurrentBadge`,
`assertLatestActivityVersion` in `advertisement.flow.js`) needed to leave the overlay closed
behind them regardless of what the calling test does next, and tracking "did the previous step
already close it" by hand across ~30 call sites was too error-prone — an idempotent close made
every call site correct regardless of ordering. `settings.flow.js` (the pilot's own helpers) was
simplified to thin thin wrappers over the shared helper once Settings itself moved onto
`EntityActivityOverlay` — it no longer duplicates the open/close/restore logic.
`advertisement.flow.js`'s existing `openActivityTab(overlay)` kept its old name/signature
(deriving `page` via `overlay.page()`) so its ~15 internal call sites and 3 external ones (spec 04)
needed no changes beyond the function body itself.

**A real, separate bug caught only by explicitly checking for stale references before running
the suite (per direct user instruction mid-rollout):** `_flows/audit.flow.js` and the pilot's own
`05-seed-filter-sort-pagination.spec.js` still referenced the deleted `.settings-activity-*` CSS
classes (`.settings-activity-overlay`, `.settings-activity-close-button`,
`.settings-activity-breadcrumb-home/-settings`) after Settings was retrofitted onto the generic
`EntityActivityOverlay`'s `.entity-activity-*` classes — both fixed before running. A useful
confirmation that a "check for duplicate/stale references" pass is worth doing explicitly on any
change that renames a shared CSS contract, not just a compile-and-run pass (CSS class typos don't
fail a build).

**Verified (full rollout, all five domains):** unit-tests 77/77, integration-tests --sandbox
133/133 (no schema/repository changes — pure UI refactor), Playwright `e2e --full --ux` 50/50.

**Update (2026-07-28, same day): outer breadcrumb link fixed to close all the way to the list,
not just cancel the edit — caught by direct user testing (List→View→Edit→History, then click the
outer link).** `onCloseToOuter` had been wired to each `*FormOverlayModeHandler.Parameters
.onCancel` — semantically wrong wherever a domain has a View mode with `enteredFromView` tracking
(`AdvertisementOverlay`, `UserOverlay`, `TaxonOverlay`, `CityOverlay` all have this; only Settings,
with no View mode at all, made `onCancel` and "close to list" coincide). `onCancel` triggers
`afterDiscard()`, which reverts to VIEW (not the list) when the session was entered from View —
so the outer link only worked on the second click, and only when it happened to already be back at
VIEW. Fix: each affected `*FormOverlayModeHandler.Parameters` gained a distinct `@NonNull Runnable
onCloseToList`, wired at each `*Overlay.switchTo()`'s EDIT/CREATE branch (and, for
Taxon/City, also in `save()`'s post-save `Parameters` rebuild) to `this::closeToList` — a genuine,
unconditional exit, never routed through `afterDiscard()`. `buildHistoryButton()` in all four
handlers now passes `.onCloseToOuter(params.getOnCloseToList())` instead of `params.getOnCancel()`.
Not caught by the rollout's own Playwright suite because no existing test exercised `via: 'outer'`
for these four domains (only Settings' pilot tests did, where the bug happens not to exist) —
new coverage added per domain for the List→View→Edit→History→outer-link path specifically.

**Same pass: Advertisement's breadcrumb parent-label wording brought in line with the other
domains.** Taxon/City/User/Settings all reuse their form card's own header text as the nested
overlay's `parentLabelKey`, and that header text happens to read as "{Entity} details" in every
one of them — except Advertisement, whose card header is `ADVERTISEMENT_OVERLAY_SECTION_BASIC`
("Basic information", a field-section label, not an entity-name label), giving a
`Advertisements › Basic information › Activity` breadcrumb that reads inconsistently with the
other four domains' `{List} › {Entity} details › Activity` shape. Fixed with a new,
breadcrumb-only key (`ADVERTISEMENT_ACTIVITY_PARENT_LABEL` = "Advertisement details" / "Деталі
оголошення") used solely as `parentLabelKey` in `AdvertisementFormOverlayModeHandler
.buildHistoryButton()` — the card header itself still reads "Basic information", unchanged, since
that label is correct in its own context (a fields-section heading, not an entity name).

**Update (2026-07-28, same day): the Edit overlay's own breadcrumb (not the nested History
overlay's) now reflects the real navigation path — a "View" link appears only when the session
was actually entered via View.** Before this, `AdvertisementOverlay`/`UserOverlay`/`TaxonOverlay`/
`CityOverlay` each built a single, fixed breadcrumb link (`breadcrumbButton`, always the list tab,
e.g. "Advertisements") once in `AbstractEntityOverlay.buildContent()` — identical whether Edit was
entered directly from the list or via View, even though the two paths behave differently on
Cancel (`afterDiscard()` reverts to View only for the latter, per the `onCloseToList` fix above).
Per direct user feedback after testing the running app: the breadcrumb should show that extra step
when it was actually taken, not hide it. `AbstractEntityOverlay` gained an overridable
`buildBreadcrumbLinks()` (default: `List.of(breadcrumbButton)`), called via `layout
.setBreadcrumbLinks(buildBreadcrumbLinks())` as the first line of every concrete `switchTo()` —
each of the four overlays overrides it to insert a second link, labeled with the new shared
`OVERLAY_BREADCRUMB_VIEW` i18n key ("View" / "Перегляд"), whenever `session.mode() == EDIT &&
session.enteredFromView()`. That link's action is `this::handleCancel` — the exact call the X
button already makes for a View-entered session — so no new transition logic was needed, only a
second visible entry point to it. Reuses the already-existing `OverlayLayout.setBreadcrumbLinks`/
`EntityOverlaySupport.createBreadcrumbButton` infrastructure from the nested-History-overlay work
above; no CSS changes needed (multi-link spacing/separators were already fixed there).

**Caught two real regressions from this change before it shipped, both by running the full
Playwright suite, not by inspection:** (1) `TaxonOverlay.buildBreadcrumbLinks()` was never actually
added — only the `switchTo()` call site was wired — so Taxon silently kept the old single-link
behavior; caught by a `toHaveCount(2)` assertion actually failing (`Received: 1`). (2)
`closeOverlayToList()` in `advertisement.flow.js` (`overlay.locator('.overlay__breadcrumb-back')
.click()`, used throughout the suite as a generic "close whatever's open, straight to list"
helper) started throwing a Playwright strict-mode violation ("resolved to 2 elements") the moment
any test called it from a View-entered Edit session — fixed with `.first()`, which is also
semantically correct since the outer list link is always index 0 in `buildBreadcrumbLinks()`'s
returned list. Both fixed, then the full suite re-run clean. New Playwright coverage: breadcrumb
link-count assertions (`toHaveCount(1)` direct-entry / `toHaveCount(2)` via-View) added at the
existing View/direct-edit entry helpers for all four domains
(`runOpenUserEditViaViewFlow`/`runOpenUserEditViaListFlow`, `openTaxonEdit`/the new taxon
View→Edit step, `openCityEdit`/the new city View→Edit step, and the Advertisement outer-link step).

**Verified (both changes together):** unit-tests 77/77, integration-tests --sandbox 133/133,
Playwright `e2e --full --ux` 50/50.

**Update (2026-07-29): the fixed "insert a View link" approach above was itself replaced with a
genuine, growing breadcrumb stack — by further direct user feedback the same day.** The previous
update inserted a *second, hardcoded* link only for the Edit-overlay's own breadcrumb; it didn't
touch the nested History overlay's breadcrumb at all, which still only ever showed a fixed
2-segment chain (`outerLabelKey`/`parentLabelKey`) regardless of how deep the actual navigation
went. The user's correction: going deeper should never rewrite existing segments, only append one
— by the same mechanism already used for Settings (a pre-built link chain handed to the nested
overlay), generalized so the chain's length reflects the real path:
- List→View: `Ads(link) > Details(current)`.
- List→View→Edit: `Ads(link) > Details(link) > Edit(current)`.
- List→Edit (direct, no View): `Ads(link) > Edit(current)` — the Details segment never appears at
  all, not just collapsed/hidden.
- ...→Edit→History: the current level's own current-page label ("Edit") becomes a link too, and
  "Activity" becomes the new current — extending the exact same chain, not recomputing it.

**New shared type — `BreadcrumbStep(String label, Runnable onClick)`** (`ui/views/components/
overlay/`), a label already resolved to a string (not an `I18nKey`) so it also fits `UserOverlay`'s
existing dynamic-name current-label, not just the other three domains' static title keys.
`AbstractEntityOverlay.buildBreadcrumbSteps()` (default: one step, the list tab) replaces the
previous update's `buildBreadcrumbLinks()`; `buildBreadcrumbLinks()` is now a `final`-in-spirit
derived method that turns steps into rendered `OverlayBreadcrumbBackButton`s via
`EntityOverlaySupport.createBreadcrumbButton(String, Runnable)` (new overload; the existing
`I18nKey` overload now delegates to it). Each of the four domains' `buildBreadcrumbSteps()`
override just appends the "View" step (unchanged from the previous update) — the growth logic
lives entirely in how that list gets *consumed* downstream, not in how it's built.

**`EntityActivityOverlay.Parameters` no longer takes `outerLabelKey`/`parentLabelKey`/
`onCloseToOuter`.** It now takes `parentSteps` (`List<BreadcrumbStep>` — literally
`*Overlay.buildBreadcrumbSteps()`'s own output, passed through the `*FormOverlayModeHandler
.Parameters.breadcrumbSteps` field added for this purpose) and `parentFormLabel` (`String` — the
Edit form's own current-page label: `getValue(*_OVERLAY_TITLE_EDIT)` for Advertisement/Taxon/City,
`params.getUser().name()` for User, matching `*Overlay.switchTo()`'s own current-text logic
exactly). `openFor()` renders every `parentStep` as a link whose click handler is `closeNested()`
then the step's own `onClick()` — so the "List" step's action (`this::closeToList`, inherited from
the base `buildBreadcrumbSteps()`) still closes all the way out in one click regardless of chain
length, and a "View" step's action (`this::handleCancel`) still reverts the underlying Edit
overlay to View — then appends one more link for `parentFormLabel` itself (action: `closeNested()`
alone, revealing the still-open Edit form beneath, unchanged from before). CSS classes:
`.entity-activity-breadcrumb-step` on every rendered parent-step link, `.entity-activity-
breadcrumb-parent` on the trailing form-label link (same class name as before, same semantics).

**View mode's own breadcrumb-current text changed from empty to `OVERLAY_BREADCRUMB_VIEW`
("View"/"Перегляд")** in all four domains, so `List→View` alone now reads `Ads(link) >
Details(current)` instead of just `Ads(link)` with no current segment — the `layout
.getBreadcrumbCurrent().setVisible(...)` toggle tied to that empty-string case was removed
entirely (no longer needed once every mode always has a non-empty label).

**Found the fifth `AbstractEntityOverlay` subclass only by running the full suite, not by
grep-before-editing:** `SettingsOverlay` (not just `SettingsFormModeHandler`) also extends
`AbstractEntityOverlay`, and its own `switchTo()` never called `layout.setBreadcrumbLinks
(buildBreadcrumbLinks())` — a line every other override now has. Under the old design that was
harmless (the base class populated the layout's breadcrumb directly at `buildContent()` time via
the now-removed `breadcrumbButton` field); under the new design, `launchSession()` creates the
layout with an *empty* initial link list precisely because every override is expected to populate
it in its own `switchTo()`. `SettingsOverlay` was the one caller that didn't, so its own top-level
overlay silently lost its "Home" breadcrumb link entirely — caught by a Playwright timeout on
`.overlay__breadcrumb-back` in an unrelated pagination test, not by any test targeting Settings'
breadcrumb directly. Fixed with the same one-line addition every other override already has.

**A second Playwright failure in the same full run turned out to be a pre-existing flake, not a
regression** — confirmed by a full `scripts/deploy.sh --reset` (clean DB/MinIO volumes) followed
by a full re-run, which passed 50/50 including the specific test that had failed
(`userEn — locale persists across logout and re-login`, a `.header-settings-button` visibility
timeout unrelated to any overlay/breadcrumb code touched in this change).

**Verified (final, after the `SettingsOverlay` fix and a clean-DB re-run):** unit-tests 77/77,
Playwright `e2e --full --ux` 50/50.

**Duplication pass:** the four domain overlays' `buildBreadcrumbSteps()` overrides were
byte-identical, so they collapsed into two one-line overrides each (`isEditMode()`/
`enteredFromView()`), with the shared "append a View step" logic moved into
`AbstractEntityOverlay.buildBreadcrumbSteps()`. A shared generic `OverlaySession`/`Mode` type
(removing the per-subclass overrides entirely) was considered and rejected, tracked in the
backlog with the full cost/risk comparison.

---

## ADR-068: `findById()` locale fix + `AdvertisementEnrichmentService` extraction (Batch A)

**Status:** Accepted — two later changes moved past what's described below: `AdvertisementPort
.findById()`'s `Locale` parameter was subsequently removed again (category/city enrichment moved
to read-time display composition in `marketplace-orchestrator`, which resolves locale itself); and
`AdvertisementEnrichmentService` was renamed/relocated to `marketplace-orchestrator`'s
`AdvertisementDisplayEnrichmentService` (see ADR-073). This entry's own reasoning for extracting
enrichment out of `AdvertisementService` still holds — only the exact signature/location changed.

**Context:** `/deep-review full` (2026-07-29) found
`AdvertisementService.findById()` hardcoded `Locale.ENGLISH` for category/city enrichment, while
`getFiltered()` (the list view's path) correctly used the caller's real locale. The only UI path
that actually surfaces this to a user is `AdvertisementsView.openPendingDeepLinkIfAny()` — opening
`/ads/:id` directly (e.g. a shared link) — since a normal card click passes the already
list-enriched DTO straight into the view overlay without re-fetching by id.

**Decision:** `AdvertisementPort.findById()` / `AdvertisementPortImpl` / `AdvertisementService
.findById()` all take a `Locale` parameter now. `OgMetaRequestListener` and
`AdvertisementSaveService` (2 call sites, audit-snapshot building) pass `Locale.ENGLISH` explicitly
— no user session exists at OG-meta-injection time, and snapshots only need title/description/
adKind, not category names — matching `SitemapController`'s existing convention.
`AdvertisementsView.openPendingDeepLinkIfAny()` (the actual bug site) and
`AdvertisementFormOverlayModeHandler` (2 call sites) pass `localeProvider.getCurrentLocale()`.

Alongside the fix, `AdvertisementService`'s `enrichWith*`/`apply*Data` methods (category/city,
actor, media — 9 methods) were extracted into a new `AdvertisementEnrichmentService` bean in the
same package, mirroring the precedent of `AttachmentSnapshotService` being split out of
`AttachmentService`. `AdvertisementService` keeps query/filter, CRUD, and HTML-sanitization
concerns; `AdvertisementEnrichmentService` owns both the bulk (`getFiltered()`) and single-item
(`findById()`) enrichment shapes, with each single-item method querying its port directly
(`TaxonPort.getForEntity()`, `UserPort.findById()`) rather than wrapping the DTO in a
`List.of(dto)` to reuse the bulk method and unwrapping via `.getFirst()` — except media, where
`AttachmentPort` has no single-entity `getMediaSummary()` (removed as dead API surface,
see `attachment-spring-boot-starter/CLAUDE.md`), so `enrichWithMedia()` calls the bulk `getMediaSummaries()` with
`Set.of(ad.getId())` instead.

Renamed in the same pass for clarity (no remaining references to the old names):
- `AdvertisementService.enrichWithTaxons()`/`resolveTaxonFilter()` →
  `enrichWithCategoriesAndCity()`/`resolveCategoryAndCityFilter()` — the old names said "Taxon"
  (the DB/port-level umbrella term covering both `CATEGORY` and `CITY` `TaxonType` values) when the
  methods actually always handle category and city together; the new names say what's enriched.
- marketplace-app's existing `AdvertisementEnrichService` (audit-diff category-name resolution for
  Timeline/Activity tabs) → `AdvertisementAuditEnrichService`, to avoid confusion with the new
  starter-side `AdvertisementEnrichmentService` — near-identical names in different packages
  otherwise.

New `.claude/rules.md` convention adopted in the same pass: **Service Class Section Headers** — a
one-line `// ── Section Name ──...` comment above the first method of each logically distinct
block in a service class with 2+ concerns (e.g. `AdvertisementService`'s Query & filter / CRUD /
HTML sanitization; `AdvertisementEnrichmentService`'s Category & city / Actor / Media).

**Consequence:** `AdvertisementInfoDto`'s locale-dependent fields (`categoryNames`, `cityName`) are
now correct for `findById()` callers, matching `getFiltered()`. Any future split of a growing
`*Service` (flagged for `AttachmentService`) now has this as a second
concrete precedent alongside `AttachmentSnapshotService`.

## ADR-070: F-04 Batch A — `locale`/`settings` split out of `user_information` into `user_preferences`

**Status:** Accepted

**Context:** F-04 (provider profiles) originally planned to merge `locale`/
`settings` together with the new provider-facing fields into one `actor_profile` table/module.
Discussed with the user before implementation: the provider-facing half behaves like a public
catalog entity (OG/sitemap, categories, portfolio, future reviews) — closer in shape to
`Advertisement` than to `User` — while `locale`/`settings` are private, auth-adjacent preferences
with no such need for independent module toggling. Decision: 3 tables, 2 modules, not 1-and-1.
This ADR covers only the first slice (Batch A) — preferences split out of `user_information`,
still inside `user-spring-boot-starter`. The provider-facing half (new `provider-profile-spring-
boot-starter` module) is tracked separately in the backlog.

**Decision:**
- New `user_preferences` table (same module, second entity/repository — mirrors the existing
  `UserProfileUpdate`-on-`user_information` precedent) holds `locale` and `settings` (JSONB),
  keyed by `actor_id` (`BIGINT UNIQUE`, **no FK** — matches this codebase's existing no-FK
  actor-reference-column convention, e.g. `advertisement.created_by`, ADR-034 — even though this
  table lives in the same module as `user_information`; deliberate, not an oversight).
- Schema written "from scratch," not migrated: the app is pre-production, so `01-user-schema.xml`
  (which already carries `<validCheckSum>ANY</validCheckSum>`, this repo's established pattern for
  editing changeset 01 in place pre-launch) was edited directly — `locale`/`settings` columns
  removed from `user_information`'s `createTable`, a new changeSet added in the same file for
  `user_preferences`. No data-migration changeset, no backfill for pre-existing rows.
- `UserPreferencesRepository` (replaces `UserSettingsRepository`) owns both concerns since they
  share one table row: `insertDefault()` (called from `UserService.register()`, same transaction
  as the `user_information` insert — every actor gets a preferences row eagerly, same as before),
  `findLocaleByActorId`/`findLocalesByActorIds` (bulk, mirrors the existing bulk-lookup pattern
  used elsewhere, e.g. `AttachmentPort.getMediaSummaries`), `updateLocale`, `deleteByActorId`
  (called from `UserService.cleanup()`'s retention purge — no FK/cascade, so this call is the only
  thing preventing an orphaned row on hard-delete), `loadSettings`/`saveSettings` (unchanged
  JSONB-embedded-version optimistic lock, ADR-044 — this batch relocates the table, it does not
  change locking semantics).
- `User.toDto()` (ADR-069) changed from a zero-arg method reading its own `locale` field to
  `toDto(String locale)` — the entity no longer carries `locale` at all; `UserService` composes the
  full `UserDto` by calling `UserPreferencesRepository` after the `User` read (bulk lookup for
  list-returning methods, single lookup for `findById`/`findDtoByEmail`, funneled through one
  private `toDto(User)`/`enrichWithLocale(List<User>)` pair — not hand-mixed per call site).
  `UserPrincipal` gained a second constructor component (`locale`) for the same reason; both
  construction sites (`UserAutoConfiguration`'s `UserDetailsService` bean, `UserService
  .refreshSecurityContext()`) now go through one `UserService.toPrincipal(User)` method rather than
  each independently resolving the locale.
- `UserPort`'s external contract (`updateLocale`, `loadSettings`/`saveSettings`, `UserDto.locale()`)
  is unchanged — marketplace-app (`VaadinLocaleProvider`, `LocaleSelectorComponent`) needed zero
  changes.

**Bugs found and fixed during `/code-review`'s 8-angle pass, before this landed:**
- `UserPreferencesRepository.updateLocale()` did a blind UPDATE with no rows-affected check —
  silently no-oped (no error, no persisted change) for any actor without a `user_preferences` row.
  Now throws `IllegalStateException` on 0 rows affected.
- `UserService.cleanup()`'s hard-delete purge removed the `user_information` row but left the
  `user_preferences` row permanently orphaned (no FK/cascade, since this table is deliberately
  FK-less) — a genuine storage leak on every scheduled retention run. Now calls
  `preferencesRepository.deleteByActorId()` first.
- `findLocalesByActorIds`'s row mapper used `Map.entry(k, v)`, which throws `NullPointerException`
  on a null value (Java stdlib behavior) — `locale` is null until a user sets one, so this NPE'd on
  every freshly-registered actor. Fixed by accumulating into a plain `HashMap` instead (a second,
  related NPE from `Collectors.toMap()` — which *also* rejects null values internally — surfaced
  once the first was fixed, from the same root cause).
- `UserTestFixtures.createTestUser()` (the shared FK-fixture used by every other domain's
  `*RepositoryTest`) never created a matching `user_preferences` row, unlike real registration —
  latent for now (nothing currently reads locale/settings off a fixture-created actor) but a
  landmine for future tests. Added `UserTestFixtures.createTestUserWithPreferences()` as an
  additive overload; existing callers untouched.
- Test coverage for the Liquibase column default's exact JSON shape (no `schemaVersion` key) was
  dropped when `UserSettingsRepositoryTest` was renamed to `UserPreferencesRepositoryTest` — every
  new test created its row via `insertDefault()` (full Jackson serialization, always includes
  `schemaVersion`), never exercising the raw-SQL-default shape. Restored as
  `loadSettings_legacyRowWithNoSchemaVersionKey_stillLoadsCorrectly`.

**Consequence:** `user_information` now holds only auth data (email, password hash, role, name).
Provider-profile work (Batch B onward) attaches to its own table/module, never to this one.
The `findLocaleByActorId`/`findLocalesByActorIds` duplication the code review flagged was resolved
by having the single-id method delegate to the bulk one (`findLocalesByActorIds(Set.of(id))`) —
one query implementation, not two to keep in sync.

## ADR-071: `UserDto.locale` removed, `UserProfileUpdate` renamed, `UserPort` split (Batch A2)

**Status:** Accepted

**Also affects:** user-spring-boot-starter

**Context:** Reviewing Batch A's diff, the user flagged that `UserDto` still carried `locale`
after the `user_information`/`user_preferences` table split — forcing every `UserDto` construction
(`getFiltered`/`getFilteredByOffset`/`findByIds`/`findDtoByEmail`, used by the Users grid,
`AdvertisementEnrichmentService`'s actor-name enrichment, `UserActorNameService`'s audit-actor
resolution) to pay for a `user_preferences` lookup that only one real consumer
(`VaadinLocaleProvider`, resolving the current session's UI locale) ever reads. The same session
also flagged `UserProfileUpdate`'s name as misleading (reads as "the profile," about to collide
with F-04's incoming Provider Profile concept) and `UserPort`'s 19-method surface as too wide —
grep against every real consumer confirmed most inject only one of 4 clusters (query / account
mutation / authorization / preferences). See `platform-commons/DECISIONS.md` ADR-026 for the
starter-module-level rationale for the port split itself.

**Decision:**
- `UserDto` (platform-commons) loses `locale`. The current session's locale is read from
  `AuthenticatedPrincipal.locale()` (new interface method; `UserPrincipal`, already a record with
  a `locale` component since Batch A, satisfies it for free) via a new
  `AuthContextService.getCurrentUserLocale()`, which reads the already-resolved,
  session-cached `SecurityContextHolder` principal — no extra DB call versus before.
  `VaadinLocaleProvider` repointed accordingly. `User.toDto()` reverts to a plain no-arg method;
  `UserService`'s `enrichWithLocale()`/private `toDto(User)` composition helpers (added in Batch A)
  are deleted entirely — `getFiltered`/`getFilteredByOffset`/`findById`/`findDtoByEmail`/`findByIds`
  all go back to a plain `User::toDto` method reference, touching `user_preferences` not at all.
  `UserPreferencesRepository.findLocalesByActorIds()` (the bulk lookup Batch A added) is deleted as
  dead code — the only remaining locale read is `UserService.toPrincipal()`'s single-actor lookup
  at login/`refreshSecurityContext()` time, confirmed via grep to be the sole call site left.
- `UserProfileUpdate` → `UserEditableFields` (`user-spring-boot-starter` only — internal rename,
  `UserProfileDto`, the platform-commons DTO, is unchanged). `UserProfileCrudRepository` →
  `UserEditableFieldsCrudRepository`.
- `UserPort` split into 4 platform-commons interfaces — see ADR-026 for the full rationale and the
  per-port method list. `UserSettingsService` renamed to `UserPreferencesService`, gained
  `updateLocale()`/`findLocale()` (both thin delegations to `UserPreferencesRepository`, matching
  its existing `load()`/`save()` shape) — `UserPreferencesPortImpl` calls this service directly,
  never through `UserService`, closing the "middleman doing nothing" gap `UserService.updateLocale()`
  had before this batch (it forwarded to `UserPreferencesRepository` with zero added logic).
  Every real consumer (`AccessEvaluator`, `UserDeleteService`, `UserPickerField`, `UserView`,
  `SettingsFormModeHandler`, `SettingsPaginationService`, `LocaleSelectorComponent`, `SignUpDialog`,
  `UserFormOverlayModeHandler`, `AdvertisementEnrichmentService`, `UserActorNameService`,
  `AuditDomainHookImpl`, plus their test files) repointed to inject only the port(s) it actually
  calls, verified against the live codebase via grep before editing, not assumed.

**Found and fixed during `/code-review`'s 8-angle pass:** `AuthenticatedPrincipal` gained a second
abstract method (`locale()`), which silently breaks any lambda-based implementation (no longer a
functional interface) — the one such site (`AuthContextServiceTest`) was converted to an anonymous
class. Two of the three new port-impl classes (`UserAccountPortImpl`, `UserPreferencesPortImpl`)
initially omitted the class-level `@Transactional(readOnly = true)` the pre-split `UserPortImpl`
and every other established `*PortImpl` in this codebase carries — added for consistency. A stale
`{@link UserPort}` Javadoc reference survived in `AccessEvaluatorTest` after its field was renamed
to `UserAuthorizationPort` — corrected.

**Consequence:** `UserDto` is now a pure identity/auth view — no field on it exists for the benefit
of exactly one consumer. Any future addition to `UserDto` should clear the same bar: is this read
by more than the one place asking for it, or does it belong behind a narrower lookup instead.

## ADR-073: `AdvertisementSaveService`/`UserDeleteService` move to `marketplace-orchestrator`; `AdvertisementAuditEnrichService` stays
**Status:** Accepted — the "`AdvertisementAuditEnrichService` stays in `marketplace-app`" call below
was itself reversed shortly after, see `marketplace-orchestrator/DECISIONS.md` ADR-005: its two
real UI-shell touchpoints (current locale, an `AdKind` label) turned out to be single-value lookups
rather than the HTML-diff formatting itself, so both moved behind the `CurrentLocaleHook`/
`UiLabelHook` forwarder-SPI pair and `AdvertisementAuditEnrichService` now lives in
`marketplace-orchestrator` too, not `marketplace-app`.

**Context:** `AdvertisementSaveService` and `UserDeleteService` lived in `marketplace-app` but
performed cross-domain application-level orchestration (multi-Port composition for one atomic use
case) — exactly the concern a new `marketplace-orchestrator` module was extracted to own. See
`marketplace-orchestrator/DECISIONS.md` ADR-001 for the full extraction rationale.

**Decision:** Both classes moved to `org.ost.orchestrator.services` (the module's flat services
package — not a per-domain sub-package), unchanged in behavior. `AdvertisementAuditEnrichService`
(audit-diff display-string resolution for the Timeline/Activity tabs) was evaluated for the same
move and rejected at the time: it field-injected `LocaleProvider`/`I18nService`
(`marketplace-app/services/i18n`), an application-shell/UI-formatting concern this repo's
Architecture Guidelines explicitly keep in `marketplace-app`, not the orchestrator. It stayed here,
simplified to depend on `marketplace-orchestrator`'s `TaxonLookupService` collaborator instead of
its own `ComponentFactory<TaxonPort>` field — see the Status line above for how this was later
reversed.

**Consequences:** `AdvertisementFormOverlayModeHandler`/`AdvertisementCardView`/`AdvertisementsView`
now inject `AdvertisementSaveService`/`AdvertisementDisplayEnrichmentService` from
`org.ost.orchestrator.*` instead of composing `AdvertisementPort`/`TaxonPort`/`UserPort`/
`AttachmentPort` directly for display data — `AdvertisementPort.getFiltered()`/`findById()` now
return raw (unenriched) data; the calling UI code enriches explicitly via the orchestrator service.
`UserView` injects `UserDeleteService` from the same new package. `UserService.cleanup()`
(`user-spring-boot-starter`) gained a `ProviderProfilePort.findOwnerIds()` purge-guard check
alongside its existing `AdvertisementPort` one — found while designing `UserDeleteService`'s target
shape, not previously wired despite `provider-profile-spring-boot-starter/CLAUDE.md` documenting
the intent.

---

## ADR-074: `SettingsOverlay`/`UserOverlay` unified into `AccountOverlay`; Provider Profile gains a View/Edit split

**Status:** Accepted

**Context:** ADR-067 anticipated this batch as "the future Account overlay" while designing the
stacked-history-overlay mechanism it now reuses. Separately, once Name/Settings/Provider Profile
sections were planned to live side by side in one overlay, an inconsistency became visible: only
Settings had its own Activity/history entry point, and only the Name section had a View/Edit mode
split — Settings and the then-new Provider Profile section opened directly into an editable form
with no read-only view first.

**Decision:** `SettingsOverlay` and `UserOverlay` are replaced by one `AccountOverlay`, opened from
both the header (self-service) and the Users grid (admin/moderator editing another user), with
three tabs: Name, Settings, Provider Profile. Provider Profile gains the same View/Edit split
Advertisement/Taxon/City already have (`ProviderProfileViewModeHandler`/
`ProviderProfileFormOverlayModeHandler`), so all three tabs — and every other domain overlay in the
app — now follow the same View-first, Edit-on-request shape. History/restore for each tab reuses
ADR-067's shared `EntityActivityOverlay` stacked-overlay mechanism, opened via a
`.{domain}-history-button` icon button rather than a tab.

Both `AccountNameFormModeHandler`/`SettingsFormModeHandler` gained a defensive
`AccessEvaluator.canEditUserAccount()` re-check inside `activate()` — the Users grid's Edit action
reaches a form handler directly, bypassing the View mode's own Edit-button gate (which only blocks
navigating *into* Edit, not the form's own field/button state once already there), and Settings has
no View mode to gate at all.

**Consequences:** the old `UserFormOverlayModeHandler`/`UserViewOverlayModeHandler` classes are
gone, replaced by `AccountNameFormModeHandler`/`AccountNameViewModeHandler`. Landed in the same
session as ADR-030 (`platform-commons/DECISIONS.md`), which moved provider-profile write ownership
to `marketplace-orchestrator` and fixed the `targetUserId`/`actingUserId` conflation this batch's
admin-edits-another-user path surfaced during manual testing. Playwright gained
`04-provider-profile-flow.spec.js` (create/edit provider profile, moderator read-only view, admin
editing another user's profile end-to-end); spec files 04-07 renumbered to 05-08 to make room.

---

## ADR-075: Providers public catalog — OG/sitemap/deep-link pattern applied to a second domain, view-only catalog overlay

**Status:** Accepted

**Also affects:** provider-profile-spring-boot-starter, marketplace-orchestrator

**Context:** `provider-profile-spring-boot-starter`/`ProviderProfilePort` (ADR-027) and the
self-service `AccountOverlay` Provider Profile tab (ADR-074) already let an actor create/edit their
own provider profile, but nothing surfaced a profile outside its owner's own account — no public
listing, no shareable link with a rich preview, no search-engine visibility. ADR-059 already
established the public-catalog/OG-meta/sitemap/deep-link pattern for the Advertisement domain; this
batch is that same pattern applied to Provider Profile, not a new design.

**Decision:**
- New `ui/views/main/tabs/providers/` package mirrors the Advertisement domain's own public-facing
  structure field-for-field: `ProvidersView` (catalog listing, mirrors `AdvertisementsView` minus
  the add-button/keyboard-shortcut/refresh-polling machinery, since creation only ever happens via
  `AccountOverlay`), `ProviderProfileCardView` (catalog card, text-only — kind badge, `about`
  excerpt, city/category info lines — no photo, since `ProviderProfileDto` carries no media field
  and portfolio support is out of scope), `query/ProviderProfileFilterMeta`/`SortMeta`/
  `QueryBlock`/`QueryConfig` (mirrors the Advertisement query-layer trio, using the already-existing
  `ProviderProfileFilterDto`'s `kinds`/`categoryIds`/`cityTaxonId` fields — no new filter fields
  added, no title/date-range rows since that DTO has none of those).
- `ProviderProfileCatalogOverlay` extends `BaseOverlay` directly, **not**
  `AbstractEntityOverlay<H>` — a deliberate deviation from `AdvertisementOverlay`'s shape. This
  catalog overlay is view-only and never enters an edit mode: editing a provider profile already
  has its own dedicated, self-service path (`AccountOverlay`'s Provider Profile tab). Forcing it
  through `AbstractEntityOverlay`'s form-handler-typed generic bound would mean carrying unused
  `saveConfig()`/`proceed()`/`afterDiscard()` overrides and a phantom form-handler type for a mode
  that structurally cannot exist here. Consequently `ProviderProfileCatalogViewModeHandler` has no
  Edit button and no history button — history/restore has only ever been a form/edit-mode concern
  in this codebase (see `.claude/rules.md`'s Form Handler Pattern), and a view-only overlay
  correctly has nothing to attach it to.
- New `ProviderProfileReadService` (`marketplace-orchestrator`) mirrors `AdvertisementReadService`
  — wraps `ComponentFactory<ProviderProfilePort>`'s `getFiltered`/`count`/`findById`/`isAvailable`,
  since the existing `ProviderProfileSaveService` only exposed the write/single-lookup methods a
  self-service form needs, not the paginated listing a public catalog needs.
- `OgMetaRequestListener` gains a `PROVIDER_PATH = Pattern.compile("^/providers/(\\d+)$")`
  alongside `AD_PATH`, injects `ProviderProfileReadService`, and reuses the existing
  `og:title`/`og:description`/`og:url`/JSON-LD shape. `og:type` is `"profile"` (not `"product"`,
  which stays Advertisement-only) and the JSON-LD `@type` is `"ProfilePage"` — a provider profile
  represents a person/business entity page, not a purchasable listing; `"profile"` is the closest
  standard Open Graph type for that shape. Injection is skipped entirely when no profile is found
  for the id (same early-return shape as the advertisement path).
- The sitemap builder gains a parallel `allProviderProfiles()` page-walk (same
  `Stream.iterate`+`takeWhile`+`flatMap` shape as `allAdvertisements()`) appended to the same
  `/sitemap.xml` response, and `AppLinkService` gains `providerProfileUrl(Long id)` mirroring
  `advertisementUrl()` (see ADR-076 for where the sitemap builder itself ended up living).
- `ProviderProfileDeepLinkView` (`@Route("providers")`) mirrors `AdvertisementDeepLinkView`'s
  session-attribute pending-deep-link pattern, but takes `HasUrlParameter<String>` rather than
  `<Long>` — the optional slug in `/providers/42-ivan-plytochnyk` needs the leading numeric id
  parsed out of the path segment manually (`AdvertisementDeepLinkView` has no slug support at all
  today, so there was no existing `Long`-based precedent to copy for this part).
- Delete button added in two places, both calling the existing `ProviderProfileSaveService.delete()`
  (already wired end-to-end, only missing a UI trigger): `ProviderProfileCatalogViewModeHandler`
  (public catalog) and `ProviderProfileViewModeHandler` (`AccountOverlay`'s own tab) — both visible
  only under `AccessEvaluator.canEditUserAccount()`. The shared confirm-dialog-plus-delete-call
  logic is factored into one `ProviderProfileDeleteUtil.confirmAndDelete()` static helper
  (`ui/views/utils`-style, reused by both call sites) rather than duplicated, found and fixed during
  this batch's own `/review` pass.
- `MainView`'s new Providers tab is gated on `providerProfileReadService.isAvailable()`, matching
  the existing Reference Data tab's `taxonCatalogService.isAvailable()` gate for the same kind of
  optional-starter (`provider-profile-spring-boot-starter` is `<scope>runtime</scope>`) —
  found and fixed during this batch's own `/review` pass; the tab would otherwise render
  permanently empty instead of disappearing if the starter is ever excluded from the classpath.

**Consequences:** Playwright's `04-provider-profile-flow.spec.js` gained public-catalog coverage
(anonymous browsing/filtering by kind/category/city, deep-link navigation + sitemap.xml + crawler
meta tags, delete-from-catalog, `SUPPORT`-kind disabled-not-removed for a non-privileged actor
already holding that kind) alongside its existing `AccountOverlay` tab coverage — no new spec file,
per this repo's "extend before adding a new one" Playwright convention. `improvement-124`'s last
open batch (public Providers catalog) is now closed. The `SitemapController`/browser-History
mechanics this ADR describes were both revised shortly after by ADR-076 — see that entry for the
current shape.

---

## ADR-076: `OverlayNavigationRegistry` fans out browser History to every deep-linkable overlay; `SitemapController` thins to a `marketplace-orchestrator` `SitemapService`

**Status:** Accepted

**Context:** Verifying `improvement-179`'s Providers catalog against real running code (not just
the plan) surfaced two real bugs, both directly caused by the pattern ADR-059/ADR-075 established
for Advertisement being copied as-is for a second domain:
1. ADR-059's own "Known limitation, deliberately not addressed" already predicted this: Vaadin's
   `History.setHistoryStateChangeHandler(...)` is a single-handler slot, not an `addListener`-style
   multi-registration. `AdvertisementOverlay` and the new `ProviderProfileCatalogOverlay` were each
   calling it directly in their own `@PostConstruct`, so whichever overlay's bean initialized last
   silently owned the only working browser-back/forward handler — the other domain's deep link
   would open the overlay via `pushState` but back/forward navigation would go straight past it.
2. `SitemapController`'s single-entry, 15-minute Caffeine cache lived in `marketplace-app` and had
   no invalidation hook. A newly-added Playwright test for the provider-profile deep link
   (`04-provider-profile-flow.spec.js`, running before the advertisement spec) pre-warmed that
   cache with stale data, which the pre-existing advertisement sitemap test then observed as a
   silent regression — passing or failing depending on run order, not on the code under test.

**Decision:**
- New `ui/views/services/OverlayNavigationRegistry` (`@SpringComponent @UIScope`) registers itself
  as the *only* caller of `setHistoryStateChangeHandler` (once, in its own `@PostConstruct`) and
  fans every incoming `HistoryStateChangeEvent` out to a `List<History.HistoryStateChangeHandler>`
  built via `register(...)`. `AdvertisementOverlay` and `ProviderProfileCatalogOverlay` both now
  call `navigationRegistry.register(...)` instead of touching `UI.getCurrent().getPage().getHistory()`
  directly — this is the shared per-UI history dispatcher ADR-059 said the next domain would need,
  now built rather than deferred again for a third domain.
- `SitemapController` (`marketplace-app`, `rest/`) is now a thin adapter over a new
  `marketplace-orchestrator` service, `SitemapService` — the cache, the `buildSitemap()` page-walk
  over both `AdvertisementReadService`/`ProviderProfileReadService`, and a new `invalidate()` method
  all moved there. `AdvertisementSaveService`/`ProviderProfileSaveService` (already living in the
  same module) call `sitemapService.invalidate()` after every save/delete — a same-module direct
  call, no cross-module event needed, since both save services and the sitemap builder were already
  colocated in `marketplace-orchestrator`. This both fixes the stale-cache/test-order bug and moves
  the sitemap-building logic to the module that already owns cross-domain read composition, rather
  than leaving it as a `marketplace-app` REST controller doing orchestration work directly.

**Consequences:** No behavior change for a real user — the sitemap still updates within at most one
save's `invalidate()` call, just deterministically now instead of only after the 15-minute TTL
expired. Any future domain adding its own deep-linkable overlay registers into
`OverlayNavigationRegistry` instead of calling `setHistoryStateChangeHandler` directly; any future
domain needing sitemap entries adds its own page-walk method to `SitemapService` and an
`invalidate()` call from its own save service, following this same shape.

---

## ADR-077: Provider Profile catalog gains real date-range filters, mirroring Advertisement's exact mechanism

**Status:** Accepted

**Context:** `ProviderProfileQueryBlock` attached its only sort control (`updatedAt`) to the Kind
filter row's own `filterRow(...)` call — a field the Kind row has no relationship to.
`ProviderProfileSortMeta.CREATED_AT` was defined but completely unreferenced. The real root cause
was not UI placement: `ProviderProfileFilterDto` had no date-range filter fields at all, unlike
`AdvertisementFilterDto`, which offers `createdAtStart/End`/`updatedAtStart/End` range filters
paired with sort. The sort icon was bolted onto an unrelated row as a workaround for a missing
filter capability, rather than the gap being closed. Separately, `provider-profile-card.css` had
the card's Share button (`.provider-profile-share`) sharing the same `opacity: 0` hover/focus-reveal
rule as Delete, diverging from Advertisement's own Share button, which is always visible — an
unintentional divergence from mirroring the Edit/Delete hover-reveal pattern, not a deliberate
choice.

**Decision:** `ProviderProfileFilterDto` gains `createdAtStart`/`createdAtEnd`/`updatedAtStart`/
`updatedAtEnd` fields plus the same `@ValidRange` annotations `AdvertisementFilterDto` carries,
field-for-field. `ProviderProfileRepository` gains 4 matching `SqlBoundFilter` date-range entries;
`ProviderProfileFilterMeta` gains 4 matching `FilterFieldMeta` constants. `ProviderProfileQueryBlock`
reverts the Kind row to the plain 3-arg `filterRow(...)` and adds Created/Updated rows using the
*existing* 4-arg `filterRow(...)` overload — the same one `AdvertisementQueryBlock` already uses —
with no new shared-infrastructure method. `provider-profile-card.css` drops `.provider-profile-share`
from the opacity/hover/focus-reveal selectors, leaving only `.provider-profile-delete` gated.

**Rejected alternatives:** A novel `QueryBlock<T>.sortOnlyRow()` method for a row with no filter
field was considered and rejected — it would have fixed the Kind-row attachment but left Providers
and Advertisement with two different sort/filter mechanisms, trading one asymmetry for another. The
chosen approach makes the two domains genuinely symmetric instead: same filter capability, same UI
mechanism, reusing existing infrastructure rather than inventing a new one-off shape for a single
domain.

**Consequences:** Providers become filterable/sortable by when they joined or were last active, the
same capability Advertisement already offered. Any future domain needing date-range filter/sort
follows this exact field-for-field pattern rather than a per-domain variant.

