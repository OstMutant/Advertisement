# Architecture & Technical Decisions — marketplace-app

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

## ADR-004: Dependency versions
**Status:** Accepted

**Context:** Spring Boot 4.0.6 is the latest stable patch; Vaadin 25.1.5 aligns with the
Spring Boot 4.x BOM; AWS SDK bumped from 2.25.60 for security patches and API improvements.

**Decision:** Spring Boot 4.0.6, Vaadin 25.1.5, AWS S3 SDK 2.44.4.

**Consequences:** Rejected: Jackson 3 migration (`tools.jackson:jackson-databind:3.1.2`) —
Maven artifacts for the `tools.jackson` groupId are unverified. Revisit when official release
is confirmed.

---

## ADR-005: Modular storage — contract in attachment-starter, not marketplace
**Status:** Accepted

**Context:** Storage only exists to serve attachments — no use case for storage without attachments.

**Decision:** `StorageService` interface and implementations live in `attachment-spring-boot-starter`
(`org.ost.attachment.storage`). UI components use `ObjectProvider.ifAvailable()` to degrade
gracefully when the attachment starter is absent.

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
SPIs (`AuditDomainHook`, `AuditActivityFieldsHook`, `AuditActivityEnrichHook`) implemented in
`marketplace-app`.

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
**Status:** Accepted

**Context:** Rapid double-click submits the form twice, causing duplicate saves.

**Decision:** Save buttons in `AdvertisementFormOverlayModeHandler`, `UserFormOverlayModeHandler`,
and `SettingsOverlay` are guarded with `setEnabled(false)` at the start of the click listener
and re-enabled in a `finally` block.

**Consequences:** The guard lives in `activate()` of the FormModeHandler (not in the Overlay)
because the save button is a Spring-injected field of the handler.

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
entry's snapshot (`getSnapshotContent`). `getPreviousSnapshotContent` is reserved for diff
display only.

**Consequences:** All restore flows (`UserOverlay.handleRestoreUser`,
`SettingsOverlay.loadAndShowSettingsRestore`, `AdvertisementService.restoreToSnapshot`) must
call `AuditPort.getSnapshotContent(snapshotId, entityType)`.

---

## ADR-011: AuditSnapshotBinder used directly — no AuditUiPort
**Status:** Accepted

**Context:** `AuditUiPort` was removed 2026-06-15 as unnecessary indirection — all Vaadin UI
lives in marketplace-app, so there is no second consumer that would require the SPI.

**Decision:** `AuditSnapshotBinder` used directly in marketplace-app UI components.

**Consequences:** Do not re-introduce `AuditUiPort`.

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

---

## ADR-013: AbstractViewOverlayModeHandler — template method for tabbed view overlays
**Status:** Accepted

**Context:** `AdvertisementViewOverlayModeHandler` and `UserViewOverlayModeHandler` had identical
tab-switching and lazy-loading boilerplate (~15 lines each).

**Decision:** All "view mode" overlay handlers extend `AbstractViewOverlayModeHandler`. The base
class provides `final activate(OverlayLayout)` that assembles the tab layout; subclasses implement
five abstract methods: `tabsCssClass()`, `buildPrimaryTab()`, `buildPrimaryContent()`,
`buildSecondaryTab()`, `buildHeaderActions()`.

`SecondaryTabDef` record `(Tab tab, String cssClass, Supplier<Component> loader)` represents the
optional second tab. Returning `null` from `buildSecondaryTab()` produces a single-tab layout.

**Consequences:** Rejected: `TabbedOverlayContent` as a Spring `@Prototype` `Configurable` bean
— passing live UI components as `Parameters` violates the convention that Parameters carry
data/config, not pre-built component trees.

---

## ADR-014: Audit writes belong in the service layer, not the UI
**Status:** Accepted

**Context:** `SettingsOverlay.handleSave()` was calling `auditPortFactory.captureUpdate(...)`
directly — the only place in the entire UI layer that fired an audit write. All other entities
correctly placed audit calls inside the service.

**Decision:** `AuditPort.captureUpdate` / `captureCreation` / `captureDeletion` must only be
called from `*Service` classes — never from UI overlays, view components, or `*HookImpl` classes.
`UserSettingsService.save()` now loads old settings, saves, publishes the domain event, and
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

## ADR-017: Known decoupling debt — open boundary violations

**Architecture rule (2026-06-15):** marketplace-app UI is a monolith — decoupling is required
only at the service ↔ UI boundary (starters vs marketplace-app). Within marketplace-app, UI
components may reference each other freely. UI ports/hooks (`AuditUiPort`, `AttachmentGalleryPort`,
`AuditActivityRowHook`) were removed as unnecessary indirection.

### Open violation

→ [improvement-001-attachment-ui-boundary-violation](../features/issues/improvement-001-attachment-ui-boundary-violation.md)

### ✅ Resolved — marketplace-app → org.ost.user.* internals (2026-06-15)

All 22 files now use `UserDto`/`UserPort` from platform-commons exclusively. See ADR-016.

### ✅ Resolved — org.ost.marketplace.security.* uses User entity (2026-06-15)

`OwnershipChecker`, `RoleChecker`, `AccessEvaluator` updated. `AuthContextService.getCurrentUser()`
returns `Optional<UserDto>`. `AuthenticatedPrincipal` SPI is the boundary.

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

## [OPEN GOAL] Activity field visibility — filter by viewer's role

→ [goal-001-activity-field-visibility-by-role](../features/issues/goal-001-activity-field-visibility-by-role.md)
