const { test, expect, loginAs, screenshot, openSettings, waitForOverlayClosed } = require('./_test-helpers');

async function setAdsPageSize(page, size) {
  await openSettings(page);
  const overlay = page.locator('.base-overlay.overlay--visible');
  const pageSizeField = overlay.locator('.settings-overlay-content vaadin-integer-field').first();
  await pageSizeField.locator('input').click({ clickCount: 3 });
  await pageSizeField.locator('input').fill(String(size));
  await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
  // Settings overlay stays open after save — close it with Escape
  await page.keyboard.press('Escape');
  await waitForOverlayClosed(page);
}

// Locale-independent pagination button selectors (by icon, not by title text)
const nextBtn  = page => page.locator('vaadin-button:has(vaadin-icon[icon="vaadin:angle-right"])').first();
const prevBtn  = page => page.locator('vaadin-button:has(vaadin-icon[icon="vaadin:angle-left"])').first();
const lastBtn  = page => page.locator('vaadin-button:has(vaadin-icon[icon="vaadin:angle-double-right"])').first();
const firstBtn = page => page.locator('vaadin-button:has(vaadin-icon[icon="vaadin:angle-double-left"])').first();

test.describe('Pagination — ads', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page);
    await expect(page.locator('.advertisement-card').first()).toBeVisible({ timeout: 8000 });
  });

  test('page size 1: exactly 1 card, next/prev navigate pages', async ({ page }) => {
    await test.step('Set page size to 1', async () => {
      await setAdsPageSize(page, 1);
      await expect(page.locator('.advertisement-card')).toHaveCount(1, { timeout: 8000 });
      await screenshot(page, 'pagination-01-size-1');
    });

    await test.step('Next navigates to page 2', async () => {
      const btn = nextBtn(page);
      if (await btn.isEnabled()) {
        await btn.click();
        await expect(page.locator('.advertisement-card')).toHaveCount(1, { timeout: 5000 });
        await screenshot(page, 'pagination-02-page-2');
        await expect(prevBtn(page)).toBeEnabled();
      }
    });

    await test.step('Prev goes back to page 1', async () => {
      const btn = prevBtn(page);
      if (await btn.isEnabled()) {
        await btn.click();
        await screenshot(page, 'pagination-03-back-page-1');
      }
    });

    await test.step('Last and First buttons work', async () => {
      const btn = lastBtn(page);
      if (await btn.isEnabled()) {
        await btn.click();
        await screenshot(page, 'pagination-04-last-page');
        await firstBtn(page).click();
        await screenshot(page, 'pagination-05-first-page');
      }
    });

    await test.step('Restore page size to 10', async () => {
      await setAdsPageSize(page, 10);
    });
  });

  test('pagination count label shows record range', async ({ page }) => {
    const countLabel = page.locator('.pagination-count').first();
    await expect(countLabel).toBeVisible({ timeout: 8000 });
    const text = await countLabel.textContent();
    expect(text).toMatch(/\d/);
    await screenshot(page, 'pagination-count-label');
  });
});

test.describe('Pagination — users (admin)', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, 'user3@example.com');
    await page.locator('vaadin-tab').filter({ hasText: /users|користувач/i }).first().click();
    await expect(page.locator('.user-pagination')).toBeVisible({ timeout: 8000 });
  });

  test('users pagination visible with count label', async ({ page }) => {
    await expect(page.locator('.user-pagination .pagination-count')).toBeVisible({ timeout: 5000 });
    await screenshot(page, 'pagination-users-count');
  });
});
