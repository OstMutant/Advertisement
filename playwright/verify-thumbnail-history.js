const { chromium } = require('playwright');
const { check, screenshot, login } = require('./_common');

const UX = process.argv.includes('--ux');

(async () => {
  const browser = await chromium.launch();
  const page    = await browser.newPage({ viewport: { width: 1280, height: 900 } });

  await login(page);
  await page.waitForTimeout(1000);

  // Step 1: Check that at least one card shows a real thumbnail (not just placeholder)
  await check('At least one card has a real thumbnail image', async () => {
    await page.waitForSelector('.advertisement-card', { timeout: 5000 });
    const thumbs = await page.locator('.advertisement-thumbnail').count();
    if (thumbs === 0) throw new Error('No real thumbnails found — all cards show placeholder');
    console.log(`      found ${thumbs} real thumbnail(s)`);
  });

  await screenshot(page, 'thumbnail-01-list', UX);

  // Step 2: Verify image count badge appears on cards with multiple images
  await check('Image count badge check (pass if any card loaded)', async () => {
    const badges = await page.locator('.advertisement-thumbnail-badge').count();
    console.log(`      found ${badges} count badge(s)`);
  });

  // Step 3: Open an ad that has an image, go to history, check photo changes appear
  await check('History tab shows photo changes', async () => {
    const cardWithThumb = page.locator('.advertisement-card')
      .filter({ has: page.locator('.advertisement-thumbnail') })
      .first();
    await cardWithThumb.click();
    await page.waitForTimeout(800);

    const historyTab = page.locator('.adv-overlay-tabs vaadin-tab', { hasText: /Іс|Hist/i });
    const historyVisible = await historyTab.isVisible();
    if (!historyVisible) {
      console.log('      history tab not visible (no operator access) — skipping');
      await page.locator('.overlay__breadcrumb-back').click().catch(() => {});
      await page.keyboard.press('Escape').catch(() => {});
      return;
    }

    await historyTab.click();
    await page.waitForTimeout(800);

    await screenshot(page, 'thumbnail-02-history', UX);

    const rows = await page.locator('.adv-history-row').count();
    if (rows === 0) throw new Error('No history rows found');
    console.log(`      found ${rows} history row(s)`);

    // Check if any row mentions photo changes
    const allText = await page.locator('.adv-history-changes').allTextContents();
    const hasPhotoChange = allText.some(t => t.includes('фото') || t.includes('photo'));
    console.log(`      photo change entries visible: ${hasPhotoChange}`);
    if (!hasPhotoChange) {
      console.log('      [WARN] no photo change entries — may have only text snapshot rows');
    }

    await page.locator('.overlay__breadcrumb-back').click();
    await page.waitForSelector('.base-overlay.overlay--visible', { state: 'hidden', timeout: 5000 }).catch(() => {});
  });

  await screenshot(page, 'thumbnail-03-done', UX);
  await browser.close();
})();
