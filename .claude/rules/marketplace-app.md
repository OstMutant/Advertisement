---
paths: ["marketplace-app/**"]
---

## marketplace-app: UI Layer

The main Vaadin application. All UI code lives here — no UI in starters.

Java package root: `org.ost.marketplace`

---

## UI Component Patterns

### Configurable prototype beans

Vaadin UI components that require runtime data use the `Configurable<T, P>` pattern:

```java
@SpringComponent
@Scope("prototype")
public class MyPanel extends Div
        implements Configurable<MyPanel, MyPanel.Parameters>, Initialization<MyPanel> {

    // Parameters: Java record for ≤4 simple fields; Lombok @Builder for 5+ or any callback
    @Value @lombok.Builder
    public static class Parameters {
        @NonNull Long entityId;
        Runnable onSave;
        Runnable onCancel;
    }

    @Override @PostConstruct
    public MyPanel init() {
        // structural setup only: CSS classes, layout skeleton — no data, no service calls
        addClassName("my-panel");
        return this;
    }

    @Override
    public MyPanel configure(Parameters p) {
        // data loading, button wiring, value binding — called once per use
        return this;
    }
}
```

Instantiation via `UiComponentFactory<MyPanel>` (declared as a bean in `ComponentFactoryConfig`):
```java
componentFactory.build(MyPanel.Parameters.builder().entityId(id).onSave(onSave).build());
```

**Rules:**
- `init()` — structural setup only. No data, no service calls.
- `configure()` — data + behavior. Called once after `init()`.
- `Parameters` as Java record when ≤4 simple fields: `new MyPanel.Parameters(id, name)`.
- `Parameters` with Lombok `@Builder` when 5+ fields or any `Runnable`/`Consumer` callback.
- `Configurable` lives in `org.ost.marketplace.ui.core`.
- `Initialization` lives in `org.ost.marketplace.ui.core`.
- `UiComponentFactory<T extends Configurable<T, P>, P>` (marketplace-app `ui.core`) extends `ComponentFactory<T>` (platform-commons); `build(P params)` calls `get().configure(params)` directly, no cast.
- Use `UiComponentFactory<T, P>` for Configurable prototype UI beans (those that implement `Configurable` and need `.build(params)`), with `P` matching the bean's own `Parameters` type (e.g. `UiComponentFactory<MyPanel, MyPanel.Parameters>`).
- Use `ComponentFactory<T>` for optional singleton services/ports (e.g. `AdvertisementPort`, `AuditPort`) — starters declare `ComponentFactory<X>` beans, inject as `ComponentFactory<X>` even in UI classes.

**When NOT to use Configurable:**
- Component has distinct modes with different UI structure → use explicit named methods:
  `configureForView(Long id)`, `configureForEdit(Long id)`, `configureForCreate(String sessionId)`.
- Component needs only 1–2 simple setters → plain setters, no `Parameters`.
- Do NOT use positional-argument methods with 4+ parameters in any module — use `Parameters` instead.

---

## Overlay Pattern

### OverlaySession — immutable state machine
Every overlay uses a `record OverlaySession(Mode mode, EntityDto entity, Runnable onSaved, boolean enteredFromView)`.
Mode transitions return new instances — never mutate fields directly:
```java
session = session.toEdit();          // correct
session = session.toView();          // correct
session = session.withEntity(fresh); // correct — after save
session.mode = Mode.EDIT;            // wrong — record, mutation is impossible
```

### switchTo() — the only way to transition between modes
Always call `switchTo()` to transition between overlay modes. Never call `launchSession()`
for transitions — `launchSession()` resets the entire layout and triggers an unnecessary
JS scroll-reset. `launchSession()` is only for the initial overlay open.

```java
// correct — transitioning between modes
session = session.toEdit();
switchTo();

// wrong — resets layout
session = session.toEdit();
launchSession(this::switchTo);
```

### currentFormHandler — reset before switchTo()
At the start of `switchTo()` always reset `currentFormHandler = null` before the switch expression.
Without this, after VIEW→EDIT→VIEW the handler remains non-null and `hasUnsavedChanges()`
returns `true` even though the form is already closed.

```java
@Override
protected void switchTo() {
    currentFormHandler = null;  // always the first line
    OverlayModeHandler handler = switch (session.mode()) { ... };
    ...
}
```

### afterSave() — update entity in session
After saving in EDIT mode always update the entity in the session via `withEntity()`:
```java
if (session.mode() == Mode.EDIT) {
    currentFormHandler.afterSave(true);
    EntityDto fresh = currentFormHandler.getSavedDto();
    if (fresh != null) session = session.withEntity(fresh);
} else {
    closeToList();
}
```

### discardChanges() — the only name for resetting a form
The method for resetting form state in FormOverlayModeHandler is always named `discardChanges()`.
In `doCancel()` of the overlay always call it before transitioning:
```java
if (currentFormHandler != null) currentFormHandler.discardChanges();
```

---

## View Pattern

### init() — structure and visibility
The `@PostConstruct init()` method in all View classes is always `protected`, never `public`.
The order inside init() is fixed:

```java
@PostConstruct
protected void init() {
    // 1. CSS class and sizing
    // 2. Build main component (grid / container)
    // 3. Build contentWrapper
    // 4. add(contentWrapper, overlay)
    // 5. Subscriptions (queryBar, pagination, shortcuts)
    // 6. settingsPaginationBinding.register(...)
    // 7. refresh()
}
```

Do not split init() into small `initXxx()` methods if each does 1–2 lines.
Extract into a separate method only when logic is complex (e.g. Grid column configuration
via a dedicated `*GridConfigurator` class).

### refresh() — always with try/catch
The `refresh()` method in all View classes is always `private` and always guarded:

```java
private void refresh() {
    try {
        // fetch + render logic
    } catch (ConstraintViolationException ex) {
        log.warn("Validation error: {}", ex.getMessage(), ex);
        showValidationErrors(ex);
        clearContent();
        paginationBar.setTotalCount(0);
    } catch (Exception ex) {
        log.error("Failed to refresh view", ex);
        notificationService.error(...);
        clearContent();
        paginationBar.setTotalCount(0);
    } finally {
        queryStatusBar.update(); // if queryStatusBar is present in this view
    }
}
```

Never leave `refresh()` without try/catch — an unhandled exception means a blank screen for the user.

### Refresh method name
The data refresh method in a View is always named `refresh()`, never `refreshGrid()`,
`refreshData()`, or any other variant.

---

## Query Layer Pattern

### FilterMeta and SortMeta — Fields.* constants only
In `*SortMeta` and `*FilterMeta` classes, fields always reference typed `Fields.*` constants
from the DTO, never raw strings:

```java
// correct — compiler catches renames
SortFieldMeta.of(AdvertisementInfoDto.Fields.updatedAt, ADVERTISEMENT_SORT_UPDATED_AT)

// wrong — silent failure on refactoring
SortFieldMeta.of("updatedAt", ADVERTISEMENT_SORT_UPDATED)
```

---

## Form Handler Pattern

### buildBinder() — separate method
Binder creation and field binding logic is always extracted into a separate `buildBinder(EntityDto dto)`
method, never inlined into `activate()`.

### History access — an icon button opening EntityActivityOverlay, not a tab
`AbstractFormOverlayModeHandler` has no tab machinery at all (`buildTabbedContent()`,
`buildContentWithActivity()`, `ActivityTabParams` were removed once all five domains migrated —
see `.claude/nav/adr-index.md`). `layout.setContent(...)` is called unconditionally;
history/restore is a header-action icon button (`.{domain}-history-button`) that opens the shared
`EntityActivityOverlay` (`ui/views/components/audit/`) stacked on top via `BaseOverlay.openNested()`:

```java
private UiIconButton buildHistoryButton() {
    UiIconButton historyBtn = new UiIconButton(getValue(USER_ACTIVITY_BUTTON), VaadinIcon.CLOCK.create());
    historyBtn.addClassName("user-history-button");
    historyBtn.addClickListener(_ -> entityActivityOverlay.openFor(EntityActivityOverlay.Parameters.builder()
            .entityRef(new EntityRef(EntityType.USER, params.getUser().id()))
            .parentSteps(params.getBreadcrumbSteps())
            .parentFormLabel(params.getUser().name())
            .currentLabelKey(USER_ACTIVITY_BUTTON)
            .onRestoreRequested(this::handleRestoreFromActivity)
            .build()));
    return historyBtn;
}
```

---

## Breadcrumb Pattern

### BreadcrumbStep — a growing stack, not a fixed back-link
`record BreadcrumbStep(String label, Runnable onClick)` (`ui/views/components/overlay/`) — `label`
is an already-resolved `String` (not an `I18nKey`), so it also fits dynamic labels (e.g.
`UserOverlay`'s current user name). `AbstractEntityOverlay.buildBreadcrumbSteps()` returns the
list-tab step by default, plus a "View" step (`OVERLAY_BREADCRUMB_VIEW`) whenever
`isEditMode() && enteredFromView()` — never rewrite existing segments when navigating deeper, only
append. `OverlayLayout.setBreadcrumbLinks(List<Component>)` renders the chain with `›` separators
only *between* links. `EntityActivityOverlay.openFor(...)` extends the calling overlay's own
`buildBreadcrumbSteps()` (passed as `parentSteps`) with one more segment (`parentFormLabel`), so
the nested history overlay's breadcrumb reflects the real navigation path taken, not a fixed
2-segment pair. See `.claude/nav/adr-index.md` for the full history of this design.

---

## Reference Implementations

When adding a new domain, use these as reference:
- View: `AdvertisementsView` (init structure) + `UserView` (refresh guard)
- Overlay: `AdvertisementOverlay` (OverlaySession, afterSave, mode switching)
- ViewModeHandler: `AdvertisementViewOverlayModeHandler` (AbstractViewOverlayModeHandler)
- FormModeHandler: `AccountNameFormModeHandler` (buildBinder separate)
- QueryBlock: `AdvertisementQueryBlock` and `UserQueryBlock` (identical structure)
- FilterMeta: `AdvertisementFilterMeta` (Fields.* constants)
- SortMeta: `AdvertisementSortMeta` (Fields.* constants)

---

## I18n

`I18nService`, `InstantFormatter`, `LocaleProvider` live in `org.ost.marketplace.services.i18n`. Starters have no i18n infrastructure of their own — all UI i18n lives here.

Translation keys — single consolidated enum:
- `org.ost.marketplace.services.i18n.I18nKey` — all app keys (main app, audit, attachment). Has `forAction(ActionType)` static method.

**Rules:**
- Never use raw `MessageSource` directly in UI components — use `I18nService.get(I18nKey)`.
- Never use `msg(String key, String fallback)` — missing keys must fail fast, not silently fall back.
- Never build keys dynamically: `"changes.field." + fieldName` — use typed enum with explicit mapping. `AuditTimelineRowRenderer`'s `labelFor(EntityType, String rawFieldKey)` is the one place resolving a raw DTO field-name constant to its label — a `switch` on the already-compiler-checked `Fields.*` constant, not a dynamically-built key, so it already follows this rule rather than needing an exception.
- `I18nParams` interface: implement `getI18nService()` to get `getValue(I18nKey, ...)` and `formatAction(ActionType)` as defaults.

---

## Security: @PreAuthorize and Vaadin

`@EnableMethodSecurity` is active. **Never put `@PreAuthorize` at class level on service beans.** Vaadin initializes view beans on the first HTTP request before the user authenticates; a class-level annotation causes an `AuthorizationDeniedException` during view wiring, preventing any view from loading.

- Method-level `@PreAuthorize` is fine for REST controller endpoints.
- Services (`AdvertisementService`, `ActivityService`, etc.) intentionally have no `@PreAuthorize`.
- Non-Vaadin REST controllers live in the sibling `marketplace-rest-api` module, not here — see
  `.claude/rules/marketplace-rest-api.md`. `GET /health` and `GET /sitemap.xml` (both routed from
  that module) still need their own explicit `permitAll()` rule in *this* module's own
  `SecurityConfig`, ahead of the catch-all (see next bullet), since Spring Security's URL-based
  rules are independent of which module physically contains the controller class.
- `SecurityConfig` uses `anyRequest().permitAll()` at the URL layer — deny-by-default does not
  apply to this app's single-route Vaadin SPA model (see `.claude/nav/adr-index.md`).
- Login (`AuthService.login()`) and registration (`UserPort.register()` → `UserService.register()`)
  are rate-limited via an in-memory Caffeine cache (5 attempts / 15 min), counting only real
  failures — never successes (see `.claude/nav/adr-index.md`).
- `application-prod.yml` sets `server.forward-headers-strategy: framework` — required for
  `request.getRemoteAddr()` to resolve the real client IP behind Render's proxy, not Render's
  own internal edge address (see `.claude/nav/adr-index.md`).

---

## Naming Conventions

### Class suffixes
- `*Service` — stateless business logic. Lives in `services/` or `ui/views/services/` (UI-layer services).
- `*Panel` — Spring bean that assembles a Vaadin UI subtree (returns `Div`/component). Lives in `ui/views/components/`.
- `*Util` — static-only utility class (`@NoArgsConstructor(access = PRIVATE)`). Lives in `ui/views/utils/`.
- `*Binding` — prototype bean that manages a lifecycle (register/unregister listeners). Lives next to the service it supports (e.g. `ui/views/services/pagination/`).
- `*Overlay` — full-screen Vaadin overlay (extends `AbstractEntityOverlay` or `BaseOverlay`).
- `*Config` — Spring `@Configuration` class. Infrastructure-level configs live in `config/`. Feature-scoped factory configs stay next to the components they configure.

→ SPI interface naming (`*Port`, `*Hook`): @platform-commons/CLAUDE.md

### Package structure
- `config/` — app-level Spring configuration (`config/db/`, `config/ui/`, `config/seo/` for sub-domains)
- `services/auth/` — authentication (`AuthService` — login/logout + rate limiting, `AuthContextService` — current-user context)
- `services/i18n/` — `I18nKey` enum, `I18nService`, `I18nServiceImpl`, `LocaleProvider`, `InstantFormatter`
- `services/security/` — `AccessEvaluator` (role/ownership checks live in `user-spring-boot-starter` — see this module's own `README.md` "Responsibilities" section)
- `spi/` — thin, dedicated forwarders over a UI-shell resource, implementing the `UiLabelHook`/`SessionActorHook` SPIs that `marketplace-orchestrator`'s own `AuditDomainHookImpl`/`CurrentActorHookImpl`/`ActivityEnrichHookImpl` call through — see `.claude/rules/marketplace-orchestrator.md`'s "Forwarder SPI pattern" for the full table. `UiLabelHookImpl` wraps `I18nService` (`translateActorDeletedSuffix`, `labelFor(AdKind)`, `markDeleted(String)` — wraps a name in strikethrough markup, `noMediaPlaceholder()` — one interface, since all four wrap the same resource or produce presentation output); `SessionActorHookImpl` wraps `AuthContextService`. Three more forwarder SPIs are implemented directly by an existing class in its own natural package instead of a dedicated `spi/` wrapper, since each already owns the wrapped resource: `UiLocaleHookImpl` (`ui/views/services/`) implements `UiLocaleHook` over the UI-scoped `LocaleProvider`, the only locale class importing Vaadin; `SettingsPaginationService` (`ui/views/services/pagination/`) implements `SettingsChangeHook`; `AuthContextService` (`services/auth/`) implements `CurrentUserHook` against `SecurityContextHolder`. `AuditActivityEnrichHook`'s only real implementation, `ActivityEnrichHookImpl`, now lives in `marketplace-orchestrator` (not here) — see `.claude/rules/marketplace-orchestrator.md`.
- `ui/core/` — `Configurable<T,P>`, `Initialization<T>`, `UiComponentFactory<T>`, `PaginationDefaults`
- `ui/dto/` — `Identifiable` and other shared UI DTOs
- `ui/mappers/` — UI-form ↔ DTO mappers (`AdvertisementMapper`, `UserMapper`, `*FilterMapper`)
- `ui/query/` — query-bar infrastructure (`QueryBlock`, `QueryStatusBar`, `filter/`, `sort/`, `elements/`, `utils/`)
- `ui/views/components/` — reusable Vaadin UI components (incl. `audit/`, `attachment/`, `fields/` subpackages). `fields/` contains `QuillEditor` (rich-text web component wrapping Quill v2) and standard field wrappers.
- `ui/views/utils/` — pure static utilities only (`*Util` classes)
- `ui/views/services/` — UI-layer Spring services; `*Binding` beans live in the same subpackage as the service they support

All modules use `config` (not `configuration`) for Spring configuration packages.
