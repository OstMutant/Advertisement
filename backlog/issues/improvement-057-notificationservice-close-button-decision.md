# improvement-057: `NotificationService` close-button — decide whether to convert to `UiIconButton` wrapper

**Type:** design decision — deliberately excluded from improvement-026's mechanical raw-`Button`
conversion, needs an explicit yes/no.
**Module:** `marketplace-app` (`ui/views/services/NotificationService.java`).
**Priority:** low — no UX bug, purely a consistency question.
**When:** independent, no blockers.

## Problem

`NotificationService.createLayout()` (`NotificationService.java:82-83`) builds its notification
close button as a raw `Button`:
```java
Button closeButton = new Button(new Icon("lumo", "cross"), _ -> notification.close());
closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
```
Two things distinguish this from every other raw-`Button` spot
[improvement-026](../completed/issues/improvement-026-duplicate-raw-buttons-instead-of-ui-button-wrappers.md) converted:

1. `NotificationService` is a plain `@Service`, not a Vaadin `@SpringComponent` UI bean —
   converting it means a non-UI-scoped service reaching into `UiComponentFactory<UiIconButton>`
   machinery, a different kind of dependency than every other consumer (all of which are
   themselves Vaadin components).
2. The icon is a raw Lumo font glyph (`new Icon("lumo", "cross")`), not `VaadinIcon.*` — every
   `Ui*Button` consumer in the app uses `VaadinIcon`. Converting would visibly change the rendered
   icon, not just wrap existing behavior — not a pure refactor.

## Options (undecided — pick one)

1. **Convert anyway:** inject `UiComponentFactory<UiIconButton>` into `NotificationService`,
   switch icon to `VaadinIcon.CLOSE_SMALL` (or similar) — full consistency, accepts the small
   visual change and the service-reaching-into-UI-factory dependency.
2. **Leave as-is permanently:** document in `marketplace-app/CLAUDE.md`'s naming/pattern rules as
   an accepted exception (plain `@Service` classes are not required to use `Ui*Button` wrappers).
3. **Partial:** keep the raw Lumo `Icon`/glyph, but still route the button itself through a
   variant-flexible wrapper if one becomes available — splits the two concerns (button consistency
   vs. icon-set consistency) instead of forcing both at once.

## Related

- [improvement-026](../completed/issues/improvement-026-duplicate-raw-buttons-instead-of-ui-button-wrappers.md) —
  flagged this exact spot as "decide explicitly whether to include it or leave it; do not silently
  skip or silently convert."
