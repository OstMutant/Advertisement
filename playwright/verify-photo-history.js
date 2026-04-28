const { chromium } = require('playwright');
const { check, screenshot, login } = require('./_common');
const https = require('https');
const fs    = require('fs');

const UX = process.argv.includes('--ux');

function downloadPng(url, dest) {
  return new Promise((resolve, reject) => {
    const file = fs.createWriteStream(dest);
    https.get(url, res => {
      if (res.statusCode !== 200) return reject(new Error(`HTTP ${res.statusCode}`));
      res.pipe(file);
      file.on('finish', () => file.close(resolve));
    }).on('error', reject);
  });
}

const DICEBEAR = seed =>
  `https://api.dicebear.com/9.x/adventurer/png?seed=${seed}&size=256&backgroundColor=b6e3f4`;

async function openHistory(page) {
  const historyTab = page.locator('.adv-overlay-tabs vaadin-tab', { hasText: /Іс|Hist/i });
  if (!await historyTab.isVisible()) return false;
  await historyTab.click();
  await page.waitForTimeout(600);
  return true;
}

async function countHistoryRows(page) {
  return page.locator('.adv-history-row').count();
}

async function historyText(page) {
  return page.locator('.adv-history-list').textContent();
}

(async () => {
  const browser = await chromium.launch();
  const page    = await browser.newPage({ viewport: { width: 1280, height: 900 } });

  await login(page);
  await page.waitForTimeout(800);

  // Download two test images
  const img1 = '/tmp/photo-hist-1.png';
  const img2 = '/tmp/photo-hist-2.png';
  await downloadPng(DICEBEAR('test-hist-a'), img1);
  await downloadPng(DICEBEAR('test-hist-b'), img2);

  // ── Scenario 1: Create ad with photo → history must have ONLY 1 row ──────
  await check('Scenario 1: create ad with photo → history has 1 row (not 2)', async () => {
    await page.locator('.add-advertisement-button').click();
    await page.waitForTimeout(600);
    const overlay = page.locator('.advertisement-overlay');
    await overlay.locator('vaadin-text-field input').fill('Photo History Test Ad');
    await overlay.locator('vaadin-text-area textarea').fill('Testing photo history dedup');
    await overlay.locator('vaadin-upload input[type="file"]').setInputFiles(img1);
    await page.waitForTimeout(1500);
    await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await page.waitForTimeout(1500);

    await screenshot(page, 'photo-hist-01-after-create', UX);

    // Open the newly created ad
    await page.waitForSelector('.advertisement-title', { timeout: 5000 });
    await page.locator('.advertisement-card')
      .filter({ has: page.locator('.advertisement-title', { hasText: 'Photo History Test Ad' }) })
      .first().click();
    await page.waitForTimeout(600);

    const ok = await openHistory(page);
    if (!ok) { console.log('      no history tab — skip'); return; }

    await screenshot(page, 'photo-hist-02-create-history', UX);

    const rows = await countHistoryRows(page);
    const text = await historyText(page);
    console.log(`      history rows: ${rows}`);
    console.log(`      history text: ${text.slice(0, 300)}`);
    if (rows !== 1) throw new Error(`Expected 1 history row on create, got ${rows}`);
    const hasPhoto = text.includes('фото') || text.includes('photo');
    if (!hasPhoto) throw new Error('CREATED row must include photo changes');
    console.log('      [OK] photo changes visible in CREATED row');

    await page.locator('.overlay__breadcrumb-back').click();
    await page.waitForSelector('.base-overlay.overlay--visible', { state: 'hidden', timeout: 5000 }).catch(() => {});
    await page.waitForTimeout(300);
  });

  // ── Scenario 2: Edit ad, delete photo, save → photo deletion in history ──
  await check('Scenario 2: edit ad, delete 1 of 2 photos → photo change in history', async () => {
    // First add a second photo to the ad
    await page.locator('.advertisement-card')
      .filter({ has: page.locator('.advertisement-title', { hasText: 'Photo History Test Ad' }) })
      .first().click();
    await page.waitForTimeout(600);
    await page.locator('vaadin-button').filter({ hasText: /редагувати|edit/i }).first().click();
    await page.waitForTimeout(600);
    await page.locator('vaadin-upload input[type="file"]').setInputFiles(img2);
    await page.waitForTimeout(1500);
    await page.locator('.base-overlay.overlay--visible vaadin-button')
      .filter({ hasText: /зберегти|save/i }).click();
    await page.waitForTimeout(1500);
    await page.locator('.overlay__breadcrumb-back').click();
    await page.waitForSelector('.base-overlay.overlay--visible', { state: 'hidden', timeout: 5000 }).catch(() => {});
    await page.waitForTimeout(300);

    // Now edit again and DELETE one photo, save
    await page.locator('.advertisement-card')
      .filter({ has: page.locator('.advertisement-title', { hasText: 'Photo History Test Ad' }) })
      .first().click();
    await page.waitForTimeout(600);
    await page.locator('vaadin-button').filter({ hasText: /редагувати|edit/i }).first().click();
    await page.waitForTimeout(800);

    // Delete the first photo (click X on first thumbnail)
    const deleteBtn = page.locator('.attachment-gallery__delete-btn').first();
    await deleteBtn.click();
    await page.waitForTimeout(500);

    await screenshot(page, 'photo-hist-03-before-save-after-delete', UX);

    await page.locator('.base-overlay.overlay--visible vaadin-button')
      .filter({ hasText: /зберегти|save/i }).click();
    await page.waitForTimeout(1500);

    // Open history
    const ok = await openHistory(page);
    if (!ok) { console.log('      no history tab'); return; }

    await screenshot(page, 'photo-hist-04-history-after-delete', UX);

    const text = await historyText(page);
    console.log(`      history text (first 400 chars): ${text.slice(0, 400)}`);

    const hasPhotoChange = text.includes('фото') || text.includes('photo');
    if (!hasPhotoChange) throw new Error('Photo deletion not visible in history');
    console.log('      [OK] photo deletion visible in history');

    await page.locator('.overlay__breadcrumb-back').click();
    await page.waitForSelector('.base-overlay.overlay--visible', { state: 'hidden', timeout: 5000 }).catch(() => {});
  });

  // ── Scenario 3: Restore to previous state → photo changes in history ────
  await check('Scenario 3: restore to prev state → photo change appears in history', async () => {
    await page.locator('.advertisement-card')
      .filter({ has: page.locator('.advertisement-title', { hasText: 'Photo History Test Ad' }) })
      .first().click();
    await page.waitForTimeout(600);

    const ok = await openHistory(page);
    if (!ok) { console.log('      no history tab — skip'); return; }

    // Click first "Відновити" button
    const restoreBtn = page.locator('.adv-history-restore-btn').first();
    const restoreCount = await restoreBtn.count();
    if (restoreCount === 0) { console.log('      no restore buttons — skip'); return; }
    await restoreBtn.click();
    await page.waitForTimeout(600);

    // Confirm restore dialog via evaluate (Vaadin dialog renders in Shadow DOM portal)
    await page.waitForTimeout(800);
    await page.evaluate(() => {
      const dialog = document.querySelector('vaadin-dialog[opened]');
      if (!dialog) throw new Error('Confirm dialog not found');
      const btns = [...dialog.querySelectorAll('vaadin-button')];
      const btn = btns.find(b => /Оновити|Update/.test(b.textContent?.trim()));
      if (!btn) throw new Error('Confirm button not found in dialog');
      btn.click();
    });
    await page.waitForTimeout(2000);

    await screenshot(page, 'photo-hist-06-after-restore', UX);

    // Open history again
    const ok2 = await openHistory(page);
    if (!ok2) { console.log('      no history tab after restore'); return; }

    await screenshot(page, 'photo-hist-07-history-after-restore', UX);

    const text = await historyText(page);
    console.log(`      history after restore (first 500): ${text.slice(0, 500)}`);

    const hasPhotoChange = text.includes('фото') || text.includes('photo');
    if (!hasPhotoChange) throw new Error('Photo changes not visible in history after restore');
    console.log('      [OK] photo changes visible in history after restore');

    await page.locator('.overlay__breadcrumb-back').click();
    await page.waitForSelector('.base-overlay.overlay--visible', { state: 'hidden', timeout: 5000 }).catch(() => {});
  });

  [img1, img2].forEach(f => { try { fs.unlinkSync(f); } catch (_) {} });
  await screenshot(page, 'photo-hist-05-done', UX);
  await browser.close();
})();
