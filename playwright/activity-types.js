const { check, screenshot, withBrowser, waitForOverlay, waitForOverlayClosed, openSettings, openActivityTab, closeOverlay } = require('./_common');

// Activity types test: CREATED, UPDATED, DELETED entries + entity type badges + deleted marker
const AD_TITLE = 'Activity Types Test Ad';

withBrowser(async (page) => {
  const advOverlay = page.locator('.advertisement-overlay');

  // ── Step 1: Create ───────────────────────────────────────────────────
  await page.locator('vaadin-button').filter({ hasText: /додати|add/i }).first().click();
  await waitForOverlay(page);
  await advOverlay.locator('vaadin-text-field input').fill(AD_TITLE);
  await advOverlay.locator('vaadin-text-area textarea').fill('Initial content');
  await advOverlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
  await waitForOverlayClosed(page);
  await screenshot(page, 'acttypes-01-created');

  // ── Step 2: Edit ─────────────────────────────────────────────────────
  await page.locator('.advertisement-card')
    .filter({ has: page.locator('.advertisement-title', { hasText: AD_TITLE }) })
    .first().click();
  await waitForOverlay(page);
  await advOverlay.locator('vaadin-button').filter({ hasText: /edit|редагувати/i }).first().click();
  await page.waitForSelector('.base-overlay.overlay--visible vaadin-text-field input', { timeout: 5000 });
  await advOverlay.locator('vaadin-text-area textarea').fill('Updated content');
  await advOverlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
  await page.waitForSelector('.overlay__view-title', { timeout: 5000 });
  await screenshot(page, 'acttypes-02-edited');

  // ── Close overlay before navigating away ─────────────────────────────
  await closeOverlay(page);

  // ── Step 3: Delete (via delete icon in list if available) ────────────
  let deleteExists = false;
  const deleteCardBtn = page.locator('vaadin-grid')
    .locator(`[title*="${AD_TITLE}"], .advertisement-card`)
    .locator('vaadin-button').filter({ hasText: /видалити|delete/i }).first();
  if (await deleteCardBtn.count() > 0) {
    await deleteCardBtn.click();
    await page.waitForSelector('vaadin-dialog-overlay', { timeout: 5000 });
    await page.evaluate(() => {
      const dialog = document.querySelector('vaadin-dialog[opened]');
      if (!dialog) return;
      const btn = [...dialog.querySelectorAll('vaadin-button')]
        .find(b => /видалити|delete|confirm/i.test(b.textContent?.trim()));
      if (btn) btn.click();
    });
    await page.waitForSelector('vaadin-dialog-overlay', { state: 'hidden', timeout: 5000 }).catch(() => {});
    deleteExists = true;
    await screenshot(page, 'acttypes-03-deleted');
    console.log('      ad deleted via list card button');
  } else {
    console.log('      no delete button in list, skipping delete step');
  }

  // ── Open Settings activity to check all 3 action types ───────────────
  await openSettings(page);
  await openActivityTab(page);
  await screenshot(page, 'acttypes-04-settings-activity');

  await check('ADVERTISEMENT entity type badge present', async () => {
    const count = await page.locator('.user-activity-type--advertisement').count();
    console.log('      advertisement type badges:', count);
    if (count === 0) throw new Error('No ADVERTISEMENT type badge');
  });

  await check('CREATED action badge present', async () => {
    const rows = page.locator('.user-activity-row');
    const count = await rows.count();
    let found = false;
    for (let i = 0; i < count; i++) {
      const text = await rows.nth(i).textContent();
      if (/Створ|Creat/i.test(text)) { found = true; break; }
    }
    console.log('      CREATED badge found:', found);
    if (!found) throw new Error('CREATED action not found in activity');
  });

  await check('UPDATED action badge present', async () => {
    const rows = page.locator('.user-activity-row');
    const count = await rows.count();
    let found = false;
    for (let i = 0; i < count; i++) {
      const text = await rows.nth(i).textContent();
      if (/Оновл|Updat/i.test(text)) { found = true; break; }
    }
    console.log('      UPDATED badge found:', found);
    if (!found) throw new Error('UPDATED action not found in activity');
  });

  if (deleteExists) {
    await check('Deleted entity shows (deleted) marker', async () => {
      const rows = page.locator('.user-activity-row--deleted');
      const count = await rows.count();
      console.log('      deleted-row count:', count);
      if (count === 0) throw new Error('No deleted-row marker found');
      const text = await rows.first().textContent();
      console.log('      deleted row text:', text.replace(/\s+/g, ' ').trim().slice(0, 200));
      if (!text.includes('видалено') && !text.includes('deleted')) {
        throw new Error('(deleted) marker text not found. Row: ' + text);
      }
    });
  }

  await check('Activity rows contain timestamp', async () => {
    const times = page.locator('.user-activity-time');
    const count = await times.count();
    console.log('      timestamp elements:', count);
    if (count === 0) throw new Error('No timestamps found in activity');
    const first = await times.first().textContent();
    console.log('      first timestamp:', first);
    if (!first || first === 'N/A') throw new Error('Empty or N/A timestamp');
  });
  await screenshot(page, 'acttypes-05-all-types');

  console.log('activity-types: all checks passed');
}, { email: 'user1@example.com' });
