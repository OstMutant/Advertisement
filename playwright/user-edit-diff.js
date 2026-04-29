const { check, screenshot, withBrowser, waitForOverlay, waitForOverlayClosed, openSettings, openActivityTab } = require('./_common');

withBrowser(async (page) => {
  // ── Go to Users tab ──────────────────────────────────────────────────
  await page.locator('vaadin-tab').filter({ hasText: /users|юзер|користувач/i }).first().click();
  await page.waitForSelector('vaadin-grid.user-grid', { timeout: 8000 });
  await screenshot(page, 'userdiff-01-users-list');

  // ── Open first user ──────────────────────────────────────────────────
  const nameCells = page.locator('vaadin-grid.user-grid vaadin-grid-cell-content .user-grid-name');
  await nameCells.first().click();
  await waitForOverlay(page);
  await screenshot(page, 'userdiff-02-user-opened');

  // ── Click Edit (admin can edit others) ───────────────────────────────
  const editBtn = page.locator('.base-overlay.overlay--visible vaadin-button').filter({ hasText: /edit|редагувати/i });
  if (await editBtn.count() === 0) {
    console.log('No edit button — try next user');
    await page.keyboard.press('Escape');
    await waitForOverlayClosed(page);
    await nameCells.nth(1).click();
    await waitForOverlay(page);
    await page.locator('.base-overlay.overlay--visible vaadin-button').filter({ hasText: /edit|редагувати/i }).first().click();
  } else {
    await editBtn.first().click();
  }
  await page.waitForSelector('.base-overlay.overlay--visible vaadin-text-field input', { timeout: 5000 });
  await screenshot(page, 'userdiff-03-edit-form');

  // ── Change the name ───────────────────────────────────────────────────
  const nameInput = page.locator('.base-overlay.overlay--visible vaadin-text-field input').first();
  const oldName = await nameInput.inputValue();
  const base = oldName.replace(/ (Edited|Updated)$/, '');
  const newName = oldName.endsWith(' Edited') ? base + ' Updated' : base + ' Edited';
  await nameInput.fill(newName);
  console.log('Renamed:', oldName, '->', newName);

  // ── Save ──────────────────────────────────────────────────────────────
  await page.locator('.base-overlay.overlay--visible vaadin-button').filter({ hasText: /зберегти|save/i }).first().click();
  await page.waitForSelector('.user-view-name', { timeout: 5000 });
  await screenshot(page, 'userdiff-04-saved');

  // ── Close user overlay ────────────────────────────────────────────────
  await page.locator('.base-overlay.overlay--visible vaadin-button').filter({ hasText: /користувач|users/i }).first().click();
  await waitForOverlayClosed(page);

  // ── Open Settings and switch to Activity tab ──────────────────────────
  await openSettings(page);
  await openActivityTab(page);
  await screenshot(page, 'userdiff-05-settings-activity');

  await check('Changes summary in activity', async () => {
    const changes = page.locator('.user-activity-changes');
    const count = await changes.count();
    console.log('      changes elements:', count);
    if (count === 0) throw new Error('No changes summary found');
    const texts = await changes.allTextContents();
    console.log('      changes texts:', texts);
  });

  console.log('user-edit-diff: all checks passed');
}, { email: 'user3@example.com' });
