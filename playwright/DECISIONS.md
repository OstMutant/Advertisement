# Architecture & Technical Decisions — playwright

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
