# improvement-198: Playwright test isolation, parallel workers, and future Playwright feature work

**Type:** improvement — test architecture, investigation
**Module:** `playwright/` (`playwright.config.js`, `playwright/e2e/README.md`, all specs)
**Priority:** 🔵 larger tech-debt — no live bug, needs a design decision before any implementation
**When:** independent, no blockers

## Current state

`playwright.config.js` runs `fullyParallel: false`/`workers: 1`, with its own code comment stating
this is because "Vaadin + shared DB — parallel runs cause race conditions." `playwright/e2e/README.md`
states "tests are serial and ordered — each spec depends on state left by the previous one." This
does not follow the official Playwright "isolate tests by default" best practice (see
[`docs/best-practices.md`](../../docs/best-practices.md)'s Playwright section). A concrete
consequence already observed: a failed test cannot be safely retried, since a retry would not start
from a clean state — `improvement-195`'s own Phase 2a rejected enabling `retries: 1` specifically
for this reason.

Since this issue was first raised, `improvement-073`'s Phase 4 (2026-09-03) moved spec 06's own
seeding (`06-seed-filter-sort-pagination.spec.js`) from browser-UI-driven to REST-API-driven
(`seedUsersViaApi`/`seedTaxonsViaApi`/`seedAdvertisementsViaApi` in `playwright/e2e/_flows/seed.flow.js`),
closing `improvement-035`. That removes a real chunk of per-run browser load from the suite, but
does not by itself remove the shared-DB constraint the `workers: 1` comment names — the two are
related but distinct questions.

## Why change

Two related open questions worth resolving deliberately rather than leaving as an unexamined
default:

1. Whether serial/shared-state execution is a deliberate, acceptable tradeoff for this project's
   Vaadin-session/shared-DB constraints, or worth the cost of changing (per-spec-file DB reset,
   isolated browser contexts/storage state per test) to get retryability and standard-practice
   isolation.
2. Whether `workers: 2` (or higher) is now viable, given that spec 06's seeding no longer drives
   the browser — worth re-measuring instead of assuming the original constraint still holds
   unchanged.

## Expected benefit

Either a documented, deliberate decision to keep the current serial design (with the tradeoff made
explicit rather than implicit), or a measured, real speedup in full-suite runtime with retryable
tests — not a guess either way.

## Approach

### 1. Playwright test isolation — open discussion, not decided

**Carried over from `improvement-195` Phase 7 (raised 2026-09-17), text unchanged:**

Not analyzed yet: whether this is worth changing (e.g. per-spec-file database reset, isolated
browser contexts/storage state per test instead of per suite), what it would cost given the current
Vaadin-session/shared-DB constraint, or whether the current serial-and-ordered design is a
deliberate, acceptable tradeoff that should just be documented more prominently rather than changed.
Pick this up as its own conversation when ready — do not start implementation from this entry
alone.

### 2. Investigate whether `workers: 2` is viable now

Not yet investigated. Starting points: measure whether spec-level DB state collisions actually occur
under `workers: 2` now that spec 06 no longer drives 100 browser-UI signups/creations; check whether
any other spec still relies on UI-driven bulk creation with the same race-condition risk; confirm
whether Vaadin's own session/websocket model has a per-worker isolation concern independent of the
DB. Depends on question 1's outcome — isolation design and worker count are the same underlying
constraint (shared mutable state across concurrent test runs), so a decision on one likely
constrains the other.

### 3. Additional Playwright feature work

Open section, no concrete items yet — placeholder for future Playwright-specific feature/tooling
work to accumulate here rather than being scattered into unrelated tasks.

## Related

- [improvement-195](improvement-195-best-practices-reference-and-audit-fixes.md) — Phase 7 originally
  raised the test-isolation question; carved out here instead of staying as an unscheduled entry in
  an otherwise-closed audit task.
- [improvement-035](../completed/tasks/improvement-035-sql-seeding-for-playwright-spec-05.md) — closed
  2026-09-22; its own unfinished `workers: 2` follow-up step is question 2 above.
- [improvement-073](../completed/tasks/improvement-073-rest-endpoint-infrastructure-test-seeding.md) —
  landed the REST-based seeding that changes question 2's starting assumptions.
- `docs/best-practices.md` — "Isolate tests by default" entry, the official practice this project
  currently deviates from.
