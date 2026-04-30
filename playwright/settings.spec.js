const { test, expect, loginAs, screenshot,
        waitForOverlay, waitForOverlayClosed } = require('./_test-helpers');

test.describe('Settings', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page);
  });

  test('opens overlay and saves page size change', async ({ page }) => {
    await test.step('Settings button visible', async () => {
      await expect(page.locator('.header-settings-button')).toBeVisible();
    });

    await test.step('Open settings overlay', async () => {
      await page.locator('.header-settings-button').click();
      await waitForOverlay(page);
      await screenshot(page, 'settings-02-overlay');
    });

    await test.step('Ads page size field present', async () => {
      await expect(page.locator('.settings-overlay-content vaadin-integer-field').first()).toBeVisible();
    });

    await test.step('Change ads page size', async () => {
      const field = page.locator('.settings-overlay-content vaadin-integer-field').first().locator('input');
      await field.click({ clickCount: 3 });
      await field.fill('20');
    });

    await test.step('Save settings', async () => {
      await page.locator('.base-overlay.overlay--visible vaadin-button')
        .filter({ hasText: /зберегти|save/i }).click();
      await page.waitForLoadState('networkidle');
      await screenshot(page, 'settings-03-saved');
    });
  });
});
