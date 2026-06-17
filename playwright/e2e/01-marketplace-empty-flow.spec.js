const { test, expect } = require('@playwright/test');
const { runOpenDefaultLocaleFlow, runSwitchToUkrainianFlow, runSwitchToEnglishFlow } = require('./_flows/language-switch.flow');
const { runOpenFilterPanelFlow, runFillTitleFilterFlow, runApplyFilterFlow, runVerifyFilterStatusFlow, runClearFilterFlow, runCloseFilterPanelFlow } = require('./_flows/advertisement-filter.flow');

test.describe.configure({ mode: 'serial' });

test.describe('Language switch (no auth)', () => {
  let page;

  test.beforeAll(async ({ browser }) => {
    page = await browser.newPage();
  });

  test.afterAll(async () => {
    await page.close();
  });

  test('open app — default locale is English', async () => {
    await runOpenDefaultLocaleFlow(page, expect);
    await expect(page.locator('.add-advertisement-button')).not.toBeVisible();
    await expect(page.locator('.advertisement-edit').first()).not.toBeVisible();
    await expect(page.locator('.advertisement-delete').first()).not.toBeVisible();
    await expect(page.locator('.pagination-count')).toBeVisible();
    await expect(page.locator('vaadin-tab').filter({ hasText: 'Users' }).first()).not.toBeVisible();
    await expect(page.locator('vaadin-tab').filter({ hasText: 'Reference Data' }).first()).not.toBeVisible();
  });

  test('switch to Ukrainian', async () => {
    await runSwitchToUkrainianFlow(page, expect);
    await expect(page.locator('.add-advertisement-button')).not.toBeVisible();
    await expect(page.locator('.advertisement-edit').first()).not.toBeVisible();
    await expect(page.locator('.advertisement-delete').first()).not.toBeVisible();
    await expect(page.locator('.pagination-count')).toBeVisible();
    await expect(page.locator('vaadin-tab').filter({ hasText: 'Користувачі' }).first()).not.toBeVisible();
    await expect(page.locator('vaadin-tab').filter({ hasText: 'Довідники' }).first()).not.toBeVisible();
  });

  test('advertisement filter panel accessible without login', async () => {
    await runOpenFilterPanelFlow(page, expect);
    await runFillTitleFilterFlow(page, 'Test');
    await runApplyFilterFlow(page, expect);
    await runVerifyFilterStatusFlow(page, expect, 'Test');
    await runClearFilterFlow(page, expect);
    await runCloseFilterPanelFlow(page, expect);
  });

  test('switch back to English', async () => {
    await runSwitchToEnglishFlow(page, expect);
  });
});
