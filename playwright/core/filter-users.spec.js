const { test, expect, loginAs, screenshot,
        waitForOverlay, waitForOverlayClosed } = require('./_test-helpers');

test.describe('Filter users (admin)', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, 'user3@example.com');
  });

  test('name search, role multi-select, clear', async ({ page }) => {
    await test.step('Open Users tab', async () => {
      await page.locator('vaadin-tab').filter({ hasText: /users|користувач/i }).first().click();
      await page.locator('vaadin-grid.user-grid').waitFor({ timeout: 8000 });
    });

    await test.step('Open filter panel', async () => {
      await page.locator('.query-status-bar').filter({ visible: true }).first().click();
      await expect(page.locator('.user-query-block')).toBeVisible({ timeout: 5000 });
      await screenshot(page, 'filter-users-02-filter-panel-open');
    });

    await test.step('Filter by name "User 1"', async () => {
      await page.locator('.user-query-block .query-text input').first().fill('User 1');
      await page.locator('.user-query-block vaadin-button[title*="Застосувати"]').first().click();
      await page.locator('vaadin-grid.user-grid .user-grid-name').first().waitFor({ timeout: 5000 });
      await screenshot(page, 'filter-users-03-filtered-by-name');
    });

    await test.step('Clear and filter by role ADMIN', async () => {
      await page.locator('.user-query-block vaadin-button[title*="Очистити"]').first().click();
      await page.locator('vaadin-grid.user-grid .user-grid-name').first().waitFor({ timeout: 5000 });

      const roleField = page.locator('.user-query-block vaadin-multi-select-combo-box, .user-query-block vaadin-combo-box').first();
      if (await roleField.count() > 0) {
        await roleField.locator('input').click();
        await page.locator('vaadin-combo-box-item, vaadin-multi-select-combo-box-item')
          .filter({ hasText: /admin/i }).first().click();
        await page.keyboard.press('Escape'); // close dropdown before clicking apply
        await page.locator('.user-query-block vaadin-button[title*="Застосувати"]').first().click();
        await page.locator('vaadin-grid.user-grid .user-grid-name').first().waitFor({ timeout: 5000 });
        await screenshot(page, 'filter-users-04-filtered-by-role');
      }
    });

    await test.step('Clear all filters', async () => {
      await page.locator('.user-query-block vaadin-button[title*="Очистити"]').first().click();
      await page.locator('vaadin-grid.user-grid .user-grid-name').first().waitFor({ timeout: 5000 });
    });
  });
});
