/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Flow helpers for the advertisement list's filter panel -- opening/closing the
 *   query block, filling the title filter, applying/clearing filters, and asserting the
 *   query-status-bar text reflects the active filter.
 * Usage: None -- a library only, required by spec files (see Input).
 * Uses: ../_helpers (screenshot).
 * Env: None.
 * Input: required by 01-marketplace-empty-flow.spec.js.
 * Outputs: exports runOpenFilterPanelFlow, runFillTitleFilterFlow, runApplyFilterFlow,
 *   runVerifyFilterStatusFlow, runClearFilterFlow, runCloseFilterPanelFlow.
 * Returns: N/A
 * ──────────────────────────────────────────────────────────────────────────── */
const { screenshot } = require('../_helpers');

/**
 * Opens the advertisement query/filter panel by clicking the query status bar.
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Expect} expect
 * @returns {Promise<void>}
 */
async function runOpenFilterPanelFlow(page, expect) {
  await page.locator('.query-status-bar').first().click();
  await expect(page.locator('.advertisement-query-block')).toBeVisible({ timeout: 5000 });
  await screenshot(page, 'filter-panel-open');
}

/**
 * Fills the title filter input inside the open query block.
 * @param {import('@playwright/test').Page} page
 * @param {string} text
 * @returns {Promise<void>}
 */
async function runFillTitleFilterFlow(page, text) {
  await page.locator('.advertisement-query-block .query-text input').first().fill(text);
  await screenshot(page, 'filter-title-filled');
}

/**
 * Clicks the query block's Apply button and waits for the filtered card list to render.
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Expect} expect
 * @returns {Promise<void>}
 */
async function runApplyFilterFlow(page, expect) {
  await page.locator('.advertisement-query-block vaadin-button[title*="Застосувати"], .advertisement-query-block vaadin-button[title*="Apply"]').first().click();
  await expect(page.locator('.advertisement-card').first()).toBeVisible({ timeout: 8000 }).catch(() => {});
  await screenshot(page, 'filter-applied');
}

/**
 * Clicks the query block's Clear button and waits for the pagination count to reappear.
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Expect} expect
 * @returns {Promise<void>}
 */
async function runClearFilterFlow(page, expect) {
  await page.locator('.advertisement-query-block vaadin-button[title*="Очистити"], .advertisement-query-block vaadin-button[title*="Clear"]').first().click();
  await expect(page.locator('.pagination-count')).toBeVisible({ timeout: 8000 });
  await screenshot(page, 'filter-cleared');
}

/**
 * Asserts the query-status-bar text contains the expected active-filter summary.
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Expect} expect
 * @param {string} text expected substring of the status bar text.
 * @returns {Promise<void>}
 */
async function runVerifyFilterStatusFlow(page, expect, text) {
  await expect(page.locator('.query-status-bar')).toContainText(text, { timeout: 5000 });
  await screenshot(page, 'filter-status-active');
}

/**
 * Closes the advertisement query/filter panel by clicking the query status bar again.
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Expect} expect
 * @returns {Promise<void>}
 */
async function runCloseFilterPanelFlow(page, expect) {
  await page.locator('.query-status-bar').first().click();
  await expect(page.locator('.advertisement-query-block')).not.toBeVisible({ timeout: 5000 });
  await screenshot(page, 'filter-panel-closed');
}

module.exports = {
  runOpenFilterPanelFlow,
  runFillTitleFilterFlow,
  runApplyFilterFlow,
  runVerifyFilterStatusFlow,
  runClearFilterFlow,
  runCloseFilterPanelFlow,
};
