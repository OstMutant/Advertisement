const { check, screenshot, withBrowser, waitForOverlay, openSettings, openActivityTab, confirmDialog } = require('./_common');

// Settings activity: page size change creates activity entry, restore button reverts it
async function getAdsPageSizeValue(page) {
  return page.evaluate(() => {
    const field = document.querySelector('.settings-overlay-content vaadin-integer-field');
    return field ? Number(field.value) : null;
  });
}

withBrowser(async (page) => {
  // ── Open settings and record original page size ──────────────────────
  await openSettings(page);
  await screenshot(page, 'setact-01-settings-open');

  const overlay = page.locator('.base-overlay.overlay--visible');
  const originalSize = await getAdsPageSizeValue(page);
  const newSize = originalSize === 10 ? 15 : 10;
  console.log('      original adsPageSize:', originalSize, '→ newSize:', newSize);

  const saveBtn = () => overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i });
  const sizeInput = () => overlay.locator('vaadin-integer-field').first().locator('input');

  // ── Save newSize → then originalSize so activity has both snapshots ──
  await check('Change ads page size', async () => {
    await sizeInput().fill(String(newSize));
  });
  await saveBtn().click();
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(1100); // ensure different created_at timestamp

  await sizeInput().fill(String(originalSize));
  await saveBtn().click();
  await page.waitForLoadState('networkidle');
  await screenshot(page, 'setact-02-saved');

  await check('Activity tab present in settings', async () => {
    const tabs = await page.locator('.base-overlay.overlay--visible vaadin-tab').allTextContents();
    console.log('      settings tabs:', tabs);
    if (!tabs.some(t => /activ|активн/i.test(t))) throw new Error('Activity tab not found');
  });

  await openActivityTab(page);
  await screenshot(page, 'setact-03-activity-tab');

  await check('Settings change appears in activity', async () => {
    const rows = page.locator('.user-activity-row');
    const count = await rows.count();
    console.log('      activity rows:', count);
    if (count === 0) throw new Error('No activity rows');
    const body = await page.locator('.user-activity-list').textContent();
    console.log('      activity body:', body.replace(/\s+/g, ' ').trim().slice(0, 400));
    if (!body.includes('сторінці') && !body.includes('page')) throw new Error('Settings change summary not found in activity');
  });

  await check('Page size diff shown in activity', async () => {
    const count = await page.locator('.user-activity-changes').count();
    console.log('      changes elements:', count);
    if (count === 0) throw new Error('No changes summary found');
    const allText = await page.locator('.user-activity-list').textContent();
    if (!allText.includes('→')) throw new Error('No diff arrow → found in activity');
  });

  await check('Restore settings button present', async () => {
    const restoreBtns = page.locator('.user-activity-list .adv-history-restore-btn');
    const count = await restoreBtns.count();
    console.log('      restore buttons in activity:', count);
    if (count === 0) throw new Error('No restore button for settings');
    console.log('      restore btn text:', await restoreBtns.first().textContent());
  });
  await screenshot(page, 'setact-04-restore-btn');

  // ── Click restore — restores to newSize ───────────────────────────────
  const restoreBtns = page.locator('.user-activity-list .adv-history-restore-btn');
  const btnCount = await restoreBtns.count();
  console.log('      clicking restore btn 0 of', btnCount);
  await restoreBtns.nth(0).click();
  await confirmDialog(page, 'Оновити|Update');
  await page.waitForLoadState('networkidle');
  await screenshot(page, 'setact-05-after-restore');

  // After restore: auto-switched to Settings tab
  await page.waitForSelector('.settings-overlay-content vaadin-integer-field', { timeout: 5000 });

  await check('Settings overlay on settings tab after restore', async () => {
    const visible = await page.locator('.base-overlay.overlay--visible').isVisible().catch(() => false);
    if (!visible) throw new Error('Overlay should stay open after restore');
    const settingsPanelVisible = await page.locator('.overlay__form-fields-card').isVisible();
    console.log('      settings panel visible:', settingsPanelVisible);
    if (!settingsPanelVisible) throw new Error('Settings tab should be active after restore');
  });

  await check('Page size changed after restore', async () => {
    const currentSize = await getAdsPageSizeValue(page);
    console.log('      size after restore:', currentSize, '(expected:', newSize, ')');
    if (currentSize !== newSize) throw new Error(`Expected ${newSize} after restore, got ${currentSize}`);
    console.log('      restore worked: size is now', currentSize);
  });
  await screenshot(page, 'setact-06-size-verified');

  await page.locator('.base-overlay.overlay--visible vaadin-button')
    .filter({ hasText: /додому|home|закрити|close/i }).last().click();

  console.log('settings-activity: all checks passed');
});
