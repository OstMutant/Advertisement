# improvement-035: SQL-based seeding for Playwright spec 05 — full e2e 11 min → ~7-8 min

**Type:** improvement — test performance. Migrated from `backlog/process-improvements.md` Part 1,
item 6.
**Module:** `playwright/e2e/05-seed-filter-sort-pagination.spec.js`
**Priority:** low — pure speed optimization, no coverage gap; the current approach is slow but not
wrong
**When:** independent, no blockers — but low urgency; revisit if the ~9-10 min full-suite runtime
becomes a real friction point (it hasn't blocked anything so far this session, per the actual
observed runtimes: 48/48 in 9.6-9.7 min)

## Problem

Spec `05-seed-filter-sort-pagination` seeds 50 users + 50 advertisements by driving the actual
signup/creation UI in the browser (measured: ~1.3 min for the 50 users, ~45-57s for the 50 ads).
The signup flow itself is already covered by spec 01/02; re-exercising it 50 times through the UI
adds runtime, not additional coverage — it's needed only to produce enough rows for the
filter/sort/pagination assertions later in the same spec.

## Suggested fix

- Seed the 50 users + 50 advertisements via a SQL fixture (`docker exec psql` against the running
  dev DB) or a test-only seeding endpoint, instead of driving signup/creation through the UI 100
  times.
- Keep exactly one UI-created entity in the seeding step to preserve an actual end-to-end
  creation path being exercised somewhere in the suite.
- Once seeding is confirmed data-isolated (no cross-test interference), gradually enable
  `workers: 2` in the Playwright config (currently `workers: 1, fullyParallel: false` per
  `playwright.config.js`) for further speedup.

## Related

- `backlog/process-improvements.md` Part 1, item 6 — source item, now superseded by this issue.
- `playwright/CLAUDE.md` — documents the current `--full` flag behavior this issue would change
  the internals of (external behavior — "seeds 50 users + 50 ads" — stays the same; only the
  mechanism changes from UI-driven to SQL-driven).
