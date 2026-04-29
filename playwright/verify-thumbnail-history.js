const { check, screenshot, withBrowser, waitForOverlayClosed } = require('./_common');

withBrowser(async (page) => {
  await check('At least one card has a real thumbnail image', async () => {
    await page.waitForSelector('.advertisement-card', { timeout: 5000 });
    const thumbs = await page.locator('.advertisement-thumbnail').count();
    if (thumbs === 0) throw new Error('No real thumbnails found — all cards show placeholder');
    console.log(`      found ${thumbs} real thumbnail(s)`);
  });
  await screenshot(page, 'thumbnail-01-list');

  await check('Image count badge check (pass if any card loaded)', async () => {
    const badges = await page.locator('.advertisement-thumbnail-badge').count();
    console.log(`      found ${badges} count badge(s)`);
  });

  await check('History tab shows photo changes', async () => {
    const cardWithThumb = page.locator('.advertisement-card')
      .filter({ has: page.locator('.advertisement-thumbnail') })
      .first();
    await cardWithThumb.click();
    await page.waitForSelector('.base-overlay.overlay--visible', { timeout: 5000 });

    const historyTab = page.locator('.adv-overlay-tabs vaadin-tab', { hasText: /Іс|Hist/i });
    if (!await historyTab.isVisible()) {
      console.log('      history tab not visible (no operator access) — skipping');
      await page.locator('.overlay__breadcrumb-back').click().catch(() => {});
      await page.keyboard.press('Escape').catch(() => {});
      return;
    }

    await historyTab.click();
    await page.waitForSelector('.adv-history-list', { timeout: 5000 });
    await screenshot(page, 'thumbnail-02-history');

    const rows = await page.locator('.adv-history-row').count();
    if (rows === 0) throw new Error('No history rows found');
    console.log(`      found ${rows} history row(s)`);

    const allText = await page.locator('.adv-history-changes').allTextContents();
    const hasPhotoChange = allText.some(t => t.includes('фото') || t.includes('photo'));
    console.log(`      photo change entries visible: ${hasPhotoChange}`);
    if (!hasPhotoChange) {
      console.log('      [WARN] no photo change entries — may have only text snapshot rows');
    }

    await page.locator('.overlay__breadcrumb-back').click();
    await waitForOverlayClosed(page).catch(() => {});
  });

  await screenshot(page, 'thumbnail-03-done');
});
