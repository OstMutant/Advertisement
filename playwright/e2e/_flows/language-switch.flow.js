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

async function runOpenDefaultLocaleFlow(page, expect) {
  await page.goto('/');
  await expect(page.locator('.locale-combobox')).toBeVisible();
  await expect(page.locator('vaadin-tab').filter({ hasText: 'Advertisements' }).first()).toBeVisible({ timeout: 8000 });
  await expect(page.locator('vaadin-button').filter({ hasText: 'Log In' }).first()).toBeVisible();
  await screenshot(page, 'lang-01-default-english');
}

async function runSwitchToUkrainianFlow(page, expect) {
  await switchLocale(page, expect, /ukrainian|укр/i, 'Оголошення');
  await expect(page.locator('vaadin-button').filter({ hasText: 'Увійти' }).first()).toBeVisible();
  await screenshot(page, 'lang-02-ukrainian');
}

async function runSwitchToEnglishFlow(page, expect) {
  await switchLocale(page, expect, /english|англійська/i, 'Advertisements');
  await expect(page.locator('vaadin-button').filter({ hasText: 'Log In' }).first()).toBeVisible();
  await screenshot(page, 'lang-03-back-to-english');
}

async function runSwitchToUkrainianLoggedInFlow(page, expect) {
  await switchLocale(page, expect, /ukrainian|укр/i, 'Оголошення');
  await screenshot(page, 'lang-switch-to-uk-logged-in');
}

async function runSwitchToEnglishLoggedInFlow(page, expect) {
  await switchLocale(page, expect, /english|англійська/i, 'Advertisements');
  await screenshot(page, 'lang-switch-to-en-logged-in');
}

module.exports = { runOpenDefaultLocaleFlow, runSwitchToUkrainianFlow, runSwitchToEnglishFlow, runSwitchToUkrainianLoggedInFlow, runSwitchToEnglishLoggedInFlow };
