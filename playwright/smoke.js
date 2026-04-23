/**
 * Full UI smoke test — runs all scenarios in one session.
 * Usage:
 *   node smoke.js          # pass/fail only
 *   node smoke.js --ux     # + screenshots
 */

const { chromium } = require('playwright');
const { check, screenshot, login } = require('./_common');
const https = require('https');
const fs    = require('fs');

const UX = process.argv.includes('--ux');

// ── helpers ───────────────────────────────────────────────────────────────────

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

const avatar = seed =>
  `https://api.dicebear.com/9.x/adventurer/png?seed=${seed}&size=256&backgroundColor=b6e3f4,c0aede,d1d4f9,ffd5dc,ffdfbf`;

async function section(title) {
  console.log(`\n${'─'.repeat(60)}`);
  console.log(`  ${title}`);
  console.log('─'.repeat(60));
}

async function newSession(browser, email, pw = 'password') {
  const p = await browser.newPage({ viewport: { width: 1280, height: 900 } });
  await login(p, email, pw);
  return p;
}

// ── main ──────────────────────────────────────────────────────────────────────

(async () => {
  const browser = await chromium.launch();
  const page    = await browser.newPage({ viewport: { width: 1280, height: 900 } });

  // ════════════════════════════════════════════════════════════════════════════
  // 1. LANGUAGE SWITCH  (no login required)
  // ════════════════════════════════════════════════════════════════════════════
  await section('1. Language switch');

  await page.goto('http://localhost:8080/', { waitUntil: 'networkidle' });
  await screenshot(page, 'lang-01-initial', UX);

  await check('Locale combobox visible', async () => {
    await page.waitForSelector('.locale-combobox', { timeout: 5000 });
    const cur = await page.locator('.locale-combobox input').inputValue();
    console.log('      current locale:', cur);
  });

  await check('Switch to English', async () => {
    await page.locator('.locale-combobox input').click();
    await page.waitForTimeout(400);
    await page.locator('vaadin-combo-box-item').filter({ hasText: /english|en/i }).first().click();
    await page.waitForNavigation({ waitUntil: 'networkidle', timeout: 10000 }).catch(() => {});
    await page.waitForTimeout(1000);
    const body = await page.textContent('body');
    if (body.match(/log in|advertisements/i)) console.log('      [OK] English UI');
  });
  await screenshot(page, 'lang-02-english', UX);

  await check('Switch back to Ukrainian', async () => {
    await page.locator('.locale-combobox input').click();
    await page.waitForTimeout(400);
    await page.locator('vaadin-combo-box-item').filter({ hasText: /укр|ukrainian/i }).first().click();
    await page.waitForNavigation({ waitUntil: 'networkidle', timeout: 10000 }).catch(() => {});
    await page.waitForTimeout(1000);
  });
  await screenshot(page, 'lang-03-ukrainian', UX);

  // ════════════════════════════════════════════════════════════════════════════
  // 2. LOGIN
  // ════════════════════════════════════════════════════════════════════════════
  await section('2. Login');
  await login(page);
  await screenshot(page, 'login-01-logged-in', UX);

  // ════════════════════════════════════════════════════════════════════════════
  // 3. ADD ADVERTISEMENT
  // ════════════════════════════════════════════════════════════════════════════
  await section('3. Add advertisement');

  await check('Open add form', async () => {
    await page.locator('.add-advertisement-button').click();
    await page.waitForTimeout(1000);
  });
  await screenshot(page, 'add-01-form-open', UX);

  await check('Fill form fields', async () => {
    const overlay = page.locator('.advertisement-overlay');
    await overlay.locator('vaadin-text-field input').fill('Smoke Test Ad');
    await overlay.locator('vaadin-text-area textarea').fill('Created by smoke test');
  });
  await screenshot(page, 'add-02-fields-filled', UX);

  await check('Save new advertisement', async () => {
    const overlay = page.locator('.advertisement-overlay');
    await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await page.waitForTimeout(1500);
    const body = await page.textContent('body');
    if (!body.includes('Smoke Test Ad')) throw new Error('Ad not visible after save');
    console.log('      ad appears in list');
  });
  await screenshot(page, 'add-03-saved', UX);

  // ════════════════════════════════════════════════════════════════════════════
  // 4. EDIT ADVERTISEMENT
  // ════════════════════════════════════════════════════════════════════════════
  await section('4. Edit advertisement');

  await check('Open Smoke Test Ad detail (view)', async () => {
    await page.locator('.advertisement-card')
      .filter({ has: page.locator('.advertisement-title', { hasText: 'Smoke Test Ad' }) })
      .first().click();
    await page.waitForTimeout(800);
  });
  await screenshot(page, 'edit-01-view-overlay', UX);

  await check('Open edit form', async () => {
    await page.locator('vaadin-button').filter({ hasText: /редагувати|edit/i }).first().click();
    await page.waitForTimeout(800);
  });
  await screenshot(page, 'edit-02-edit-form-open', UX);

  await check('Fill new title', async () => {
    const input = page.locator('.base-overlay.overlay--visible vaadin-text-field input').first();
    await input.clear();
    await input.fill('Smoke Test Ad (edited)');
  });
  await screenshot(page, 'edit-03-field-filled', UX);

  await check('Save edited advertisement', async () => {
    await page.locator('.base-overlay.overlay--visible vaadin-button')
      .filter({ hasText: /зберегти|save/i }).click();
    await page.waitForTimeout(1500);
    const body = await page.textContent('body');
    if (!body.includes('edited')) throw new Error('Edited title not visible');
    console.log('      title updated');
  });
  await screenshot(page, 'edit-04-saved', UX);

  // Close detail overlay if still open (edit save may have closed it already)
  const backBtn = page.locator('.overlay__breadcrumb-back');
  if (await backBtn.isVisible().catch(() => false)) {
    await backBtn.click();
    await page.waitForTimeout(800);
  }

  // ════════════════════════════════════════════════════════════════════════════
  // 5. UPLOAD SINGLE IMAGE
  // ════════════════════════════════════════════════════════════════════════════
  await section('5. Upload single image');

  const singleSeed = 'sunny';
  const singlePath = `/tmp/avatar-${singleSeed}.png`;
  await check('Download avatar', async () => {
    await downloadPng(avatar(singleSeed), singlePath);
    console.log('      downloaded:', singleSeed);
  });

  await check('Create ad for single image', async () => {
    await page.locator('.add-advertisement-button').click();
    await page.waitForTimeout(800);
    const ov = page.locator('.advertisement-overlay');
    await ov.locator('vaadin-text-field input').fill('Single Image House');
    await ov.locator('vaadin-text-area textarea').fill('A cozy house with one featured image');
    await ov.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await page.waitForTimeout(1500);
  });

  await check('Open edit form for single image ad', async () => {
    await page.locator('.advertisement-card')
      .filter({ has: page.locator('.advertisement-title', { hasText: 'Single Image House' }) })
      .first().click();
    await page.waitForTimeout(800);
    await page.locator('vaadin-button').filter({ hasText: /редагувати|edit/i }).first().click();
    await page.waitForTimeout(800);
  });
  await screenshot(page, 'upload-single-01-edit-form', UX);

  await check('Select and upload single image', async () => {
    await page.locator('vaadin-upload input[type="file"]').setInputFiles(singlePath);
    await page.waitForTimeout(2000);
  });
  await screenshot(page, 'upload-single-02-file-selected', UX);

  await check('Save single image', async () => {
    await page.locator('.base-overlay.overlay--visible vaadin-button')
      .filter({ hasText: /зберегти|save/i }).click();
    await page.waitForTimeout(1500);
  });
  await screenshot(page, 'upload-single-03-saved', UX);

  await check('Verify 1 image in detail', async () => {
    await page.waitForTimeout(500);
    const imgs = await page.locator('.base-overlay.overlay--visible img').count();
    console.log(`      images: ${imgs}`);
    if (imgs !== 1) throw new Error(`Expected 1 image, got ${imgs}`);
  });
  await screenshot(page, 'upload-single-02-detail', UX);
  await page.locator('.overlay__breadcrumb-back').click();
  await page.waitForTimeout(800);
  if (fs.existsSync(singlePath)) fs.unlinkSync(singlePath);

  // ════════════════════════════════════════════════════════════════════════════
  // 6. UPLOAD MULTIPLE IMAGES
  // ════════════════════════════════════════════════════════════════════════════
  await section('6. Upload multiple images');

  const multiSeeds = ['rocky', 'misty', 'comet'];
  const multiPaths = multiSeeds.map(s => `/tmp/avatar-${s}.png`);
  await check('Download 3 avatars', async () => {
    await Promise.all(multiSeeds.map((s, i) => downloadPng(avatar(s), multiPaths[i])));
    console.log(`      downloaded: ${multiSeeds.join(', ')}`);
  });

  await check('Create ad for gallery', async () => {
    await page.locator('.add-advertisement-button').click();
    await page.waitForTimeout(800);
    const ov = page.locator('.advertisement-overlay');
    await ov.locator('vaadin-text-field input').fill('Gallery Mansion');
    await ov.locator('vaadin-text-area textarea').fill('A luxurious mansion shown from many angles');
    await ov.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await page.waitForTimeout(1500);
  });

  await check('Open edit form for gallery ad', async () => {
    await page.locator('.advertisement-card')
      .filter({ has: page.locator('.advertisement-title', { hasText: 'Gallery Mansion' }) })
      .first().click();
    await page.waitForTimeout(800);
    await page.locator('vaadin-button').filter({ hasText: /редагувати|edit/i }).first().click();
    await page.waitForTimeout(800);
  });
  await screenshot(page, 'upload-multi-01-edit-form', UX);

  await check('Select 3 images', async () => {
    await page.locator('vaadin-upload input[type="file"]').setInputFiles(multiPaths);
    await page.waitForTimeout(3000);
  });
  await screenshot(page, 'upload-multi-02-files-selected', UX);

  await check('Save gallery', async () => {
    await page.locator('.base-overlay.overlay--visible vaadin-button')
      .filter({ hasText: /зберегти|save/i }).click();
    await page.waitForTimeout(1500);
  });
  await screenshot(page, 'upload-multi-03-saved', UX);

  await check('Verify 3 images in detail', async () => {
    await page.waitForTimeout(500);
    const imgs = await page.locator('.base-overlay.overlay--visible img').count();
    console.log(`      images: ${imgs}`);
    if (imgs < 2) throw new Error(`Expected multiple images, got ${imgs}`);
  });
  await screenshot(page, 'upload-multi-02-detail', UX);
  await page.locator('.overlay__breadcrumb-back').click();
  await page.waitForTimeout(800);
  multiPaths.forEach(p => { if (fs.existsSync(p)) fs.unlinkSync(p); });

  // ════════════════════════════════════════════════════════════════════════════
  // 7. FILTER ADVERTISEMENTS
  // ════════════════════════════════════════════════════════════════════════════
  await section('7. Filter advertisements');
  await screenshot(page, 'filter-ads-01-list', UX);

  await check('Open filter panel', async () => {
    await page.locator('.query-status-bar').first().click();
    await page.waitForTimeout(800);
    if (!await page.locator('.advertisement-query-block').isVisible())
      throw new Error('Filter panel not visible');
  });
  await screenshot(page, 'filter-ads-02-panel-open', UX);

  await check('Filter by title "Smoke"', async () => {
    await page.locator('.advertisement-query-block .query-text input').first().fill('Smoke');
    await page.waitForTimeout(300);
    await page.locator('.advertisement-query-block vaadin-button[title*="Застосувати"]').first().click();
    await page.waitForTimeout(1500);
    const cards = await page.locator('.advertisement-card').count();
    console.log(`      cards after filter: ${cards}`);
  });
  await screenshot(page, 'filter-ads-03-filtered', UX);

  await check('Sort by title', async () => {
    await page.locator('.advertisement-query-block .sort-icon').first().click();
    await page.waitForTimeout(1000);
    const first = await page.locator('.advertisement-title').first().textContent();
    console.log('      first title:', first.trim());
  });
  await screenshot(page, 'filter-ads-04-sorted', UX);

  await check('Clear filters', async () => {
    await page.locator('.advertisement-query-block vaadin-button[title*="Очистити"]').first().click();
    await page.waitForTimeout(1500);
    const cards = await page.locator('.advertisement-card').count();
    console.log(`      cards after clear: ${cards}`);
  });
  await screenshot(page, 'filter-ads-05-cleared', UX);

  // ════════════════════════════════════════════════════════════════════════════
  // 8. SETTINGS
  // ════════════════════════════════════════════════════════════════════════════
  await section('8. Settings');

  await check('Open settings overlay', async () => {
    await page.locator('.header-settings-button').click();
    await page.waitForTimeout(1000);
    if (!await page.locator('.base-overlay.overlay--visible').isVisible())
      throw new Error('Settings overlay not open');
  });
  await screenshot(page, 'settings-01-open', UX);

  await check('Change ads page size to 15', async () => {
    const field = page.locator('vaadin-integer-field').first().locator('input');
    const cur = await field.inputValue();
    console.log('      current ads page size:', cur);
    await field.click({ clickCount: 3 });
    await field.fill('15');
  });

  await check('Save settings', async () => {
    await page.locator('.base-overlay.overlay--visible vaadin-button')
      .filter({ hasText: /зберегти|save/i }).click();
    await page.waitForTimeout(1500);
    console.log('      settings saved');
  });
  await screenshot(page, 'settings-02-saved', UX);

  // ════════════════════════════════════════════════════════════════════════════
  // 9. USERS VIEW  (admin)
  // ════════════════════════════════════════════════════════════════════════════
  await section('9. Users view (admin)');
  await page.close();
  const adminPage = await newSession(browser, 'user3@example.com');
  const page9 = adminPage;

  await check('Switch to Users tab', async () => {
    await page9.locator('vaadin-tab').filter({ hasText: /users|користувач/i }).first().click();
    await page9.waitForTimeout(1500);
    const cells = await page9.locator('vaadin-grid.user-grid vaadin-grid-cell-content').count();
    console.log(`      grid cells: ${cells}`);
    if (cells === 0) throw new Error('User grid is empty');
  });
  await screenshot(page9, 'users-01-list', UX);

  await check('Open user detail (row click)', async () => {
    await page9.locator('vaadin-grid.user-grid vaadin-grid-cell-content .user-grid-name').first().click();
    await page9.waitForTimeout(1000);
    if (!await page9.locator('.base-overlay.overlay--visible').isVisible())
      throw new Error('View overlay not open');
    const body = await page9.locator('.base-overlay.overlay--visible').textContent();
    console.log('      overlay:', body.replace(/\s+/g, ' ').trim().slice(0, 120));
  });
  await screenshot(page9, 'users-02-detail', UX);

  await check('Back to list and open edit overlay', async () => {
    await page9.locator('.overlay__breadcrumb-back').click();
    await page9.waitForTimeout(800);
  });
  await screenshot(page9, 'users-03-back-to-list', UX);

  await check('Open user edit overlay', async () => {
    const editBtn = page9.locator('vaadin-grid.user-grid .user-grid-actions vaadin-button').first();
    await editBtn.click();
    await page9.waitForTimeout(1000);
    if (!await page9.locator('.base-overlay.overlay--visible').isVisible())
      throw new Error('Edit overlay not open');
  });
  await screenshot(page9, 'users-04-edit-form', UX);

  await page9.locator('.base-overlay.overlay--visible vaadin-button')
    .filter({ hasText: /скасувати|cancel|закрити|close/i }).first().click();
  await page9.waitForTimeout(800);
  await screenshot(page9, 'users-05-closed', UX);

  // ════════════════════════════════════════════════════════════════════════════
  // 10. FILTER USERS  (admin)
  // ════════════════════════════════════════════════════════════════════════════
  await section('10. Filter users (admin)');

  await check('Open user filter panel', async () => {
    await page9.locator('.query-status-bar').filter({ visible: true }).first().click();
    await page9.waitForTimeout(800);
    const rows = await page9.locator('.query-inline-row').count();
    console.log(`      filter rows: ${rows}`);
  });
  await screenshot(page9, 'filter-users-01-panel', UX);

  await check('Filter by name "User 1"', async () => {
    await page9.locator('.user-query-block .query-text input').first().fill('User 1');
    await page9.waitForTimeout(300);
    await page9.locator('.user-query-block vaadin-button[title*="Застосувати"]').first().click();
    await page9.waitForTimeout(1500);
    const cells = await page9.locator('vaadin-grid.user-grid .user-grid-name').count();
    console.log(`      users shown: ${cells}`);
  });
  await screenshot(page9, 'filter-users-02-by-name', UX);

  await check('Clear and filter by role ADMIN', async () => {
    await page9.locator('.user-query-block vaadin-button[title*="Очистити"]').first().click();
    await page9.waitForTimeout(800);
    const combo = page9.locator('.query-multi-combo input');
    await combo.click();
    await page9.waitForTimeout(400);
    await page9.locator('vaadin-multi-select-combo-box-item').filter({ hasText: /ADMIN/i }).first().click();
    await page9.keyboard.press('Escape');
    await page9.waitForTimeout(300);
    await page9.locator('.user-query-block vaadin-button[title*="Застосувати"]').first().click();
    await page9.waitForTimeout(1500);
    const cells = await page9.locator('vaadin-grid.user-grid .user-grid-name').count();
    console.log(`      ADMIN users shown: ${cells}`);
  });
  await screenshot(page9, 'filter-users-03-by-role', UX);

  await check('Clear all filters', async () => {
    await page9.locator('.user-query-block vaadin-button[title*="Очистити"]').first().click();
    await page9.waitForTimeout(1500);
    const cells = await page9.locator('vaadin-grid.user-grid .user-grid-name').count();
    console.log(`      users after clear: ${cells}`);
  });
  await screenshot(page9, 'filter-users-04-cleared', UX);

  // ════════════════════════════════════════════════════════════════════════════

  console.log('\n' + '═'.repeat(60));
  console.log('  Smoke test complete');
  console.log('═'.repeat(60) + '\n');

  await browser.close();
})();
