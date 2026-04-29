const { check, screenshot, withBrowser, waitForOverlay, waitForOverlayClosed } = require('./_common');

// Tests the Users tab — requires ADMIN role (user3@example.com)
// Grid: row click → view overlay | edit button (pencil) → edit overlay
withBrowser(async (page) => {
  await check('Users tab visible for admin', async () => {
    const tabs = await page.locator('vaadin-tab').allTextContents();
    console.log('      tabs:', tabs);
    if (!tabs.some(t => /users|юзер|користувач/i.test(t))) throw new Error('Users tab not found');
  });

  await check('Click Users tab', async () => {
    await page.locator('vaadin-tab').filter({ hasText: /users|юзер|користувач/i }).first().click();
    await page.waitForSelector('vaadin-grid.user-grid', { timeout: 8000 });
  });
  await screenshot(page, 'users-01-list');

  await check('User grid loaded', async () => {
    const count = await page.locator('vaadin-grid.user-grid vaadin-grid-cell-content').count();
    console.log(`      found ${count} grid cells`);
    if (count === 0) throw new Error('Grid is empty');
  });

  await check('Click row to open view overlay', async () => {
    await page.locator('vaadin-grid.user-grid vaadin-grid-cell-content .user-grid-name').first().click();
    await waitForOverlay(page);
    const body = await page.textContent('.base-overlay.overlay--visible');
    console.log('      view overlay content:', body.replace(/\s+/g, ' ').trim().slice(0, 200));
  });
  await screenshot(page, 'users-02-view-overlay');

  await check('Close view overlay', async () => {
    await page.locator('.base-overlay.overlay--visible vaadin-button')
      .filter({ hasText: /закрити|close|користувачі|users/i }).last().click();
    await waitForOverlayClosed(page);
  });

  await check('Click edit button to open edit overlay', async () => {
    await page.locator('vaadin-grid.user-grid .user-grid-actions vaadin-button, vaadin-grid.user-grid .user-grid-actions button').first().click();
    await waitForOverlay(page);
    const body = await page.textContent('.base-overlay.overlay--visible');
    console.log('      edit overlay content:', body.replace(/\s+/g, ' ').trim().slice(0, 200));
  });
  await screenshot(page, 'users-03-edit-overlay');

  await check('Close edit overlay', async () => {
    await page.locator('.base-overlay.overlay--visible vaadin-button')
      .filter({ hasText: /скасувати|cancel|закрити|close/i }).first().click();
    await waitForOverlayClosed(page);
  });
  await screenshot(page, 'users-04-back-to-list');
}, { email: 'user3@example.com' });
