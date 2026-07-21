# improvement-097: Modal surfaces render without a visible scrim; lightbox close button is detached from the lightbox

**Type:** improvement — UX (modality perception). Found via UX review over the 2026-07-19 e2e
screenshot set.
**Module:** `marketplace-app` (`dialogs.css`, `card-lightbox.css`, `CardLightboxViewer`/
`AttachmentLightbox`, theme tokens)
**Priority:** medium-high (UX) — every dialog and lightbox in the app is affected; cheap CSS-level
fix
**When:** Batch L (UX quick pass) — see `backlog/BACKLOG.md` "Execution batches"

## Problem

1. **No visible backdrop dimming on any modal surface.** Login dialog, logout/delete confirm
   dialogs, and the card lightbox all float over a fully bright, undimmed page — the modality
   boundary doesn't read, and the eye has no cue about what is interactive. `card-lightbox.css`
   references `var(--app-overlay-backdrop)` (line ~99) but the rendered scenes show no dimming —
   either the token is (near-)transparent or the backdrop element doesn't cover the viewport;
   Vaadin `Dialog`s' modality curtain is left at its default (fully transparent).
2. **Lightbox close button is detached from the lightbox.** In list-view lightbox screenshots the
   close `×` sits at the top-right of the *viewport*, overlapping the header/language-selector
   area, visually unrelated to the image frame it closes. Convention is inside (or anchored to)
   the lightbox frame's corner.

## Suggested fix

- Define one semi-opaque scrim token (e.g. `--app-overlay-backdrop: rgba(0,0,0,0.5)`) and apply
  it consistently: `vaadin-dialog-overlay::part(backdrop)` for all Vaadin dialogs + the lightbox
  backdrop element. Verify the token actually paints (screenshot diff before/after).
- Move the lightbox close button inside the lightbox frame (top-right corner of the media area,
  same `CLOSE_SMALL` icon convention as everywhere else), with `aria-label` (coordinate with
  improvement-098). Esc-to-close should keep working.
- One Playwright `--ux` screenshot pass over: login dialog, logout confirm, delete confirm,
  image lightbox, video lightbox.

## Related

- `backlog/issues/improvement-081-lightbox-embedurl-and-iframe-attrs-duplication.md` — same two
  lightbox classes; if Batch F runs first, rebase this on its extraction (or vice versa).

## Keyboard accessibility for the lightbox (added 2026-07-19, edge-case review)

The lightbox is a hand-rolled component (`AttachmentLightbox extends Div`,
`CardLightboxViewer extends HorizontalLayout`), not a Vaadin `Dialog`, so it gets **no keyboard
support for free** and currently wires none: no Esc-to-close handler, and focus is neither moved
into the lightbox on open nor restored on close. A keyboard or screen-reader user who opens the
lightbox is trapped — Esc does nothing and the close `×` isn't reliably reachable by Tab.
Mouse users can dismiss via backdrop-click or the `×`; keyboard users cannot.

Fold into this issue's lightbox rework (it already moves the close button): add an Esc
`Shortcuts.addShortcutListener` scoped to the lightbox (removed on close, same lifecycle as
`BaseOverlay`'s Esc handling — reuse that pattern), move focus onto the close button (or the
lightbox root) on open, and restore focus to the trigger element on close. The overlay analog
(focus-into / focus-trap for the big edit overlays) is tracked in improvement-098's keyboard
addendum, not here.
