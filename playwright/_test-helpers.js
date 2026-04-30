const { test: base, expect } = require('@playwright/test');
const fs   = require('fs');
const path = require('path');
const https = require('https');

const SCREENSHOT_DIR = process.env.SCREENSHOT_DIR || '/tmp/screenshots';
const MAX_SCREENSHOTS = 100;
const UX = process.env.PW_UX === '1';

// ── Core ──────────────────────────────────────────────────────────────────────

async function loginAs(page, email = 'user1@example.com', password = 'password') {
  await page.goto('/');
  await page.locator('vaadin-button').filter({ hasText: /log in|увійти/i }).first().click();
  await page.locator('vaadin-email-field').waitFor({ timeout: 5000 });
  await page.locator('vaadin-email-field input').fill(email);
  await page.locator('vaadin-password-field input').fill(password);
  await page.locator('vaadin-button').filter({ hasText: /log in|увійти/i }).last().click();
  await page.locator('.header-settings-button').waitFor({ timeout: 10000 });
  const body = await page.textContent('body');
  if (body.includes('Not signed in')) throw new Error('Login failed for ' + email);
}

// ── Overlay helpers ───────────────────────────────────────────────────────────

async function waitForOverlay(page, timeout = 10000) {
  await page.locator('.base-overlay.overlay--visible').waitFor({ timeout });
}

async function waitForOverlayClosed(page, timeout = 10000) {
  await page.locator('.base-overlay.overlay--visible').waitFor({ state: 'hidden', timeout });
}

async function closeOverlay(page) {
  await page.locator('.overlay__breadcrumb-back').click();
  await waitForOverlayClosed(page).catch(() => {});
}

// ── Navigation helpers ────────────────────────────────────────────────────────

async function openAdDetail(page, title) {
  await page.locator('.advertisement-title').first().waitFor({ timeout: 5000 });
  await page.locator('.advertisement-card')
    .filter({ has: page.locator('.advertisement-title', { hasText: title }) })
    .first().click();
  await waitForOverlay(page);
}

async function openHistory(page) {
  const historyTab = page.locator('.adv-overlay-tabs vaadin-tab', { hasText: /Іс|Hist/i });
  if (!await historyTab.isVisible()) return false;
  await historyTab.click();
  await page.locator('.adv-history-list').waitFor({ timeout: 5000 });
  return true;
}

async function openSettings(page) {
  await page.locator('.header-settings-button').click();
  await waitForOverlay(page);
}

async function openActivityTab(page, overlaySelector = '.base-overlay.overlay--visible') {
  await page.locator(`${overlaySelector} vaadin-tab`)
    .filter({ hasText: /activ|активн/i }).click();
  await page.locator('.user-activity-list').first().waitFor({ timeout: 5000 });
}

async function confirmDialog(page, textSource = 'Оновити|Update') {
  await page.locator('vaadin-dialog-overlay').waitFor({ timeout: 5000 });
  await page.evaluate((src) => {
    const re = new RegExp(src, 'i');
    const dialog = document.querySelector('vaadin-dialog[opened]');
    if (!dialog) throw new Error('No open dialog');
    const btn = [...dialog.querySelectorAll('vaadin-button')].find(b => re.test(b.textContent?.trim()));
    if (!btn) throw new Error('Confirm button not found: ' + src);
    btn.click();
  }, textSource);
  await page.locator('vaadin-dialog-overlay').waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {});
}

// ── Ad creation helper ────────────────────────────────────────────────────────

async function createAd(page, { title, description, imagePath } = {}) {
  await page.locator('.add-advertisement-button').click();
  await waitForOverlay(page);
  const overlay = page.locator('.advertisement-overlay');
  await overlay.locator('vaadin-text-field input').fill(title || 'Test Ad');
  await overlay.locator('vaadin-text-area textarea').fill(description || 'Test description');
  if (imagePath) {
    await overlay.locator('vaadin-upload input[type="file"]').setInputFiles(imagePath);
    await page.locator('.attachment-gallery__item').first().waitFor({ timeout: 10000 });
  }
  await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
  await waitForOverlayClosed(page);
}

// ── Screenshot helper ─────────────────────────────────────────────────────────

async function screenshot(page, name) {
  const buffer = await page.screenshot({ fullPage: false });

  // Always attach to Playwright HTML report
  await base.info().attach(name, { body: buffer, contentType: 'image/png' });

  // Save locally only in --ux mode (for AI visual analysis via Read tool)
  if (!UX) return;
  if (!fs.existsSync(SCREENSHOT_DIR)) fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
  const files = fs.readdirSync(SCREENSHOT_DIR)
    .filter(f => f.endsWith('.png'))
    .map(f => ({ f, t: fs.statSync(path.join(SCREENSHOT_DIR, f)).mtimeMs }))
    .sort((a, b) => a.t - b.t);
  while (files.length >= MAX_SCREENSHOTS) fs.unlinkSync(path.join(SCREENSHOT_DIR, files.shift().f));
  const file = path.join(SCREENSHOT_DIR, `${name}.png`);
  fs.writeFileSync(file, buffer);
  console.log(`[SCREENSHOT] Attached to report & saved to ${file}`);
}

// ── Download helper ───────────────────────────────────────────────────────────

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

const test = base;

module.exports = {
  test, expect,
  loginAs,
  waitForOverlay, waitForOverlayClosed, closeOverlay,
  openAdDetail, openHistory, openSettings, openActivityTab,
  confirmDialog, createAd,
  screenshot, downloadPng,
};
