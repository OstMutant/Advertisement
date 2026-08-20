/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Flow helper for the full sign-up scenario -- fills and submits the sign-up
 *   dialog, verifies the success notification and signed-out header state, then logs the new
 *   user in (verifying their registered role) and logs back out.
 * Usage: None -- a library only, required by spec files (see Input).
 * Uses: ../_helpers (screenshot, closeNotification), ./auth.flow (runFillLoginFormFlow,
 *   runSubmitLoginFlow, runLogoutFlow).
 * Env: None.
 * Input: required by 02-marketplace-authentication-flow.spec.js, 03-marketplace-promotion-flow.spec.js.
 * Outputs: exports runSignUpFlow.
 * Returns: N/A
 * ──────────────────────────────────────────────────────────────────────────── */
const { screenshot } = require('../_helpers');
const { closeNotification } = require('../_helpers');
const { runFillLoginFormFlow, runSubmitLoginFlow, runLogoutFlow } = require('./auth.flow');

/**
 * Runs the full sign-up scenario: fills and submits the sign-up dialog, verifies the success
 * notification and signed-out header state, logs the new user in (verifying registeredRole and
 * sessionLocale), optionally runs afterLoginHook while still logged in, then logs out.
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Expect} expect
 * @param {{name: string, email: string, password: string}} user
 * @param {string} [registeredRole='USER'] expected role after login.
 * @param {(() => Promise<void>)|null} [afterLoginHook] optional extra step run while logged in.
 * @param {string} [sessionLocale='en'] expected session locale after login.
 * @returns {Promise<void>}
 */
async function runSignUpFlow(page, expect, user, registeredRole = 'USER', afterLoginHook = null, sessionLocale = 'en') {
  await page.locator('vaadin-button').filter({ hasText: /sign up|зареєструватися/i }).first().click();
  await page.locator('[data-testid="signup-name-label"]').waitFor({ timeout: 5000 });
  await screenshot(page, 'signup-dialog-open');

  await page.locator('[data-testid="signup-name-label"] input').fill(user.name);
  await page.locator('[data-testid="signup-email-label"] input').fill(user.email);
  await page.locator('[data-testid="signup-password-label"] input').fill(user.password);
  await screenshot(page, 'signup-form-filled');

  await page.getByRole('button', { name: /sign up|зареєструватися/i }).last().click();
  await expect(page.locator('vaadin-notification-container')).toContainText(
    /User registered successfully|Користувача успішно зареєстровано/i,
    { timeout: 8000 }
  );
  await page.locator('vaadin-dialog-overlay[theme~="signup-dialog"]').waitFor({ state: 'hidden', timeout: 5000 });
  await expect(page.locator('.header-auth-row span').first()).toContainText(/Not signed in|Не увійшли/i);
  await screenshot(page, 'signup-success');
  await closeNotification(page);

  await runFillLoginFormFlow(page, user);
  await runSubmitLoginFlow(page, expect, { ...user, role: registeredRole }, sessionLocale);
  await closeNotification(page);
  if (afterLoginHook) await afterLoginHook();
  await runLogoutFlow(page, expect);
}

module.exports = { runSignUpFlow };
