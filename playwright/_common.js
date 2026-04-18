const fs = require('fs');
const path = require('path');

const SCREENSHOT_DIR = process.env.SCREENSHOT_DIR || '/screenshots';
const MAX_SCREENSHOTS = 100;

async function check(label, fn) {
  try {
    await fn();
    console.log(`[OK] ${label}`);
  } catch (e) {
    console.log(`[FAIL] ${label}: ${e.message}`);
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
  await page.waitForTimeout(2000);
  const body = await page.textContent('body');
  if (body.includes('Not signed in')) throw new Error('Login failed');
  console.log('[OK] Logged in as', email);
}

module.exports = { check, screenshot, login };
