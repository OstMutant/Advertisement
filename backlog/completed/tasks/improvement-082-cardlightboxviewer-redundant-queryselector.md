# improvement-082: CardLightboxViewer — remove redundant querySelector JS, use held element references directly

**Type:** improvement + latent bug fix — dead/redundant code that also carries a real (if unlikely)
correctness risk.
**Module:** `marketplace-app` (`ui/views/components/attachment/CardLightboxViewer.java`).
**Priority:** medium — confirmed redundant code plus a genuine (if low-probability) cross-instance
bug; cheap, safe fix.
**When:** anytime — independent of the other items in this batch.

## Problem

`CardLightboxViewer.update()` (`.java:74-107`) uses
`getUI().ifPresent(ui -> ui.getPage().executeJs("document.querySelector('.card-lightbox__...')..."))`
to manipulate the iframe/video elements — three separate times — despite the class already
holding direct field references (`private final IFrame iframe` at `.java:28`, `private final
Element videoEl` at `.java:29`). Confirmed directly: `iframe.getElement().setAttribute("src", ...)`
is called on one line, immediately followed by a redundant `executeJs` querySelector-and-set on
the very next lines targeting the same element by CSS class. Same pattern repeated for `videoEl`.

**Latent bug:** `document.querySelector` matches the *first* element with that class in the whole
page — if two `CardLightboxViewer` instances were ever open at once (however unlikely today),
they would cross-control each other's iframe/video elements via these `querySelector` calls, since
neither call is scoped to the specific instance.

## Suggested fix

Replace the `document.querySelector`-based JS calls with direct calls on the held references
(`iframe.getElement().executeJs(...)` / `videoEl.executeJs(...)` or `.setAttribute(...)` as
appropriate — the direct `setAttribute` calls already present in the method appear to make the
`executeJs` calls entirely redundant, not just redundant-but-different; verify each removed call
has no distinct effect before deleting).

## Related

- Filed as part of a verified 8-item Vaadin UI refactor batch (2026-07-17); see improvement-076
  through improvement-081 and improvement-083 for the rest.
