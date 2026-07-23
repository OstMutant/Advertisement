# improvement-039: Dark mode — step 1 (tokenization) done via improvement-037, step 2 (actual dark palette + toggle) still open

**Type:** improvement — UX/theming. Migrated from `backlog/process-improvements.md` Part 3,
item 20.
**Module:** `marketplace-app` theme CSS
**Priority:** low — no functional impact, purely a modern-UX expectation gap
**When:** independent, no blockers — the prerequisite is done; whenever picked up, it's now just
step 2 below

## Problem

The app is light-theme-only. Component CSS used to use raw hex values throughout instead of named
custom properties, which would have made a dark palette non-trivial — swapping colors in place
across 21 files with no shared source isn't a real option.

## Status update (2026-07-16)

**Step 1 (tokenization) is done** — shipped as part of
[improvement-037](../completed/issues/improvement-037-accessibility-contrast-and-aria.md)'s WCAG
contrast fix, since both issues needed the exact same prerequisite and the source plan recommended
doing both in one pass. Every color in the theme is now a named `--app-*` custom property in
`styles.css`'s `:root` block (full mapping: `marketplace-app/DECISIONS.md` ADR-038). This issue's
own scope is now narrower — only step 2 remains.

## Suggested fix (remaining scope)

1. Define a dark palette — a second value set for every existing `--app-*` token name (e.g. inside
   a `[data-theme="dark"]` or `@media (prefers-color-scheme: dark)` block in `styles.css`). No
   further per-component CSS file touches needed — the color layer is already fully abstracted
   behind the token names.
2. Add a dark-mode toggle (UI control + persisted preference, likely alongside the existing
   locale/settings mechanism) plus a `prefers-color-scheme` default for first-time visitors.

## Related

- `backlog/process-improvements.md` Part 3, item 20 — source item, now superseded by this issue.
- [improvement-037-accessibility-contrast-and-aria](../completed/issues/improvement-037-accessibility-contrast-and-aria.md) —
  shipped this issue's own tokenization prerequisite as part of its own WCAG fix.
- `marketplace-app/DECISIONS.md` ADR-038 — the token infrastructure this issue now builds on.
