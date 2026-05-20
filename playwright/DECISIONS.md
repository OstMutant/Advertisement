# Architecture & Technical Decisions — playwright

---

## 2026-05-13 — data-testid convention for form field selectors

**Decision:** All `Ui*` field components (`UiTextField`, `UiPasswordField`, `UiEmailField`, `UiTextArea`, `UiComboBox`) set `data-testid` on their root element in `configure()`. The value is the `I18nKey` name converted to kebab-case (e.g. `SIGNUP_EMAIL_LABEL` → `"signup-email-label"`).

**Why:** Vaadin's Shadow DOM makes CSS-class selectors fragile and theme-dependent. `data-testid` attributes survive theming changes and provide stable, intent-expressing selectors.

**How to use in Playwright:** `page.locator('[data-testid="signup-email-label"] input')` — note the inner `input` selector due to Shadow DOM.

---

## 2026-05-12 — `--ux` flag controls screenshots

**Decision:** Named screenshots are only taken and attached to the HTML report when `--ux` is passed to `run.sh`. Without `--ux`, `screenshot()` calls are no-ops.

**Why:** Screenshots slow down CI runs and add noise to reports when not needed for UX analysis. Keeping them opt-in makes the default run fast and clean.

**Implementation:** `run.sh` sets `PW_SCREENSHOTS=1` env var when `--ux` is present; `_test-helpers.js` `screenshot()` guards on `process.env.PW_SCREENSHOTS`. Screenshots are attached to the HTML report via `test.info().attach()` and stored in `pw-report/data/`.
