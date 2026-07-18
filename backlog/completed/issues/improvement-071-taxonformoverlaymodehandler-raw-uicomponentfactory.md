# improvement-071: `TaxonFormOverlayModeHandler` uses a raw `UiComponentFactory<OverlayFormBinder>` — the other 3 form handlers already don't

**Type:** improvement — type-safety cleanup, proven pattern already used elsewhere. Found via
direct code review, verified against current source (2026-07-16).
**Module:** `marketplace-app` (`ui/views/main/tabs/referencedata/overlay/modes/TaxonFormOverlayModeHandler.java`).
**Priority:** medium — cheap, safe, low-risk fix; a real inconsistency but not a live bug (the
current `@SuppressWarnings` correctly suppresses what would otherwise be compiler warnings, not a
correctness gap).
**When:** independent, no blockers.

## Problem

`TaxonFormOverlayModeHandler`:
```java
@SuppressWarnings("rawtypes")
private final UiComponentFactory<OverlayFormBinder>                    formBinderFactory;
...
@SuppressWarnings("unchecked")
private void buildBinder(TaxonEditDto dto) {
    binder = formBinderFactory.build(...)
```
is the **only** one of the four form handlers using `OverlayFormBinder` that declares its factory
field with a raw type. Confirmed via direct comparison — the other three already parameterize the
field properly, with no `@SuppressWarnings` at all:
```java
// SettingsFormModeHandler
private final UiComponentFactory<OverlayFormBinder<SettingsEditDto>> formBinderFactory;
// AdvertisementFormOverlayModeHandler
private final UiComponentFactory<OverlayFormBinder<AdvertisementEditDto>>  formBinderFactory;
// UserFormOverlayModeHandler
private final UiComponentFactory<OverlayFormBinder<UserEditDto>> formBinderFactory;
```
This works today (Java allows assigning a raw-typed bean into a parameterized field reference via
Spring's reflection-based injection, which doesn't go through a compile-time-checked assignment
expression the way direct Java code would) — the underlying `@Bean
overlayFormBinderFactory(ObjectProvider<OverlayFormBinder> p)` in `ComponentFactoryConfig` is
itself raw for a structural reason (see improvement-072, out of scope for this narrower fix): one
shared prototype bean definition serves all four `EditDto` type parameters, since
`OverlayFormBinder<T>`'s `T` is only resolved at `configure(Parameters<T>)` call time, not at
bean-instantiation time. That constraint doesn't prevent the **field injection site** in each
handler from being properly parameterized, though — three of four already do it.

## Suggested fix

Change `TaxonFormOverlayModeHandler`'s field to `UiComponentFactory<OverlayFormBinder<TaxonEditDto>>`
(matching the other three handlers exactly) and remove both `@SuppressWarnings` annotations
(`"rawtypes"` on the field, `"unchecked"` on `buildBinder()`) — the proven pattern already
established by `SettingsFormModeHandler`/`AdvertisementFormOverlayModeHandler`/
`UserFormOverlayModeHandler` needs no such suppressions once the field itself is properly typed.

## Related

- `marketplace-app/src/main/java/org/ost/marketplace/ui/views/main/tabs/advertisements/overlay/modes/AdvertisementFormOverlayModeHandler.java`,
  `.../header/settings/SettingsFormModeHandler.java`,
  `.../users/overlay/modes/UserFormOverlayModeHandler.java` — the three siblings already using the
  pattern this issue proposes matching.
- `backlog/issues/improvement-072-uicomponentfactory-generics-design-debt.md` — the broader,
  design-level generics questions (`UiComponentFactory<T>`'s dual role, `AuditReadService`'s raw
  hook dispatch, `AuditDomainHookImpl.castIfKnown`'s missing type token) this narrower fix doesn't
  attempt to resolve.
