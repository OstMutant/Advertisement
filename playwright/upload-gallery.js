const { check, screenshot, withBrowser, waitForOverlay, waitForOverlayClosed, downloadPng, createAd } = require('./_common');
const fs = require('fs');

const avatar = seed =>
  `https://api.dicebear.com/9.x/adventurer/png?seed=${seed}&size=256&backgroundColor=b6e3f4,c0aede,d1d4f9,ffd5dc,ffdfbf`;

async function openEditOverlay(page, title) {
  await page.waitForSelector('.advertisement-title', { timeout: 5000 });
  await page.locator('.advertisement-card')
    .filter({ has: page.locator('.advertisement-title', { hasText: title }) })
    .first().click();
  await waitForOverlay(page);
  await page.locator('vaadin-button').filter({ hasText: /редагувати|edit/i }).first().click();
  await page.waitForSelector('.base-overlay.overlay--visible vaadin-text-field input', { timeout: 5000 });
}

withBrowser(async (page) => {
  const seeds  = ['sunny', 'rocky', 'misty', 'comet'];
  const paths  = seeds.map(s => `/tmp/avatar-${s}.png`);

  await check('Download cartoon avatars', async () => {
    await Promise.all(seeds.map((s, i) => downloadPng(avatar(s), paths[i])));
    console.log(`      downloaded ${seeds.length} avatars`);
  });

  const SINGLE_TITLE   = 'Single Image House';
  const MULTIPLE_TITLE = 'Gallery Mansion';

  await check('Create test ads', async () => {
    await createAd(page, { title: SINGLE_TITLE,   description: 'A cozy house with one featured image' });
    await createAd(page, { title: MULTIPLE_TITLE, description: 'A luxurious mansion shown from many angles' });
    console.log('      2 ads created');
  });
  await screenshot(page, 'gallery-01-ads-created');

  // ── Case 1: single image ──────────────────────────────────────────────
  await check(`Upload single image to "${SINGLE_TITLE}"`, async () => {
    await openEditOverlay(page, SINGLE_TITLE);
    await page.locator('vaadin-upload input[type="file"]').setInputFiles(paths[0]);
    await page.waitForSelector('.attachment-gallery__item', { timeout: 10000 });
    const uploaded = await page.locator('.base-overlay.overlay--visible vaadin-upload').textContent();
    console.log('      upload area:', uploaded.replace(/\s+/g, ' ').trim().slice(0, 120));
    await page.locator('.base-overlay.overlay--visible vaadin-button')
      .filter({ hasText: /зберегти|save/i }).click();
    await page.waitForSelector('.overlay__view-title', { timeout: 5000 });
  });
  await screenshot(page, 'gallery-02-single-image-saved');

  await check('Verify single image visible in detail', async () => {
    const imgs = await page.locator('.base-overlay.overlay--visible img').count();
    console.log(`      images in detail overlay: ${imgs}`);
    if (imgs === 0) throw new Error('No image found in detail overlay');
  });
  await screenshot(page, 'gallery-03-single-image-detail');

  await page.locator('.overlay__breadcrumb-back').click();
  await waitForOverlayClosed(page).catch(() => {});

  // ── Case 2: multiple images ───────────────────────────────────────────
  await check(`Upload 3 images to "${MULTIPLE_TITLE}"`, async () => {
    await openEditOverlay(page, MULTIPLE_TITLE);
    await page.locator('vaadin-upload input[type="file"]').setInputFiles([paths[1], paths[2], paths[3]]);
    await page.waitForSelector('.attachment-gallery__item', { timeout: 10000 });
    const uploaded = await page.locator('.base-overlay.overlay--visible vaadin-upload').textContent();
    console.log('      upload area:', uploaded.replace(/\s+/g, ' ').trim().slice(0, 120));
    await page.locator('.base-overlay.overlay--visible vaadin-button')
      .filter({ hasText: /зберегти|save/i }).click();
    await page.waitForSelector('.overlay__view-title', { timeout: 5000 });
  });
  await screenshot(page, 'gallery-04-multiple-images-saved');

  await check('Verify multiple images visible in detail', async () => {
    const imgs = await page.locator('.base-overlay.overlay--visible img').count();
    console.log(`      images in detail overlay: ${imgs}`);
    if (imgs < 2) throw new Error(`Expected multiple images, got ${imgs}`);
  });
  await screenshot(page, 'gallery-05-multiple-images-detail');

  await page.locator('.overlay__breadcrumb-back').click();
  await waitForOverlayClosed(page).catch(() => {});

  await screenshot(page, 'gallery-06-final-list');
  paths.forEach(p => { if (fs.existsSync(p)) fs.unlinkSync(p); });
});
