/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Shared Playwright test library -- test-user fixtures, media constants, overlay/
 *   notification wait helpers, a screenshot helper gated on PW_SCREENSHOTS, single-value field
 *   assertions for cards/overlays, computed-style/geometry assertions, and a PNG download helper.
 *   Required by every spec file and every _flows/*.flow.js file in this suite.
 * Usage: None -- a library only, never run directly.
 * Uses: @playwright/test (test, expect), Node's fs and https modules.
 * Env: PW_SCREENSHOTS -- when unset/falsy, screenshot() is a no-op; when set, it attaches a
 *   full-page-false PNG screenshot to the current test's report.
 * Input: None.
 * Outputs: exports test, expect, TEST_USERS, YT_URL, avatar, waitForOverlayClosed, closeOverlay,
 *   closeNotification, screenshot, downloadPng, assertCardHasText, assertOverlayHasText,
 *   assertComputedColor, assertRightAligned. TEST_USERS (password "password" for all six):
 *   - userEn      -- user.en@example.com      -- USER      -- en
 *   - userUk      -- user.uk@example.com      -- USER      -- uk
 *   - moderatorEn -- moderator.en@example.com -- MODERATOR -- en
 *   - moderatorUk -- moderator.uk@example.com -- MODERATOR -- uk
 *   - adminEn     -- admin.en@example.com     -- ADMIN     -- en
 *   - adminUk     -- admin.uk@example.com     -- ADMIN     -- uk
 * Returns: N/A
 * ──────────────────────────────────────────────────────────────────────────── */
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

/**
 * Waits until the currently-visible base overlay is hidden.
 * @param {import('@playwright/test').Page} page
 * @param {number} [timeout=10000]
 * @returns {Promise<void>}
 */
async function waitForOverlayClosed(page, timeout = 10000) {
  await page.locator('.base-overlay.overlay--visible').waitFor({ state: 'hidden', timeout });
}

/**
 * Clicks the breadcrumb back button on the currently-visible overlay and waits for it to close.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<void>}
 */
async function closeOverlay(page) {
  // Scoped to the currently-visible overlay -- more than one *Overlay component (e.g. a
  // header-level overlay plus a nested one) can be initialized in the DOM at once, though only
  // one is ever ".overlay--visible" at a time.
  await page.locator('.base-overlay.overlay--visible .overlay__breadcrumb-back').click();
  await waitForOverlayClosed(page).catch(() => {});
}

// ── Notification helpers ──────────────────────────────────────────────────────

/**
 * Dismisses every currently-stacked Vaadin notification card, one at a time.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<void>}
 */
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

/**
 * Attaches a full-page-false PNG screenshot to the current test's report, gated on PW_SCREENSHOTS.
 * @param {import('@playwright/test').Page} page
 * @param {string} name attachment name shown in the HTML report.
 * @returns {Promise<void>}
 */
async function screenshot(page, name) {
  if (!process.env.PW_SCREENSHOTS) return;
  const buffer = await page.screenshot({ fullPage: false });
  await test.info().attach(name, { body: buffer, contentType: 'image/png' });
}

// ── Single-value field assertions (card / view overlay) ───────────────────────

/**
 * Asserts a card's child element is visible and contains (or equals) the expected text.
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Expect} expect
 * @param {import('@playwright/test').Locator} card
 * @param {string} selector CSS selector scoped inside card.
 * @param {string} expectedText
 * @param {string} [screenshotName] when set, takes a screenshot after the assertion passes.
 * @param {boolean} [exact=false] use toHaveText (exact) instead of toContainText.
 * @returns {Promise<void>}
 */
async function assertCardHasText(page, expect, card, selector, expectedText, screenshotName, exact = false) {
  const el = card.locator(selector);
  await expect(el).toBeVisible({ timeout: 5000 });
  if (exact) await expect(el).toHaveText(expectedText, { timeout: 5000 });
  else await expect(el).toContainText(expectedText, { timeout: 5000 });
  if (screenshotName) await screenshot(page, screenshotName);
}

/**
 * Asserts an overlay's child element is visible and contains (or equals) the expected text.
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Expect} expect
 * @param {import('@playwright/test').Locator} overlay
 * @param {string} selector CSS selector scoped inside overlay.
 * @param {string} expectedText
 * @param {string} [screenshotName] when set, takes a screenshot after the assertion passes.
 * @param {boolean} [exact=false] use toHaveText + a non-filtered locator instead of hasText-filtered.
 * @returns {Promise<void>}
 */
async function assertOverlayHasText(page, expect, overlay, selector, expectedText, screenshotName, exact = false) {
  const el = exact ? overlay.locator(selector) : overlay.locator(selector, { hasText: expectedText });
  if (exact) await expect(el).toHaveText(expectedText, { timeout: 5000 });
  else await expect(el).toBeVisible({ timeout: 5000 });
  if (screenshotName) await screenshot(page, screenshotName);
}

// ── Computed style assertion (proves a CSS rule actually resolved, not just that a class is present) ──

/**
 * Asserts an element's computed CSS property value equals the expected value.
 * @param {import('@playwright/test').Expect} expect
 * @param {import('@playwright/test').Locator} locator
 * @param {string} cssProperty getComputedStyle property name (e.g. 'color').
 * @param {string} expectedRgb expected resolved value (e.g. an 'rgb(...)' string).
 * @returns {Promise<void>}
 */
async function assertComputedColor(expect, locator, cssProperty, expectedRgb) {
  const actual = await locator.evaluate((el, prop) => getComputedStyle(el)[prop], cssProperty);
  expect(actual, `expected ${cssProperty} to be ${expectedRgb}, got ${actual}`).toBe(expectedRgb);
}

// ── Geometry assertion (proves an element is actually flush against its container's right edge) ──

/**
 * Asserts an element's right edge sits within tolerance of its container's right edge.
 * @param {import('@playwright/test').Expect} expect
 * @param {import('@playwright/test').Locator} elementLocator
 * @param {import('@playwright/test').Locator} containerLocator
 * @param {number} [toleranceInPx=3]
 * @returns {Promise<void>}
 */
async function assertRightAligned(expect, elementLocator, containerLocator, toleranceInPx = 3) {
  const elBox = await elementLocator.boundingBox();
  const containerBox = await containerLocator.boundingBox();
  expect(elBox, 'element must have a bounding box').not.toBeNull();
  expect(containerBox, 'container must have a bounding box').not.toBeNull();
  const gap = containerBox.x + containerBox.width - (elBox.x + elBox.width);
  expect(gap, `expected element's right edge within ${toleranceInPx}px of container's right edge, gap was ${gap}px`).toBeLessThanOrEqual(toleranceInPx);
}

// ── Download helper ───────────────────────────────────────────────────────────

/**
 * Downloads a PNG from a URL to a local file path via HTTPS.
 * @param {string} url
 * @param {string} dest local file path to write to.
 * @returns {Promise<void>} resolves when the file has been fully written.
 */
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
  assertCardHasText, assertOverlayHasText, assertComputedColor, assertRightAligned,
};
