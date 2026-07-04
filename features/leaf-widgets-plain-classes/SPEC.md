# Feature: Convert Stateless Leaf UI Widgets from Prototype Beans to Plain Java Classes

## Goal

Remove the `@SpringComponent @Scope("prototype")` + `Configurable<T, Parameters>` +
`Initialization` + `UiComponentFactory` ceremony from leaf UI widgets whose only dependency is
`I18nService` for label resolution. Convert them to plain Java classes with constructor
arguments; i18n resolution moves to the call site (ModeHandlers/Views, which stay Spring beans
and already inject `I18nService`).

## Problem

These widgets are container-managed for no reason — no real dependency graph, no state, no
lifecycle needs. Consequences:

- Every parent ModeHandler/View constructor-injects either a `UiComponentFactory<UiX>` per
  widget or the prototype widget itself. `UserFormOverlayModeHandler` has 13 injected fields
  for a 2-field form; 5 of them are leaf widgets or their factories.
- ~50 lines of boilerplate per widget (`@Value @Builder Parameters` record, `Configurable`,
  `Initialization`, `I18nParams`, `@PostConstruct init()`) for what a plain constructor does
  in ~15.
- Container overhead for cheap, short-lived objects.
- `UiComboBox<Role>` as a prototype bean is also a type-safety wart — Spring injects the raw
  type; a plain generic class fixes it.

## Files to convert (marketplace-app, `ui/views/components/`)

| File | Call sites (files) |
|---|---|
| `buttons/UiPrimaryButton.java` | 14 |
| `buttons/UiTertiaryButton.java` | 8 |
| `buttons/UiIconButton.java` | 9 |
| `buttons/action/DeleteActionButton.java` | 3 |
| `buttons/action/EditActionButton.java` | 3 |
| `fields/UiTextField.java` | 5 |
| `fields/UiTextArea.java` | 1 |
| `fields/UiEmailField.java` | 3 |
| `fields/UiPasswordField.java` | 3 |
| `fields/UiComboBox.java` | 1 |
| `fields/UiLabeledField.java` | 2 |
| `EmptyStateView.java` | 2 |
| `overlay/fields/OverlayBreadcrumbBackButton.java` | 3 |
| `dialogs/ConfirmActionDialog.java` | 5 |

Estimated blast radius: ~30-40 files.

## Target pattern

From:

```java
@SpringComponent @Scope("prototype")
public class UiTextField extends TextField
        implements Configurable<UiTextField, Parameters>, I18nParams, Initialization<UiTextField> {
    @Value @Builder public static class Parameters { @NonNull I18nKey labelKey; ... }
    @PostConstruct public UiTextField init() { setWidthFull(); addClassName("text-field"); return this; }
    public UiTextField configure(Parameters p) { setLabel(getValue(p.getLabelKey())); ... }
}
```

To:

```java
public class UiTextField extends TextField {
    public UiTextField(String label, String placeholder, int maxLength, boolean required, String testId) {
        setWidthFull();
        addClassName("text-field");
        setLabel(label);
        setPlaceholder(placeholder);
        if (maxLength > 0) setMaxLength(maxLength);
        setRequired(required);
        getElement().setAttribute("data-testid", testId);
    }
}
```

Call site (ModeHandler/View — stays a Spring bean with `I18nService`):

```java
new UiTextField(i18n.get(FIELD_NAME_LABEL), i18n.get(FIELD_NAME_PLACEHOLDER), 255, true,
                FIELD_NAME_LABEL.toTestId())
```

### data-testid decision (FIXED — do not change mid-refactor)

`data-testid` is passed as an **explicit `String` constructor parameter**; the call site
derives it via `I18nKey.toTestId()`. Rationale: 61 `data-testid` selector usages in the
Playwright e2e suite depend on these attributes — values must be byte-identical post-refactor.
An explicit parameter keeps the widget free of `I18nKey`/`I18nService` while making the
attribute value visible at the call site.

## Do NOT touch (real dependencies or state — stay Spring beans)

- `OverlayFormBinder` — snapshot-diff / restore logic
- `AuditActivityPanel`, `AuditActivityListRenderer`, `AuditActivityRowRenderer`,
  `AuditTimelineListRenderer`, `AuditTimelineRowRenderer` — `AuditPort` dependency
- `AttachmentGallery`, `AttachmentGalleryService`, `CardMediaLightbox` — `AttachmentPort`
  dependency; also tracked separately under improvement-011, do not conflate
- `PaginationBar` — holds state (current page, listeners, `SettingsPaginationBinding`
  registration); verified, stays a bean
- `DialogLayout`, `OverlayLayout` — verify individually: if only i18n, convert same way;
  if state/logic, leave as-is
- `BaseActionButton`, `BaseDialog`, `AbstractEntityOverlay`, `AbstractFormOverlayModeHandler`,
  `AbstractViewOverlayModeHandler`, `OverlayModeHandler`, `EntityOverlaySupport` — already
  plain or legitimate SPI, out of scope

## Steps

Work in three chunks; `mvn` build after each chunk, full Playwright e2e once at the end.

1. **Chunk 1 — buttons:** `UiPrimaryButton`, `UiTertiaryButton`, `UiIconButton`,
   `DeleteActionButton`, `EditActionButton`, `OverlayBreadcrumbBackButton`.
2. **Chunk 2 — fields:** `UiTextField`, `UiTextArea`, `UiEmailField`, `UiPasswordField`,
   `UiComboBox`, `UiLabeledField`.
3. **Chunk 3 — misc:** `EmptyStateView`, `ConfirmActionDialog` (+ `DialogLayout` /
   `OverlayLayout` if they qualify after individual verification).

For every chunk:
- Convert the widget per the target pattern.
- Update every call site: `xFactory.build(Parameters.builder()...)` → `new UiX(...)`, and
  direct prototype injections (`private final UiTextField titleField` in
  `AdvertisementFormOverlayModeHandler:86` etc.) → field created once in the same lifecycle
  spot the injection covered (handler construction / `activate()`), NOT per `switchTo()`.
- Remove now-unused `UiComponentFactory<UiX>` constructor injections from ModeHandlers/Views.
- Delete the corresponding `UiComponentFactory<UiX>` bean declarations from
  `MarketplaceUiConfiguration` (and `ComponentFactoryConfig` if present there).

Finalization:
4. Check whether `I18nParams` and `Initialization` interfaces have remaining users; delete if
   dead.
5. Run full build + full Playwright e2e — `data-testid` attributes must be identical
   post-refactor or tests break silently.
6. Update `marketplace-app/CLAUDE.md`: add to "When NOT to use Configurable" — *stateless leaf
   widget (only i18n labels/testid) → plain class with constructor args*; adjust the
   `UiComponentFactory` usage rule accordingly.
7. Add a `marketplace-app/DECISIONS.md` entry: prototype-scoped beans for stateless leaf
   widgets removed; `Configurable`/DI reserved for components with real service dependencies.

## Acceptance criteria

- None of the 14 listed widgets carry Spring annotations or implement
  `Configurable`/`Initialization`/`I18nParams`.
- No `UiComponentFactory` bean declarations remain for converted widgets.
- `UserFormOverlayModeHandler` constructor dependency count drops from 13 to ≤9.
- Full e2e suite (46 tests) passes without modifying any spec selectors.
- CLAUDE.md + DECISIONS.md updated in the same PR.
