const { check, screenshot, withBrowser } = require('./_common');

// Tests user filters: name search, role multi-select, ID range
// Requires ADMIN role — uses user3@example.com
withBrowser(async (page) => {
  await check('Open Users tab', async () => {
    await page.locator('vaadin-tab').filter({ hasText: /users|користувач/i }).first().click();
    await page.waitForSelector('vaadin-grid.user-grid', { timeout: 8000 });
  });
  await screenshot(page, 'filter-users-01-initial-list');

  await check('Open filter panel', async () => {
    await page.locator('.query-status-bar').filter({ visible: true }).first().click();
    await page.waitForSelector('.user-query-block', { timeout: 3000 });
    if (!await page.locator('.user-query-block').isVisible())
      throw new Error('User filter panel did not open');
    const rows = await page.locator('.query-inline-row').count();
    console.log(`      filter rows visible: ${rows}`);
  });
  await screenshot(page, 'filter-users-02-filter-panel-open');

  await check('Filter by name "User 1"', async () => {
    await page.locator('.user-query-block .query-text input').first().fill('User 1');
    await page.locator('.user-query-block vaadin-button[title*="Застосувати"]').first().click();
    await page.waitForSelector('vaadin-grid.user-grid .user-grid-name', { timeout: 5000 });
    const cells = await page.locator('vaadin-grid.user-grid .user-grid-name').count();
    console.log(`      users after name filter: ${cells}`);
  });
  await screenshot(page, 'filter-users-03-filtered-by-name');

  await check('Clear and filter by role ADMIN', async () => {
    await page.locator('.user-query-block vaadin-button[title*="Очистити"]').first().click();
    await page.waitForSelector('vaadin-grid.user-grid .user-grid-name', { timeout: 5000 });

    const roleCombo = page.locator('.query-multi-combo input');
    await roleCombo.click();
    await page.waitForSelector('vaadin-multi-select-combo-box-item', { timeout: 3000 });
    await page.locator('vaadin-multi-select-combo-box-item').filter({ hasText: /ADMIN/i }).first().click();
    await page.keyboard.press('Escape');

    await page.locator('.user-query-block vaadin-button[title*="Застосувати"]').first().click();
    await page.waitForSelector('vaadin-grid.user-grid .user-grid-name', { timeout: 5000 });
    const cells = await page.locator('vaadin-grid.user-grid .user-grid-name').count();
    console.log(`      ADMIN users shown: ${cells}`);
  });
  await screenshot(page, 'filter-users-04-filtered-by-role');

  await check('Clear all filters', async () => {
    await page.locator('.user-query-block vaadin-button[title*="Очистити"]').first().click();
    await page.waitForSelector('vaadin-grid.user-grid .user-grid-name', { timeout: 5000 });
    const cells = await page.locator('vaadin-grid.user-grid .user-grid-name').count();
    console.log(`      users after clear: ${cells}`);
  });
  await screenshot(page, 'filter-users-05-cleared');
}, { email: 'user3@example.com' });
