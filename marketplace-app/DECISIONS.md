# Architecture & Technical Decisions — marketplace-app

---

## 2026-06-13 — Planned: extract Vaadin UI into a dedicated module; domain modules for advertisement and user

**Decision (planned, not yet implemented):** The module structure will evolve toward:

```
advertisement-parent
├── platform-commons          — SPI/DTO contracts (unchanged)
├── query-starter             — SQL filter/sort library (unchanged)
├── audit-starter             — audit domain logic, no Vaadin UI
├── attachment-starter        — attachment domain logic, no Vaadin UI
├── advertisement-module      — advertisement domain: services, repositories, domain events
├── user-module               — user domain: services, repositories, domain events
├── marketplace-ui            — ALL Vaadin UI + CSS (depends on all starters and domain modules)
└── marketplace-app           — Spring Boot entry point + SPI orchestration (*HookImpl, *PortImpl)
```

**Why — CSS production build limitation:** Vaadin 25 production builds (Vite) do not include CSS from `@CssImport` annotations on components in JAR starters. The `injectGlobalCss` mechanism is absent from the production bundle. Moving all Vaadin UI to a single `marketplace-ui` module eliminates the need for `@CssImport` across module boundaries — CSS lives in the theme of the UI module and is always bundled correctly.

**Why — domain separation:** Advertisement and user logic are distinct bounded contexts. Separating them enables independent evolution and testing. `marketplace-app` becomes a thin orchestrator: it wires SPI implementations (`*HookImpl`, `*PortImpl`) and holds the Spring Boot entry point.

**Why — platform-commons stays:** SPI interfaces (`AuditPort`, `AttachmentPort`, `CurrentActorHook`, etc.) must be visible to both starters and marketplace without circular dependencies. `platform-commons` remains the neutral contract zone.

**Graceful degradation preserved:** `marketplace-ui` references starters via `ObjectProvider` + `optional` Maven dependencies — starter absence degrades gracefully, same as today.

**SPI implementations:** `*HookImpl` and `*PortImpl` classes stay in `marketplace-app` (the orchestrator), not in domain modules, so domain modules remain free of starter dependencies.

**How to apply:** Implement incrementally — `marketplace-ui` first (resolves the CSS bug), domain split second (advertisement-module, user-module). Do not merge all changes in one PR.

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

## 2026-05-29 — AbstractViewOverlayModeHandler: Template Method for tabbed view overlays

**Decision:** All "view mode" overlay handlers extend `AbstractViewOverlayModeHandler` (in `ui/views/components/overlay/`). The base class provides a `final activate(OverlayLayout)` that assembles the tab layout; subclasses implement five abstract methods: `tabsCssClass()`, `buildPrimaryTab()`, `buildPrimaryContent()`, `buildSecondaryTab()`, `buildHeaderActions()`.

The `SecondaryTabDef` record `(Tab tab, String cssClass, Supplier<Component> loader)` represents the optional second tab. Returning `null` from `buildSecondaryTab()` produces a single-tab layout. Lazy loading and visibility toggling live entirely in the private `assembleTabbedContent()` static helper.

**Why:** `AdvertisementViewOverlayModeHandler` and `UserViewOverlayModeHandler` had identical tab-switching and lazy-loading boilerplate (~15 lines each). More view handlers are planned; the base class gives each one a consistent structure with zero boilerplate.

**Rejected:** `TabbedOverlayContent` as a Spring `@Prototype` `Configurable` bean — passing live UI components (`Tabs`, `Tab`, `Div`) as `Parameters` violated the project convention that Parameters carry data/config, not pre-built component trees.
