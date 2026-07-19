# improvement-110: In-progress edits are silently lost on top-nav tab switch and browser unload — no unsaved-changes guard

**Type:** bug — data-loss UX. Found via edge-case review (2026-07-19).
**Module:** `marketplace-app` (`ui/.../main/MainView.java`, overlay/`EntityOverlaySupport`
unsaved-changes plumbing)
**Priority:** medium — real loss of user work with no warning, via the most ordinary action
(clicking another nav tab); the overlay already knows it has unsaved changes, the guard just
isn't consulted on navigation
**When:** Batch L (UX quick pass) — see `backlog/BACKLOG.md` "Execution batches"

## Problem

`MainView` switches top-nav tabs by pure visibility toggling, with no guard:

```java
tabs.addSelectedChangeListener(_ -> {
    tabsToPages.values().forEach(page -> page.setVisible(false));
    tabsToPages.get(tabs.getSelectedTab()).setVisible(true);
});
```

The overlay tracks `hasUnsavedChanges()` and prompts on its own X/cancel button
(`EntityOverlaySupport.handleCancel`), but a tab switch bypasses that entirely: an open edit
overlay inside e.g. `advertisementsView` just gets `setVisible(false)`, discarding in-progress
edits with no prompt.

Reproduce: open an advertisement edit overlay → type changes → click the "Users" tab → work is
gone, no warning.

Same class, second surface: **browser refresh / tab close** has no `beforeunload` handler either,
so a reload mid-edit silently loses the same work.

## Suggested fix

- **Tab switch:** in the `addSelectedChangeListener`, before hiding the current page, ask the
  active view whether it has an open overlay with unsaved changes (expose a
  `boolean hasBlockingUnsavedChanges()` on the view / a small registry). If so, show the same
  discard-confirm dialog the overlay already uses; on cancel, veto the switch by reverting
  `tabs.setSelectedTab(previous)` (guard against the re-entrant selected-change event the revert
  itself fires). Reuse `EntityOverlaySupport`'s existing confirm copy — don't invent a second one.
- **Browser unload:** register a `beforeunload` handler via `Page.executeJs` while an overlay has
  unsaved changes, and remove it when clean, so the browser shows its native "leave site?" prompt.
  (Vaadin can't fully control that dialog's text — the native prompt is the expected behavior.)

## Verification

Playwright: open edit overlay, make a change, click another tab → assert the discard-confirm
appears and that cancelling keeps you on the edit; confirm switches and discards. (beforeunload is
awkward to assert in Playwright — cover the tab-switch path automatically, verify unload manually.)

## Related

- `marketplace-app/CLAUDE.md` / `.claude/rules.md` "Overlay Pattern" — `hasUnsavedChanges()` /
  `discardChanges()` already exist; this issue wires them into navigation, no new state model.
- `backlog/issues/improvement-099-confirm-dialogs-action-verbs-danger-styling.md` — the
  discard-confirm copy this reuses; land 099's verb-button change first so the reused dialog is
  already correct.
