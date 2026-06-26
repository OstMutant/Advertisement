# Architecture & Technical Decisions — marketplace-app

---

## 2026-06-13 — UI layer restructuring: all Vaadin UI consolidated in marketplace-app

**Final state:** All Vaadin UI lives in `marketplace-app` (`org.ost.marketplace.ui.*`). No starter contains UI code. `marketplace-app` imports starters only via platform-commons contracts (`UserPort`, `AdvertisementPort`, `AuditPort`, `AttachmentPort`, `UserDto`, etc.). `*HookImpl` and `*PortImpl` orchestrators live in `marketplace-app`.

**Why:** Two phases — (1) UI extracted from individual starters into a short-lived `marketplace-ui` module; (2) `marketplace-ui` merged back into `marketplace-app` (no second consumer existed, Maven module boundary added cost with no benefit) and domain logic moved to dedicated starters (`user-spring-boot-starter`, `advertisement-spring-boot-starter`).

**CSS rule:** All CSS lives in `marketplace-app/src/main/frontend/themes/my-app/` — Vaadin 25 Vite build does not include CSS from `@CssImport` in JAR starters.

---

## 2026-06-15 — Full user decoupling: marketplace-app no longer imports org.ost.user.* internals

**What changed:**
- Added `AuthenticatedPrincipal` SPI interface to `platform-commons` (`org.ost.platform.user.spi`) — the single contract that `marketplace-app` uses to extract `UserDto` from the Spring Security principal.
- `UserPrincipal` (user-starter) now implements `AuthenticatedPrincipal.toUserDto()` — extracts `UserDto` without exposing the `User` entity.
- `AuthContextService` rewritten: no `UserPrincipal`/`User` imports; reads `AuthenticatedPrincipal` via pattern matching; returns `Optional<UserDto>`.
- `UserPort` gains two new methods: `save(UserProfileDto, Long actingUserId)` (corrects the actor-id bug) and `refreshCurrentUserInContext(Long userId)` (reloads `User` entity + updates Spring Security principal — stays inside user-starter).
- All 22 files in marketplace-app that imported `org.ost.user.entity.User`, `org.ost.user.services.UserService`, `org.ost.user.services.UserSettingsService`, or `org.ost.user.security.UserPrincipal` now use `UserDto`/`UserPort` from platform-commons exclusively.
- `UserSortMeta` and `UserQueryConfig`: `User.Fields.*` replaced with string literals (field names are stable).
- `UserMapper` now maps `UserDto → UserEditDto` instead of `User → UserEditDto`.

**Why:** Enforces the module import rule — marketplace may only import starters via platform-commons contracts. `User` entity and service internals are not published contracts.

**Key design — `AuthenticatedPrincipal` in platform-commons:** Two modules need this interface (marketplace reads it, user-starter implements it) so it belongs in platform-commons as a SPI, not in either module.

**Key design — `refreshCurrentUserInContext` in `UserPort`:** Updating the Spring Security principal requires creating a `UserPrincipal(User)` — which needs the password hash (not in `UserDto`). Moving this responsibility into `UserPortImpl` (user-starter) avoids exposing either `User` entity or password hash across the module boundary.

---

## 2026-05-21 — Inline SQL repository style (supersedes Descriptor pattern)

**Decision:** All repositories inline SQL directly in the method that executes it. No `TABLE`, `ALIAS`, `SOURCE`, or single-use SQL string constants. The Descriptor layer (`SqlEntityDescriptor`, `SqlCommand`, `SqlDescriptorField`, `SqlEntityProjection`, `SqlFixedQuery`, `RepositoryCustom`) has been fully removed from the codebase.

Rules:
- SQL inline per method — text block `"""..."""` for multi-line, single-line string otherwise.
- `@SuppressWarnings("java:S1192")` on every repository class — duplicate literals are intentional.
- `MapSqlParameterSource` constructed inline per method — no shared param-factory helpers. Locality beats brevity.
- Dynamic SQL assembled with `.formatted()`, never `+` concatenation.
- `SqlFilterBuilder.build(params, filter, prefix)` — prefix (` WHERE ` or ` AND `) is part of the call.
- `OrderByBuilder.build(sort, map)` returns `" ORDER BY ..."` with leading space — no ternary at call sites.
- `@RequiredArgsConstructor` + `@Slf4j` everywhere; `@Qualifier` on fields propagates via `lombok.config`.

**Why:** SQL is trivially readable without jumping to a constants class. Each method is self-contained — a reviewer sees the full query in one place. Duplication of short WHERE fragments is acceptable; hidden indirection is not.

**How to apply:** When adding a new repository method, write the full SQL inline. Never extract a SQL fragment to a constant unless it is shared across 3+ methods and genuinely non-trivial.

---

## 2026-05-12 — Dependency versions locked

**Decision:** Spring Boot 4.0.6, Vaadin 25.1.5, AWS S3 SDK 2.44.4.

**Why:** Spring Boot 4.0.6 is the latest stable patch; Vaadin 25.1.5 aligns with the Spring Boot 4.x BOM; AWS SDK bumped from 2.25.60 to pick up security patches and API improvements.

**Rejected:** Jackson 3 migration (`tools.jackson:jackson-databind:3.1.2`) — Maven artifacts for the `tools.jackson` groupId are unverified. Revisit when official release is confirmed.

---

## Ongoing — No JPA / no Hibernate

**Decision:** All database access via `JdbcClient` with SQL inlined directly in repository methods. No JPA, no Hibernate, no Spring Data JPA. `CrudRepository<T, Long>` is used only for trivial `save`/`findById`/`deleteById` — never for custom queries. See the 2026-05-21 inline SQL decision for the full rule set.

**Why:** Explicit SQL gives full control over queries, avoids N+1, and eliminates hidden lazy-loading bugs.

**Rejected:** Spring Data JPA — too much hidden magic, incompatible with the "explicit over implicit" architecture principle.

---

## 2026-05-24 — Restore semantics: restore TO snapshot, not BEFORE snapshot

**Decision:** Clicking a restore button in any activity feed restores the entity to the state captured in the clicked entry's snapshot (i.e., `getSnapshotContent`). The previous behavior (`getPreviousSnapshotContent`) was wrong — it restored to the state *before* that change, not *to* that snapshot.

**Rule:** All restore flows (`UserOverlay.handleRestoreUser`, `SettingsOverlay.loadAndShowSettingsRestore`, `AdvertisementService.restoreToSnapshot`) must call `AuditPort.getSnapshotContent(snapshotId, entityType)`. `getPreviousSnapshotContent` is reserved for diff display only.

**Why:** The UX expectation is "click this history entry → entity becomes what it looked like at that point". Using the previous snapshot inverts the semantics and produces the wrong result.

---

## 2026-05-12 — @EnableMethodSecurity added; @PreAuthorize not used on Vaadin services

**Decision:** `@EnableMethodSecurity` added to `SecurityConfig`. `@PreAuthorize("isAuthenticated()")` is NOT applied at class level on services.

**Why:** Vaadin view beans are initialized on first HTTP request — before the user authenticates. Class-level `@PreAuthorize` on services breaks this initialization with `AuthorizationDeniedException`. The `/health` REST endpoint is intentionally public (load balancer / monitoring). Future non-public REST endpoints should use `@PreAuthorize` at the method level on the controller.

**Rejected:** Class-level `@PreAuthorize("isAuthenticated()")` on `AdvertisementService`, `AuditReadService`, `AuditReadService`, `AuditReadService`, `UserSettingsService` — confirmed broken via smoke tests.

---

## 2026-05-13 — Double-click guard on save buttons via setEnabled

**Decision:** Save buttons in `AdvertisementFormOverlayModeHandler`, `UserFormOverlayModeHandler`, and `SettingsOverlay` are guarded with `setEnabled(false)` at the start of the click listener and re-enabled in a `finally` block.

**Why:** Rapid double-click submits the form twice, causing duplicate saves. The guard lives in `activate()` of the FormModeHandler (not in the Overlay) because the save button is a Spring-injected field of the handler.

---

## 2026-05-13 — ValidRange.List does NOT carry @Constraint; ValidRangeValidator caches field lookups

**Decision:** The inner `@List` annotation on `@ValidRange` intentionally omits `@Constraint(validatedBy = {})`. `ValidRangeValidator` caches reflected fields in a static `ConcurrentHashMap` keyed by `FieldKey(class, start, end)`.

**Why:** Jakarta Bean Validation 3.0 spec says container annotations should carry `@Constraint(validatedBy = {})`, but Hibernate Validator (as used in Spring Boot 4.0.6) does NOT support this for class-level (`ElementType.TYPE`) constraints — adding it causes `HV000030: No validator could be found` at runtime. HV handles repeatable constraints automatically via `@Repeatable` without requiring the container to be annotated as a constraint. Caching avoids repeated `getDeclaredField` + `setAccessible` on every validation call.

**Rejected:** Adding `@Constraint(validatedBy = {})` to `ValidRange.List` — confirmed broken at runtime via smoke tests.

---

## Ongoing — Modular storage: contract vs implementation

**Decision:** `StorageService` interface lives in `attachment-spring-boot-starter`. `S3StorageService` and `NoOpStorageService` implementations live in the same module. UI components use `ObjectProvider.ifAvailable()` to degrade gracefully when the attachment starter is absent from the classpath.

**Why:** Storage only exists to serve attachments — there is no use case for storage without attachments. Contract/implementation split keeps domain logic independent of infrastructure.

**Rejected:** Keeping `storage-s3-spring-boot-starter` as a separate module — two modules with a mandatory one-way dependency and no realistic decoupling scenario.

---

## 2026-05-13 — Audit subsystem extracted to audit-spring-boot-starter

**Decision:** The full audit subsystem (write side: `DefaultAuditPort`, `AuditableSnapshot.diff()`, `AuditLogRepository`; read side: `AuditHistoryService`, `AuditQueryService`, `ActivityService`) lives in `audit-spring-boot-starter`. All Vaadin audit UI lives in `marketplace-app`. Domain services call `AuditPort` (contract interface). The starter contains zero advertisement-specific knowledge — all domain coupling is expressed through SPIs (`AuditDomainHook`, `AuditActivityFieldsHook`, `AuditActivityEnrichHook`) implemented in `marketplace-app`.

**Why:** Audit is infrastructure, not domain. Enables deploying audit-free variants. `AuditableSnapshot` marker interface carries `entityType()` — eliminates stringly-typed entity-type strings.

**Rejected:** Conditional Spring annotations in `platform-commons` — contracts must be Spring-free pure Java.

---

## 2026-05-21 — AuditSnapshotBinder used directly in marketplace-app

**Decision:** `AuditSnapshotBinder` is used directly in `marketplace-app` UI components. `AuditUiPort` was removed (2026-06-15) as unnecessary indirection — all Vaadin UI lives in marketplace-app, so there is no second consumer that would require the SPI.

**Rule:** Do not re-introduce `AuditUiPort`.

---

## 2026-05-26 — No shared UI module needed; plain-class pattern for future sharing

**Decision:** No new UI module (`advertisement-ui-core` or similar) will be created. As of 2026-05-26, there is no actual cross-module UI duplication: `PaginationBar` and `EmptyStateView` exist only in `marketplace-app`; each starter owns its own UI components (`AuditHistoryPanel`, `AuditActivityPanel`, `AttachmentGallery`) with no overlap.

**If cross-module sharing ever becomes real:** move the component to `platform-commons` `ui` package as a plain class — no `@SpringComponent`, no `@Scope`. Each module that wants a Spring-managed instance declares its own `@Bean @Scope("prototype")` in a local `@Configuration`. No `@AutoConfiguration` in `platform-commons`, no new module.

```java
// in the consuming module's @Configuration
@Bean
@Scope("prototype")
PaginationBar paginationBar(I18nService i18nService) {
    return new PaginationBar(i18nService);
}
```

**Why no separate module:** multiplying modules has real cost (pom.xml overhead, dependency graph complexity). The plain-class + local `@Bean` pattern achieves sharing with zero new modules and zero `@AutoConfiguration`.

**Why not make platform-commons a starter:** platform-commons contains only pure Java contracts (DTOs, SPI interfaces, annotations). All Vaadin UI lives in marketplace-app; `AuditActivityRowHook`, `AuditUiPort`, and `AttachmentGalleryPort` were removed (2026-06-15) — platform-commons has no Vaadin dependency. Adding `@AutoConfiguration` to commons is unnecessary — the plain-class pattern is simpler and sufficient.

**Prerequisite for any component moved to commons:** remove all marketplace-specific imports (`I18nKey`, `I18nParams`, `PaginationDefaults`) — pass them as constructor parameters instead. `TranslationKey` was deleted and does not exist in platform-commons.

**Supersedes:** the earlier `advertisement-ui-core` proposal.

---

## 2026-06-10 — Audit writes belong in the service layer, not the UI

**Rule:** `AuditPort.captureUpdate` / `captureCreation` / `captureDeletion` must only be called from `*Service` classes — never from UI overlays, view components, or `*HookImpl` classes.

**Why:** Discovered during architecture audit. `SettingsOverlay.handleSave()` was calling `auditPortFactory.captureUpdate(...)` directly — the only place in the entire UI layer that fired an audit write. All other entities (`AdvertisementService`, `UserService`) correctly place audit calls inside the service. Moving it to `UserSettingsService.save()` restores consistency and keeps the UI layer free of infrastructure concerns.

**How to apply:** When a save/update/delete operation needs to be audited, put the `AuditPort` call at the end of the corresponding `*Service.save()` / `*Service.delete()` method. The UI layer calls the service; the service handles persistence + audit atomically.

**Fixed in:** `UserSettingsService.save()` now loads old settings, saves, publishes the domain event, and captures the audit entry. `SettingsOverlay.handleSave()` only constructs the DTO and calls `settingsService.save()`.

---

## 2026-06-10 — *HookImpl must not inspect snapshot internals — delegate to the owning service

**Rule:** `*HookImpl` methods must not access fields of snapshot DTOs or apply any formatting/resolution logic. Each switch branch must contain exactly one service call.

**Why:** `AuditDomainHookImpl.resolveDisplayName()` was directly accessing `ad.title()` and `u.name()` from snapshot DTOs and applying a null guard — business logic embedded in a hook. The hook's role is entity-type routing only. The resolution of "what name to display for this snapshot" belongs to the service that owns the entity.

**How to apply:** If a `*HookImpl` method needs entity-specific data, add a method to the corresponding `*Service` and delegate. Entity-type routing (a `switch` over `EntityType`) is the only permitted logic in a `*HookImpl`.

**Fixed in:** `AuditDomainHookImpl.resolveDisplayName()` now delegates to `advertisementService.resolveDisplayName(snapshot)` and `userService.resolveDisplayName(snapshot)`. The i18n label for `USER_SETTINGS` (which has no snapshot content to display) stays in the hook as a trivial lookup, not business logic.

---

## 2026-06-15 — Known decoupling debt (open backlog from codebase audit)

Direct imports that violate the "marketplace-app UI accesses starters only via platform-commons contracts" rule. Recorded here as open work items, not accepted permanent exceptions.

**Architecture rule (2026-06-15):** marketplace-app UI is a monolith — decoupling is required only at the service ↔ UI boundary (starters vs marketplace-app). Within marketplace-app, UI components may reference each other freely. UI ports/hooks (AuditUiPort, AttachmentGalleryPort, AuditActivityRowHook) were removed as unnecessary indirection.

### 1. `org.ost.marketplace.ui.views.components.attachment.*` → attachment-starter internals (service ↔ UI boundary violation)

→ [improvement-001-attachment-ui-boundary-violation](../features/issues/improvement-001-attachment-ui-boundary-violation.md)

### ✅ 3. marketplace-app → `org.ost.user.*` internals — RESOLVED (2026-06-15)

All 22 files that previously imported `org.ost.user.entity.User`, `org.ost.user.services.UserService`, `org.ost.user.services.UserSettingsService`, or `org.ost.user.security.UserPrincipal` now use `UserDto`/`UserPort` from platform-commons exclusively. See "2026-06-15 — Full user decoupling" entry above.

### ✅ 4. `org.ost.marketplace.security.*` uses `User` entity instead of DTO — RESOLVED (2026-06-15)

`OwnershipChecker`, `RoleChecker`, and `AccessEvaluator` were updated as part of the full user decoupling. `AuthContextService.getCurrentUser()` now returns `Optional<UserDto>`; security classes no longer import `org.ost.user.entity.User`. `AuthenticatedPrincipal` SPI in platform-commons is the boundary — `UserPrincipal` (user-starter) implements it and exposes `toUserDto()`.

---

## 2026-06-23 — DONE: Top-level Timeline tab

**Decision:** Added a dedicated **Timeline** navigation tab (alongside Listings and Users) with filter, sort, and pagination. Inline timeline tabs removed from UserOverlay and SettingsOverlay.

**Components:** `TimelineView` (`@UIScope`), `TimelineQueryBlock` (filter panel), `AuditTimelineListRenderer`, `AuditTimelineRowRenderer`, `PaginationBar`. Visibility gated by `access.canView()` — MODERATOR/ADMIN only. USER role sees only their own activity (actor filter forced server-side by `AccessEvaluator`).

**Key lesson:** `TimelineView` must override `setVisible(boolean)` to call `refreshFeed()` — tab switching in `MainView` uses `setVisible()`, not component detach/attach, so `@PostConstruct` alone produces stale data after mutations.

**Closes:** `audit-spring-boot-starter/DECISIONS.md` 2026-06-11 (planned top-level Timeline tab).

---

## 2026-05-29 — AbstractViewOverlayModeHandler: Template Method for tabbed view overlays

**Decision:** All "view mode" overlay handlers extend `AbstractViewOverlayModeHandler` (in `ui/views/components/overlay/`). The base class provides a `final activate(OverlayLayout)` that assembles the tab layout; subclasses implement five abstract methods: `tabsCssClass()`, `buildPrimaryTab()`, `buildPrimaryContent()`, `buildSecondaryTab()`, `buildHeaderActions()`.

The `SecondaryTabDef` record `(Tab tab, String cssClass, Supplier<Component> loader)` represents the optional second tab. Returning `null` from `buildSecondaryTab()` produces a single-tab layout. Lazy loading and visibility toggling live entirely in the private `assembleTabbedContent()` static helper.

**Why:** `AdvertisementViewOverlayModeHandler` and `UserViewOverlayModeHandler` had identical tab-switching and lazy-loading boilerplate (~15 lines each). More view handlers are planned; the base class gives each one a consistent structure with zero boilerplate.

**Rejected:** `TabbedOverlayContent` as a Spring `@Prototype` `Configurable` bean — passing live UI components (`Tabs`, `Tab`, `Div`) as `Parameters` violated the project convention that Parameters carry data/config, not pre-built component trees.

---

## [OPEN GOAL] Activity field visibility: filter by viewer's role vs. actor's role at change time

→ [goal-001-activity-field-visibility-by-role](../features/issues/goal-001-activity-field-visibility-by-role.md)
