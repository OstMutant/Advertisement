const { chromium } = require('playwright');
const { check, screenshot, login } = require('./_common');

// Tests user filters: name search, role multi-select, ID range
// Requires ADMIN role — uses user3@example.com
const UX = process.argv.includes('--ux');

(async () => {
  const browser = await chromium.launch();
  const page    = await browser.newPage({ viewport: { width: 1280, height: 900 } });

  await login(page, 'user3@example.com', 'password');

  await check('Open Users tab', async () => {
    await page.locator('vaadin-tab').filter({ hasText: /users|користувач/i }).first().click();
    await page.waitForTimeout(1500);
  });
  await screenshot(page, 'filter-users-01-initial-list', UX);

  // Open filter panel
  await check('Open filter panel', async () => {
    await page.locator('.query-status-bar').filter({ visible: true }).first().click();
    await page.waitForTimeout(800);
    const visible = await page.locator('.user-query-block').isVisible();
    if (!visible) throw new Error('User filter panel did not open');
    const rows = await page.locator('.query-inline-row').count();
    console.log(`      filter rows visible: ${rows}`);
  });
  await screenshot(page, 'filter-users-02-filter-panel-open', UX);

  // Filter by name
  await check('Filter by name "User"', async () => {
    const nameInput = page.locator('.user-query-block .query-text input').first();
    await nameInput.fill('User 1');
    await page.waitForTimeout(300);
    await page.locator('.user-query-block vaadin-button[title*="Застосувати"]').first().click();
    await page.waitForTimeout(1500);
    const cells = await page.locator('vaadin-grid.user-grid .user-grid-name').count();
    console.log(`      users after name filter: ${cells}`);
  });
  await screenshot(page, 'filter-users-03-filtered-by-name', UX);

  // Clear and filter by role
  await check('Clear and filter by role ADMIN', async () => {
    await page.locator('.user-query-block vaadin-button[title*="Очистити"]').first().click();
    await page.waitForTimeout(1000);

    // Multi-select combo for role
    const roleCombo = page.locator('.query-multi-combo input');
    await roleCombo.click();
    await page.waitForTimeout(500);
    await page.locator('vaadin-multi-select-combo-box-item').filter({ hasText: /ADMIN/i }).first().click();
    await page.keyboard.press('Escape');
    await page.waitForTimeout(300);

    await page.locator('.user-query-block vaadin-button[title*="Застосувати"]').first().click();
    await page.waitForTimeout(1500);
    const cells = await page.locator('vaadin-grid.user-grid .user-grid-name').count();
    console.log(`      ADMIN users shown: ${cells}`);
  });
  await screenshot(page, 'filter-users-04-filtered-by-role', UX);

  // Clear all
  await check('Clear all filters', async () => {
    await page.locator('.user-query-block vaadin-button[title*="Очистити"]').first().click();
    await page.waitForTimeout(1500);
    const cells = await page.locator('vaadin-grid.user-grid .user-grid-name').count();
    console.log(`      users after clear: ${cells}`);
  });
  await screenshot(page, 'filter-users-05-cleared', UX);

  await browser.close();
})();
