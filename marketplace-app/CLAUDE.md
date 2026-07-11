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
- `UiComponentFactory<T>` (marketplace-app `ui.core`) extends `ComponentFactory<T>` (platform-commons) and adds `build(P params)` for UI prototype wiring.
- Use `UiComponentFactory<T>` for Configurable prototype UI beans (those that implement `Configurable` and need `.build(params)`).
- Use `ComponentFactory<T>` for optional singleton services/ports (e.g. `AdvertisementPort`, `AuditPort`) — starters declare `ComponentFactory<X>` beans, inject as `ComponentFactory<X>` even in UI classes.

**When NOT to use Configurable:**
- Component has distinct modes with different UI structure → use explicit named methods:
  `configureForView(Long id)`, `configureForEdit(Long id)`, `configureForCreate(String sessionId)`.
- Component needs only 1–2 simple setters → plain setters, no `Parameters`.
- Do NOT use positional-argument methods with 4+ parameters in any module — use `Parameters` instead.

---

## Overlay, View, and Query Layer Rules

Detailed cross-cutting rules for the Overlay pattern (OverlaySession, switchTo vs launchSession,
currentFormHandler reset, afterSave update), View pattern (init() structure, refresh() guard),
Query Layer (FilterMeta/SortMeta Fields.* constants), and Form Handler pattern (buildTabbedContent,
buildBinder) are documented in: @../.claude/rules.md

---

## I18n

`I18nService`, `InstantFormatter`, `LocaleProvider` live in `org.ost.marketplace.services.i18n`. Starters have no i18n infrastructure of their own — all UI i18n lives here.

Translation keys — single consolidated enum:
- `org.ost.marketplace.services.i18n.I18nKey` — all app keys (main app, audit, attachment). Has `forAction(ActionType)` static method.

**Rules:**
- Never use raw `MessageSource` directly in UI components — use `I18nService.get(I18nKey)`.
- Never use `msg(String key, String fallback)` — missing keys must fail fast, not silently fall back.
- Never build keys dynamically: `"changes.field." + fieldName` — use typed enum with explicit mapping.
- `I18nParams` interface: implement `getI18nService()` to get `getValue(I18nKey, ...)` and `formatAction(ActionType)` as defaults.

---

## Security: @PreAuthorize and Vaadin

`@EnableMethodSecurity` is active. **Never put `@PreAuthorize` at class level on service beans.** Vaadin initializes view beans on the first HTTP request before the user authenticates; a class-level annotation causes an `AuthorizationDeniedException` during view wiring, preventing any view from loading.

- Method-level `@PreAuthorize` is fine for future REST controller endpoints.
- Services (`AdvertisementService`, `ActivityService`, etc.) intentionally have no `@PreAuthorize`.
- `/health` is intentionally public (load balancer probe).
- `SecurityConfig` uses `anyRequest().permitAll()` at the URL layer — deny-by-default does not
  apply to this app's single-route Vaadin SPA model (see `DECISIONS.md` ADR-025). Any future
  non-Vaadin REST controller must add its own explicit `requestMatchers(...)` rule ahead of the
  catch-all.
- Login (`AuthService.login()`) and registration (`UserPort.register()` → `UserService.register()`)
  are rate-limited via an in-memory Caffeine cache (5 attempts / 15 min), counting only real
  failures — never successes (see `DECISIONS.md` ADR-026).
- `application-prod.yml` sets `server.forward-headers-strategy: framework` — required for
  `request.getRemoteAddr()` to resolve the real client IP behind Render's proxy, not Render's
  own internal edge address (see `DECISIONS.md` ADR-027).

---

## Naming Conventions

### Class suffixes
- `*Projection` — SQL query object that owns its SQL (text block) and `mapRow()`. Lives in `repository/*`.
- `*Service` — stateless business logic. Lives in `services/` or `ui/views/services/` (UI-layer services).
- `*Panel` — Spring bean that assembles a Vaadin UI subtree (returns `Div`/component). Lives in `ui/views/components/`.
- `*Util` — static-only utility class (`@NoArgsConstructor(access = PRIVATE)`). Lives in `ui/views/utils/`.
- `*Binding` — prototype bean that manages a lifecycle (register/unregister listeners). Lives next to the service it supports (e.g. `ui/views/services/pagination/`).
- `*Overlay` — full-screen Vaadin overlay (extends `AbstractEntityOverlay` or `BaseOverlay`).
- `*Config` — Spring `@Configuration` class. Infrastructure-level configs live in `config/`. Feature-scoped factory configs stay next to the components they configure.

→ SPI interface naming (`*Port`, `*Hook`): @platform-commons/CLAUDE.md

### Package structure
- `config/` — app-level Spring configuration (`config/db/`, `config/ui/` for sub-domains)
- `services/audit/` — audit snapshot DTOs, diff engine, `@AuditedField` annotation
- `services/auth/` — authentication context (`AuthContextService`)
- `services/i18n/` — `I18nKey` enum, `I18nService`, `I18nServiceImpl`, `LocaleProvider`, `InstantFormatter`
- `services/security/` — security beans (`AccessEvaluator`, `RoleChecker`, `OwnershipChecker`, etc.)
- `repository/activity/` — activity feed SQL repositories + projections
- `ui/core/` — `Configurable<T,P>`, `Initialization<T>`, `UiComponentFactory<T>`, `PaginationDefaults`
- `ui/dto/` — `Identifiable` and other shared UI DTOs
- `ui/views/components/` — reusable Vaadin UI components (incl. `audit/`, `attachment/`, `fields/` subpackages). `fields/` contains `QuillEditor` (rich-text web component wrapping Quill v2) and standard field wrappers.
- `ui/views/utils/` — pure static utilities only (`*Util` classes)
- `ui/views/services/` — UI-layer Spring services; `*Binding` beans live in the same subpackage as the service they support

All modules use `config` (not `configuration`) for Spring configuration packages.
