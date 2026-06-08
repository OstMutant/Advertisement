const { screenshot } = require('../_test-helpers');

// Default locale resolution (no auth):
//   VaadinLocaleProvider → Vaadin session locale → browser Accept-Language
//   Headless Chromium sends Accept-Language: en-US → default = English

async function runOpenDefaultLocaleFlow(page, expect) {
  await page.goto('/');
  await expect(page.locator('.locale-combobox')).toBeVisible();
  await expect(page.locator('vaadin-tab').filter({ hasText: 'Advertisements' }).first()).toBeVisible({ timeout: 8000 });
  await expect(page.locator('vaadin-button').filter({ hasText: 'Log In' }).first()).toBeVisible();
  await screenshot(page, 'lang-01-default-english');
}

async function runSwitchToUkrainianFlow(page, expect) {
  await page.locator('.locale-combobox input').click();
  await page.locator('vaadin-combo-box-item').filter({ hasText: /ukrainian|укр/i }).first().click();
  await page.waitForLoadState('networkidle').catch(() => {});
  await expect(page.locator('vaadin-tab').filter({ hasText: 'Оголошення' }).first()).toBeVisible({ timeout: 8000 });
  await expect(page.locator('vaadin-button').filter({ hasText: 'Увійти' }).first()).toBeVisible();
  await screenshot(page, 'lang-02-ukrainian');
}

async function runSwitchToEnglishFlow(page, expect) {
  await page.locator('.locale-combobox input').click();
  await page.locator('vaadin-combo-box-item').filter({ hasText: /english|англійська/i }).first().click();
  await page.waitForLoadState('networkidle').catch(() => {});
  await expect(page.locator('vaadin-tab').filter({ hasText: 'Advertisements' }).first()).toBeVisible({ timeout: 8000 });
  await expect(page.locator('vaadin-button').filter({ hasText: 'Log In' }).first()).toBeVisible();
  await screenshot(page, 'lang-03-back-to-english');
}

async function runSwitchToUkrainianLoggedInFlow(page, expect) {
  await page.locator('.locale-combobox input').click();
  await page.locator('vaadin-combo-box-item').filter({ hasText: /ukrainian|укр/i }).first().click();
  await page.waitForLoadState('networkidle').catch(() => {});
  await expect(page.locator('vaadin-tab').filter({ hasText: 'Оголошення' }).first()).toBeVisible({ timeout: 8000 });
  await screenshot(page, 'lang-switch-to-uk-logged-in');
}

async function runSwitchToEnglishLoggedInFlow(page, expect) {
  await page.locator('.locale-combobox input').click();
  await page.locator('vaadin-combo-box-item').filter({ hasText: /english|англійська/i }).first().click();
  await page.waitForLoadState('networkidle').catch(() => {});
  await expect(page.locator('vaadin-tab').filter({ hasText: 'Advertisements' }).first()).toBeVisible({ timeout: 8000 });
  await screenshot(page, 'lang-switch-to-en-logged-in');
}

module.exports = { runOpenDefaultLocaleFlow, runSwitchToUkrainianFlow, runSwitchToEnglishFlow, runSwitchToUkrainianLoggedInFlow, runSwitchToEnglishLoggedInFlow };
