# Architecture & Technical Decisions — playwright

---

## 2026-05-13 — data-testid convention for form field selectors

**Decision:** All `Ui*` field components (`UiTextField`, `UiPasswordField`, `UiEmailField`, `UiTextArea`, `UiComboBox`) set `data-testid` on their root element in `configure()`. The value is the `I18nKey` name converted to kebab-case (e.g. `SIGNUP_EMAIL_LABEL` → `"signup-email-label"`).

**Why:** Vaadin's Shadow DOM makes CSS-class selectors fragile and theme-dependent. `data-testid` attributes survive theming changes and provide stable, intent-expressing selectors.

**How to use in Playwright:** `page.locator('[data-testid="signup-email-label"] input')` — note the inner `input` selector due to Shadow DOM.

---

## 2026-06-11 — No waitForTimeout — use Vaadin state attributes instead

**Decision:** Never use `page.waitForTimeout()` in tests to wait for UI animations or dialog rendering. Always wait on a deterministic DOM condition.

**Why:** Fixed timeouts are fragile — too short means flaky tests, too long wastes time.

**Pattern for Vaadin confirm dialog (screenshot-safe):**
```js
await page.locator('vaadin-confirm-dialog-overlay[opened]:not([opening])').waitFor({ state: 'attached', timeout: 8000 });
await screenshot(page, 'some-dialog');
```

**Why `:not([opening])`:** Vaadin sets `[opening]` during the CSS open animation and removes it only when the animation completes. `[opened]` alone fires at animation start (opacity still near 0 in shadow DOM). Using `[opened]:not([opening])` guarantees the overlay is fully rendered before the screenshot. Note: `page.waitForFunction` + `getComputedStyle` does NOT work here because `document.querySelector` cannot pierce Playwright's shadow DOM — use the locator selector approach instead.

---

## 2026-05-12 — `--ux` flag controls screenshots

**Decision:** Named screenshots are only taken and attached to the HTML report when `--ux` is passed to `run.sh`. Without `--ux`, `screenshot()` calls are no-ops.

**Why:** Screenshots slow down CI runs and add noise to reports when not needed for UX analysis. Keeping them opt-in makes the default run fast and clean.

**Implementation:** `run.sh` sets `PW_SCREENSHOTS=1` env var when `--ux` is present; `e2e/_helpers.js` `screenshot()` guards on `process.env.PW_SCREENSHOTS`. Screenshots are attached to the HTML report via `test.info().attach()` and stored in `pw-report/data/`.
