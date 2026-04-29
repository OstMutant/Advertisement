const { test, expect, loginAs, waitForOverlay, waitForOverlayClosed } = require('./_test-helpers');

test.describe('Add advertisement', () => {

  test.beforeEach(async ({ page }) => {
    await loginAs(page);
  });

  test('creates new advertisement and shows it in the list', async ({ page }) => {
    await page.locator('.add-advertisement-button').click();
    await waitForOverlay(page);

    const overlay = page.locator('.advertisement-overlay');
    await overlay.locator('vaadin-text-field input').fill('Spec Test Ad');
    await overlay.locator('vaadin-text-area textarea').fill('Created by spec test');
    await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await waitForOverlayClosed(page);

    await expect(
      page.locator('.advertisement-card').filter({ hasText: 'Spec Test Ad' }).first()
    ).toBeVisible();
  });

});
