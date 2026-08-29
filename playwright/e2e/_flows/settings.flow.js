/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Flow helpers for the Settings overlay -- changing/reading pagination page sizes,
 *   and a thin Settings-specific wrapper around the shared entity-activity history overlay helpers.
 * Usage: None -- a library only, required by spec files (see Input).
 * Uses: @playwright/test (expect), ./entity-activity.flow (openEntityActivity, closeEntityActivity,
 *   restoreFromEntityActivity).
 * Env: None.
 * Input: required directly by 06-seed-filter-sort-pagination.spec.js, and indirectly by every
 *   other spec that goes through ./audit.flow, which requires this file internally.
 * Outputs: exports changePageSizes, openHistory, closeHistory, restoreLatestFromActivity, getPageSizes.
 * Returns: N/A
 * ──────────────────────────────────────────────────────────────────────────── */
const { expect } = require('@playwright/test');
const { openEntityActivity, closeEntityActivity, restoreFromEntityActivity } = require('./entity-activity.flow');

/**
 * Opens the Settings overlay, sets both page size fields, and saves.
 * @param {import('@playwright/test').Page} page
 * @param {number} adsSize advertisements page size.
 * @param {number} usersSize users page size.
 * @returns {Promise<void>}
 */
async function changePageSizes(page, adsSize, usersSize) {
  await page.locator('.header-settings-button').click();
  await page.locator('.base-overlay.overlay--visible').waitFor({ timeout: 5000 });
  const adsInput   = page.locator('.settings-overlay-content vaadin-integer-field').nth(0).locator('input');
  const usersInput = page.locator('.settings-overlay-content vaadin-integer-field').nth(1).locator('input');
  await adsInput.click({ clickCount: 3 });
  await adsInput.fill(String(adsSize));
  await usersInput.click({ clickCount: 3 });
  await usersInput.fill(String(usersSize));
  await page.locator('.base-overlay.overlay--visible vaadin-button')
    .filter({ hasText: /зберегти|save/i }).click();
}

/**
 * Opens the Settings history overlay -- thin Settings-specific wrapper over the shared
 * entity-activity helper (Settings uses the same EntityActivityOverlay as every other domain now).
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<import('@playwright/test').Locator>} the entity-activity-list locator.
 */
async function openHistory(page) {
  return openEntityActivity(page, '.settings-history-button');
}

/**
 * Closes the history overlay. `via: 'settings'` (default) and `via: 'x'` both go back to
 * Settings; `via: 'home'` exits all the way out. Maps onto the shared helper's generic
 * 'parent'/'x'/'outer'.
 * @param {import('@playwright/test').Page} page
 * @param {'settings'|'x'|'home'} [via='settings']
 * @returns {Promise<void>}
 */
async function closeHistory(page, via = 'settings') {
  const mapped = { settings: 'parent', x: 'x', home: 'outer' }[via];
  await closeEntityActivity(page, mapped);
}

/**
 * Restores the first history entry, then saves the staged values -- same two-step contract as
 * the underlying shared helper.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<void>}
 */
async function restoreLatestFromActivity(page) {
  await restoreFromEntityActivity(page);
  await expect(
    page.locator('.base-overlay.overlay--visible vaadin-button').filter({ hasText: /зберегти|save/i })
  ).toBeEnabled({ timeout: 5000 });
  await page.locator('.base-overlay.overlay--visible vaadin-button')
    .filter({ hasText: /зберегти|save/i }).click();
}

/**
 * Opens the Settings overlay, reads both page size fields, closes the overlay, and returns them.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<{adsPageSize: number, usersPageSize: number}>}
 */
async function getPageSizes(page) {
  await page.locator('.header-settings-button').click();
  await page.locator('.base-overlay.overlay--visible').waitFor({ timeout: 5000 });
  const adsPageSize   = parseInt(await page.locator('.settings-overlay-content vaadin-integer-field').nth(0).locator('input').inputValue(), 10);
  const usersPageSize = parseInt(await page.locator('.settings-overlay-content vaadin-integer-field').nth(1).locator('input').inputValue(), 10);
  await page.locator('.base-overlay.overlay--visible .overlay__breadcrumb-back').click();
  await page.locator('.base-overlay.overlay--visible').waitFor({ state: 'hidden', timeout: 5000 });
  return { adsPageSize, usersPageSize };
}

module.exports = { changePageSizes, openHistory, closeHistory, restoreLatestFromActivity, getPageSizes };
