const { test, expect, loginAs,
        waitForOverlay, waitForOverlayClosed, downloadPng, screenshot } = require('./_test-helpers');

const avatar = seed =>
  `https://api.dicebear.com/9.x/adventurer/png?seed=${seed}&size=256&backgroundColor=b6e3f4`;

test.describe('Upload image', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page);
  });

  test('upload single image to advertisement', async ({ page }) => {
    const imgPath = '/tmp/upload-single.png';
    await downloadPng(avatar('test-upload'), imgPath);

    await test.step('Create advertisement', async () => {
      await page.locator('.add-advertisement-button').click();
      await waitForOverlay(page);
      const ov = page.locator('.advertisement-overlay');
      await ov.locator('vaadin-text-field input').fill('Upload Image Test');
      await ov.locator('vaadin-text-area textarea').fill('Testing single upload');
      await ov.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
      await waitForOverlayClosed(page);
    });

    await test.step('Open edit mode and upload image', async () => {
      await page.locator('.advertisement-card')
        .filter({ has: page.locator('.advertisement-title', { hasText: 'Upload Image Test' }) })
        .first().click();
      await waitForOverlay(page);
      await page.locator('vaadin-button').filter({ hasText: /редагувати|edit/i }).first().click();
      await page.locator('.base-overlay.overlay--visible vaadin-text-field input').first().waitFor();
      await page.locator('vaadin-upload input[type="file"]').setInputFiles(imgPath);
      await page.locator('.attachment-gallery__item').first().waitFor({ timeout: 10000 });
    });

    await test.step('Save and verify image visible', async () => {
      await page.locator('.base-overlay.overlay--visible vaadin-button')
        .filter({ hasText: /зберегти|save/i }).click();
      await page.locator('.overlay__view-title').waitFor();
      const imgs = await page.locator('.base-overlay.overlay--visible img').count();
      if (imgs < 1) throw new Error(`Expected at least 1 image, got ${imgs}`);
      await screenshot(page, 'upload-image-01-saved');
    });

    const fs = require('fs');
    if (fs.existsSync(imgPath)) fs.unlinkSync(imgPath);
  });
});
