const { test, expect, loginAs, screenshot,
        waitForOverlay, waitForOverlayClosed } = require('./_test-helpers');

test.describe('Users view (admin)', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, 'user3@example.com');
  });

  test('grid loads, row click opens view, edit button opens edit overlay', async ({ page }) => {
    await test.step('Users tab visible for admin', async () => {
      await expect(page.locator('vaadin-tab').filter({ hasText: /users|користувач/i }).first()).toBeVisible();
    });

    await test.step('Click Users tab', async () => {
      await page.locator('vaadin-tab').filter({ hasText: /users|користувач/i }).first().click();
    });

    await test.step('User grid loaded', async () => {
      await page.locator('vaadin-grid.user-grid').waitFor({ timeout: 8000 });
      const cells = await page.locator('vaadin-grid.user-grid vaadin-grid-cell-content').count();
      if (cells === 0) throw new Error('User grid is empty');
      await screenshot(page, 'users-01-list');
    });

    await test.step('Click row to open view overlay', async () => {
      await page.locator('vaadin-grid.user-grid vaadin-grid-cell-content .user-grid-name').first().click();
      await waitForOverlay(page);
      await screenshot(page, 'users-02-view-overlay');
    });

    await test.step('Close view overlay', async () => {
      await page.locator('.overlay__breadcrumb-back').click();
      await waitForOverlayClosed(page);
    });

    await test.step('Click edit button to open edit overlay', async () => {
      await page.locator('vaadin-grid.user-grid .user-grid-actions vaadin-button').first().click();
      await waitForOverlay(page);
      await screenshot(page, 'users-03-edit-overlay');
    });

    await test.step('Close edit overlay', async () => {
      await page.locator('.base-overlay.overlay--visible vaadin-button')
        .filter({ hasText: /скасувати|cancel|закрити|close/i }).first().click();
      await waitForOverlayClosed(page).catch(() => {});
    });
  });
});
