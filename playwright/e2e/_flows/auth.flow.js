/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Flow helpers for the login/logout cycle -- opening the login form and filling
 *   credentials, submitting login and asserting the post-login header/tabs match the user's role
 *   and locale, canceling a logout confirmation, and completing a full logout.
 * Usage: None -- a library only, required by spec files (see Input).
 * Uses: ../_helpers (screenshot).
 * Env: None.
 * Input: required by 02-marketplace-authentication-flow.spec.js,
 *   03-marketplace-promotion-flow.spec.js, 04-provider-profile-flow.spec.js,
 *   05-marketplace-advertisement-flow.spec.js, 07-marketplace-delete-flow.spec.js,
 *   08-accessibility.spec.js, and _flows/signup.flow.js.
 * Outputs: exports runFillLoginFormFlow, runSubmitLoginFlow, runCancelLogoutFlow, runLogoutFlow.
 * Returns: N/A
 * ──────────────────────────────────────────────────────────────────────────── */
const { screenshot } = require('../_helpers');

/**
 * Opens the login form and fills in the given user's email and password.
 * @param {import('@playwright/test').Page} page
 * @param {{email: string, password: string}} user
 * @returns {Promise<void>}
 */
async function runFillLoginFormFlow(page, user) {
  await page.locator('vaadin-button').filter({ hasText: /log in|увійти/i }).first().click();
  await page.locator('[data-testid="login-email-label"]').waitFor({ timeout: 5000 });
  await page.locator('[data-testid="login-email-label"] input').fill(user.email);
  await page.locator('[data-testid="login-password-label"] input').fill(user.password);
  await screenshot(page, 'auth-fill-credentials-entered');
}

const TAB_LABELS = {
  en: { advertisements: 'Advertisements', users: 'Users' },
  uk: { advertisements: 'Оголошення',     users: 'Користувачі' },
};

/**
 * Submits the login form and asserts the post-login header/tabs match the user's role and locale.
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Expect} expect
 * @param {{role: string, locale: string}} user
 * @param {string} [locale=user.locale] locale to resolve tab labels against.
 * @returns {Promise<void>}
 */
async function runSubmitLoginFlow(page, expect, user, locale = user.locale) {
  await page.locator('vaadin-button').filter({ hasText: /log in|увійти/i }).last().click();
  // login triggers a full page reload server-side, not an in-place push update
  await page.waitForLoadState('networkidle').catch(() => {});
  await expect(page.locator('.header-settings-button')).toBeVisible({ timeout: 15000 });

  const labels = TAB_LABELS[locale];
  await expect(page.locator('vaadin-tab').filter({ hasText: labels.advertisements }).first()).toBeVisible({ timeout: 15000 });

  if (user.role === 'MODERATOR' || user.role === 'ADMIN') {
    await expect(page.locator('vaadin-tab').filter({ hasText: labels.users }).first()).toBeVisible({ timeout: 15000 });
  } else {
    await expect(page.locator('vaadin-tab').filter({ hasText: labels.users }).first()).not.toBeVisible();
  }

  await screenshot(page, 'auth-submit-logged-in');
}

/**
 * Opens the logout confirmation dialog and cancels it, asserting the user stays logged in.
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Expect} expect
 * @returns {Promise<void>}
 */
async function runCancelLogoutFlow(page, expect) {
  await page.locator('.header-logout-button').click();
  await page.locator('vaadin-confirm-dialog-overlay[opened]:not([opening])').waitFor({ state: 'attached', timeout: 8000 });
  await screenshot(page, 'auth-logout-cancel-dialog');

  await page.getByRole('button', { name: /cancel|скасувати/i }).click();
  await page.locator('vaadin-confirm-dialog-overlay').waitFor({ state: 'hidden', timeout: 5000 });
  await expect(page.locator('.header-settings-button')).toBeVisible();
  await screenshot(page, 'auth-logout-cancel-stays-logged-in');
}

/**
 * Opens the logout confirmation dialog, confirms it, and asserts the user is logged out.
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Expect} expect
 * @returns {Promise<void>}
 */
async function runLogoutFlow(page, expect) {
  await page.locator('.header-logout-button').click();
  await page.locator('vaadin-confirm-dialog-overlay[opened]:not([opening])').waitFor({ state: 'attached', timeout: 8000 });
  await screenshot(page, 'auth-logout-confirm-dialog');

  await page.getByRole('button', { name: /^log out$|^вийти$/i }).last().click();

  await expect(page.locator('.header-settings-button')).not.toBeVisible({ timeout: 8000 });
  await expect(page.locator('vaadin-button').filter({ hasText: /log in|увійти/i }).first()).toBeVisible({ timeout: 5000 });
  await screenshot(page, 'auth-logout-logged-out');
}

module.exports = { runFillLoginFormFlow, runSubmitLoginFlow, runCancelLogoutFlow, runLogoutFlow };
