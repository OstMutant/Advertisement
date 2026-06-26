const { expect } = require('@playwright/test');
const { screenshot } = require('../_helpers');

// ── panel helpers ─────────────────────────────────────────────────────────────

// Both views (ads + users) have a .query-status-bar; only the active tab's bar is visible.
// Use :visible so we click the correct one regardless of which tab is active.
async function openQueryPanel(page, blockSelector) {
  await page.locator('.query-status-bar:visible').click();
  await page.locator(blockSelector).waitFor({ state: 'visible', timeout: 5000 });
}

async function closeQueryPanel(page, blockSelector) {
  await page.locator('.query-status-bar:visible').click();
  await page.locator(blockSelector).waitFor({ state: 'hidden', timeout: 5000 });
}

// ── action helpers ────────────────────────────────────────────────────────────

// Waits for Vaadin to finish the server round-trip triggered by a button click.
// setTimeout(0) yields so Vaadin can enqueue its WebSocket message before we check isActive().
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

async function applyFilter(page, blockSelector) {
  await page.locator(`${blockSelector} vaadin-button[title*="Apply"]`).click();
  await waitForVaadin(page);
}

async function clearFilter(page, blockSelector) {
  await page.locator(`${blockSelector} vaadin-button[title*="Clear"]`).click();
  await waitForVaadin(page);
}

// ── row locator ───────────────────────────────────────────────────────────────

function getRow(page, blockSelector, labelText) {
  return page.locator(`${blockSelector} .query-inline-row`)
    .filter({ has: page.locator('.query-inline-label-sort', { hasText: labelText }) });
}

// ── sort helpers ──────────────────────────────────────────────────────────────

// Cycles NEUTRAL → ASC → DESC → NEUTRAL on each call and immediately applies.
// After apply, waits for resultItemSelector to be visible (data refresh confirmation).
async function clickSort(page, blockSelector, labelText, resultItemSelector) {
  await getRow(page, blockSelector, labelText).locator('.sort-icon').click();
  await applyFilter(page, blockSelector);
  if (resultItemSelector) {
    await page.locator(resultItemSelector).first().waitFor({ state: 'visible', timeout: 8000 });
  }
}

// Both blocks default to "Updated At DESC, Created At DESC".
// clearFilter() resets to that default — not to NEUTRAL.
// This helper clicks each default sort icon once (DESC → NEUTRAL) and applies, yielding a
// fully neutral sort state so individual sort tests can run in isolation.
async function resetDefaultSorts(page, blockSelector) {
  await getRow(page, blockSelector, 'Updated At').locator('.sort-icon').click();
  await applyFilter(page, blockSelector);
  await getRow(page, blockSelector, 'Created At').locator('.sort-icon').click();
  await applyFilter(page, blockSelector);
}

// ── field fill helpers ────────────────────────────────────────────────────────

async function fillText(page, blockSelector, labelText, value) {
  await getRow(page, blockSelector, labelText).locator('.query-text input').fill(value);
}

async function fillNumber(page, blockSelector, labelText, minVal, maxVal) {
  const inputs = getRow(page, blockSelector, labelText).locator('.query-number input');
  if (minVal !== undefined) await inputs.nth(0).fill(String(minVal));
  if (maxVal !== undefined) await inputs.nth(1).fill(String(maxVal));
}

// Selects one role value from the multi-select combo in the block.
// Overlay item elements are inside shadow DOM — use keyboard navigation instead of locator.
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

// Selects one category value from the multi-select combo in the advertisement query block.
// Uses keyboard navigation — same approach as fillRole — because the combo overlay has popover="manual".
async function fillCategory(page, blockSelector, categoryName) {
  const combo = page.locator(`${blockSelector} vaadin-multi-select-combo-box`);
  await combo.locator('input').click();
  await page.locator('vaadin-multi-select-combo-box-overlay').first().waitFor({ state: 'visible', timeout: 5000 });
  await page.keyboard.type(categoryName);
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Enter');
  await page.evaluate((sel) => {
    const el = document.querySelector(sel + ' vaadin-multi-select-combo-box');
    if (el) el.opened = false;
  }, blockSelector);
  await page.locator('vaadin-multi-select-combo-box-overlay').first().waitFor({ state: 'hidden', timeout: 5000 });
}

// Sets a date range using the Vaadin date-picker JS API.
// pickerStartIndex: 0 = first date-pair (created), 2 = second date-pair (updated).
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

// Parses total count from ".pagination-count" — format "{from}–{to} of {total} records".
async function getTotalCount(page) {
  const text = await page.locator('.pagination-count:visible').textContent({ timeout: 5000 });
  const m = text.match(/of\s+(\d+)/);
  return m ? parseInt(m[1], 10) : 0;
}

// ── pagination helpers ────────────────────────────────────────────────────────

async function goToNextPage(page) {
  await page.locator('vaadin-button[title*="Next"]:visible').click();
  await waitForVaadin(page);
}

async function goToPrevPage(page) {
  await page.locator('vaadin-button[title*="Prev"]:visible').click();
  await waitForVaadin(page);
}

async function goToFirstPage(page) {
  await page.locator('vaadin-button[title*="First"]:visible').click();
  await waitForVaadin(page);
}

async function goToLastPage(page) {
  await page.locator('vaadin-button[title*="Last"]:visible').click();
  await waitForVaadin(page);
}

// ── sort verification ─────────────────────────────────────────────────────────

// Verifies ASC/DESC sort for one column including setup, assertions and screenshots.
//
// setup.reset:
//   'all'        → resetDefaultSorts only (clearFilter already done by caller)
//   'clearAll'   → clearFilter + resetDefaultSorts
//   '<column>'   → clearFilter + clickSort(column) to neutralise one default sort,
//                  leaving the other default active at DESC (use with startDesc: true)
//
// startDesc: true when the column starts at DESC after setup (Created At / Updated At);
//   asserts DESC first, then clicks → ASC.
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

// Verifies created-at and updated-at date range filters for a given query block.
// Expects at least `minCount` results for today's range and 0 for boundary dates.
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

// Verifies 3-page navigation (next×2, first, last, prev) against a 20-per-page
// default with `total` records. Assumes the caller has already applied a filter
// that yields exactly `total` results and is on page 1.
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
  openQueryPanel, closeQueryPanel,
  applyFilter, clearFilter,
  clickSort, resetDefaultSorts,
  fillText, fillNumber, fillRole, fillCategory, setDateRange,
  getTotalCount,
  goToNextPage, goToPrevPage, goToFirstPage, goToLastPage,
  verifyPagination, verifyDateRangeFilters, verifySortColumn,
};
