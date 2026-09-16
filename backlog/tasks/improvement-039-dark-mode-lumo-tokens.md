# improvement-039: Dark mode — step 1 (tokenization) done via improvement-037, step 2 (actual dark palette + toggle) still open

**Type:** improvement — UX/theming. Migrated from `backlog/process-improvements.md` Part 3,
item 20.
**Module:** `marketplace-app` theme CSS
**Priority:** ⚪ under reconsideration — deprioritized to the very bottom of the backlog
2026-09-16, per explicit user request, after a first real implementation attempt failed. **Before
picking this up again: decide whether this feature is actually wanted at all**, not just how to
build it — the first attempt showed this needs meaningfully more effort than it looked like
up front, and that cost/value trade-off hasn't been reassessed since.
**When:** independent, no blockers, but see the Priority line above — not a quick pickup.

## Status update (2026-09-16) — first implementation attempt, failed

An `improvement-188` sub-task ("Task B") worked through the full design and a real implementation
attempt for this issue's own remaining scope (dark palette + toggle) — moved here in full since it
*is* this issue's scope, not a separate one. Real branch **`improvement-039`** (created 2026-09-16,
one commit, not merged) holds the actual attempted code: a `theme_preference` column on
`user_preferences`, `ThemePreference` enum + `UserPreferencesPort`/`Service`/`Repository` methods,
a `ThemeSelectorComponent` in the header mirroring `LocaleSelectorComponent`, and a
`ThemeBootstrapListener` for flash-prevention. The backend layer passes its own unit (165) and
integration (237) tests. **The UI layer does not work** — confirmed directly via manual Playwright
testing by the user: neither saving nor switching the theme actually takes effect in the running
app. Root cause not chased down before this was shelved — the background implementation run hit a
session rate limit mid-way through its own verification pass (Sonar/Playwright/ADR steps never
completed), so the broken state was never debugged, just committed as-is for future reference.
**Do not assume the design below is proven correct** — it compiled and passed backend tests, but
the one thing that actually matters for this feature (does the toggle work in the browser) is
confirmed broken.

## Problem

The app is light-theme-only. Component CSS used to use raw hex values throughout instead of named
custom properties, which would have made a dark palette non-trivial — swapping colors in place
across 21 files with no shared source isn't a real option.

## Status update (2026-07-16)

**Step 1 (tokenization) is done** — shipped as part of
[improvement-037](../completed/tasks/improvement-037-accessibility-contrast-and-aria.md)'s WCAG
contrast fix, since both issues needed the exact same prerequisite and the source plan recommended
doing both in one pass. Every color in the theme is now a named `--app-*` custom property in
`styles.css`'s `:root` block (full mapping: `marketplace-app/DECISIONS.md` ADR-038). This issue's
own scope is now narrower — only step 2 remains.

## Suggested fix (remaining scope, per the failed attempt's own design)

1. Define a dark palette — a second value set for every existing `--app-*` token name via CSS
   `light-dark(light-value, dark-value)` pairs declared at the token's own definition (not a
   separate `[data-theme="dark"]` override block) plus a `:root { color-scheme: light dark; }`
   baseline. Dark values need real WCAG AA contrast checking, the same discipline
   `improvement-037` used for `--app-text-muted` — not picked casually.
2. Apply/persist the preference via Vaadin 25's real `Page.setColorScheme(ColorScheme.Value)` API
   (verified via `javap` against the actual jar — not `getThemeList().add("dark")`, which the
   Vaadin docs confirm does nothing under Aura and only paints `<body>`), a dedicated
   `theme_preference` column on `user_preferences` (mirrors the `locale` column, not the audited
   `settings` JSONB blob — deliberately no audit trail, matching `locale`'s own existing gap), and
   a header control mirroring `LocaleSelectorComponent` (immediate apply, no Save step).
3. Flash-of-wrong-theme prevention via an `IndexHtmlRequestListener` mutating the bootstrap HTML
   for an explicit `LIGHT`/`DARK` choice (a `SYSTEM` preference needs no server-side handling — the
   CSS baseline resolves it synchronously against the OS preference).
4. **What's actually broken and needs real debugging before any of the above can be trusted:** the
   UI wiring on the `improvement-039` branch (`ThemeSelectorComponent`, `ThemeBootstrapListener`,
   `HeaderBar` integration) — neither persistence nor live switching works in the running app per
   direct manual testing. Start by reproducing the failure with the app running locally and
   checking the browser console/network tab and server logs before touching the design above.

## Related

- `backlog/process-improvements.md` Part 3, item 20 — source item, now superseded by this issue.
- [improvement-037-accessibility-contrast-and-aria](../completed/tasks/improvement-037-accessibility-contrast-and-aria.md) —
  shipped this issue's own tokenization prerequisite as part of its own WCAG fix.
- `marketplace-app/DECISIONS.md` ADR-038 — the token infrastructure this issue now builds on.
- [improvement-188](improvement-188-theme-modernization-aura-modern-css.md) — this issue's scope
  used to be tracked there as "Task B"; moved back here in full 2026-09-16 since it's this issue's
  own scope, not a separate one. `improvement-188`'s Tasks C/D (accent-color `oklch()`/`color-mix()`,
  `@layer` cascade) are unaffected and remain there.
- Branch `improvement-039` (git, not merged) — the failed first attempt's actual code.
