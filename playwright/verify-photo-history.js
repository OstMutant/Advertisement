const { check, screenshot, withBrowser, waitForOverlay, waitForOverlayClosed, openHistory, closeOverlay, confirmDialog, downloadPng } = require('./_common');
const fs = require('fs');

// NOTE: Photo changes in history tab require AdvertisementHistoryExtension (Task 3).
// Until Task 3 is done, scenarios 1-3 will FAIL — this is expected.

const DICEBEAR = seed =>
  `https://api.dicebear.com/9.x/adventurer/png?seed=${seed}&size=256&backgroundColor=b6e3f4`;

async function hasPhotoChangesInHistory(page) {
  const items = page.locator('.adv-history-changes-item');
  const count = await items.count();
  for (let i = 0; i < count; i++) {
    const text = await items.nth(i).textContent();
    if (text && (text.includes('photos:') || text.includes('фото:'))) return true;
  }
  return false;
}

withBrowser(async (page) => {
  const img1 = '/tmp/photo-hist-1.png';
  const img2 = '/tmp/photo-hist-2.png';
  await downloadPng(DICEBEAR('test-hist-a'), img1);
  await downloadPng(DICEBEAR('test-hist-b'), img2);

  // ── Scenario 1: Create ad with photo → history must show photo changes ──
  await check('Scenario 1: create ad with photo → photo changes visible in history', async () => {
    await page.locator('.add-advertisement-button').click();
    await waitForOverlay(page);
    const overlay = page.locator('.advertisement-overlay');
    await overlay.locator('vaadin-text-field input').fill('Photo History Test Ad');
    await overlay.locator('vaadin-text-area textarea').fill('Testing photo history');
    await overlay.locator('vaadin-upload input[type="file"]').setInputFiles(img1);
    await page.waitForSelector('.attachment-gallery__item', { timeout: 10000 });
    await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await waitForOverlayClosed(page);
    await screenshot(page, 'photo-hist-01-after-create');

    await page.locator('.advertisement-card')
      .filter({ has: page.locator('.advertisement-title', { hasText: 'Photo History Test Ad' }) })
      .first().click();
    await waitForOverlay(page);

    const ok = await openHistory(page);
    if (!ok) throw new Error('No history tab');
    await screenshot(page, 'photo-hist-02-create-history');

    const rows = await page.locator('.adv-history-row').count();
    console.log(`      history rows: ${rows}`);
    if (rows !== 1) throw new Error(`Expected 1 history row on create, got ${rows}`);

    const hasPhoto = await hasPhotoChangesInHistory(page);
    if (!hasPhoto) throw new Error('No "photos:" or "фото:" entry found in history changes — photo changes not visible');
    console.log('      [OK] photo changes visible in CREATED row');

    await closeOverlay(page);
  });

  // ── Scenario 2: Edit ad, delete photo → photo deletion in history ──────
  await check('Scenario 2: delete photo → photo deletion visible in history', async () => {
    await page.locator('.advertisement-card')
      .filter({ has: page.locator('.advertisement-title', { hasText: 'Photo History Test Ad' }) })
      .first().click();
    await waitForOverlay(page);
    await page.locator('vaadin-button').filter({ hasText: /редагувати|edit/i }).first().click();
    await page.waitForSelector('.base-overlay.overlay--visible vaadin-text-field input', { timeout: 5000 });
    await page.locator('vaadin-upload input[type="file"]').setInputFiles(img2);
    await page.waitForSelector('.attachment-gallery__item', { timeout: 10000 });
    await page.locator('.base-overlay.overlay--visible vaadin-button')
      .filter({ hasText: /зберегти|save/i }).click();
    await page.waitForSelector('.overlay__view-title', { timeout: 5000 });
    await closeOverlay(page);

    await page.locator('.advertisement-card')
      .filter({ has: page.locator('.advertisement-title', { hasText: 'Photo History Test Ad' }) })
      .first().click();
    await waitForOverlay(page);
    await page.locator('vaadin-button').filter({ hasText: /редагувати|edit/i }).first().click();
    await page.waitForSelector('.base-overlay.overlay--visible vaadin-text-field input', { timeout: 5000 });

    const deleteBtn = page.locator('.attachment-gallery__delete-btn').first();
    await deleteBtn.click();
    await screenshot(page, 'photo-hist-03-before-save-after-delete');

    await page.locator('.base-overlay.overlay--visible vaadin-button')
      .filter({ hasText: /зберегти|save/i }).click();
    await page.waitForSelector('.overlay__view-title', { timeout: 5000 });

    const ok = await openHistory(page);
    if (!ok) throw new Error('No history tab');
    await screenshot(page, 'photo-hist-04-history-after-delete');

    const hasPhoto = await hasPhotoChangesInHistory(page);
    if (!hasPhoto) throw new Error('No "photos:" or "фото:" entry found — photo deletion not visible in history');
    console.log('      [OK] photo deletion visible in history');

    await closeOverlay(page);
  });

  // ── Scenario 3: Restore → photo change appears in history ─────────────
  await check('Scenario 3: restore → photo change visible in history', async () => {
    await page.locator('.advertisement-card')
      .filter({ has: page.locator('.advertisement-title', { hasText: 'Photo History Test Ad' }) })
      .first().click();
    await waitForOverlay(page);

    const ok = await openHistory(page);
    if (!ok) throw new Error('No history tab');

    const restoreBtn = page.locator('.adv-history-restore-btn').first();
    if (await restoreBtn.count() === 0) throw new Error('No restore buttons');
    await restoreBtn.click();

    await confirmDialog(page);
    await page.waitForSelector('.overlay__view-title', { timeout: 5000 });
    await screenshot(page, 'photo-hist-06-after-restore');

    const ok2 = await openHistory(page);
    if (!ok2) throw new Error('No history tab after restore');
    await screenshot(page, 'photo-hist-07-history-after-restore');

    const hasPhoto = await hasPhotoChangesInHistory(page);
    if (!hasPhoto) throw new Error('No "photos:" or "фото:" entry — photo changes not visible after restore');
    console.log('      [OK] photo changes visible in history after restore');

    await closeOverlay(page);
  });

  [img1, img2].forEach(f => { try { fs.unlinkSync(f); } catch (_) {} });
  await screenshot(page, 'photo-hist-05-done');
  console.log('verify-photo-history: all checks passed');
});
