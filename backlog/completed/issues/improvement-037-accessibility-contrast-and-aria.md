# improvement-037: Accessibility gaps — verified WCAG AA contrast failure, plus a legal angle — ✅ DONE (2026-07-16)

**Type:** improvement — accessibility/compliance. Migrated from `backlog/process-improvements.md`
Part 3, item 18.
**Module:** `marketplace-app` theme CSS
**Priority:** medium-high — this is not purely cosmetic: the European Accessibility Act applies to
e-commerce since June 2025, and the marketplace's public-facing catalog is exactly the kind of
service this covers if/when it serves EU users
**When:** independent, no blockers — the concrete contrast fix is hours of work; do not defer this
one under "cosmetic" batching the way improvement-008/010/014 are deferred

## Problem

Verified concretely against the actual theme CSS (not assumed):
- Header text `#94a3b8` on white background ≈ 2.5:1 contrast ratio — **fails** WCAG AA (4.5:1
  required for body text).
- Card description text `#64748b` ≈ 4.76:1 — passes, but only barely.
- No verification done yet of focus-visible states across custom components, or ARIA labels on
  custom chips (category/city chips) and the attachment lightbox.

## Suggested fix

- Fix the header text contrast — either darken `#94a3b8` to meet 4.5:1, or use a Lumo token with a
  guaranteed-compliant shade (this also sets up improvement-039/dark-mode's token migration, if
  that lands later).
- Add visible focus states across custom interactive elements (chips, lightbox controls,
  pagination buttons) — verify against keyboard-only navigation, not just visual inspection.
- Add ARIA labels to custom chips (category/city) and the attachment lightbox controls (prev/next/
  close) — these are custom `Div`/`Button` compositions, not native form controls, so they need
  explicit labels.
- The existing 'N' keyboard shortcut (with its shadow-DOM guard, referenced in the source audit)
  is called out as "a good foundation" to build the rest of keyboard-accessibility on top of.

## Resolution (2026-07-16)

Done in two passes, both fully verified (full `deploy.sh` + `bash scripts/playwright.sh e2e --full
--ux`, 48/48 both times):

1. **Contrast fix, via full tokenization** (bigger than originally scoped — done together with
   improvement-039's prerequisite rather than a narrow 2-color inline fix, per this issue's own
   suggestion). All 49 unique hex colors / ~180 occurrences across the theme's 21 CSS files were
   named as `--app-*` custom properties in a new `:root` block in `styles.css`. The failing
   `#94a3b8` (~2.5:1) was merged into the same token as the already-compliant `#64748b` (~4.76:1)
   — `--app-text-muted` — the only value intentionally changed; every other token preserves its
   exact prior color. Full rationale: `marketplace-app/DECISIONS.md` ADR-038.
2. **Focus states + ARIA labels:**
   - `UiIconButton.configure()` now sets `aria-label` alongside `title` — fixes every icon-only
     button app-wide in one shared-component edit (lightbox prev/next/close, pagination nav,
     attachment-thumbnail delete, `UserPickerField` clear/open), not per-call-site.
   - Category chip list (`AdvertisementViewOverlayModeHandler`) got `role="list"` on the wrapper
     and `role="listitem"` on each chip, plus `aria-label` on the wrapper — correct screen-reader
     grouping for a custom `Div`/`Span` composition. "City chips" don't exist in the codebase yet
     (still a private-roadmap feature, F-02) — nothing to fix there.
   - `.primary-button/.tertiary-button/.icon-button:focus-visible` added to `styles.css`, matching
     the pre-existing `.advertisement-card:focus-visible` treatment — no explicit focus style
     existed for the `Ui*Button` family before this.
   - The 'N' keyboard shortcut's shadow-DOM guard (`AdvertisementsView.java`) was reviewed, not
     changed — confirmed it already correctly excludes shadow-DOM input/textarea/contentEditable
     targets before firing.

## Related

- `backlog/process-improvements.md` Part 3, item 18 — source item, now superseded by this issue.
- [improvement-039-dark-mode-lumo-tokens](../../issues/improvement-039-dark-mode-lumo-tokens.md) —
  its own prerequisite (color tokenization) shipped here; the dark-mode toggle itself is still open.
- `marketplace-app/DECISIONS.md` ADR-038 — full tokenization rationale and mapping.
