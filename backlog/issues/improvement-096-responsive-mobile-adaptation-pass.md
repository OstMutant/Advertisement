# improvement-096: Responsive/mobile adaptation pass — the UI is desktop-only today

**Type:** improvement — UX foundation gap. Found via UX review over the 2026-07-19 e2e screenshot
set + theme CSS audit.
**Module:** `marketplace-app` (theme CSS, view layouts), `playwright` (mobile viewport coverage)
**Priority:** high (UX) — a marketplace's buyer traffic is predominantly mobile; the app currently
has no meaningful adaptation below desktop widths
**When:** its own multi-phase program (like improvement-025) — too large to ride in another batch;
schedule before any public launch

## Problem

Across all 26 theme CSS files there are exactly **2 `@media` queries** (one in
`advertisement-overlay.css`, one in `main-view.css`). Everything else — card list, query/filter
block, header bar, users grid, taxon view, gallery, timeline feed, pagination bar — renders a
fixed desktop layout at any viewport. Playwright runs a desktop viewport only, so mobile
regressions are invisible to the test suite by construction.

Concretely at phone widths (from layout structure, not speculation): the header's
signed-in-email + Settings + Log Out row overflows; the expanded filter panel's 4-input
date+time rows (`Created from [date][time] to [date][time]`) cannot fit; the users grid has 7
columns; overlay forms assume a wide two-column-ish content width; gallery thumbnails are
fixed-size.

## Suggested fix

Phased program, each phase e2e-verified (add a mobile-viewport Playwright project first so every
later phase has regression coverage):

1. **Phase 0 — tooling:** add a `Pixel/iPhone`-class viewport project to `playwright.config.js`
   running specs 01–02 (auth + browse) as the mobile smoke set; screenshots with `--ux`.
2. **Phase 1 — browse path (buyer-critical):** header bar collapse (menu or wrap), card list
   single-column, filter panel stacking, pagination bar compaction.
3. **Phase 2 — overlays:** advertisement view/edit overlay single-column stacking, gallery grid
   wrap, lightbox fit-to-viewport.
4. **Phase 3 — admin surfaces (lower priority, admins are desktop-first):** users grid column
   priority/collapse, timeline feed, reference data view.

Use container-relative sizing and the existing Lumo breakpoint conventions; record the chosen
breakpoint set in `marketplace-app/DECISIONS.md`.

## Related

- `backlog/issues/improvement-055-ui-vaadin-template-consistency-audit.md` — consistency polish
  items collected there should be applied in whatever layout survives this pass, not before.
- `backlog/issues/improvement-063-playwright-stability-guard-async-init-components.md` — mobile
  viewport runs will stress async-init components harder; do 063's ready-signals first if flakes
  appear in Phase 0.
