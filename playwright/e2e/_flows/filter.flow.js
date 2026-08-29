/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Shared query-bar helpers for the filter/sort/pagination panels used by both the
 *   advertisement and user list views -- opening the panel, applying/clearing filters,
 *   waiting for Vaadin server round-trips, filling every filter field type (text, number, role,
 *   category, city, ad-kind, date range), sort-icon cycling, pagination navigation, and higher-
 *   level verification helpers that assert a full sort/date-range/pagination scenario end to end.
 * Usage: None -- a library only, required by spec files (see Input).
 * Uses: @playwright/test (expect), ../_helpers (screenshot).
 * Env: None.
 * Input: required directly by 06-seed-filter-sort-pagination.spec.js, and indirectly by every
 *   other spec that goes through ./timeline.flow, which requires this file internally.
 * Outputs: exports openQueryPanel, applyFilter, clearFilter, waitForVaadin,
 *   clickSort, resetDefaultSorts, fillText, fillNumber, fillRole, fillCategory, fillCity,
 *   fillAdKind, setDateRange, getRow, getTotalCount, goToNextPage, goToPrevPage, goToFirstPage,
 *   goToLastPage, verifyPagination, verifyDateRangeFilters, verifySortColumn.
 * Returns: N/A
 * ──────────────────────────────────────────────────────────────────────────── */
const { expect } = require('@playwright/test');
const { screenshot } = require('../_helpers');

// ── panel helpers ─────────────────────────────────────────────────────────────

/**
 * Opens the query filter/sort panel by clicking the visible query-status-bar. Both views (ads +
 * users) have a .query-status-bar; only the active tab's bar is visible, so :visible scopes the
 * click to whichever one is currently on screen.
 * @param {import('@playwright/test').Page} page
 * @param {string} blockSelector CSS selector for the panel expected to become visible.
 * @returns {Promise<void>}
 */
async function openQueryPanel(page, blockSelector) {
  await page.locator('.query-status-bar:visible').click();
  await page.locator(blockSelector).waitFor({ state: 'visible', timeout: 5000 });
}

// ── action helpers ────────────────────────────────────────────────────────────

/**
 * Waits for Vaadin to finish the server round-trip triggered by a button click. setTimeout(0)
 * yields so Vaadin can enqueue its WebSocket message before isActive() is checked.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<void>}
 */
async function waitForVaadin(page) {
  await page.evaluate(() => new Promise(r => setTimeout(r, 0)));
  await page.waitForFunction(() => {
    if (window.Vaadin?.Flow?.clients) {
      const clients = Object.values(window.Vaadin.Flow.clients);
      if (clients.some(c => typeof c.isActive === 'function' && c.isActive())) return false;
    }
    return true;
  }, { timeout: 8000 });
}

/**
 * Clicks the Apply button and waits for Vaadin to settle. Scoped to .query-action-block (the
 * Apply/Clear button wrapper QueryActionBlock adds around itself) rather than the whole
 * blockSelector -- fields inside the block (e.g. UserPickerField's own clear button) can carry a
 * title containing "Apply"/"Clear" too, which would otherwise match.
 * @param {import('@playwright/test').Page} page
 * @param {string} blockSelector
 * @returns {Promise<void>}
 */
async function applyFilter(page, blockSelector) {
  await page.locator(`${blockSelector} .query-action-block vaadin-button[title*="Apply"]`).click();
  await waitForVaadin(page);
}

/**
 * Clicks the Clear button and waits for Vaadin to settle. Same scoping rationale as applyFilter.
 * @param {import('@playwright/test').Page} page
 * @param {string} blockSelector
 * @returns {Promise<void>}
 */
async function clearFilter(page, blockSelector) {
  await page.locator(`${blockSelector} .query-action-block vaadin-button[title*="Clear"]`).click();
  await waitForVaadin(page);
}

// ── row locator ───────────────────────────────────────────────────────────────

/**
 * Locates the query-inline-row whose sort label matches labelText, scoped inside blockSelector.
 * @param {import('@playwright/test').Page} page
 * @param {string} blockSelector
 * @param {string} labelText
 * @returns {import('@playwright/test').Locator}
 */
function getRow(page, blockSelector, labelText) {
  return page.locator(`${blockSelector} .query-inline-row`)
    .filter({ has: page.locator('.query-inline-label-sort', { hasText: labelText }) });
}

// ── sort helpers ──────────────────────────────────────────────────────────────

/**
 * Clicks a column's sort icon and applies. Cycles NEUTRAL → ASC → DESC → NEUTRAL on each call.
 * After apply, waits for resultItemSelector to be visible (data refresh confirmation).
 * @param {import('@playwright/test').Page} page
 * @param {string} blockSelector
 * @param {string} labelText sort column label.
 * @param {string} [resultItemSelector] when set, waited on for visibility after apply.
 * @returns {Promise<void>}
 */
async function clickSort(page, blockSelector, labelText, resultItemSelector) {
  await getRow(page, blockSelector, labelText).locator('.sort-icon').click();
  await applyFilter(page, blockSelector);
  if (resultItemSelector) {
    await page.locator(resultItemSelector).first().waitFor({ state: 'visible', timeout: 8000 });
  }
}

/**
 * Resets both default sort columns (Updated At, Created At -- both DESC by default) to NEUTRAL.
 * Both blocks default to "Updated At DESC, Created At DESC"; clearFilter() resets to that default,
 * not to NEUTRAL, so this helper clicks each default sort icon once (DESC → NEUTRAL) and applies,
 * yielding a fully neutral sort state so individual sort tests can run in isolation.
 * @param {import('@playwright/test').Page} page
 * @param {string} blockSelector
 * @returns {Promise<void>}
 */
async function resetDefaultSorts(page, blockSelector) {
  await getRow(page, blockSelector, 'Updated At').locator('.sort-icon').click();
  await applyFilter(page, blockSelector);
  await getRow(page, blockSelector, 'Created At').locator('.sort-icon').click();
  await applyFilter(page, blockSelector);
}

// ── field fill helpers ────────────────────────────────────────────────────────

/**
 * Fills a text filter field identified by its row label.
 * @param {import('@playwright/test').Page} page
 * @param {string} blockSelector
 * @param {string} labelText
 * @param {string} value
 * @returns {Promise<void>}
 */
async function fillText(page, blockSelector, labelText, value) {
  await getRow(page, blockSelector, labelText).locator('.query-text input').fill(value);
}

/**
 * Fills a min/max number range filter identified by its row label. Either bound may be omitted.
 * @param {import('@playwright/test').Page} page
 * @param {string} blockSelector
 * @param {string} labelText
 * @param {number} [minVal]
 * @param {number} [maxVal]
 * @returns {Promise<void>}
 */
async function fillNumber(page, blockSelector, labelText, minVal, maxVal) {
  const inputs = getRow(page, blockSelector, labelText).locator('.query-number input');
  if (minVal !== undefined) await inputs.nth(0).fill(String(minVal));
  if (maxVal !== undefined) await inputs.nth(1).fill(String(maxVal));
}

/**
 * Selects one role value from the multi-select combo in the block. Overlay item elements are
 * inside shadow DOM -- uses keyboard navigation instead of a locator click.
 * @param {import('@playwright/test').Page} page
 * @param {string} blockSelector
 * @param {string} role
 * @returns {Promise<void>}
 */
async function fillRole(page, blockSelector, role) {
  const combo = page.locator(`${blockSelector} vaadin-multi-select-combo-box`);
  await combo.locator('input').click();
  await page.locator('vaadin-multi-select-combo-box-overlay').first().waitFor({ state: 'visible', timeout: 5000 });
  await page.keyboard.type(role);
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Enter');
  await page.evaluate((sel) => {
    const combo = document.querySelector(sel + ' vaadin-multi-select-combo-box');
    if (combo) combo.opened = false;
  }, blockSelector);
  await page.locator('vaadin-multi-select-combo-box-overlay').first().waitFor({ state: 'hidden', timeout: 5000 });
}

/**
 * Selects one category value from the multi-select combo. Scoped by data-testid and waits on the
 * combo's own `opened` property -- avoids colliding with the other multi-select combo on this block.
 * @param {import('@playwright/test').Page} page
 * @param {string} blockSelector
 * @param {string} categoryName
 * @returns {Promise<void>}
 */
async function fillCategory(page, blockSelector, categoryName) {
  const selector = `${blockSelector} [data-testid="advertisement-filter-categories"]`;
  const combo = page.locator(selector);
  await combo.locator('input').click();
  await page.waitForFunction((sel) => document.querySelector(sel)?.opened === true, selector, { timeout: 5000 });
  await page.keyboard.type(categoryName);
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Enter');
  await page.evaluate((sel) => {
    const el = document.querySelector(sel);
    if (el) el.opened = false;
  }, selector);
  await page.waitForFunction((sel) => document.querySelector(sel)?.opened === false, selector, { timeout: 5000 });
}

/**
 * Selects one value from the single-select city combo in the advertisement query block.
 * @param {import('@playwright/test').Page} page
 * @param {string} blockSelector
 * @param {string} cityName
 * @returns {Promise<void>}
 */
async function fillCity(page, blockSelector, cityName) {
  const combo = page.locator(`${blockSelector} vaadin-combo-box`);
  await combo.locator('input').click();
  await combo.locator('input').fill(cityName);
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Enter');
}

/**
 * Selects one ad-kind value from the multi-select combo -- see fillCategory's own doc for the
 * same shadow-DOM/opened-property rationale.
 * @param {import('@playwright/test').Page} page
 * @param {string} blockSelector
 * @param {string} adKindName
 * @returns {Promise<void>}
 */
async function fillAdKind(page, blockSelector, adKindName) {
  const selector = `${blockSelector} [data-testid="advertisement-filter-ad-kind"]`;
  const combo = page.locator(selector);
  await combo.locator('input').click();
  await page.waitForFunction((sel) => document.querySelector(sel)?.opened === true, selector, { timeout: 5000 });
  await page.keyboard.type(adKindName);
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Enter');
  await page.evaluate((sel) => {
    const el = document.querySelector(sel);
    if (el) el.opened = false;
  }, selector);
  await page.waitForFunction((sel) => document.querySelector(sel)?.opened === false, selector, { timeout: 5000 });
}

/**
 * Sets a date range using the Vaadin date-picker JS API.
 * @param {import('@playwright/test').Page} page
 * @param {string} blockSelector
 * @param {number} pickerStartIndex 0 = first date-pair (created), 2 = second date-pair (updated).
 * @param {string} [startDate] ISO date string.
 * @param {string} [endDate] ISO date string.
 * @returns {Promise<void>}
 */
async function setDateRange(page, blockSelector, pickerStartIndex, startDate, endDate) {
  await page.evaluate(({ selector, idx, start, end }) => {
    const pickers = document.querySelectorAll(`${selector} vaadin-date-picker`);
    const dispatch = (el, date) => {
      el.value = date;
      el.dispatchEvent(new Event('change', { bubbles: true }));
      el.dispatchEvent(new CustomEvent('value-changed', { bubbles: true, detail: { value: date } }));
    };
    if (start && pickers[idx])     dispatch(pickers[idx],     start);
    if (end   && pickers[idx + 1]) dispatch(pickers[idx + 1], end);
  }, { selector: blockSelector, idx: pickerStartIndex, start: startDate, end: endDate });
}

// ── count helper ──────────────────────────────────────────────────────────────

/**
 * Parses total count from ".pagination-count" -- format "{from}-{to} of {total} records".
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<number>}
 */
async function getTotalCount(page) {
  const text = await page.locator('.pagination-count:visible').textContent({ timeout: 5000 });
  const m = text.match(/of\s+(\d+)/);
  return m ? parseInt(m[1], 10) : 0;
}

// ── pagination helpers ────────────────────────────────────────────────────────

/**
 * Clicks the Next page button and waits for Vaadin to settle.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<void>}
 */
async function goToNextPage(page) {
  await page.locator('vaadin-button[title*="Next"]:visible').click();
  await waitForVaadin(page);
}

/**
 * Clicks the Previous page button and waits for Vaadin to settle.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<void>}
 */
async function goToPrevPage(page) {
  await page.locator('vaadin-button[title*="Prev"]:visible').click();
  await waitForVaadin(page);
}

/**
 * Clicks the First page button and waits for Vaadin to settle.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<void>}
 */
async function goToFirstPage(page) {
  await page.locator('vaadin-button[title*="First"]:visible').click();
  await waitForVaadin(page);
}

/**
 * Clicks the Last page button and waits for Vaadin to settle.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<void>}
 */
async function goToLastPage(page) {
  await page.locator('vaadin-button[title*="Last"]:visible').click();
  await waitForVaadin(page);
}

// ── sort verification ─────────────────────────────────────────────────────────

/**
 * Verifies ASC/DESC sort for one column including setup, assertions and screenshots.
 * @param {import('@playwright/test').Page} page
 * @param {Object} params
 * @param {string} params.block blockSelector.
 * @param {string} params.sortCol sort column label.
 * @param {string} params.itemSelector result-item selector waited on after each sort click.
 * @param {string} params.assertSelector selector whose text is asserted against firstAsc/firstDesc.
 * @param {Object} [params.setup] setup.reset:
 *   'all'        -> resetDefaultSorts only (clearFilter already done by caller)
 *   'clearAll'   -> clearFilter + resetDefaultSorts
 *   '<column>'   -> clearFilter + clickSort(column) to neutralise one default sort,
 *                   leaving the other default active at DESC (use with startDesc: true)
 *   setup.filter, when set, is applied via fillText + applyFilter before sort assertions.
 * @param {boolean} [params.startDesc=false] true when the column starts at DESC after setup
 *   (Created At / Updated At); asserts DESC first, then clicks -> ASC.
 * @param {string} params.firstAsc expected assertSelector text when sorted ASC.
 * @param {string} params.firstDesc expected assertSelector text when sorted DESC.
 * @param {string} params.prefix screenshot name prefix.
 * @returns {Promise<void>}
 */
async function verifySortColumn(page, {
  block, sortCol, itemSelector, assertSelector,
  setup,
  startDesc = false,
  firstAsc, firstDesc,
  prefix,
}) {
  if (setup?.reset === 'all') {
    await resetDefaultSorts(page, block);
  } else if (setup?.reset === 'clearAll') {
    await clearFilter(page, block);
    await resetDefaultSorts(page, block);
  } else if (setup?.reset) {
    await clearFilter(page, block);
    await clickSort(page, block, setup.reset, itemSelector);
  }
  if (setup?.filter) {
    await fillText(page, block, setup.filter.field, setup.filter.value);
    await applyFilter(page, block);
  }

  const slug = sortCol.toLowerCase().replace(/ /g, '-');
  if (startDesc) {
    await expect(page.locator(assertSelector).first()).toContainText(firstDesc, { timeout: 8000 });
    await screenshot(page, `${prefix}-sort-${slug}-desc`);
    // Sort cycle is NEUTRAL→ASC→DESC→NEUTRAL; from DESC two clicks reach ASC.
    await clickSort(page, block, sortCol, itemSelector); // DESC → NEUTRAL
    await clickSort(page, block, sortCol, itemSelector); // NEUTRAL → ASC
    await expect(page.locator(assertSelector).first()).toContainText(firstAsc, { timeout: 8000 });
    await screenshot(page, `${prefix}-sort-${slug}-asc`);
  } else {
    await clickSort(page, block, sortCol, itemSelector);
    await expect(page.locator(assertSelector).first()).toContainText(firstAsc, { timeout: 8000 });
    await screenshot(page, `${prefix}-sort-${slug}-asc`);
    await clickSort(page, block, sortCol, itemSelector);
    await expect(page.locator(assertSelector).first()).toContainText(firstDesc, { timeout: 8000 });
    await screenshot(page, `${prefix}-sort-${slug}-desc`);
  }
}

// ── date range filter verification ───────────────────────────────────────────

/**
 * Verifies created-at and updated-at date range filters for a given query block. Expects at
 * least `minCount` results for today's range and 0 for boundary dates.
 * @param {import('@playwright/test').Page} page
 * @param {string} block blockSelector.
 * @param {string} prefix screenshot name prefix.
 * @param {number} minCount minimum expected result count for today's date range.
 * @returns {Promise<void>}
 */
async function verifyDateRangeFilters(page, block, prefix, minCount) {
  const today = new Date().toISOString().slice(0, 10);

  await setDateRange(page, block, 0, today, today);
  await applyFilter(page, block);
  expect(await getTotalCount(page)).toBeGreaterThanOrEqual(minCount);
  await screenshot(page, `${prefix}-filter-created-range`);
  await clearFilter(page, block);

  await setDateRange(page, block, 2, today, today);
  await applyFilter(page, block);
  expect(await getTotalCount(page)).toBeGreaterThanOrEqual(minCount);
  await screenshot(page, `${prefix}-filter-updated-range`);
  await clearFilter(page, block);

  await setDateRange(page, block, 0, '2099-12-31', undefined);
  await applyFilter(page, block);
  expect(await getTotalCount(page)).toBe(0);
  await screenshot(page, `${prefix}-filter-created-future-start`);
  await clearFilter(page, block);

  await setDateRange(page, block, 0, undefined, '2000-01-01');
  await applyFilter(page, block);
  expect(await getTotalCount(page)).toBe(0);
  await screenshot(page, `${prefix}-filter-created-past-end`);
  await clearFilter(page, block);
}

// ── pagination verification ───────────────────────────────────────────────────

/**
 * Verifies 3-page navigation (next x2, first, last, prev) against a 20-per-page default with
 * `total` records. Assumes the caller has already applied a filter that yields exactly `total`
 * results and is on page 1.
 * @param {import('@playwright/test').Page} page
 * @param {string} prefix screenshot name prefix.
 * @param {number} total total record count.
 * @returns {Promise<void>}
 */
async function verifyPagination(page, prefix, total) {
  const last = total;

  await expect(page.locator('.pagination-count:visible'))
    .toContainText(`1\u201320 of ${total}`, { timeout: 8000 });
  await screenshot(page, `${prefix}-pagination-page1`);

  await goToNextPage(page);
  await expect(page.locator('.pagination-count:visible'))
    .toContainText(`21\u201340 of ${total}`, { timeout: 5000 });
  await screenshot(page, `${prefix}-pagination-page2`);

  await goToNextPage(page);
  await expect(page.locator('.pagination-count:visible'))
    .toContainText(`41\u2013${last} of ${total}`, { timeout: 5000 });
  await screenshot(page, `${prefix}-pagination-page3`);

  await goToFirstPage(page);
  await expect(page.locator('.pagination-count:visible'))
    .toContainText(`1\u201320 of ${total}`, { timeout: 5000 });

  await goToLastPage(page);
  await expect(page.locator('.pagination-count:visible'))
    .toContainText(`41\u2013${last} of ${total}`, { timeout: 5000 });
  await screenshot(page, `${prefix}-pagination-last`);

  await goToPrevPage(page);
  await expect(page.locator('.pagination-count:visible'))
    .toContainText(`21\u201340 of ${total}`, { timeout: 5000 });
  await screenshot(page, `${prefix}-pagination-prev`);
}

module.exports = {
  openQueryPanel,
  applyFilter, clearFilter, waitForVaadin,
  clickSort, resetDefaultSorts,
  fillText, fillNumber, fillRole, fillCategory, fillCity, fillAdKind, setDateRange,
  getRow,
  getTotalCount,
  goToNextPage, goToPrevPage, goToFirstPage, goToLastPage,
  verifyPagination, verifyDateRangeFilters, verifySortColumn,
};
