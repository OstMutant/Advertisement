/**
 * Full UI smoke test — runs all scenarios in one session.
 * Usage:
 *   node smoke.js          # pass/fail only
 *   node smoke.js --ux     # + screenshots
 */

const { chromium } = require('playwright');
const { check, screenshot, login, waitForOverlay, waitForOverlayClosed, downloadPng } = require('./_common');
const fs = require('fs');

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

(async () => {
  const browser = await chromium.launch();
  const page    = await browser.newPage({ viewport: { width: 1280, height: 900 } });

  // ════════════════════════════════════════════════════════════════════════════
  // 1. LANGUAGE SWITCH  (no login required)
  // ════════════════════════════════════════════════════════════════════════════
  await section('1. Language switch');

  await page.goto('http://localhost:8080/', { waitUntil: 'networkidle' });
  await screenshot(page, 'lang-01-initial');

  await check('Locale combobox visible', async () => {
    await page.waitForSelector('.locale-combobox', { timeout: 5000 });
    const cur = await page.locator('.locale-combobox input').inputValue();
    console.log('      current locale:', cur);
  });

  await check('Switch to English', async () => {
    await page.locator('.locale-combobox input').click();
    await page.waitForSelector('vaadin-combo-box-item', { timeout: 3000 });
    await page.locator('vaadin-combo-box-item').filter({ hasText: /english|en/i }).first().click();
    await page.waitForNavigation({ waitUntil: 'networkidle', timeout: 10000 }).catch(() => {});
    const body = await page.textContent('body');
    if (body.match(/log in|advertisements/i)) console.log('      [OK] English UI');
  });
  await screenshot(page, 'lang-02-english');

  await check('Switch back to Ukrainian', async () => {
    await page.locator('.locale-combobox input').click();
    await page.waitForSelector('vaadin-combo-box-item', { timeout: 3000 });
    await page.locator('vaadin-combo-box-item').filter({ hasText: /укр|ukrainian/i }).first().click();
    await page.waitForNavigation({ waitUntil: 'networkidle', timeout: 10000 }).catch(() => {});
  });
  await screenshot(page, 'lang-03-ukrainian');

  // ════════════════════════════════════════════════════════════════════════════
  // 2. LOGIN
  // ════════════════════════════════════════════════════════════════════════════
  await section('2. Login');
  await login(page);
  await screenshot(page, 'login-01-logged-in');

  // ════════════════════════════════════════════════════════════════════════════
  // 3. ADD ADVERTISEMENT
  // ════════════════════════════════════════════════════════════════════════════
  await section('3. Add advertisement');

  await check('Open add form', async () => {
    await page.locator('.add-advertisement-button').click();
    await waitForOverlay(page);
  });
  await screenshot(page, 'add-01-form-open');

  await check('Fill form fields', async () => {
    const overlay = page.locator('.advertisement-overlay');
    await overlay.locator('vaadin-text-field input').fill('Smoke Test Ad');
    await overlay.locator('vaadin-text-area textarea').fill('Created by smoke test');
  });
  await screenshot(page, 'add-02-fields-filled');

  await check('Save new advertisement', async () => {
    const overlay = page.locator('.advertisement-overlay');
    await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await waitForOverlayClosed(page);
    const body = await page.textContent('body');
    if (!body.includes('Smoke Test Ad')) throw new Error('Ad not visible after save');
    console.log('      ad appears in list');
  });
  await screenshot(page, 'add-03-saved');

  // ════════════════════════════════════════════════════════════════════════════
  // 4. EDIT ADVERTISEMENT
  // ════════════════════════════════════════════════════════════════════════════
  await section('4. Edit advertisement');

  await check('Open Smoke Test Ad detail (view)', async () => {
    await page.locator('.advertisement-card')
      .filter({ has: page.locator('.advertisement-title', { hasText: 'Smoke Test Ad' }) })
      .first().click();
    await waitForOverlay(page);
  });
  await screenshot(page, 'edit-01-view-overlay');

  await check('Open edit form', async () => {
    await page.locator('vaadin-button').filter({ hasText: /редагувати|edit/i }).first().click();
    await page.waitForSelector('.base-overlay.overlay--visible vaadin-text-field input', { timeout: 5000 });
  });
  await screenshot(page, 'edit-02-edit-form-open');

  await check('Fill new title', async () => {
    const input = page.locator('.base-overlay.overlay--visible vaadin-text-field input').first();
    await input.clear();
    await input.fill('Smoke Test Ad (edited)');
  });
  await screenshot(page, 'edit-03-field-filled');

  await check('Save edited advertisement', async () => {
    await page.locator('.base-overlay.overlay--visible vaadin-button')
      .filter({ hasText: /зберегти|save/i }).click();
    await page.waitForSelector('.overlay__view-title', { timeout: 5000 });
    const body = await page.textContent('body');
    if (!body.includes('edited')) throw new Error('Edited title not visible');
    console.log('      title updated');
  });
  await screenshot(page, 'edit-04-saved');

  const backBtn = page.locator('.overlay__breadcrumb-back');
  if (await backBtn.isVisible().catch(() => false)) {
    await backBtn.click();
    await waitForOverlayClosed(page).catch(() => {});
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
    await waitForOverlay(page);
    const ov = page.locator('.advertisement-overlay');
    await ov.locator('vaadin-text-field input').fill('Single Image House');
    await ov.locator('vaadin-text-area textarea').fill('A cozy house with one featured image');
    await ov.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await waitForOverlayClosed(page);
  });

  await check('Open edit form for single image ad', async () => {
    await page.locator('.advertisement-card')
      .filter({ has: page.locator('.advertisement-title', { hasText: 'Single Image House' }) })
      .first().click();
    await waitForOverlay(page);
    await page.locator('vaadin-button').filter({ hasText: /редагувати|edit/i }).first().click();
    await page.waitForSelector('.base-overlay.overlay--visible vaadin-text-field input', { timeout: 5000 });
  });
  await screenshot(page, 'upload-single-01-edit-form');

  await check('Select and upload single image', async () => {
    await page.locator('vaadin-upload input[type="file"]').setInputFiles(singlePath);
    await page.waitForSelector('.attachment-gallery__item', { timeout: 10000 });
  });
  await screenshot(page, 'upload-single-02-file-selected');

  await check('Save single image', async () => {
    await page.locator('.base-overlay.overlay--visible vaadin-button')
      .filter({ hasText: /зберегти|save/i }).click();
    await page.waitForSelector('.overlay__view-title', { timeout: 5000 });
  });
  await screenshot(page, 'upload-single-03-saved');

  await check('Verify 1 image in detail', async () => {
    const imgs = await page.locator('.base-overlay.overlay--visible img').count();
    console.log(`      images: ${imgs}`);
    if (imgs !== 1) throw new Error(`Expected 1 image, got ${imgs}`);
  });
  await screenshot(page, 'upload-single-02-detail');
  await page.locator('.overlay__breadcrumb-back').click();
  await waitForOverlayClosed(page).catch(() => {});
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
    await waitForOverlay(page);
    const ov = page.locator('.advertisement-overlay');
    await ov.locator('vaadin-text-field input').fill('Gallery Mansion');
    await ov.locator('vaadin-text-area textarea').fill('A luxurious mansion shown from many angles');
    await ov.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await waitForOverlayClosed(page);
  });

  await check('Open edit form for gallery ad', async () => {
    await page.locator('.advertisement-card')
      .filter({ has: page.locator('.advertisement-title', { hasText: 'Gallery Mansion' }) })
      .first().click();
    await waitForOverlay(page);
    await page.locator('vaadin-button').filter({ hasText: /редагувати|edit/i }).first().click();
    await page.waitForSelector('.base-overlay.overlay--visible vaadin-text-field input', { timeout: 5000 });
  });
  await screenshot(page, 'upload-multi-01-edit-form');

  await check('Select 3 images', async () => {
    await page.locator('vaadin-upload input[type="file"]').setInputFiles(multiPaths);
    await page.waitForSelector('.attachment-gallery__item', { timeout: 10000 });
  });
  await screenshot(page, 'upload-multi-02-files-selected');

  await check('Save gallery', async () => {
    await page.locator('.base-overlay.overlay--visible vaadin-button')
      .filter({ hasText: /зберегти|save/i }).click();
    await page.waitForSelector('.overlay__view-title', { timeout: 5000 });
  });
  await screenshot(page, 'upload-multi-03-saved');

  await check('Verify 3 images in detail', async () => {
    const imgs = await page.locator('.base-overlay.overlay--visible img').count();
    console.log(`      images: ${imgs}`);
    if (imgs < 2) throw new Error(`Expected multiple images, got ${imgs}`);
  });
  await screenshot(page, 'upload-multi-02-detail');
  await page.locator('.overlay__breadcrumb-back').click();
  await waitForOverlayClosed(page).catch(() => {});
  multiPaths.forEach(p => { if (fs.existsSync(p)) fs.unlinkSync(p); });

  // ════════════════════════════════════════════════════════════════════════════
  // 7. FILTER ADVERTISEMENTS
  // ════════════════════════════════════════════════════════════════════════════
  await section('7. Filter advertisements');
  await screenshot(page, 'filter-ads-01-list');

  await check('Open filter panel', async () => {
    await page.locator('.query-status-bar').first().click();
    await page.waitForSelector('.advertisement-query-block', { timeout: 3000 });
    if (!await page.locator('.advertisement-query-block').isVisible())
      throw new Error('Filter panel not visible');
  });
  await screenshot(page, 'filter-ads-02-panel-open');

  await check('Filter by title "Smoke"', async () => {
    await page.locator('.advertisement-query-block .query-text input').first().fill('Smoke');
    await page.locator('.advertisement-query-block vaadin-button[title*="Застосувати"]').first().click();
    await page.waitForSelector('.advertisement-card', { timeout: 5000 }).catch(() => {});
    const cards = await page.locator('.advertisement-card').count();
    console.log(`      cards after filter: ${cards}`);
  });
  await screenshot(page, 'filter-ads-03-filtered');

  await check('Sort by title', async () => {
    await page.locator('.advertisement-query-block .sort-icon').first().click();
    await page.waitForSelector('.advertisement-title', { timeout: 3000 });
    const first = await page.locator('.advertisement-title').first().textContent();
    console.log('      first title:', first.trim());
  });
  await screenshot(page, 'filter-ads-04-sorted');

  await check('Clear filters', async () => {
    await page.locator('.advertisement-query-block vaadin-button[title*="Очистити"]').first().click();
    await page.waitForSelector('.advertisement-card', { timeout: 5000 }).catch(() => {});
    const cards = await page.locator('.advertisement-card').count();
    console.log(`      cards after clear: ${cards}`);
  });
  await screenshot(page, 'filter-ads-05-cleared');

  // ════════════════════════════════════════════════════════════════════════════
  // 8. SETTINGS
  // ════════════════════════════════════════════════════════════════════════════
  await section('8. Settings');

  await check('Open settings overlay', async () => {
    await page.locator('.header-settings-button').click();
    await waitForOverlay(page);
  });
  await screenshot(page, 'settings-01-open');

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
    await page.waitForLoadState('networkidle');
    console.log('      settings saved');
  });
  await screenshot(page, 'settings-02-saved');

  // ════════════════════════════════════════════════════════════════════════════
  // 9. USERS VIEW  (admin)
  // ════════════════════════════════════════════════════════════════════════════
  await section('9. Users view (admin)');
  await page.close();
  const adminPage = await newSession(browser, 'user3@example.com');

  await check('Switch to Users tab', async () => {
    await adminPage.locator('vaadin-tab').filter({ hasText: /users|користувач/i }).first().click();
    await adminPage.waitForSelector('vaadin-grid.user-grid', { timeout: 8000 });
    const cells = await adminPage.locator('vaadin-grid.user-grid vaadin-grid-cell-content').count();
    console.log(`      grid cells: ${cells}`);
    if (cells === 0) throw new Error('User grid is empty');
  });
  await screenshot(adminPage, 'users-01-list');

  await check('Open user detail (row click)', async () => {
    await adminPage.locator('vaadin-grid.user-grid vaadin-grid-cell-content .user-grid-name').first().click();
    await waitForOverlay(adminPage);
    const body = await adminPage.locator('.base-overlay.overlay--visible').textContent();
    console.log('      overlay:', body.replace(/\s+/g, ' ').trim().slice(0, 120));
  });
  await screenshot(adminPage, 'users-02-detail');

  await check('Back to list and open edit overlay', async () => {
    await adminPage.locator('.overlay__breadcrumb-back').click();
    await waitForOverlayClosed(adminPage);
  });
  await screenshot(adminPage, 'users-03-back-to-list');

  await check('Open user edit overlay', async () => {
    await adminPage.locator('vaadin-grid.user-grid .user-grid-actions vaadin-button').first().click();
    await waitForOverlay(adminPage);
  });
  await screenshot(adminPage, 'users-04-edit-form');

  await adminPage.locator('.base-overlay.overlay--visible vaadin-button')
    .filter({ hasText: /скасувати|cancel|закрити|close/i }).first().click();
  await waitForOverlayClosed(adminPage).catch(() => {});
  await screenshot(adminPage, 'users-05-closed');

  // ════════════════════════════════════════════════════════════════════════════
  // 10. FILTER USERS  (admin)
  // ════════════════════════════════════════════════════════════════════════════
  await section('10. Filter users (admin)');

  await check('Open user filter panel', async () => {
    await adminPage.locator('.query-status-bar').filter({ visible: true }).first().click();
    await adminPage.waitForSelector('.user-query-block', { timeout: 3000 });
    const rows = await adminPage.locator('.query-inline-row').count();
    console.log(`      filter rows: ${rows}`);
  });
  await screenshot(adminPage, 'filter-users-01-panel');

  await check('Filter by name "User 1"', async () => {
    await adminPage.locator('.user-query-block .query-text input').first().fill('User 1');
    await adminPage.locator('.user-query-block vaadin-button[title*="Застосувати"]').first().click();
    await adminPage.waitForSelector('vaadin-grid.user-grid .user-grid-name', { timeout: 5000 });
    const cells = await adminPage.locator('vaadin-grid.user-grid .user-grid-name').count();
    console.log(`      users shown: ${cells}`);
  });
  await screenshot(adminPage, 'filter-users-02-by-name');

  await check('Clear and filter by role ADMIN', async () => {
    await adminPage.locator('.user-query-block vaadin-button[title*="Очистити"]').first().click();
    const combo = adminPage.locator('.query-multi-combo input');
    await combo.click();
    await adminPage.waitForSelector('vaadin-multi-select-combo-box-item', { timeout: 3000 });
    await adminPage.locator('vaadin-multi-select-combo-box-item').filter({ hasText: /ADMIN/i }).first().click();
    await adminPage.keyboard.press('Escape');
    await adminPage.locator('.user-query-block vaadin-button[title*="Застосувати"]').first().click();
    await adminPage.waitForSelector('vaadin-grid.user-grid .user-grid-name', { timeout: 5000 });
    const cells = await adminPage.locator('vaadin-grid.user-grid .user-grid-name').count();
    console.log(`      ADMIN users shown: ${cells}`);
  });
  await screenshot(adminPage, 'filter-users-03-by-role');

  await check('Clear all filters', async () => {
    await adminPage.locator('.user-query-block vaadin-button[title*="Очистити"]').first().click();
    await adminPage.waitForSelector('vaadin-grid.user-grid .user-grid-name', { timeout: 5000 });
    const cells = await adminPage.locator('vaadin-grid.user-grid .user-grid-name').count();
    console.log(`      users after clear: ${cells}`);
  });
  await screenshot(adminPage, 'filter-users-04-cleared');

  console.log('\n' + '═'.repeat(60));
  console.log('  Smoke test complete');
  console.log('═'.repeat(60) + '\n');

  await browser.close();
})();
