const { test, expect, loginAs, screenshot, waitForOverlay, waitForOverlayClosed } = require('./_test-helpers');

const ADMIN_EMAIL = 'user3@example.com';

test.describe('Delete user (admin)', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, ADMIN_EMAIL);
    await page.locator('vaadin-tab').filter({ hasText: /users|користувач/i }).click();
    await expect(page.locator('.user-grid')).toBeVisible({ timeout: 8000 });
  });

  test('delete button shows confirm dialog; cancel keeps row', async ({ page }) => {
    await test.step('Grid has rows', async () => {
      await expect(page.locator('.user-grid-name').first()).toBeVisible({ timeout: 5000 });
      await screenshot(page, 'delete-user-01-grid');
    });

    await test.step('Click delete on first row', async () => {
      await page.locator('.user-grid-actions').first().locator('vaadin-button').nth(1).click();
      await expect(page.locator('vaadin-dialog-overlay')).toBeVisible({ timeout: 5000 });
      await screenshot(page, 'delete-user-02-confirm-dialog');
    });

    await test.step('Cancel — row still present', async () => {
      await page.evaluate(() => {
        const dialog = document.querySelector('vaadin-dialog[opened]');
        const btn = [...dialog.querySelectorAll('vaadin-button')]
          .find(b => /cancel|скасувати/i.test(b.textContent?.trim()));
        btn?.click();
      });
      await page.locator('vaadin-dialog-overlay').waitFor({ state: 'hidden', timeout: 5000 });
      await expect(page.locator('.user-grid-name').first()).toBeVisible();
      await screenshot(page, 'delete-user-03-cancel-stays');
    });
  });
});
