const { chromium } = require('playwright');
const { check, screenshot, login } = require('./_common');
const https = require('https');
const fs    = require('fs');

// NOTE: Photo changes in history tab require AdvertisementHistoryExtension (Task 3).
// Until Task 3 is done, scenarios 1-3 will FAIL — this is expected.

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

async function hasPhotoChangesInHistory(page) {
  // Look for .adv-history-changes-item that contains "photos:" or "фото:"
  const items = page.locator('.adv-history-changes-item');
  const count = await items.count();
  for (let i = 0; i < count; i++) {
    const text = await items.nth(i).textContent();
    if (text && (text.includes('photos:') || text.includes('фото:'))) return true;
  }
  return false;
}

(async () => {
  const browser = await chromium.launch();
  const page    = await browser.newPage({ viewport: { width: 1280, height: 900 } });

  await login(page);
  await page.waitForTimeout(800);

  const img1 = '/tmp/photo-hist-1.png';
  const img2 = '/tmp/photo-hist-2.png';
  await downloadPng(DICEBEAR('test-hist-a'), img1);
  await downloadPng(DICEBEAR('test-hist-b'), img2);

  // ── Scenario 1: Create ad with photo → history must show photo changes ──────
  await check('Scenario 1: create ad with photo → photo changes visible in history', async () => {
    await page.locator('.add-advertisement-button').click();
    await page.waitForTimeout(600);
    const overlay = page.locator('.advertisement-overlay');
    await overlay.locator('vaadin-text-field input').fill('Photo History Test Ad');
    await overlay.locator('vaadin-text-area textarea').fill('Testing photo history');
    await overlay.locator('vaadin-upload input[type="file"]').setInputFiles(img1);
    await page.waitForTimeout(1500);
    await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await page.waitForTimeout(1500);

    await screenshot(page, 'photo-hist-01-after-create', UX);

    await page.waitForSelector('.advertisement-title', { timeout: 5000 });
    await page.locator('.advertisement-card')
      .filter({ has: page.locator('.advertisement-title', { hasText: 'Photo History Test Ad' }) })
      .first().click();
    await page.waitForTimeout(600);

    const ok = await openHistory(page);
    if (!ok) throw new Error('No history tab');

    await screenshot(page, 'photo-hist-02-create-history', UX);

    const rows = await page.locator('.adv-history-row').count();
    console.log(`      history rows: ${rows}`);
    if (rows !== 1) throw new Error(`Expected 1 history row on create, got ${rows}`);

    const hasPhoto = await hasPhotoChangesInHistory(page);
    if (!hasPhoto) throw new Error('No "photos:" or "фото:" entry found in history changes — photo changes not visible');
    console.log('      [OK] photo changes visible in CREATED row');

    await page.locator('.overlay__breadcrumb-back').click();
    await page.waitForSelector('.base-overlay.overlay--visible', { state: 'hidden', timeout: 5000 }).catch(() => {});
    await page.waitForTimeout(300);
  });

  // ── Scenario 2: Edit ad, delete photo → photo deletion in history ──────────
  await check('Scenario 2: delete photo → photo deletion visible in history', async () => {
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

    await page.locator('.advertisement-card')
      .filter({ has: page.locator('.advertisement-title', { hasText: 'Photo History Test Ad' }) })
      .first().click();
    await page.waitForTimeout(600);
    await page.locator('vaadin-button').filter({ hasText: /редагувати|edit/i }).first().click();
    await page.waitForTimeout(800);

    const deleteBtn = page.locator('.attachment-gallery__delete-btn').first();
    await deleteBtn.click();
    await page.waitForTimeout(500);

    await screenshot(page, 'photo-hist-03-before-save-after-delete', UX);

    await page.locator('.base-overlay.overlay--visible vaadin-button')
      .filter({ hasText: /зберегти|save/i }).click();
    await page.waitForTimeout(1500);

    const ok = await openHistory(page);
    if (!ok) throw new Error('No history tab');

    await screenshot(page, 'photo-hist-04-history-after-delete', UX);

    const hasPhoto = await hasPhotoChangesInHistory(page);
    if (!hasPhoto) throw new Error('No "photos:" or "фото:" entry found — photo deletion not visible in history');
    console.log('      [OK] photo deletion visible in history');

    await page.locator('.overlay__breadcrumb-back').click();
    await page.waitForSelector('.base-overlay.overlay--visible', { state: 'hidden', timeout: 5000 }).catch(() => {});
  });

  // ── Scenario 3: Restore → photo change appears in history ─────────────────
  await check('Scenario 3: restore → photo change visible in history', async () => {
    await page.locator('.advertisement-card')
      .filter({ has: page.locator('.advertisement-title', { hasText: 'Photo History Test Ad' }) })
      .first().click();
    await page.waitForTimeout(600);

    const ok = await openHistory(page);
    if (!ok) throw new Error('No history tab');

    const restoreBtn = page.locator('.adv-history-restore-btn').first();
    if (await restoreBtn.count() === 0) throw new Error('No restore buttons');
    await restoreBtn.click();
    await page.waitForTimeout(800);

    await page.evaluate(() => {
      const dialog = document.querySelector('vaadin-dialog[opened]');
      if (!dialog) throw new Error('Confirm dialog not found');
      const btn = [...dialog.querySelectorAll('vaadin-button')]
        .find(b => /Оновити|Update/.test(b.textContent?.trim()));
      if (!btn) throw new Error('Confirm button not found');
      btn.click();
    });
    await page.waitForTimeout(2000);

    await screenshot(page, 'photo-hist-06-after-restore', UX);

    const ok2 = await openHistory(page);
    if (!ok2) throw new Error('No history tab after restore');

    await screenshot(page, 'photo-hist-07-history-after-restore', UX);

    const hasPhoto = await hasPhotoChangesInHistory(page);
    if (!hasPhoto) throw new Error('No "photos:" or "фото:" entry — photo changes not visible after restore');
    console.log('      [OK] photo changes visible in history after restore');

    await page.locator('.overlay__breadcrumb-back').click();
    await page.waitForSelector('.base-overlay.overlay--visible', { state: 'hidden', timeout: 5000 }).catch(() => {});
  });

  [img1, img2].forEach(f => { try { fs.unlinkSync(f); } catch (_) {} });
  await screenshot(page, 'photo-hist-05-done', UX);
  console.log('verify-photo-history: all checks passed');
  await browser.close();
})();
