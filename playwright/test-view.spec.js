const { test, expect, loginAs, waitForOverlay, waitForOverlayClosed } = require('./_test-helpers');

test.describe('Advertisement view overlay', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page);
  });

  test('opens first ad and shows view overlay', async ({ page }) => {
    await test.step('Advertisement list loaded', async () => {
      await expect(page.locator('.advertisement-card').first()).toBeVisible({ timeout: 8000 });
    });

    await test.step('Click first card opens overlay', async () => {
      await page.locator('.advertisement-card').first().click();
      await waitForOverlay(page);
      await expect(page.locator('.overlay__view-title')).toBeVisible();
    });

    await test.step('Close overlay', async () => {
      await page.locator('.overlay__breadcrumb-back').click();
      await waitForOverlayClosed(page);
    });
  });
});
