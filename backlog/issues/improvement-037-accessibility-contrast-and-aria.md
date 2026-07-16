# improvement-037: Accessibility gaps — verified WCAG AA contrast failure, plus a legal angle

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

## Related

- `backlog/process-improvements.md` Part 3, item 18 — source item, now superseded by this issue.
- `backlog/issues/improvement-039-dark-mode-lumo-tokens.md` — shares the same CSS-tokenization
  prerequisite (hardcoded hex → Lumo custom properties); consider sequencing them together since
  both touch the same theme files.
