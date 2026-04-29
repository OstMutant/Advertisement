const { check, screenshot, withBrowser, waitForOverlay, waitForOverlayClosed, openHistory, confirmDialog } = require('./_common');

withBrowser(async (page) => {
  const overlay = page.locator('.advertisement-overlay');

  // ── Create advertisement ──────────────────────────────────────────────
  await page.locator('vaadin-button').filter({ hasText: /new|add|create|нове|додати/i }).first().click();
  await waitForOverlay(page);
  await overlay.locator('vaadin-text-field input').fill('History Test Ad');
  await overlay.locator('vaadin-text-area textarea').fill('Original description v1');
  await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save|submit/i }).click();
  await waitForOverlayClosed(page);
  await screenshot(page, 'history-01-ad-created');

  // ── Open ad and check initial history ────────────────────────────────
  await page.locator('.advertisement-card')
    .filter({ has: page.locator('.advertisement-title', { hasText: 'History Test Ad' }) })
    .first().click();
  await waitForOverlay(page);
  await screenshot(page, 'history-02-ad-opened');

  await openHistory(page);
  await screenshot(page, 'history-03-history-tab');

  await check('History list visible', async () => {
    const visible = await page.locator('.adv-history-list').isVisible();
    if (!visible) throw new Error('adv-history-list not visible');
  });

  const rowCountInit = await page.locator('.adv-history-row').count();
  await check('History has entries after create', async () => {
    if (rowCountInit === 0) throw new Error('No history rows');
    console.log('      rows:', rowCountInit);
  });

  // ── Edit the ad ───────────────────────────────────────────────────────
  await overlay.locator('vaadin-tab').filter({ hasText: /view|перегляд/i }).click();
  await page.waitForSelector('.overlay__view-title', { timeout: 3000 });

  await overlay.locator('vaadin-button').filter({ hasText: /edit|редагувати/i }).first().click();
  await page.waitForSelector('.base-overlay.overlay--visible vaadin-text-field input', { timeout: 5000 });

  await overlay.locator('vaadin-text-area textarea').fill('Updated description v2');
  await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save|submit/i }).click();
  await page.waitForSelector('.overlay__view-title', { timeout: 5000 });
  await screenshot(page, 'history-04-after-edit');

  // ── Check history grew ────────────────────────────────────────────────
  await openHistory(page);
  await screenshot(page, 'history-05-history-with-diff');

  const rowCountAfter = await page.locator('.adv-history-row').count();
  await check('History has 2+ entries after edit', async () => {
    if (rowCountAfter < 2) throw new Error(`Expected >=2 rows, got ${rowCountAfter}`);
    console.log('      rows after edit:', rowCountAfter);
  });

  await check('Changes summary shown', async () => {
    const count = await page.locator('.adv-history-changes').count();
    console.log('      changes summary elements:', count);
    if (count === 0) throw new Error('No changes summary found');
    const text = await page.locator('.adv-history-changes').first().textContent();
    console.log('      changes text:', text);
  });

  // ── Restore from oldest snapshot ──────────────────────────────────────
  const restoreBtns = page.locator('.adv-history-restore-btn');
  const btnCount = await restoreBtns.count();
  await check('Restore buttons present', async () => {
    if (btnCount === 0) throw new Error('No restore buttons');
    console.log('      restore buttons:', btnCount);
  });

  await restoreBtns.last().click();
  await screenshot(page, 'history-06-restore-confirm-dialog');

  await check('Restore confirm dialog shown', async () => {
    const dialogOverlay = page.locator('vaadin-dialog-overlay');
    const visible = await dialogOverlay.isVisible();
    if (!visible) throw new Error('Confirm dialog overlay not visible');
  });

  await confirmDialog(page);
  await page.waitForSelector('.overlay__view-title', { timeout: 5000 });
  await screenshot(page, 'history-07-after-restore');

  await check('View shown after restore', async () => {
    const visible = await page.locator('.overlay__view-title').isVisible();
    if (!visible) throw new Error('view title not visible after restore');
    const title = await page.locator('.overlay__view-title').textContent();
    console.log('      title after restore:', title);
  });

  await openHistory(page);
  await screenshot(page, 'history-08-history-after-restore');

  await check('No RESTORED badge (all entries are UPDATED)', async () => {
    const restored = await page.locator('.adv-history-action--restored').count();
    if (restored > 0) throw new Error('RESTORED badge found but should not exist');
    const updated = await page.locator('.adv-history-action--updated').count();
    console.log('      updated badges:', updated);
    if (updated === 0) throw new Error('No UPDATED badge found after restore');
  });

  console.log('advertisement-history: all checks passed');
});
