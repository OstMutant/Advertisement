# Architecture & Technical Decisions — playwright

---

## ADR-005: Isolated parallel e2e suite — setup project, own data per test, per-worker accounts with saved sessions
**Status:** Accepted

**Applied:** 1

**Context:** The suite ran one worker, in file order, over one shared database: each spec used the
accounts, categories and cities the previous one left behind. A failed test could not be retried,
one spec could not run alone, nothing ran side by side, and the full run took 15.9 minutes. The
config comment blamed Vaadin; the real causes were shared database rows and shared test accounts.

**Decision:** Keep one shared database and make every test own its data.
- A `setup` project (`e2e/global.setup.js`) signs up the first administrator on an empty database and
  skips itself otherwise; the `e2e` project depends on it and runs fully parallel with one retry.
- Every test creates what it needs with unique names (`unique`, `createUser`, REST seeding through
  `_flows/seed.flow.js`) and checks only its own data: list, grid, timeline and count checks filter
  on the test's own title, e-mail or actor first.
- Fixtures live in `e2e/_fixtures.js`: one account per worker and role with a saved login session
  (`pageAs`, state kept in the container's output folder), a fresh account for any test that changes
  its own actor (locale, page sizes, profile, logout, deletion).
- The worker count is `PW_WORKERS` in the repo-root `.env`; more than 2 workers did not shorten the run
  on a 4-core machine (measured: 14.1 min on 1 worker, 10.5 on 2, 10.4 on 3, about 10.5 on 4).
- Locators are role, label and text first, `getByTestId` for `Ui*` fields, raw CSS only where an
  element has no role or name; `PW_LINT_MAX_WARNINGS` in `.env` makes the lint run fail when the count
  of raw locators grows.

**Consequences:**
- Any spec, any single test and the whole suite run in any order and in parallel; a spec passes when
  run twice in a row on the same database (`--repeat-each 2`). The full run takes about 10.4 minutes.
- The database is still reset before a run (`run.sh`), but no test depends on it.
- Some checks became narrower: "exactly 4 profiles in the whole catalog" is "the test's own 4".
- Heavy tests set their own timeout; the provider catalog test needs 180 s under 4 workers.

**Rejected alternatives:**
- A database and app copy per worker: strongest isolation, but a second full stack per worker and
  changes to the deploy, run and CI scripts.
- Keep the order and only add retries: a retry would start on the data its failed attempt left behind.
- Reset the database before every spec: needs an app restart around each reset (the login limiter and
  caches survive a truncate) and still runs one file at a time.
- One shared account for every worker: tests change their own account, and parallel tests would wait on
  one Vaadin session.

---

## ADR-004: ESLint via eslint-plugin-playwright, delegated to pw-runner not ci-runner
**Status:** Accepted

**Applied:** 1

**Context:** `playwright/e2e` had no static-analysis gate at all — nothing catches an un-awaited
Playwright API call (a real, silent test-failure mode: the test continues past an action that
never actually happened, producing hard-to-diagnose flakiness instead of a hard failure at the
mistake itself).

**Decision:** Add `eslint-plugin-playwright` (not a generic floating-promise rule) — its
`playwright/missing-playwright-await` rule is purpose-built for this exact API surface. Wired as a
new `--lint` mode on `playwright/run.sh` (this script-group's single entry point), not a new
sibling script — reuses `run.sh`'s own `pw-runner` container/lifecycle instead of a second Docker
orchestration path. `scripts/ci/dagu/ci.yaml`'s new `lint` stage calls `playwright/run.sh --lint`
directly rather than installing Node.js into the `ci-runner` image itself — `pw-runner` already
owns the Node/npm runtime this suite needs, the same delegation shape the existing `e2e` stage
already uses.

**Rejected alternatives:**
- `@typescript-eslint/no-floating-promises` — requires TypeScript type information; this suite is
  plain JS.
- Installing Node.js/ESLint directly inside `ci-runner` — would duplicate a runtime `pw-runner`
  already provides and couples an unrelated image to Node.js version churn.

---

## ADR-001: data-testid convention for form field selectors
**Status:** Accepted

**Applied:** 1

**Context:** Vaadin's Shadow DOM makes CSS-class selectors fragile and theme-dependent.
Stable, intent-expressing selectors are needed that survive theming changes.

**Decision:** All `Ui*` field components (`UiTextField`, `UiPasswordField`, `UiEmailField`,
`UiTextArea`, `UiComboBox`) set `data-testid` on their root element in `configure()`.
The value is the `I18nKey` name converted to kebab-case
(e.g. `SIGNUP_EMAIL_LABEL` → `"signup-email-label"`).

**Consequences:**
- Playwright selects via `page.getByTestId('signup-email-label').locator('input')` —
  note the inner `input` selector due to Shadow DOM.
- `data-testid` must be set in `configure()`, never in `init()`.

---

## ADR-002: No waitForTimeout — wait on Vaadin state attributes
**Status:** Accepted

**Verified:** 2026-10-25
**Applied:** 1

**Context:** Fixed timeouts are fragile — too short causes flaky tests, too long wastes time.
Vaadin sets DOM attributes (`[opened]`, `[opening]`) to signal animation state.

**Decision:** Never use `page.waitForTimeout()`. Always wait on a deterministic DOM condition.

**Consequences:**

Pattern for Vaadin confirm dialog (screenshot-safe):
```js
await page.locator('vaadin-confirm-dialog-overlay[opened]:not([opening])').waitFor({ state: 'attached', timeout: 8000 });
await screenshot(page, 'some-dialog');
```

`:not([opening])` is required — `[opened]` alone fires at animation start (overlay still
invisible). `page.waitForFunction` + `getComputedStyle` does NOT work here because
`document.querySelector` cannot pierce Playwright's shadow DOM.

No `page.waitForTimeout()` call is left in `e2e/` (verified 2026-10-25).

---

## ADR-003: --ux flag controls screenshots
**Status:** Accepted

**Applied:** 1

**Context:** Screenshots slow CI runs and add noise to reports when not needed for UX analysis.

**Decision:** Named screenshots are taken and attached to the HTML report only when `--ux` is
passed to `run.sh`. Without `--ux`, `screenshot()` calls are no-ops.

**Consequences:**
- `run.sh` sets `PW_SCREENSHOTS=1` when `--ux` is present.
- `e2e/_helpers.js` `screenshot()` guards on `process.env.PW_SCREENSHOTS`.
- Screenshots are attached to the HTML report via `test.info().attach()`.
- Always pass `--ux` when running tests for UX analysis or debugging.
