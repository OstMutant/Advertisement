/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Flow helpers for switching the app's UI locale via the header combobox, both
 *   unauthenticated (default-locale check, EN<->UK toggle) and logged-in.
 * Usage: None -- a library only, required by spec files (see Input).
 * Uses: ../_helpers (screenshot).
 * Env: None.
 * Input: required by 01-marketplace-empty-flow.spec.js, 02-marketplace-authentication-flow.spec.js,
 *   03-marketplace-promotion-flow.spec.js.
 * Outputs: exports runOpenDefaultLocaleFlow, runSwitchToUkrainianFlow, runSwitchToEnglishFlow,
 *   runSwitchToUkrainianLoggedInFlow, runSwitchToEnglishLoggedInFlow.
 * Returns: N/A
 * ──────────────────────────────────────────────────────────────────────────── */
const { screenshot } = require('../_helpers');

// Default locale resolution (no auth):
//   VaadinLocaleProvider → Vaadin session locale → browser Accept-Language
//   Headless Chromium sends Accept-Language: en-US → default = English

async function switchLocale(page, expect, localeText, expectedTab) {
  await page.locator('.locale-combobox input').click();
  await page.locator('vaadin-combo-box-item').filter({ hasText: localeText }).first().click();
  await page.waitForLoadState('networkidle').catch(() => {});
  await expect(page.locator('vaadin-tab').filter({ hasText: expectedTab }).first()).toBeVisible({ timeout: 8000 });
}

/**
 * Verifies the app's default (unauthenticated) locale resolves to English and takes a screenshot.
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Expect} expect
 * @returns {Promise<void>}
 */
async function runOpenDefaultLocaleFlow(page, expect) {
  await page.goto('/');
  await expect(page.locator('.locale-combobox')).toBeVisible({ timeout: 8000 });
  await expect(page.locator('vaadin-tab').filter({ hasText: 'Advertisements' }).first()).toBeVisible({ timeout: 8000 });
  await expect(page.locator('vaadin-button').filter({ hasText: 'Log In' }).first()).toBeVisible();
  await screenshot(page, 'lang-01-default-english');
}

/**
 * Switches the UI locale from English to Ukrainian (unauthenticated) and takes a screenshot.
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Expect} expect
 * @returns {Promise<void>}
 */
async function runSwitchToUkrainianFlow(page, expect) {
  await switchLocale(page, expect, /ukrainian|укр/i, 'Оголошення');
  await expect(page.locator('vaadin-button').filter({ hasText: 'Увійти' }).first()).toBeVisible();
  await screenshot(page, 'lang-02-ukrainian');
}

/**
 * Switches the UI locale from Ukrainian back to English (unauthenticated) and takes a screenshot.
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Expect} expect
 * @returns {Promise<void>}
 */
async function runSwitchToEnglishFlow(page, expect) {
  await switchLocale(page, expect, /english|англійська/i, 'Advertisements');
  await expect(page.locator('vaadin-button').filter({ hasText: 'Log In' }).first()).toBeVisible();
  await screenshot(page, 'lang-03-back-to-english');
}

/**
 * Switches the UI locale to Ukrainian while logged in and takes a screenshot.
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Expect} expect
 * @returns {Promise<void>}
 */
async function runSwitchToUkrainianLoggedInFlow(page, expect) {
  await switchLocale(page, expect, /ukrainian|укр/i, 'Оголошення');
  await screenshot(page, 'lang-switch-to-uk-logged-in');
}

/**
 * Switches the UI locale to English while logged in and takes a screenshot.
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Expect} expect
 * @returns {Promise<void>}
 */
async function runSwitchToEnglishLoggedInFlow(page, expect) {
  await switchLocale(page, expect, /english|англійська/i, 'Advertisements');
  await screenshot(page, 'lang-switch-to-en-logged-in');
}

module.exports = { runOpenDefaultLocaleFlow, runSwitchToUkrainianFlow, runSwitchToEnglishFlow, runSwitchToUkrainianLoggedInFlow, runSwitchToEnglishLoggedInFlow };
