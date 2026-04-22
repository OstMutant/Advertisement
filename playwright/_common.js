const fs = require('fs');
const path = require('path');

const SCREENSHOT_DIR = process.env.SCREENSHOT_DIR || '/screenshots';
const MAX_SCREENSHOTS = 100;

// Fast-fail: first [FAIL] kills the process so AI sees a short output
async function check(label, fn) {
  try {
    await fn();
    console.log(`[OK] ${label}`);
  } catch (e) {
    console.log(`[FAIL] ${label}: ${e.message}`);
    process.exit(1);
  }
}

async function screenshot(page, name, uxMode) {
  if (!uxMode) return;
  if (!fs.existsSync(SCREENSHOT_DIR)) fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
  const files = fs.readdirSync(SCREENSHOT_DIR)
    .filter(f => f.endsWith('.png'))
    .map(f => ({ f, t: fs.statSync(path.join(SCREENSHOT_DIR, f)).mtimeMs }))
    .sort((a, b) => a.t - b.t);
  while (files.length >= MAX_SCREENSHOTS) {
    fs.unlinkSync(path.join(SCREENSHOT_DIR, files.shift().f));
  }
  const file = path.join(SCREENSHOT_DIR, `${name}.png`);
  await page.screenshot({ path: file, fullPage: false });
  console.log(`[SCREENSHOT] ${file}`);
}

async function login(page, email = 'user1@example.com', password = 'password') {
  await page.goto('http://localhost:8080/', { waitUntil: 'networkidle' });
  await page.locator('vaadin-button').filter({ hasText: /log in|увійти/i }).first().click();
  await page.waitForSelector('vaadin-email-field', { timeout: 5000 });
  await page.locator('vaadin-email-field input').fill(email);
  await page.locator('vaadin-password-field input').fill(password);
  await page.locator('vaadin-button').filter({ hasText: /log in|увійти/i }).last().click();
  // Wait for main navigation to appear (login redirects to app)
  await page.waitForSelector('.header-settings-button', { timeout: 10000 });
  const body = await page.textContent('body');
  if (body.includes('Not signed in')) throw new Error('Login failed');
  console.log('[OK] Logged in as', email);
}

// ── Smart wait helpers (use instead of waitForTimeout) ───────────────────────

/** Wait for overlay to become visible */
async function waitForOverlay(page, timeout = 5000) {
  await page.waitForSelector('.base-overlay.overlay--visible', { timeout });
}

/** Wait for overlay to close */
async function waitForOverlayClosed(page, timeout = 5000) {
  await page.waitForSelector('.base-overlay.overlay--visible', { state: 'hidden', timeout });
}

/** Wait for vaadin-grid to contain at least one row */
async function waitForGrid(page, selector = 'vaadin-grid', timeout = 8000) {
  await page.waitForFunction(
    sel => {
      const grid = document.querySelector(sel);
      return grid && grid.shadowRoot &&
        grid.shadowRoot.querySelectorAll('tr[part~="row"]').length > 0;
    },
    selector,
    { timeout }
  );
}

module.exports = { check, screenshot, login, waitForOverlay, waitForOverlayClosed, waitForGrid };
