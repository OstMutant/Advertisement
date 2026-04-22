const { chromium } = require('playwright');
const { check, screenshot, login } = require('./_common');

// Smoke test: user activity tab in user overlay + activity in settings
const UX = process.argv.includes('--ux');

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });

  await login(page, 'user3@example.com', 'password');

  // ── Create an advertisement so there's activity to show ─────────────
  await check('Go to advertisements tab', async () => {
    await page.locator('vaadin-tab').filter({ hasText: /advertisement|оголошен/i }).first().click();
    await page.waitForTimeout(1000);
  });

  await check('Open new advertisement form', async () => {
    const addBtn = page.locator('vaadin-button').filter({ hasText: /додати|add/i }).first();
    await addBtn.click();
    await page.waitForTimeout(1500);
    const visible = await page.locator('.base-overlay.overlay--visible').isVisible();
    if (!visible) throw new Error('Form overlay did not open');
  });

  await check('Fill and save advertisement', async () => {
    await page.locator('.base-overlay.overlay--visible vaadin-text-field input').fill('Activity Test Ad');
    await page.locator('.base-overlay.overlay--visible vaadin-text-area textarea').fill('Created for activity smoke test');
    await page.locator('.base-overlay.overlay--visible vaadin-button').filter({ hasText: /зберегти|save/i }).first().click();
    await page.waitForTimeout(2000);
  });
  await screenshot(page, 'activity-01-ad-created', UX);

  // ── Open Users tab ───────────────────────────────────────────────────
  await check('Go to users tab', async () => {
    await page.locator('vaadin-tab').filter({ hasText: /users|юзер|користувач/i }).first().click();
    await page.waitForTimeout(1500);
  });

  // ── Open view overlay for the admin user (user3@example.com) ────────
  await check('Open user view overlay', async () => {
    const nameCells = page.locator('vaadin-grid.user-grid vaadin-grid-cell-content .user-grid-name');
    await nameCells.first().click();
    await page.waitForTimeout(1500);
    const visible = await page.locator('.base-overlay.overlay--visible').isVisible();
    if (!visible) throw new Error('View overlay did not open');
  });
  await screenshot(page, 'activity-02-user-overlay-profile', UX);

  // ── Click Activity tab ───────────────────────────────────────────────
  await check('Activity tab is present', async () => {
    const tabs = await page.locator('.base-overlay.overlay--visible vaadin-tab').allTextContents();
    console.log('      tabs:', tabs);
    const hasActivity = tabs.some(t => /activ|активн/i.test(t));
    if (!hasActivity) throw new Error('Activity tab not found. Tabs: ' + tabs.join(', '));
  });

  await check('Click Activity tab', async () => {
    await page.locator('.base-overlay.overlay--visible vaadin-tab')
        .filter({ hasText: /activ|активн/i }).first().click();
    await page.waitForTimeout(1500);
  });
  await screenshot(page, 'activity-03-activity-tab', UX);

  await check('Activity list rendered', async () => {
    const content = await page.locator('.base-overlay.overlay--visible .user-activity-content').isVisible();
    if (!content) throw new Error('Activity content not visible');
    const body = await page.textContent('.base-overlay.overlay--visible .user-activity-content');
    console.log('      activity content:', body.replace(/\s+/g, ' ').trim().slice(0, 300));
  });

  await check('Close user overlay', async () => {
    await page.locator('.base-overlay.overlay--visible vaadin-button')
        .filter({ hasText: /закрити|close|користувачі|users/i }).last().click();
    await page.waitForTimeout(800);
  });

  // ── Settings overlay: my activity ───────────────────────────────────
  await check('Open settings overlay', async () => {
    await page.locator('vaadin-button').filter({ hasText: /налаштування|settings/i }).first().click();
    await page.waitForTimeout(1500);
    const visible = await page.locator('.base-overlay.overlay--visible').isVisible();
    if (!visible) throw new Error('Settings overlay did not open');
  });
  await screenshot(page, 'activity-04-settings-with-activity', UX);

  await check('My activity section visible in settings', async () => {
    const body = await page.textContent('.base-overlay.overlay--visible');
    console.log('      settings content snippet:', body.replace(/\s+/g, ' ').trim().slice(0, 300));
    const hasActivitySection = /activ|активн/i.test(body);
    if (!hasActivitySection) throw new Error('Activity section not found in settings');
  });

  await check('Close settings overlay', async () => {
    await page.locator('.base-overlay.overlay--visible vaadin-button')
        .filter({ hasText: /додому|home|закрити|close/i }).last().click();
    await page.waitForTimeout(800);
  });
  await screenshot(page, 'activity-05-done', UX);

  await browser.close();
})();
