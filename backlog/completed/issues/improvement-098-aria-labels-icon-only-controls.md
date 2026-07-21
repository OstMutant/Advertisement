# improvement-098: Icon-only controls have no accessible names — 2 aria-labels in the whole UI

**Type:** improvement — accessibility (WCAG 4.1.2 name-role-value). Found via UX review + code
grep (2026-07-19).
**Module:** `marketplace-app` (all icon-only buttons across views/components)
**Priority:** medium-high (UX/a11y) — screen-reader and keyboard users currently get unnamed
buttons for every destructive and navigational action; mechanical fix
**When:** Batch L (UX quick pass) — see `backlog/BACKLOG.md` "Execution batches"

## Problem

A grep across `marketplace-app/src/main/java` finds **2** `aria-label`/`setAriaLabel` usages
total. improvement-037 (2026-07-15) fixed contrast but barely touched accessible names. Unnamed
icon-only controls include (verified against the 2026-07-19 e2e screenshots):

- Users grid row actions — edit (pencil) and **delete (trash)** buttons;
- pagination bar arrows (first/prev/next/last) in every list view;
- filter panel apply (funnel) and clear (eraser) buttons — also a discoverability problem, see
  improvement-055's polish list;
- chip-remove `×` buttons (category chips, `UserPickerField` actor chips);
- overlay/lightbox/notification close `×` buttons;
- `SortIcon` toggles on filter-row labels;
- gallery thumbnail delete `×` and the add-video `+` button.

## Suggested fix

One mechanical pass: every icon-only interactive element gets `setAriaLabel(i18n.get(...))` with
a localized key (extend `I18nKey` — keys like `ARIA_DELETE_USER`, `ARIA_PAGE_NEXT`,
`ARIA_REMOVE_CATEGORY`; follow the existing enum section conventions, see ADR-049 — one enum).
Where a control's meaning depends on its subject (delete *which* user), include the subject in
the label ("Delete user Seed User 45").

Verification: Playwright can assert `aria-label` presence cheaply — add one accessibility spec
that walks each view and asserts no `button:not([aria-label])` icon-only offenders remain
(selector-based approximation is fine; a full axe-core integration is out of scope here and can
be its own follow-up if wanted).

## Related

- `backlog/completed/issues/improvement-037-accessibility-contrast-and-aria.md` — the contrast
  half of this work; this issue finishes the aria half its title promised.
- `marketplace-app/DECISIONS.md` ADR-049 — keys go into the single consolidated `I18nKey` enum.

## Keyboard focus management for overlays (added 2026-07-19, edge-case review)

Beyond accessible *names* (this issue's core), the big edit overlays have a focus-*management*
gap that belongs to the same keyboard-accessibility sweep. `BaseOverlay extends Div` (not a
Vaadin `Dialog`) already wires Esc correctly (`onEsc()` respects the unsaved-changes guard), but:
- focus is **not moved into the overlay** on open (a grep for `.focus()`/`setAutofocus` across the
  overlay tree finds nothing) — keyboard focus stays on whatever was behind it; and
- there is **no focus trap** — Tab can walk out of the overlay onto the now-inert page behind it.

Add to this issue's sweep: on overlay open, move focus to the first field (or a sensible initial
control); trap Tab within the overlay while open; restore focus to the trigger on close. This is
WCAG 2.4.3 (Focus Order) / 2.1 (Keyboard), complementary to the 4.1.2 (Name) work above. The
lightbox's own keyboard gap (Esc + focus, a separate hand-rolled component) is tracked in
improvement-097's keyboard addendum, not here.
