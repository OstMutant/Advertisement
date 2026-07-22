# improvement-085: Playwright 1.52.0 → 1.61.1 version bump

**Type:** improvement — test-infrastructure maintenance.
**Module:** `playwright/` (test tooling, not application code) — `playwright/run.sh`
(`PLAYWRIGHT_VERSION`), the `mcr.microsoft.com/playwright:v${PLAYWRIGHT_VERSION}-jammy` Docker
image, and any `npm install playwright@... @playwright/test@...` pins.
**Priority:** low-medium — no production risk (test-only tooling), but a large enough version gap
(9 minor releases) that new API/behavior changes are plausible; routine but not entirely
mechanical.
**When:** independent — re-check the latest stable release before starting if time has passed
since this issue was filed (2026-07-19).

## Problem

Checked directly (web search, npm registry): the project pins Playwright at `1.52.0`
(`playwright/CLAUDE.md`: "Playwright version must match image: `playwright@1.52.0` +
`mcr.microsoft.com/playwright:v1.52.0-jammy`"). Current latest stable is **1.61.1** — 9 minor
versions behind. This is a larger, more active-development gap than the routine library bumps in
[improvement-040](improvement-040-spring-boot-vaadin-minor-bump.md); Playwright ships new features
and occasional API changes across minor versions (selector engine updates, trace viewer changes,
new assertion helpers), so this needs an actual read of the intervening release notes, not just a
version-number swap.

## Suggested fix

1. Read Playwright's release notes for the 1.53–1.61 range (https://playwright.dev/docs/release
   -notes) for anything that could affect this project's usage patterns — in particular, this
   codebase's heavy use of manual shadow-DOM piercing helpers (`shadowFind`/`shadowFindAll` in
   several `_flows/*.flow.js` files) and `page.evaluate()`-based DOM inspection, since some
   Playwright releases have improved native shadow-DOM piercing in locators — a version bump here
   could be an opportunity to simplify some of those hand-rolled helpers, not just a pass-through
   bump (worth a follow-up note, not necessarily in-scope for this issue itself).
2. Bump `PLAYWRIGHT_VERSION` in `playwright/run.sh` and the `mcr.microsoft.com/playwright:v...
   -jammy` image reference together — per `playwright/CLAUDE.md`, these must always match exactly.
3. Full Playwright e2e run (`bash scripts/playwright.sh e2e --full --ux`) — this is both the change
   and its own regression test; if anything in the suite breaks from the version bump itself
   (not a real app bug), diagnose against the release notes read in step 1 before assuming it's a
   flake.

## Related

- `playwright/CLAUDE.md` — documents the current pinned version and the image/package pairing
  requirement.
- [improvement-040](improvement-040-spring-boot-vaadin-minor-bump.md) — the routine
  library-version-bump issue this was split out from (different risk shape: test tooling with a
  larger gap and real release-note surface area, vs. small library patch/minor bumps).
