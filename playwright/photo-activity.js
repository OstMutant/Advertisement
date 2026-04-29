const { check, screenshot, withBrowser, waitForOverlay, waitForOverlayClosed,
        openAdDetail, openHistory, openSettings, openActivityTab, confirmDialog } = require('./_common');
const fs = require('fs');
const zlib = require('zlib');

// Scenario: create ad with photos → verify history + user/settings activity show photo changes
//           → edit photos → verify again → restore → verify growth
// Runs as user3 (admin — needed to access the Users tab)

const AD_TITLE = 'Photo Activity Smoke';

// Generate a minimal valid 64×64 RGB PNG with a solid color
function makePng(path, r, g, b) {
  const w = 64, h = 64;
  const rowSize = 1 + w * 3;
  const raw = Buffer.alloc(h * rowSize);
  for (let y = 0; y < h; y++) {
    raw[y * rowSize] = 0;
    for (let x = 0; x < w; x++) {
      raw[y * rowSize + 1 + x * 3] = r;
      raw[y * rowSize + 2 + x * 3] = g;
      raw[y * rowSize + 3 + x * 3] = b;
    }
  }
  const idat = zlib.deflateSync(raw);
  function crc32(buf) {
    let c = 0xFFFFFFFF >>> 0;
    for (let i = 0; i < buf.length; i++) {
      c ^= buf[i];
      for (let k = 0; k < 8; k++) c = ((c >>> 1) ^ (0xEDB88320 & -(c & 1))) >>> 0;
    }
    return (c ^ 0xFFFFFFFF) >>> 0;
  }
  function mkchunk(tag, data) {
    const t = Buffer.from(tag, 'ascii');
    const combined = Buffer.concat([t, data]);
    const out = Buffer.alloc(4 + 4 + data.length + 4);
    out.writeUInt32BE(data.length, 0);
    t.copy(out, 4); data.copy(out, 8);
    out.writeUInt32BE(crc32(combined), 8 + data.length);
    return out;
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; ihdr[9] = 2;
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  fs.writeFileSync(path, Buffer.concat([sig, mkchunk('IHDR', ihdr), mkchunk('IDAT', idat), mkchunk('IEND', Buffer.alloc(0))]));
}

async function openEditMode(page) {
  await page.locator('.base-overlay.overlay--visible vaadin-button')
    .filter({ hasText: /edit|редагувати/i }).first().click();
  await page.waitForSelector('.base-overlay.overlay--visible vaadin-text-field input', { timeout: 5000 });
}

// Close the ad overlay by switching to View tab then clicking breadcrumb
async function closeAdOverlay(page) {
  await page.locator('.adv-overlay-tabs vaadin-tab').filter({ hasText: /view|перегляд/i }).click();
  await page.waitForSelector('.overlay__view-title', { timeout: 3000 });
  // Scope breadcrumb click to the advertisement overlay specifically
  await page.locator('.advertisement-overlay .overlay__breadcrumb-back').click();
  await page.waitForFunction(() =>
    !document.querySelector('.advertisement-overlay.overlay--visible'), { timeout: 8000 });
}

function checkPhotoInText(text, label) {
  const count = (text.match(/фото|photo/gi) || []).length;
  console.log(`      [${label}] photo mentions: ${count}`);
  console.log(`      [${label}] text: ${text.replace(/\s+/g, ' ').trim().slice(0, 500)}`);
  if (count === 0) throw new Error(`No photo change in ${label}: ` + text.slice(0, 200));
}

withBrowser(async (page) => {
  const paths = ['/tmp/phact-1.png', '/tmp/phact-2.png', '/tmp/phact-3.png'];

  await check('Create test images', async () => {
    makePng(paths[0], 100, 150, 200);
    makePng(paths[1], 200, 100, 150);
    makePng(paths[2], 150, 200, 100);
    console.log('      created', paths.length, 'test PNGs');
  });

  // ── STEP 1: Create ad with first photo ───────────────────────────────
  await check('Create ad with first photo', async () => {
    await page.locator('.add-advertisement-button').click();
    await waitForOverlay(page);
    const overlay = page.locator('.advertisement-overlay');
    await overlay.locator('vaadin-text-field input').fill(AD_TITLE);
    await overlay.locator('vaadin-text-area textarea').fill('Photo activity test description');
    await overlay.locator('vaadin-upload input[type="file"]').setInputFiles(paths[0]);
    await page.waitForSelector('.attachment-gallery__item', { timeout: 10000 });
    await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await page.waitForFunction(() =>
      !document.querySelector('.advertisement-overlay.overlay--visible'), { timeout: 8000 });
  });
  await screenshot(page, 'phact-01-ad-created');

  // ── STEP 2: Open ad → history tab ────────────────────────────────────
  await openAdDetail(page, AD_TITLE);
  await openHistory(page);
  await screenshot(page, 'phact-02-history-after-create');

  await check('History shows photo change after create', async () => {
    const text = await page.locator('.adv-history-list').textContent();
    checkPhotoInText(text, 'history-after-create');
  });

  await closeAdOverlay(page);

  // ── STEP 3: User activity tab ─────────────────────────────────────────
  await page.locator('vaadin-tab').filter({ hasText: /users|юзер|користувач/i }).first().click();
  await page.waitForSelector('vaadin-grid.user-grid', { timeout: 8000 });
  await page.locator('vaadin-grid.user-grid vaadin-grid-cell-content .user-grid-name').first().click();
  await waitForOverlay(page);
  await screenshot(page, 'phact-03-user-overlay-profile');

  await openActivityTab(page);
  await screenshot(page, 'phact-04-user-activity-after-create');

  await check('User activity shows photo change after create', async () => {
    const text = await page.locator('.base-overlay.overlay--visible .user-activity-list').textContent();
    checkPhotoInText(text, 'user-activity-after-create');
  });

  await page.locator('.base-overlay.overlay--visible vaadin-button')
    .filter({ hasText: /користувач|users/i }).last().click();
  await waitForOverlayClosed(page);

  // ── STEP 4: Settings activity after create ────────────────────────────
  await openSettings(page);
  await openActivityTab(page);
  await screenshot(page, 'phact-05-settings-activity-after-create');

  await check('Settings activity shows photo change after create', async () => {
    const text = await page.locator('.base-overlay.overlay--visible .user-activity-list').textContent();
    checkPhotoInText(text, 'settings-activity-after-create');
  });

  await page.keyboard.press('Escape');
  await waitForOverlayClosed(page);

  // ── STEP 4b: Edit title only (no photo change) — photo must show unchanged ──
  await page.locator('vaadin-tab').filter({ hasText: /advertisement|оголошен/i }).first().click();
  await page.waitForSelector('.advertisement-container', { timeout: 5000 });
  await openAdDetail(page, AD_TITLE);
  await openEditMode(page);
  await page.locator('.base-overlay.overlay--visible vaadin-text-field input').fill(AD_TITLE + ' Renamed');
  await page.locator('.base-overlay.overlay--visible vaadin-button')
    .filter({ hasText: /зберегти|save/i }).click();
  await page.waitForSelector('.overlay__view-title', { timeout: 5000 });

  await openHistory(page);
  await screenshot(page, 'phact-05b-history-text-only-edit');
  await check('History shows photo as unchanged when only text edited', async () => {
    const rows = page.locator('.adv-history-row');
    const latest = rows.first();
    // Find the specific change item span that mentions "фото"
    const items = latest.locator('.adv-history-changes-item');
    const count = await items.count();
    let photoItem = null;
    for (let i = 0; i < count; i++) {
      const t = await items.nth(i).textContent();
      if (/фото\s*:|photos?\s*:/i.test(t)) { photoItem = t; break; }
    }
    console.log('      photo change item:', photoItem);
    if (!photoItem) throw new Error('Photo not shown in text-only-edit history row');
    if (/→/.test(photoItem)) throw new Error('Arrow → should not appear for unchanged photo: ' + photoItem);
  });

  // Rename back for subsequent steps
  await page.locator('.adv-overlay-tabs vaadin-tab').filter({ hasText: /view|перегляд/i }).click();
  await page.waitForSelector('.overlay__view-title', { timeout: 3000 });
  await page.locator('.advertisement-overlay .overlay__breadcrumb-back').click();
  await page.waitForFunction(() =>
    !document.querySelector('.advertisement-overlay.overlay--visible'), { timeout: 8000 });
  await openAdDetail(page, AD_TITLE + ' Renamed');
  await openEditMode(page);
  await page.locator('.base-overlay.overlay--visible vaadin-text-field input').fill(AD_TITLE);
  await page.locator('.base-overlay.overlay--visible vaadin-button')
    .filter({ hasText: /зберегти|save/i }).click();
  await page.waitForSelector('.overlay__view-title', { timeout: 5000 });
  await page.locator('.adv-overlay-tabs vaadin-tab').filter({ hasText: /view|перегляд/i }).click();
  await page.waitForSelector('.overlay__view-title', { timeout: 3000 });
  await page.locator('.advertisement-overlay .overlay__breadcrumb-back').click();
  await page.waitForFunction(() =>
    !document.querySelector('.advertisement-overlay.overlay--visible'), { timeout: 8000 });

  // ── STEP 5: Edit ad — add second photo ───────────────────────────────
  await page.locator('vaadin-tab').filter({ hasText: /advertisement|оголошен/i }).first().click();
  await page.waitForSelector('.advertisement-container', { timeout: 5000 });
  await openAdDetail(page, AD_TITLE);
  await openEditMode(page);

  await page.locator('.base-overlay.overlay--visible vaadin-upload input[type="file"]')
    .setInputFiles(paths[1]);
  await page.waitForSelector('.attachment-gallery__item:nth-child(2)', { timeout: 10000 });
  await page.locator('.base-overlay.overlay--visible vaadin-button')
    .filter({ hasText: /зберегти|save/i }).click();
  await page.waitForSelector('.overlay__view-title', { timeout: 5000 });
  await screenshot(page, 'phact-06-after-add-second-photo');

  // ── STEP 6: History after photo edit ─────────────────────────────────
  await openHistory(page);
  await screenshot(page, 'phact-07-history-after-photo-edit');

  await check('History shows photo diff after edit', async () => {
    const rows = await page.locator('.adv-history-row').count();
    console.log('      history rows:', rows);
    const text = await page.locator('.adv-history-list').textContent();
    checkPhotoInText(text, 'history-after-edit');
    if (!text.includes('→')) throw new Error('No diff arrow → in history');
  });

  await check('Restore button on older entry', async () => {
    const count = await page.locator('.adv-history-restore-btn').count();
    console.log('      restore buttons:', count);
    if (count === 0) throw new Error('No restore button');
  });

  await closeAdOverlay(page);

  // ── STEP 7: Settings activity after photo edit ───────────────────────
  await openSettings(page);
  await openActivityTab(page);
  await screenshot(page, 'phact-08-settings-activity-after-edit');

  await check('Settings activity shows multiple photo entries after edit', async () => {
    const text = await page.locator('.base-overlay.overlay--visible .user-activity-list').textContent();
    const photoMatches = (text.match(/фото|photo/gi) || []).length;
    console.log('      photo mentions in settings activity:', photoMatches);
    console.log('      text:', text.replace(/\s+/g, ' ').trim().slice(0, 600));
    if (photoMatches < 2) throw new Error(`Expected >=2 photo entries, found ${photoMatches}`);
  });

  await page.keyboard.press('Escape');
  await waitForOverlayClosed(page);

  // ── STEP 8: Restore to first photo version ───────────────────────────
  await page.locator('vaadin-tab').filter({ hasText: /advertisement|оголошен/i }).first().click();
  await page.waitForSelector('.advertisement-container', { timeout: 5000 });
  await openAdDetail(page, AD_TITLE);
  await openHistory(page);

  await page.locator('.adv-history-list .adv-history-restore-btn').last().click();
  await screenshot(page, 'phact-09-restore-dialog');
  await confirmDialog(page);
  await page.waitForSelector('.overlay__view-title', { timeout: 5000 });
  await screenshot(page, 'phact-10-after-restore');

  await check('History grows after restore', async () => {
    await openHistory(page);
    const rows = await page.locator('.adv-history-row').count();
    console.log('      history rows after restore:', rows);
    if (rows < 3) throw new Error(`Expected >=3 rows after restore, got ${rows}`);
    const text = await page.locator('.adv-history-list').textContent();
    checkPhotoInText(text, 'history-after-restore');
  });
  await screenshot(page, 'phact-11-history-after-restore');

  await closeAdOverlay(page);

  // ── STEP 9: Settings activity after restore ───────────────────────────
  await openSettings(page);
  await openActivityTab(page);
  await screenshot(page, 'phact-12-settings-activity-after-restore');

  await check('Settings activity shows photo changes after restore', async () => {
    const text = await page.locator('.base-overlay.overlay--visible .user-activity-list').textContent();
    const photoMatches = (text.match(/фото|photo/gi) || []).length;
    console.log('      photo mentions after restore:', photoMatches);
    console.log('      text:', text.replace(/\s+/g, ' ').trim().slice(0, 600));
    if (photoMatches < 2) throw new Error(`Expected >=2 photo entries after restore, found ${photoMatches}`);
  });

  await page.keyboard.press('Escape');
  await waitForOverlayClosed(page);

  paths.forEach(p => { if (fs.existsSync(p)) fs.unlinkSync(p); });
  console.log('photo-activity: all checks passed');
}, { email: 'user3@example.com' });
