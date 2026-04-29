const { chromium } = require('playwright');
const fs   = require('fs');
const path = require('path');
const https = require('https');

const SCREENSHOT_DIR = process.env.SCREENSHOT_DIR || '/screenshots';
const MAX_SCREENSHOTS = 100;
const UX = process.argv.includes('--ux');

async function check(label, fn) {
  try {
    await fn();
    console.log(`[OK] ${label}`);
  } catch (e) {
    console.log(`[FAIL] ${label}: ${e.message}`);
    process.exit(1);
  }
}

async function screenshot(page, name, uxMode = UX) {
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
  await page.waitForSelector('.header-settings-button', { timeout: 10000 });
  const body = await page.textContent('body');
  if (body.includes('Not signed in')) throw new Error('Login failed');
  console.log('[OK] Logged in as', email);
}

async function withBrowser(fn, { email = 'user1@example.com', password = 'password', skipLogin = false } = {}) {
  const browser = await chromium.launch();
  try {
    const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
    if (!skipLogin) await login(page, email, password);
    await fn(page);
  } finally {
    await browser.close();
  }
}

// ── Smart wait helpers ────────────────────────────────────────────────────────

async function waitForOverlay(page, timeout = 5000) {
  await page.waitForSelector('.base-overlay.overlay--visible', { timeout });
}

async function waitForOverlayClosed(page, timeout = 5000) {
  await page.waitForSelector('.base-overlay.overlay--visible', { state: 'hidden', timeout });
}

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

// ── Action helpers ────────────────────────────────────────────────────────────

function downloadPng(url, dest) {
  return new Promise((resolve, reject) => {
    const file = fs.createWriteStream(dest);
    https.get(url, res => {
      if (res.statusCode !== 200) return reject(new Error(`HTTP ${res.statusCode} for ${url}`));
      res.pipe(file);
      file.on('finish', () => file.close(resolve));
    }).on('error', reject);
  });
}

async function createAd(page, { title, description, imagePath } = {}) {
  await page.locator('.add-advertisement-button').click();
  await waitForOverlay(page);
  const overlay = page.locator('.advertisement-overlay');
  await overlay.locator('vaadin-text-field input').fill(title || 'Test Ad');
  await overlay.locator('vaadin-text-area textarea').fill(description || 'Test description');
  if (imagePath) {
    await overlay.locator('vaadin-upload input[type="file"]').setInputFiles(imagePath);
    await page.waitForSelector('.attachment-gallery__item', { timeout: 10000 });
  }
  await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
  await waitForOverlayClosed(page);
}

async function openAdDetail(page, title) {
  await page.waitForSelector('.advertisement-title', { timeout: 5000 });
  await page.locator('.advertisement-card')
    .filter({ has: page.locator('.advertisement-title', { hasText: title }) })
    .first().click();
  await waitForOverlay(page);
}

async function openHistory(page) {
  const historyTab = page.locator('.adv-overlay-tabs vaadin-tab', { hasText: /Іс|Hist/i });
  if (!await historyTab.isVisible()) return false;
  await historyTab.click();
  await page.waitForSelector('.adv-history-list', { timeout: 5000 });
  return true;
}

async function closeOverlay(page) {
  await page.locator('.overlay__breadcrumb-back').click();
  await waitForOverlayClosed(page).catch(() => {});
}

async function openSettings(page) {
  await page.locator('.header-settings-button').click();
  await waitForOverlay(page);
}

async function clickEdit(page) {
  await page.locator('.base-overlay.overlay--visible vaadin-button')
    .filter({ hasText: /edit|редагувати/i }).first().click();
  await page.waitForSelector('.base-overlay.overlay--visible vaadin-text-field input', { timeout: 5000 });
}

async function confirmDialog(page, textSource = 'Оновити|Update') {
  await page.waitForSelector('vaadin-dialog-overlay', { timeout: 5000 });
  await page.evaluate((src) => {
    const re = new RegExp(src, 'i');
    const dialog = document.querySelector('vaadin-dialog[opened]');
    if (!dialog) throw new Error('No open dialog');
    const btn = [...dialog.querySelectorAll('vaadin-button')].find(b => re.test(b.textContent?.trim()));
    if (!btn) throw new Error('Confirm button not found: ' + src);
    btn.click();
  }, textSource);
  await page.waitForSelector('vaadin-dialog-overlay', { state: 'hidden', timeout: 5000 }).catch(() => {});
}

async function openActivityTab(page, overlaySelector = '.base-overlay.overlay--visible') {
  await page.locator(`${overlaySelector} vaadin-tab`)
    .filter({ hasText: /activ|активн/i }).click();
  await page.waitForSelector('.user-activity-list', { timeout: 5000 });
}

module.exports = {
  check, screenshot, login, UX,
  withBrowser,
  waitForOverlay, waitForOverlayClosed, waitForGrid,
  downloadPng,
  createAd, openAdDetail, openHistory, closeOverlay,
  openSettings, clickEdit, confirmDialog, openActivityTab,
};
