/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Shared Playwright test library -- test-user fixtures, media constants, overlay/
 *   notification wait helpers, a screenshot helper gated on PW_SCREENSHOTS, single-value field
 *   assertions for cards/overlays, field presence/absence/vertical-order assertions,
 *   computed-style/geometry assertions, and a PNG download helper. Required by every spec file
 *   and every _flows/*.flow.js file in this suite.
 * Usage: None -- a library only, never run directly.
 * Uses: @playwright/test (test, expect), Node's fs and https modules.
 * Env: PW_SCREENSHOTS -- when unset/falsy, screenshot() is a no-op; when set, it attaches a
 *   full-page-false PNG screenshot to the current test's report.
 * Input: None.
 * Outputs: exports test, expect, TEST_USERS, YT_URL, avatar, stubYoutubeEmbed, waitForOverlayClosed,
 *   closeOverlay, closeNotification, screenshot, downloadPng, assertCardHasText, assertOverlayHasText,
 *   assertComputedColor, assertRightAligned, assertAbsent, assertVerticalOrder. TEST_USERS holds only
 *   adminEn -- admin.en@example.com, ADMIN, en, password "password" (ADMIN_EMAIL and ADMIN_PASSWORD
 *   override the e-mail and password) -- the first account on an empty database, signed up by the
 *   setup project; every other account is created by the fixtures in ./_fixtures.
 * Returns: N/A
 * ──────────────────────────────────────────────────────────────────────────── */
const fs    = require('fs');
const https = require('https');
const { test, expect } = require('@playwright/test');

// ── Test users ────────────────────────────────────────────────────────────────

const TEST_USERS = {
  adminEn: { name: 'Admin EN', email: process.env.ADMIN_EMAIL || 'admin.en@example.com', role: 'ADMIN', locale: 'en', password: process.env.ADMIN_PASSWORD || 'password' },
};

// ── Media constants ───────────────────────────────────────────────────────────

const YT_URL = 'https://www.youtube.com/watch?v=dQw4w9WgXcQ';

const avatar = seed =>
  `https://api.dicebear.com/9.x/adventurer/png?seed=${seed}&size=256&backgroundColor=b6e3f4,c0aede,d1d4f9,ffd5dc,ffdfbf`;

/**
 * Routes the YouTube embed URL pattern to a minimal fixture response, so lightbox/video tests
 * don't depend on YouTube actually being reachable from wherever Playwright runs.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<void>}
 */
async function stubYoutubeEmbed(page) {
  await page.route('https://www.youtube.com/embed/**', route =>
    route.fulfill({ status: 200, contentType: 'text/html', body: '<!doctype html><html><body></body></html>' })
  );
}

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
    await card.getByRole('button').click().catch(() => {});
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
 * @param {import('@playwright/test').Locator} el the card's child element to check.
 * @param {string} expectedText
 * @param {string} [screenshotName] when set, takes a screenshot after the assertion passes.
 * @param {boolean} [exact=false] use toHaveText (exact) instead of toContainText.
 * @returns {Promise<void>}
 */
async function assertCardHasText(page, expect, el, expectedText, screenshotName, exact = false) {
  await expect(el).toBeVisible({ timeout: 5000 });
  if (exact) await expect(el).toHaveText(expectedText, { timeout: 5000 });
  else await expect(el).toContainText(expectedText, { timeout: 5000 });
  if (screenshotName) await screenshot(page, screenshotName);
}

/**
 * Asserts an overlay's child element is visible and contains (or equals) the expected text.
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Expect} expect
 * @param {import('@playwright/test').Locator} target the overlay's child element to check.
 * @param {string} expectedText
 * @param {string} [screenshotName] when set, takes a screenshot after the assertion passes.
 * @param {boolean} [exact=false] use toHaveText + a non-filtered locator instead of hasText-filtered.
 * @returns {Promise<void>}
 */
async function assertOverlayHasText(page, expect, target, expectedText, screenshotName, exact = false) {
  const el = exact ? target : target.filter({ hasText: expectedText });
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

// ── Field presence / absence / vertical-order assertions ──────────────────────

/**
 * Asserts `field` matches no element -- for optional fields that must not render at all when
 * they have no value.
 * @param {import('@playwright/test').Expect} expect
 * @param {import('@playwright/test').Locator} field
 * @returns {Promise<void>}
 */
async function assertAbsent(expect, field) {
  await expect(field).toHaveCount(0);
}

/**
 * Asserts the given fields render top-to-bottom, in the given order.
 * A field that matches nothing is skipped (pair with assertAbsent to also require its
 * absence). Each present element must start at or below the previous present one.
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Expect} expect
 * @param {import('@playwright/test').Locator[]} fields in expected top-to-bottom order.
 * @param {string} [screenshotName] when set, takes a screenshot once every present field checks out.
 * @returns {Promise<void>}
 */
async function assertVerticalOrder(page, expect, fields, screenshotName) {
  let prevTop = -Infinity;
  let prevName = 'top of container';
  for (const field of fields) {
    const loc = field.first();
    const name = String(field);
    if ((await loc.count()) === 0) continue;
    await expect(loc, `${name} is visible`).toBeVisible({ timeout: 5000 });
    const box = await loc.boundingBox();
    expect(box, `${name} has a bounding box`).not.toBeNull();
    expect(box.y, `${name} renders below ${prevName}`).toBeGreaterThanOrEqual(prevTop - 1);
    prevTop = box.y;
    prevName = name;
  }
  if (screenshotName) await screenshot(page, screenshotName);
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
  TEST_USERS, YT_URL, avatar, stubYoutubeEmbed,
  waitForOverlayClosed, closeOverlay,
  closeNotification,
  screenshot, downloadPng,
  assertCardHasText, assertOverlayHasText, assertComputedColor, assertRightAligned,
  assertAbsent, assertVerticalOrder,
};
