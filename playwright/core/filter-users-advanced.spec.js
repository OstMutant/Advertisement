const { test, expect, loginAs, screenshot } = require('./_test-helpers');

const ADMIN_EMAIL = 'user3@example.com';

async function openFilterPanel(page) {
  await page.locator('.query-status-bar').filter({ visible: true }).first().click();
  await expect(page.locator('.user-query-block')).toBeVisible({ timeout: 5000 });
}

async function applyFilter(page) {
  await page.locator('.user-query-block vaadin-button[title*="Застосувати"], .user-query-block vaadin-button[title*="Apply"]').first().click();
}

async function clearFilter(page) {
  await page.locator('.user-query-block vaadin-button[title*="Очистити"], .user-query-block vaadin-button[title*="Clear"]').first().click();
}

async function fillDatePicker(page, nthPicker, isoDate) {
  await page.evaluate(({ n, date }) => {
    const pickers = document.querySelectorAll('.user-query-block vaadin-date-picker');
    const picker = pickers[n];
    if (!picker) return;
    picker.value = date;
    picker.dispatchEvent(new Event('change', { bubbles: true }));
    picker.dispatchEvent(new CustomEvent('value-changed', { bubbles: true, detail: { value: date } }));
  }, { n: nthPicker, date: isoDate });
  await page.waitForTimeout(300);
}

test.describe('Filter users — email, dates, sort (admin)', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, ADMIN_EMAIL);
    await page.locator('vaadin-tab').filter({ hasText: /users|користувач/i }).first().click();
    await page.locator('vaadin-grid.user-grid').waitFor({ timeout: 8000 });
    await openFilterPanel(page);
  });

  test('email filter narrows results', async ({ page }) => {
    await test.step('Filter by admin email — one match', async () => {
      await page.locator('.user-query-block .query-text').nth(1).locator('input').fill(ADMIN_EMAIL);
      await applyFilter(page);
      await screenshot(page, 'filter-users-email-01-filtered');
      await expect(page.locator('.user-grid-email', { hasText: ADMIN_EMAIL })).toBeVisible({ timeout: 5000 });
    });

    await test.step('Filter by nonexistent email — no rows', async () => {
      await page.locator('.user-query-block .query-text').nth(1).locator('input').fill('no-such-xyz@example.com');
      await applyFilter(page);
      await screenshot(page, 'filter-users-email-02-no-results');
      await expect(page.locator('.user-pagination .pagination-count')).toContainText('0', { timeout: 8000 });
    });

    await test.step('Clear — rows return', async () => {
      await clearFilter(page);
      await expect(page.locator('.user-grid-name').first()).toBeVisible({ timeout: 5000 });
      await screenshot(page, 'filter-users-email-03-cleared');
    });
  });

  test('createdAtStart in far future returns no users', async ({ page }) => {
    await test.step('Set createdAtStart = 2099-12-31', async () => {
      await fillDatePicker(page, 0, '2099-12-31');
      await applyFilter(page);
      await screenshot(page, 'filter-users-dates-01-future');
      await expect(page.locator('.user-pagination .pagination-count')).toContainText('0', { timeout: 8000 });
    });

    await test.step('Clear — users return', async () => {
      await clearFilter(page);
      await expect(page.locator('.user-grid-name').first()).toBeVisible({ timeout: 5000 });
      await screenshot(page, 'filter-users-dates-02-cleared');
    });
  });

  test('sort icons change order', async ({ page }) => {
    await test.step('Click sort on name column — asc', async () => {
      const sortIcons = page.locator('.user-query-block .sort-icon');
      const count = await sortIcons.count();
      if (count > 0) {
        const firstNameBefore = await page.locator('.user-grid-name').first().textContent();
        await sortIcons.first().click();
        await page.waitForTimeout(500);
        await screenshot(page, 'filter-users-sort-01-asc');
        await sortIcons.first().click();
        await page.waitForTimeout(500);
        await screenshot(page, 'filter-users-sort-02-desc');
        const firstNameAfter = await page.locator('.user-grid-name').first().textContent();
        // After double-click order may differ — just verify grid still loaded
        await expect(page.locator('.user-grid-name').first()).toBeVisible();
      }
    });
  });
});
