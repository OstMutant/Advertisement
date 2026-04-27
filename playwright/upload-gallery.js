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

const avatar = (seed) =>
  `https://api.dicebear.com/9.x/adventurer/png?seed=${seed}&size=256&backgroundColor=b6e3f4,c0aede,d1d4f9,ffd5dc,ffdfbf`;

async function createAd(page, title, desc) {
  await page.locator('.add-advertisement-button').click();
  await page.waitForTimeout(800);
  const overlay = page.locator('.advertisement-overlay');
  await overlay.locator('vaadin-text-field input').fill(title);
  await overlay.locator('vaadin-text-area textarea').fill(desc);
  await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
  await page.waitForTimeout(1500);
}

async function openEditOverlay(page, title) {
  await page.waitForSelector('.advertisement-title', { timeout: 5000 });
  await page.locator('.advertisement-card')
    .filter({ has: page.locator('.advertisement-title', { hasText: title }) })
    .first().click();
  await page.waitForTimeout(800);
  await page.locator('vaadin-button').filter({ hasText: /редагувати|edit/i }).first().click();
  await page.waitForTimeout(800);
}

async function saveOverlay(page) {
  await page.locator('.base-overlay.overlay--visible vaadin-button')
    .filter({ hasText: /зберегти|save/i }).click();
  await page.waitForTimeout(1500);
}

(async () => {
  const browser = await chromium.launch();
  const page    = await browser.newPage({ viewport: { width: 1280, height: 900 } });

  await login(page);
  await page.waitForTimeout(1000);

  // ── Download avatars up front ───────────────────────────────────────────────
  const seeds  = ['sunny', 'rocky', 'misty', 'comet'];
  const paths  = seeds.map(s => `/tmp/avatar-${s}.png`);
  await check('Download cartoon avatars', async () => {
    await Promise.all(seeds.map((s, i) => downloadPng(avatar(s), paths[i])));
    console.log(`      downloaded ${seeds.length} avatars`);
  });

  // ── Create two test ads ─────────────────────────────────────────────────────
  const SINGLE_TITLE   = 'Single Image House';
  const MULTIPLE_TITLE = 'Gallery Mansion';

  await check('Create test ads', async () => {
    await createAd(page, SINGLE_TITLE,   'A cozy house with one featured image');
    await createAd(page, MULTIPLE_TITLE, 'A luxurious mansion shown from many angles');
    console.log('      2 ads created');
  });
  await screenshot(page, 'gallery-01-ads-created', UX);

  // ── Case 1: single image ────────────────────────────────────────────────────
  await check(`Upload single image to "${SINGLE_TITLE}"`, async () => {
    await openEditOverlay(page, SINGLE_TITLE);
    await page.locator('vaadin-upload input[type="file"]').setInputFiles(paths[0]);
    await page.waitForTimeout(2000);
    const uploaded = await page.locator('.base-overlay.overlay--visible vaadin-upload').textContent();
    console.log('      upload area:', uploaded.replace(/\s+/g, ' ').trim().slice(0, 120));
    await saveOverlay(page);
  });
  await screenshot(page, 'gallery-02-single-image-saved', UX);

  // After save, overlay switches to view mode — verify images directly without reopening
  await check(`Verify single image visible in detail`, async () => {
    await page.waitForTimeout(500);
    const imgs = await page.locator('.base-overlay.overlay--visible img').count();
    console.log(`      images in detail overlay: ${imgs}`);
    if (imgs === 0) throw new Error('No image found in detail overlay');
  });
  await screenshot(page, 'gallery-03-single-image-detail', UX);

  // Back to list
  await page.locator('.overlay__breadcrumb-back').click();
  await page.waitForTimeout(800);

  // ── Case 2: multiple images ─────────────────────────────────────────────────
  await check(`Upload 3 images to "${MULTIPLE_TITLE}"`, async () => {
    await openEditOverlay(page, MULTIPLE_TITLE);
    // Upload all 3 at once
    await page.locator('vaadin-upload input[type="file"]').setInputFiles([paths[1], paths[2], paths[3]]);
    await page.waitForTimeout(3000);
    const uploaded = await page.locator('.base-overlay.overlay--visible vaadin-upload').textContent();
    console.log('      upload area:', uploaded.replace(/\s+/g, ' ').trim().slice(0, 120));
    await saveOverlay(page);
  });
  await screenshot(page, 'gallery-04-multiple-images-saved', UX);

  // After save, overlay switches to view mode — verify images directly
  await check(`Verify multiple images visible in detail`, async () => {
    await page.waitForTimeout(500);
    const imgs = await page.locator('.base-overlay.overlay--visible img').count();
    console.log(`      images in detail overlay: ${imgs}`);
    if (imgs < 2) throw new Error(`Expected multiple images, got ${imgs}`);
  });
  await screenshot(page, 'gallery-05-multiple-images-detail', UX);

  // Back to list via breadcrumb button
  await page.locator('.overlay__breadcrumb-back').click();
  await page.waitForTimeout(800);

  await screenshot(page, 'gallery-06-final-list', UX);
  await browser.close();

  // Cleanup temp files
  paths.forEach(p => { if (fs.existsSync(p)) fs.unlinkSync(p); });
})();
