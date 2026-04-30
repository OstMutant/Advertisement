const { test, expect, loginAs, screenshot,
        waitForOverlay } = require('./_test-helpers');

test.describe('Edit advertisement', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, 'user2@example.com');
  });

  test('opens first ad and updates title', async ({ page }) => {
    await test.step('Advertisement list loaded', async () => {
      await expect(page.locator('.advertisement-card').first()).toBeVisible({ timeout: 8000 });
    });

    await test.step('Open first advertisement detail', async () => {
      await page.locator('.advertisement-card').first().click();
      await waitForOverlay(page);
    });
    await screenshot(page, 'edit-02-advertisement-detail');

    await test.step('Click Edit button', async () => {
      await page.locator('vaadin-button').filter({ hasText: /редагувати|edit/i }).first().click();
      await page.locator('.base-overlay.overlay--visible vaadin-text-field input').first().waitFor();
    });

    await test.step('Update title field', async () => {
      const titleInput = page.locator('vaadin-text-field input').first();
      await titleInput.clear();
      await titleInput.fill('Updated by Playwright');
    });

    await test.step('Save changes', async () => {
      await page.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).first().click();
      await page.locator('.overlay__view-title').waitFor();
      const body = await page.textContent('body');
      if (!body.includes('Updated by Playwright')) throw new Error('Updated title not visible after save');
    });
  });
});
