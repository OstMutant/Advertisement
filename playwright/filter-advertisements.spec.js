const { test, expect, loginAs, screenshot,
        waitForOverlay, waitForOverlayClosed } = require('./_test-helpers');

test.describe('Filter advertisements', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page);
  });

  test('filter panel: text search, sort, clear', async ({ page }) => {
    await test.step('Open filter panel', async () => {
      await expect(page.locator('.advertisement-card').first()).toBeVisible({ timeout: 8000 });
      // no explicit wait — next action will auto-wait on cards
      await page.locator('.query-status-bar').first().click();
      await expect(page.locator('.advertisement-query-block')).toBeVisible({ timeout: 5000 });
    });

    await test.step('Filter by title "Test"', async () => {
      await page.locator('.advertisement-query-block .query-text input').first().fill('Test');
      await page.locator('.advertisement-query-block vaadin-button[title*="Застосувати"]').first().click();
      await screenshot(page, 'filter-ads-03-filtered-by-title');
    });

    await test.step('Clear filters', async () => {
      await page.locator('.advertisement-query-block vaadin-button[title*="Очистити"]').first().click();
      await expect(page.locator('.advertisement-card').first()).toBeVisible({ timeout: 8000 });
    });

    await test.step('Sort by title', async () => {
      const sortBtn = page.locator('.advertisement-query-block .sort-icon, .advertisement-query-block [class*="sort"]').first();
      if (await sortBtn.count() > 0) await sortBtn.click();
    });
  });
});
