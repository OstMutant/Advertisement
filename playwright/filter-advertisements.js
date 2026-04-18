const { chromium } = require('playwright');
const { check, screenshot, login } = require('./_common');

// Tests advertisement filters: title text search + sort toggle
const UX = process.argv.includes('--ux');

(async () => {
  const browser = await chromium.launch();
  const page    = await browser.newPage({ viewport: { width: 1280, height: 900 } });

  await login(page);
  await page.waitForTimeout(1000);
  await screenshot(page, 'filter-ads-01-initial-list', UX);

  // Open filter panel (click the "▸ Filters" status bar toggle)
  await check('Open filter panel', async () => {
    await page.locator('.query-status-bar').first().click();
    await page.waitForTimeout(800);
    const visible = await page.locator('.advertisement-query-block').isVisible();
    if (!visible) throw new Error('Filter panel did not open');
  });
  await screenshot(page, 'filter-ads-02-filter-panel-open', UX);

  // Filter by title
  await check('Filter by title "Test"', async () => {
    const titleInput = page.locator('.advertisement-query-block .query-text input').first();
    await titleInput.fill('Test');
    await page.waitForTimeout(500);
    // Apply — find Apply/Search button inside queryActionBlock
    await page.locator('.advertisement-query-block vaadin-button[title*="Застосувати"]').first().click();
    await page.waitForTimeout(1500);
    const cards = await page.locator('.advertisement-card').count();
    console.log(`      cards after filter: ${cards}`);
    const body = await page.textContent('.advertisement-container');
    console.log('      first result:', body.replace(/\s+/g, ' ').trim().slice(0, 150));
  });
  await screenshot(page, 'filter-ads-03-filtered-by-title', UX);

  // Clear filter
  await check('Clear filters', async () => {
    await page.locator('.advertisement-query-block vaadin-button[title*="Очистити"]').first().click();
    await page.waitForTimeout(1500);
    const cards = await page.locator('.advertisement-card').count();
    console.log(`      cards after clear: ${cards}`);
  });
  await screenshot(page, 'filter-ads-04-cleared', UX);

  // Sort by title ascending
  await check('Sort by title (click sort icon)', async () => {
    const sortIcons = page.locator('.advertisement-query-block .sort-icon').first();
    await sortIcons.click();
    await page.waitForTimeout(1500);
    const firstTitle = await page.locator('.advertisement-title').first().textContent();
    console.log('      first title after sort:', firstTitle.trim());
  });
  await screenshot(page, 'filter-ads-05-sorted-by-title', UX);

  await browser.close();
})();
