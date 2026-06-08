const { test, expect, loginAs, screenshot, waitForOverlay, waitForOverlayClosed } = require('./_test-helpers');

const ADMIN_EMAIL = 'user3@example.com';

test.describe('User edit — save changes', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, ADMIN_EMAIL);
    await page.locator('vaadin-tab').filter({ hasText: /users|користувач/i }).first().click();
    await expect(page.locator('.user-grid-name').first()).toBeVisible({ timeout: 8000 });
  });

  test('edit user name and verify update persists in grid', async ({ page }) => {
    const uniqueSuffix = Date.now();
    const newName = `Edited ${uniqueSuffix}`;
    let originalName;

    await test.step('Read original name of first non-admin user row', async () => {
      originalName = (await page.locator('.user-grid-name').first().textContent()).trim();
      await screenshot(page, 'user-edit-01-grid');
    });

    await test.step('Open edit overlay via actions button (first = edit)', async () => {
      await page.locator('vaadin-grid.user-grid .user-grid-actions vaadin-button').first().click();
      await waitForOverlay(page);
      await screenshot(page, 'user-edit-02-edit-mode');
    });

    await test.step('Change name field', async () => {
      const overlay = page.locator('.base-overlay.overlay--visible');
      const nameField = overlay.locator('vaadin-text-field').first().locator('input');
      await nameField.click({ clickCount: 3 });
      await nameField.fill(newName);
      await screenshot(page, 'user-edit-03-name-changed');
    });

    await test.step('Save — overlay closes', async () => {
      const overlay = page.locator('.base-overlay.overlay--visible');
      await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
      await waitForOverlayClosed(page);
      await screenshot(page, 'user-edit-04-saved');
    });

    await test.step('Grid shows updated name', async () => {
      await expect(page.locator('.user-grid-name', { hasText: newName })).toBeVisible({ timeout: 8000 });
      await screenshot(page, 'user-edit-05-grid-updated');
    });

    await test.step('Revert name back', async () => {
      await page.locator('vaadin-grid.user-grid .user-grid-actions vaadin-button').first().click();
      await waitForOverlay(page);
      const overlay = page.locator('.base-overlay.overlay--visible');
      const nameField = overlay.locator('vaadin-text-field').first().locator('input');
      await nameField.click({ clickCount: 3 });
      await nameField.fill(originalName);
      await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
      await waitForOverlayClosed(page);
    });
  });
});
