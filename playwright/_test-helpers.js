const { test: base, expect } = require('@playwright/test');
const fs    = require('fs');
const https = require('https');


// ── Core ──────────────────────────────────────────────────────────────────────

async function loginAs(page, email = 'user1@example.com', password = 'password') {
  await page.goto('/');
  await page.locator('vaadin-button').filter({ hasText: /log in|увійти/i }).first().click();
  await page.locator('[data-testid="login-email-label"]').waitFor({ timeout: 5000 });
  await page.locator('[data-testid="login-email-label"] input').fill(email);
  await page.locator('[data-testid="login-password-label"] input').fill(password);
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
  const overlay = page.locator('.advertisement-overlay');
  // Enter edit mode first if currently in view mode
  const editBtn = overlay.locator('vaadin-button').filter({ hasText: /edit|редагувати/i }).first();
  if (await editBtn.isVisible()) {
    await editBtn.click();
    await page.locator('[data-testid="advertisement-overlay-field-title"] input').waitFor({ timeout: 5000 });
  }
  const activityTab = overlay.locator('.adv-form-tabs vaadin-tab').filter({ hasText: /Activ|активн/i });
  if (!await activityTab.isVisible()) return false;
  await activityTab.click();
  await page.locator('.entity-activity-list').waitFor({ timeout: 5000 });
  return true;
}

async function openSettings(page) {
  await page.locator('.header-settings-button').click();
  await waitForOverlay(page);
}

async function openHistoryTab(page, overlaySelector = '.base-overlay.overlay--visible') {
  await page.locator(`${overlaySelector} vaadin-tab`)
    .filter({ hasText: /activity|activit|активн/i }).click();
  // Settings overlay uses .activity-feed-list; entity overlays use .entity-activity-list
  await page.locator(`${overlaySelector} .entity-activity-list, ${overlaySelector} .activity-feed-list`).first().waitFor({ timeout: 8000 });
}

async function openTimelineTab(page, overlaySelector = '.base-overlay.overlay--visible') {
  await page.locator(`${overlaySelector} vaadin-tab`)
    .filter({ hasText: /timeline|таймлайн/i }).click();
  await page.locator(`${overlaySelector} .activity-feed-list`).first().waitFor({ timeout: 8000 });
}

async function openActivityTab(page, overlaySelector = '.base-overlay.overlay--visible') {
  await openHistoryTab(page, overlaySelector);
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
  await overlay.locator('[data-testid="advertisement-overlay-field-title"] input').fill(title || 'Test Ad');
  await overlay.locator('[data-testid="advertisement-overlay-field-description"] textarea').fill(description || 'Test description');
  if (imagePath) {
    await overlay.locator('vaadin-upload input[type="file"]').setInputFiles(imagePath);
    await page.locator('.attachment-gallery__item').first().waitFor({ timeout: 10000 });
  }
  await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
  await waitForOverlayClosed(page);
}

// ── Screenshot helper ─────────────────────────────────────────────────────────

async function screenshot(page, name) {
  if (!process.env.PW_SCREENSHOTS) return;
  const buffer = await page.screenshot({ fullPage: false });
  await base.info().attach(name, { body: buffer, contentType: 'image/png' });
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
  openAdDetail, openHistory, openSettings, openActivityTab, openHistoryTab, openTimelineTab,
  confirmDialog, createAd,
  screenshot, downloadPng,
};
