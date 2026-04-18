const { chromium } = require('playwright');
const { check, screenshot, login } = require('./_common');

// Tests the Users tab — requires ADMIN role (user3@example.com, i%3=0 → ADMIN)
// Grid: row click → view overlay | edit button (pencil) → edit overlay
const UX = process.argv.includes('--ux');

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });

  await login(page, 'user3@example.com', 'password');

  await check('Users tab visible for admin', async () => {
    const tabs = await page.locator('vaadin-tab').allTextContents();
    console.log('      tabs:', tabs);
    if (!tabs.some(t => /users|юзер|користувач/i.test(t))) throw new Error('Users tab not found');
  });

  await check('Click Users tab', async () => {
    await page.locator('vaadin-tab').filter({ hasText: /users|юзер|користувач/i }).first().click();
    await page.waitForTimeout(1500);
  });
  await screenshot(page, 'users-01-list', UX);

  await check('User grid loaded', async () => {
    const cells = page.locator('vaadin-grid.user-grid vaadin-grid-cell-content');
    const count = await cells.count();
    console.log(`      found ${count} grid cells`);
    if (count === 0) throw new Error('Grid is empty');
  });

  // ── View overlay (row click) ───────────────────────────────────────────────
  await check('Click row to open view overlay', async () => {
    // Click the name/email cell (2nd column) of the first row — avoids action buttons
    const nameCells = page.locator('vaadin-grid.user-grid vaadin-grid-cell-content .user-grid-name');
    await nameCells.first().click();
    await page.waitForTimeout(1500);
    const visible = await page.locator('.base-overlay.overlay--visible').isVisible();
    if (!visible) throw new Error('View overlay did not open');
    const body = await page.textContent('.base-overlay.overlay--visible');
    console.log('      view overlay content:', body.replace(/\s+/g, ' ').trim().slice(0, 200));
  });
  await screenshot(page, 'users-02-view-overlay', UX);

  await check('Close view overlay', async () => {
    await page.locator('.base-overlay.overlay--visible vaadin-button').filter({ hasText: /закрити|close|користувачі|users/i }).last().click();
    await page.waitForTimeout(1000);
  });

  // ── Edit overlay (pencil button) ───────────────────────────────────────────
  await check('Click edit button to open edit overlay', async () => {
    // Edit button is the first button inside .user-grid-actions in the first row
    const editBtn = page.locator('vaadin-grid.user-grid .user-grid-actions vaadin-button, vaadin-grid.user-grid .user-grid-actions button').first();
    await editBtn.click();
    await page.waitForTimeout(1500);
    const visible = await page.locator('.base-overlay.overlay--visible').isVisible();
    if (!visible) throw new Error('Edit overlay did not open');
    const body = await page.textContent('.base-overlay.overlay--visible');
    console.log('      edit overlay content:', body.replace(/\s+/g, ' ').trim().slice(0, 200));
  });
  await screenshot(page, 'users-03-edit-overlay', UX);

  await check('Close edit overlay', async () => {
    await page.locator('.base-overlay.overlay--visible vaadin-button').filter({ hasText: /скасувати|cancel|закрити|close/i }).first().click();
    await page.waitForTimeout(1000);
  });
  await screenshot(page, 'users-04-back-to-list', UX);

  await browser.close();
})();
