const { check, screenshot, withBrowser } = require('./_common');

// Tests advertisement filters: title text search + sort toggle
withBrowser(async (page) => {
  await screenshot(page, 'filter-ads-01-initial-list');

  await check('Open filter panel', async () => {
    await page.locator('.query-status-bar').first().click();
    await page.waitForSelector('.advertisement-query-block', { timeout: 3000 });
    if (!await page.locator('.advertisement-query-block').isVisible())
      throw new Error('Filter panel did not open');
  });
  await screenshot(page, 'filter-ads-02-filter-panel-open');

  await check('Filter by title "Test"', async () => {
    await page.locator('.advertisement-query-block .query-text input').first().fill('Test');
    await screenshot(page, 'filter-ads-02b-dirty-state');
    await page.locator('.advertisement-query-block vaadin-button[title*="Застосувати"]').first().click();
    await page.waitForSelector('.advertisement-card', { timeout: 5000 }).catch(() => {});
    const cards = await page.locator('.advertisement-card').count();
    console.log(`      cards after filter: ${cards}`);
    const body = await page.textContent('.advertisement-container');
    console.log('      first result:', body.replace(/\s+/g, ' ').trim().slice(0, 150));
  });
  await screenshot(page, 'filter-ads-03-filtered-by-title');

  await check('Clear filters', async () => {
    await page.locator('.advertisement-query-block vaadin-button[title*="Очистити"]').first().click();
    await page.waitForSelector('.advertisement-card', { timeout: 5000 }).catch(() => {});
    const cards = await page.locator('.advertisement-card').count();
    console.log(`      cards after clear: ${cards}`);
  });
  await screenshot(page, 'filter-ads-04-cleared');

  await check('Sort by title (click sort icon)', async () => {
    await page.locator('.advertisement-query-block .sort-icon').first().click();
    await page.waitForSelector('.advertisement-title', { timeout: 3000 });
    const firstTitle = await page.locator('.advertisement-title').first().textContent();
    console.log('      first title after sort:', firstTitle.trim());
  });
  await screenshot(page, 'filter-ads-05-sorted-by-title');
});
