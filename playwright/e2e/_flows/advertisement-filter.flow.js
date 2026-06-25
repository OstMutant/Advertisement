const { screenshot } = require('../_helpers');

async function runOpenFilterPanelFlow(page, expect) {
  await page.locator('.query-status-bar').first().click();
  await expect(page.locator('.advertisement-query-block')).toBeVisible({ timeout: 5000 });
  await screenshot(page, 'filter-panel-open');
}

async function runFillTitleFilterFlow(page, text) {
  await page.locator('.advertisement-query-block .query-text input').first().fill(text);
  await screenshot(page, 'filter-title-filled');
}

async function runApplyFilterFlow(page, expect) {
  await page.locator('.advertisement-query-block vaadin-button[title*="Застосувати"], .advertisement-query-block vaadin-button[title*="Apply"]').first().click();
  await expect(page.locator('.advertisement-card').first()).toBeVisible({ timeout: 8000 }).catch(() => {});
  await screenshot(page, 'filter-applied');
}

async function runClearFilterFlow(page, expect) {
  await page.locator('.advertisement-query-block vaadin-button[title*="Очистити"], .advertisement-query-block vaadin-button[title*="Clear"]').first().click();
  await expect(page.locator('.pagination-count')).toBeVisible({ timeout: 8000 });
  await screenshot(page, 'filter-cleared');
}

async function runVerifyFilterStatusFlow(page, expect, text) {
  await expect(page.locator('.query-status-bar')).toContainText(text, { timeout: 5000 });
  await screenshot(page, 'filter-status-active');
}

async function runFillAllFiltersFlow(page) {
  await page.locator('.advertisement-query-block .query-text input').first().fill('Test');

  await page.evaluate(() => {
    const pickers = document.querySelectorAll('.advertisement-query-block vaadin-date-picker');
    const dispatch = (el, date) => {
      el.value = date;
      el.dispatchEvent(new Event('change', { bubbles: true }));
      el.dispatchEvent(new CustomEvent('value-changed', { bubbles: true, detail: { value: date } }));
    };
    if (pickers[0]) dispatch(pickers[0], '2020-01-01'); // createdAtStart
    if (pickers[1]) dispatch(pickers[1], '2099-12-31'); // createdAtEnd
    if (pickers[2]) dispatch(pickers[2], '2020-01-01'); // updatedAtStart
    if (pickers[3]) dispatch(pickers[3], '2099-12-31'); // updatedAtEnd
  });
  await page.waitForTimeout(300);

  const categoryCombo = page.locator('.advertisement-query-block vaadin-multi-select-combo-box');
  if (await categoryCombo.count() > 0) {
    await categoryCombo.locator('input').click();
    await page.locator('vaadin-multi-select-combo-box-item').first().click();
    await page.keyboard.press('Escape');
  }

  await screenshot(page, 'filter-all-filled');
}

async function runCloseFilterPanelFlow(page, expect) {
  await page.locator('.query-status-bar').first().click();
  await expect(page.locator('.advertisement-query-block')).not.toBeVisible({ timeout: 5000 });
  await screenshot(page, 'filter-panel-closed');
}

module.exports = {
  runOpenFilterPanelFlow,
  runFillTitleFilterFlow,
  runFillAllFiltersFlow,
  runApplyFilterFlow,
  runVerifyFilterStatusFlow,
  runClearFilterFlow,
  runCloseFilterPanelFlow,
};
