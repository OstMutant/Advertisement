const { test, expect, loginAs, screenshot } = require('./_test-helpers');

async function openFilterPanel(page) {
  await page.locator('.query-status-bar').first().click();
  await expect(page.locator('.advertisement-query-block')).toBeVisible({ timeout: 5000 });
}

async function applyFilter(page) {
  await page.locator('.advertisement-query-block vaadin-button[title*="Застосувати"], .advertisement-query-block vaadin-button[title*="Apply"]').first().click();
}

async function clearFilter(page) {
  await page.locator('.advertisement-query-block vaadin-button[title*="Очистити"], .advertisement-query-block vaadin-button[title*="Clear"]').first().click();
}

async function fillDatePicker(page, nthPicker, isoDate) {
  // Vaadin DatePicker inner input lives in shadow DOM — use JS to set value and dispatch events
  await page.evaluate(({ n, date }) => {
    const pickers = document.querySelectorAll('.advertisement-query-block vaadin-date-picker');
    const picker = pickers[n];
    if (!picker) return;
    picker.value = date;
    picker.dispatchEvent(new Event('change', { bubbles: true }));
    picker.dispatchEvent(new CustomEvent('value-changed', { bubbles: true, detail: { value: date } }));
  }, { n: nthPicker, date: isoDate });
  await page.waitForTimeout(300);
}

test.describe('Filter advertisements — date ranges', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page);
    await expect(page.locator('.advertisement-card').first()).toBeVisible({ timeout: 8000 });
    await openFilterPanel(page);
  });

  test('createdAtStart in far future returns no results', async ({ page }) => {
    await test.step('Set createdAtStart = 2099-12-31', async () => {
      await fillDatePicker(page, 0, '2099-12-31');
      await applyFilter(page);
      await screenshot(page, 'filter-ads-dates-01-future-start');
    });

    await test.step('No cards shown', async () => {
      await expect(page.locator('.advertisement-card')).toHaveCount(0, { timeout: 8000 });
    });

    await test.step('Clear — cards return', async () => {
      await clearFilter(page);
      await expect(page.locator('.advertisement-card').first()).toBeVisible({ timeout: 8000 });
      await screenshot(page, 'filter-ads-dates-02-cleared');
    });
  });

  test('createdAtEnd in far past returns no results', async ({ page }) => {
    await test.step('Set createdAtEnd = 2000-01-01', async () => {
      await fillDatePicker(page, 1, '2000-01-01');
      await applyFilter(page);
      await screenshot(page, 'filter-ads-dates-03-past-end');
    });

    await test.step('No cards shown', async () => {
      await expect(page.locator('.advertisement-card')).toHaveCount(0, { timeout: 8000 });
    });

    await test.step('Clear — cards return', async () => {
      await clearFilter(page);
      await expect(page.locator('.advertisement-card').first()).toBeVisible({ timeout: 8000 });
      await screenshot(page, 'filter-ads-dates-04-cleared-again');
    });
  });
});
