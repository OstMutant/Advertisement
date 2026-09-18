# Architecture & Technical Decisions — playwright

---

## ADR-004: ESLint via eslint-plugin-playwright, delegated to pw-runner not ci-runner
**Status:** Accepted

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

**Context:** Vaadin's Shadow DOM makes CSS-class selectors fragile and theme-dependent.
Stable, intent-expressing selectors are needed that survive theming changes.

**Decision:** All `Ui*` field components (`UiTextField`, `UiPasswordField`, `UiEmailField`,
`UiTextArea`, `UiComboBox`) set `data-testid` on their root element in `configure()`.
The value is the `I18nKey` name converted to kebab-case
(e.g. `SIGNUP_EMAIL_LABEL` → `"signup-email-label"`).

**Consequences:**
- Playwright selects via `page.locator('[data-testid="signup-email-label"] input')` —
  note the inner `input` selector due to Shadow DOM.
- `data-testid` must be set in `configure()`, never in `init()`.

---

## ADR-002: No waitForTimeout — wait on Vaadin state attributes
**Status:** Accepted
**Verified:** 2026-09-18

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

Known violations of this rule, verified directly (2026-09-18): one `page.waitForTimeout()` call in
`e2e/02-marketplace-authentication-flow.spec.js`, and 34 in `e2e/04-provider-profile-flow.spec.js`
— each still needs a deterministic-wait replacement; the rule itself remains the standard.
(`e2e/_flows/advertisement-filter.flow.js`, previously listed here, no longer has any.)

---

## ADR-003: --ux flag controls screenshots
**Status:** Accepted

**Context:** Screenshots slow CI runs and add noise to reports when not needed for UX analysis.

**Decision:** Named screenshots are taken and attached to the HTML report only when `--ux` is
passed to `run.sh`. Without `--ux`, `screenshot()` calls are no-ops.

**Consequences:**
- `run.sh` sets `PW_SCREENSHOTS=1` when `--ux` is present.
- `e2e/_helpers.js` `screenshot()` guards on `process.env.PW_SCREENSHOTS`.
- Screenshots are attached to the HTML report via `test.info().attach()`.
- Always pass `--ux` when running tests for UX analysis or debugging.
