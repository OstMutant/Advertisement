const fs    = require('fs');
const https = require('https');
const { test, expect } = require('@playwright/test');

// ── Test users ────────────────────────────────────────────────────────────────

const TEST_USERS = {
  userEn:      { name: 'User EN',      email: 'user.en@example.com',      role: 'USER',      locale: 'en', password: 'password' },
  userUk:      { name: 'User UK',      email: 'user.uk@example.com',      role: 'USER',      locale: 'uk', password: 'password' },
  moderatorEn: { name: 'Moderator EN', email: 'moderator.en@example.com', role: 'MODERATOR', locale: 'en', password: 'password' },
  moderatorUk: { name: 'Moderator UK', email: 'moderator.uk@example.com', role: 'MODERATOR', locale: 'uk', password: 'password' },
  adminEn:     { name: 'Admin EN',     email: 'admin.en@example.com',     role: 'ADMIN',     locale: 'en', password: 'password' },
  adminUk:     { name: 'Admin UK',     email: 'admin.uk@example.com',     role: 'ADMIN',     locale: 'uk', password: 'password' },
};

// ── Media constants ───────────────────────────────────────────────────────────

const YT_URL = 'https://www.youtube.com/watch?v=dQw4w9WgXcQ';

const avatar = seed =>
  `https://api.dicebear.com/9.x/adventurer/png?seed=${seed}&size=256&backgroundColor=b6e3f4,c0aede,d1d4f9,ffd5dc,ffdfbf`;

// ── Overlay helpers ───────────────────────────────────────────────────────────

async function waitForOverlayClosed(page, timeout = 10000) {
  await page.locator('.base-overlay.overlay--visible').waitFor({ state: 'hidden', timeout });
}

async function closeOverlay(page) {
  await page.locator('.overlay__breadcrumb-back').click();
  await waitForOverlayClosed(page).catch(() => {});
}

// ── Notification helpers ──────────────────────────────────────────────────────

async function closeNotification(page) {
  // Loop to handle multiple stacked notifications (e.g. restore + auto-save triggers 2 cards).
  for (let attempt = 0; attempt < 5; attempt++) {
    const card = page.locator('vaadin-notification-card').first();
    if (!await card.isVisible().catch(() => false)) return;
    await card.locator('vaadin-button').click().catch(() => {});
    // Wait until no notification cards remain in DOM (covers the case where a new card
    // replaces the closed one — .first() would otherwise shift to the new card and loop endlessly).
    const cleared = await page.waitForFunction(
      () => !document.querySelector('vaadin-notification-card'),
      { timeout: 8000 }
    ).then(() => true).catch(() => false);
    if (cleared) return;
  }
}

// ── Screenshot helper ─────────────────────────────────────────────────────────

async function screenshot(page, name) {
  if (!process.env.PW_SCREENSHOTS) return;
  const buffer = await page.screenshot({ fullPage: false });
  await test.info().attach(name, { body: buffer, contentType: 'image/png' });
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

module.exports = {
  test, expect,
  TEST_USERS, YT_URL, avatar,
  waitForOverlayClosed, closeOverlay,
  closeNotification,
  screenshot, downloadPng,
};
