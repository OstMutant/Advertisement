# improvement-039: Dark mode — blocked by hardcoded hex colors instead of Lumo tokens

**Type:** improvement — UX/theming. Migrated from `backlog/process-improvements.md` Part 3,
item 20.
**Module:** `marketplace-app` theme CSS
**Priority:** low — no functional impact, purely a modern-UX expectation gap
**When:** trigger-based / opportunistic — do alongside improvement-037 (accessibility), since both
require touching the same theme CSS files for the same underlying reason (hardcoded hex → Lumo
custom properties)

## Problem

The app is light-theme-only. Component CSS uses raw hex values (`#94a3b8`, `#64748b`, `#1e293b`,
and others per the source audit) instead of Lumo's `var(--lumo-*)` custom properties, which is
what would make a dark palette close to free — Lumo ships a dark palette, but only components
already using its tokens pick it up automatically.

## Suggested fix

Two-step, matching the source plan:
1. Tokenize the existing theme CSS — replace hardcoded hex values with the equivalent
   `var(--lumo-*)` tokens throughout.
2. Add a dark-mode toggle plus a `prefers-color-scheme` default, once tokenization is complete.

## Related

- `backlog/process-improvements.md` Part 3, item 20 — source item, now superseded by this issue.
- `backlog/issues/improvement-037-accessibility-contrast-and-aria.md` — shares the exact same
  CSS-tokenization prerequisite; strongly consider doing both in one pass over the theme files
  rather than two separate touches of the same CSS.
