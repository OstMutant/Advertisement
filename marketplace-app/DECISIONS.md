# Architecture & Technical Decisions — marketplace-app

---

## 2026-06-13 — UI layer restructuring: marketplace-ui created, then merged back; domain modules planned

### Phase 1 (implemented): Vaadin UI extracted from starters, package namespace unified

**What changed:**
- Created `marketplace-ui` module — moved all `org.ost.audit.ui.*`, `org.ost.attachment.ui.*`, `org.ost.query.ui.*` source files out of their respective starters
- Package names unified to `org.ost.ui.<domain>.*` pattern: `org.ost.ui.audit`, `org.ost.ui.attachment`, `org.ost.ui.query`, `org.ost.ui.marketplace`
- `MarketplaceUiConfiguration` converted to `@AutoConfiguration` registered in `AutoConfiguration.imports` (no longer found via `@SpringBootApplication` package scan)
- 5 CSS files moved from starter `META-INF/resources/frontend/` to `marketplace-app/src/main/frontend/themes/my-app/`; `@CssImport` annotations removed
- `QueryAutoConfiguration` deleted; `validationService` bean moved to `MarketplaceUiConfiguration`

**Why — CSS production build:** Vaadin 25 Vite production build does not include CSS from `@CssImport` on components in JAR starters. CSS in `marketplace-app/themes/my-app/` is always bundled.

### Phase 2 (planned): merge marketplace-ui into marketplace-app; extract domain modules

**Decision (planned, not yet implemented):** `marketplace-ui` is a transitional module — it will be absorbed into `marketplace-app`. Domain logic (Advertisement, User) moves to dedicated Spring Boot starters. Final target structure:

```
advertisement-parent
├── platform-commons                  — SPI/DTO contracts + new UserPort, AdvertisementPort
├── query-lib                     — SQL filter/sort library (no Vaadin)
├── audit-spring-boot-starter         — audit domain (no Vaadin)
├── attachment-spring-boot-starter    — attachment domain (no Vaadin)
├── user-spring-boot-starter          — User entity + UserService + UserPortImpl
├── advertisement-spring-boot-starter — Advertisement entity + AdvertisementService + AdvertisementPortImpl
└── marketplace-app                   — ALL Vaadin UI (absorbs marketplace-ui) + Spring Boot entry point
```

**Why merge marketplace-ui back into marketplace-app:** The separation adds a Maven module boundary without an architectural benefit — marketplace-app is the only consumer of marketplace-ui, and marketplace-ui cannot be reused elsewhere. The `org.ost.ui.*` package namespace is preserved; only the JAR boundary changes.

**Why domain starters:** Advertisement and user are distinct bounded contexts. Separating them into starters enables independent evolution and testing. `marketplace-app` becomes a thin UI + orchestration layer calling domain via `UserPort` / `AdvertisementPort` SPIs.

**Why — platform-commons stays unchanged:** SPI interfaces must be visible to all modules without circular dependencies. `platform-commons` remains the neutral contract zone.

**Key decoupling decision — no SQL JOIN across domain boundaries:**
`AdvertisementRepository` currently JOINs `user_information` to enrich `AdvertisementInfoDto` with creator name. After the domain split, this JOIN is replaced with a `UserPort.findActorNames(Collection<Long> ids)` call in `AdvertisementService`. The repository only queries `advertisement`; the service fetches user names in bulk and enriches the DTOs. This eliminates advertisement-starter's knowledge of the `user_information` table schema.

**SecurityConfig:** `UserDetailsService` currently depends on `UserService.findByEmail` directly. After extraction, `UserPort` gains a `findByEmail(String email)` method; `SecurityConfig` in marketplace-app injects `UserPort` instead of `UserService`.

**Implementation order:**
1. Merge `marketplace-ui` → `marketplace-app`
2. Create `user-spring-boot-starter` (simpler — no media)
3. Create `advertisement-spring-boot-starter`
4. Define `UserPort` + `AdvertisementPort` in `platform-commons`
5. Update marketplace-app: Views call ports instead of services directly

**Graceful degradation preserved:** `marketplace-ui` references starters via `ObjectProvider` + `optional` Maven dependencies — starter absence degrades gracefully, same as today.

**SPI implementations:** `*HookImpl` and `*PortImpl` classes stay in `marketplace-app` (the orchestrator), not in domain modules, so domain modules remain free of starter dependencies.

**How to apply:** Domain split second (advertisement-module, user-module). Phase 1 (marketplace-ui) is done. Do not merge all changes in one PR.

**What phase 1 changed:**
- Created `marketplace-ui` module with `MarketplaceUiConfiguration` (`@Configuration` + `@ComponentScan({"org.ost.audit.ui","org.ost.attachment.ui","org.ost.query.ui"})`)
- Moved all `org.ost.audit.ui.*`, `org.ost.attachment.ui.*`, `org.ost.query.ui.*` source files to `marketplace-ui`
- Moved `AttachmentGalleryPortImpl` to `org.ost.marketplace.ui.spi` (found via `@SpringBootApplication` scan)
- Deleted `QueryAutoConfiguration` entirely; validationService bean moved to `MarketplaceUiConfiguration`
- Removed `vaadin-spring-boot-starter` from all starter pom.xml files
- Moved 5 CSS files from starter `META-INF/resources/frontend/` to `marketplace-app/src/main/frontend/themes/my-app/`; added imports to `styles.css`; removed `@CssImport` annotations
- `marketplace-app/pom.xml`: replaced 3 starter deps + query-lib with single `marketplace-ui` dep

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

**Decision:** The full audit subsystem (write side: `DefaultAuditPort`, `AuditableSnapshot.diff()`, `AuditLogRepository`; read side: `AuditReadService`, `AuditReadService`, `AuditReadService`, Vaadin audit UI) lives in `audit-spring-boot-starter`. Domain services call `AuditPort` (contract interface). The starter contains zero advertisement-specific knowledge — all domain coupling is expressed through SPIs (`AuditDomainHook`, `EntityNameHook`, `AuditActivityFieldsHook`, `AuditActivityRowHook`) implemented in `marketplace-app`.

**Why:** Audit is infrastructure, not domain. Enables deploying audit-free variants. `AuditableSnapshot` marker interface carries `entityType()` — eliminates stringly-typed entity-type strings.

**Rejected:** Conditional Spring annotations in `platform-commons` — contracts must be Spring-free pure Java.

---

## 2026-05-21 — AuditSnapshotBinder coupling in marketplace-app UI is intentional

~~**Decision:** `SettingsOverlay` and `UserViewOverlayModeHandler` import `org.ost.audit.ui.AuditSnapshotBinder` directly. This is a known, accepted coupling — not a decoupling violation to fix.~~

~~**Rejected:** Abstracting `AuditSnapshotBinder.Builder` behind an SPI in `platform-commons` — over-engineering for a single implementation.~~

**Superseded 2026-06-02:** Direct import removed. `AuditUiPort` (platform-commons) now exposes `snapshotRowHook(SnapshotRowHookParams<T>)` — marketplace calls it via the existing `ObjectProvider<AuditUiPort>`. `AuditUiPortImpl` (audit-starter) creates the `AuditSnapshotBinder` internally. The `audit-spring-boot-starter` dependency in `marketplace-app/pom.xml` is now `<optional>true</optional>`. Marketplace has zero imports from `org.ost.audit.*`.

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

**Why not make platform-commons a starter:** platform-commons already has `vaadin-core` and Vaadin types in its SPI signatures (`AuditActivityRowHook`, `AuditUiPort`, `AttachmentGalleryPort` all return `com.vaadin.flow.component.Component`). The "Vaadin-free contracts" claim was already fiction. However, adding `@AutoConfiguration` to commons is still unnecessary — the plain-class pattern is simpler and sufficient.

**Prerequisite for any component moved to commons:** replace all marketplace-specific imports (`I18nKey`, `I18nParams`, `PaginationDefaults`) with platform-commons equivalents (`TranslationKey`, constructor parameters).

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

### 1. ~~`org.ost.ui.audit.*` → `org.ost.audit.services.AuditReadService`~~ — superseded

This item was based on the assumption that AuditUiPort should mediate the call. That port was removed (2026-06-15) as unnecessary indirection. `AuditActivityPanel` and `AuditTimelinePanel` calling `AuditReadService` directly **is** the legitimate service ↔ UI boundary — this is correct design, not a violation.

### 2. `org.ost.ui.attachment.*` → attachment-starter internals (service ↔ UI boundary violation)

`AttachmentGallery`, `AttachmentLightbox`, `AttachmentGalleryService`, `CardLightboxStrip`, `CardLightboxViewer`, `CardMediaLightbox` import `Attachment` entity, `AttachmentService`, `AttachmentSnapshotService`, `MediaContentTypeUtil`, `YoutubeUtil` directly from attachment-starter.

**Why this is a violation:** UI (marketplace-app) is calling service-layer internals of a starter directly instead of going through the platform-commons `AttachmentPort` contract.

**Fix:** expose the needed read operations on `AttachmentPort` in platform-commons (e.g. `getByEntity`, `getTempAttachments`, snapshot lookup); replace direct `AttachmentService` injection in UI components with `AttachmentPort`; replace `Attachment` entity with DTOs from `attachment.dto`; move `MediaContentTypeUtil`/`YoutubeUtil` to platform-commons or attachment.dto.

### 3. marketplace-app → `org.ost.user.*` internals (26 import sites)

`UserView`, `UserOverlay`, `SignUpDialog`, `SettingsFormModeHandler`, `UserMapper`, `VaadinLocaleProvider` and others import `User` entity and `UserService`/`UserSettingsService` directly.

**Root cause:** user domain was recently extracted into `user-spring-boot-starter`; imports were mechanical refactoring of the old package path.

**Fix:** expose missing operations via `UserPort` in platform-commons; replace `User` entity usage at call sites with `UserDto`/`UserInfoDto` from `platform.user.dto`.

**Priority:** fix #2 first; then #3 (largest).

### 4. `org.ost.marketplace.security.*` uses `User` entity instead of DTO

`OwnershipChecker` and `RoleChecker` accept `org.ost.user.entity.User` directly in method signatures. The direction (marketplace → user-starter) is correct, but the boundary is crossed by an entity instead of a DTO.

**Root cause:** `AuthContextService.getCurrentUser()` returns `Optional<User>` entity. All consumers — including `AccessEvaluator`, `RoleChecker`, `OwnershipChecker` — inherit this entity type from the auth context.

**Fix:** introduce `CurrentUserDto` (or reuse an existing `UserSnapshot`) in `platform-commons`; change `AuthContextService.getCurrentUser()` to return `Optional<CurrentUserDto>`; update `OwnershipChecker`, `RoleChecker`, and `AccessEvaluator` signatures accordingly. `UserPrincipal` in user-starter adapts `User` entity → `CurrentUserDto` at the security boundary.

**Blocked by:** fix #3 (`UserPort` / `UserDto` migration) — same root entity exposure.

---

## 2026-05-29 — AbstractViewOverlayModeHandler: Template Method for tabbed view overlays

**Decision:** All "view mode" overlay handlers extend `AbstractViewOverlayModeHandler` (in `ui/views/components/overlay/`). The base class provides a `final activate(OverlayLayout)` that assembles the tab layout; subclasses implement five abstract methods: `tabsCssClass()`, `buildPrimaryTab()`, `buildPrimaryContent()`, `buildSecondaryTab()`, `buildHeaderActions()`.

The `SecondaryTabDef` record `(Tab tab, String cssClass, Supplier<Component> loader)` represents the optional second tab. Returning `null` from `buildSecondaryTab()` produces a single-tab layout. Lazy loading and visibility toggling live entirely in the private `assembleTabbedContent()` static helper.

**Why:** `AdvertisementViewOverlayModeHandler` and `UserViewOverlayModeHandler` had identical tab-switching and lazy-loading boilerplate (~15 lines each). More view handlers are planned; the base class gives each one a consistent structure with zero boilerplate.

**Rejected:** `TabbedOverlayContent` as a Spring `@Prototype` `Configurable` bean — passing live UI components (`Tabs`, `Tab`, `Div`) as `Parameters` violated the project convention that Parameters carry data/config, not pre-built component trees.
