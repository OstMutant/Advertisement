const { test, expect, loginAs, screenshot, createAd } = require('./_test-helpers');

test.describe('Delete advertisement', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page);
    await expect(page.locator('.advertisement-card').first()).toBeVisible({ timeout: 8000 });
  });

  test('cancel keeps card, confirm removes card', async ({ page }) => {
    await test.step('Create a dedicated ad to delete', async () => {
      await createAd(page, { title: 'Ad To Delete Now', description: 'Will be deleted' });
      await expect(page.locator('.advertisement-card').filter({
        has: page.locator('.advertisement-title', { hasText: 'Ad To Delete Now' })
      }).first()).toBeVisible({ timeout: 8000 });
    });

    const card = () => page.locator('.advertisement-card').filter({
      has: page.locator('.advertisement-title', { hasText: 'Ad To Delete Now' })
    }).first();

    await test.step('Click delete — confirm dialog appears', async () => {
      await card().locator('.advertisement-delete').click();
      await expect(page.locator('vaadin-dialog-overlay')).toBeVisible({ timeout: 5000 });
      await screenshot(page, 'delete-ad-01-confirm-dialog');
    });

    await test.step('Cancel — card stays', async () => {
      await page.evaluate(() => {
        const dialog = document.querySelector('vaadin-dialog[opened]');
        const btn = [...dialog.querySelectorAll('vaadin-button')]
          .find(b => /cancel|скасувати/i.test(b.textContent?.trim()));
        btn?.click();
      });
      await page.locator('vaadin-dialog-overlay').waitFor({ state: 'hidden', timeout: 5000 });
      await expect(card()).toBeVisible();
      await screenshot(page, 'delete-ad-02-cancel-stays');
    });

    await test.step('Delete confirm — card disappears', async () => {
      await card().locator('.advertisement-delete').click();
      await page.locator('vaadin-dialog-overlay').waitFor({ timeout: 5000 });
      await screenshot(page, 'delete-ad-03-confirm-open');
      await page.evaluate(() => {
        const dialog = document.querySelector('vaadin-dialog[opened]');
        const btn = [...dialog.querySelectorAll('vaadin-button')]
          .find(b => /^delete$|^видалити$/i.test(b.textContent?.trim()));
        btn?.click();
      });
      await page.locator('vaadin-dialog-overlay').waitFor({ state: 'hidden', timeout: 5000 });
    });

    await test.step('Card no longer in list', async () => {
      await expect(page.locator('.advertisement-card').filter({
        has: page.locator('.advertisement-title', { hasText: 'Ad To Delete Now' })
      })).toHaveCount(0, { timeout: 8000 });
      await screenshot(page, 'delete-ad-04-card-gone');
    });
  });
});
