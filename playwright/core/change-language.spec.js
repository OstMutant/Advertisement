const { test, expect, screenshot } = require('./_test-helpers');

test.describe('Language switch', () => {
  test('locale combobox switches UI language', async ({ page }) => {
    await page.goto('/');

    await test.step('Locale combobox visible', async () => {
      await expect(page.locator('.locale-combobox')).toBeVisible();
    });

    await test.step('Switch to English', async () => {
      await page.locator('.locale-combobox input').click();
      await page.locator('vaadin-combo-box-item').filter({ hasText: /english|en/i }).first().click();
      await page.waitForLoadState('networkidle').catch(() => {});
      await screenshot(page, 'lang-02-after-switch');
    });

    await test.step('Switch back to Ukrainian', async () => {
      await page.locator('.locale-combobox input').click();
      await page.locator('vaadin-combo-box-item').filter({ hasText: /укр|ukrainian/i }).first().click();
      await page.waitForLoadState('networkidle').catch(() => {});
      await screenshot(page, 'lang-03-back-to-ukrainian');
    });
  });
});
