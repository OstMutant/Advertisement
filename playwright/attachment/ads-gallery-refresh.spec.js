const { test, expect, loginAs,
        waitForOverlay, waitForOverlayClosed, downloadPng, screenshot } = require('./_test-helpers');

const avatar = seed =>
  `https://api.dicebear.com/9.x/adventurer/png?seed=${seed}&size=256&backgroundColor=b6e3f4`;

test.describe('Bug 2: gallery card refreshes after media deletion via view overlay', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page);
  });

  test('deleting image via view→edit→save→close refreshes grid card', async ({ page }) => {
    const imgPath = '/tmp/gallery-refresh-test.png';
    await downloadPng(avatar('gallery-refresh'), imgPath);

    const adTitle = `Gallery Refresh Test ${Date.now()}`;

    await test.step('Create advertisement with image', async () => {
      await page.locator('.add-advertisement-button').click();
      await waitForOverlay(page);
      const ov = page.locator('.advertisement-overlay');
      await ov.locator('[data-testid="advertisement-overlay-field-title"] input').fill(adTitle);
      await ov.locator('[data-testid="advertisement-overlay-field-description"] textarea').fill('Gallery refresh test');
      await page.locator('vaadin-upload input[type="file"]').setInputFiles(imgPath);
      await page.locator('.attachment-gallery__item').first().waitFor({ timeout: 10000 });
      await ov.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
      await waitForOverlayClosed(page);
    });
    await screenshot(page, 'gallery-refresh-01-card-with-image');

    await test.step('Grid card shows image thumbnail', async () => {
      const card = page.locator('.advertisement-card')
        .filter({ has: page.locator('.advertisement-title', { hasText: adTitle }) }).first();
      await expect(card.locator('.advertisement-thumbnail-wrapper').first()).toBeVisible({ timeout: 5000 });
    });

    await test.step('Open ad in VIEW mode', async () => {
      await page.locator('.advertisement-card')
        .filter({ has: page.locator('.advertisement-title', { hasText: adTitle }) })
        .first().click();
      await waitForOverlay(page);
    });

    await test.step('Go to edit mode from VIEW', async () => {
      await page.locator('.base-overlay.overlay--visible vaadin-button')
        .filter({ hasText: /редагувати|edit/i }).first().click();
      await page.locator('[data-testid="advertisement-overlay-field-title"] input').waitFor();
    });

    await test.step('Delete the image', async () => {
      await page.locator('.attachment-gallery__delete-btn').first().click();
      await page.locator('.attachment-gallery__item').waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {});
    });

    await test.step('Save — overlay returns to VIEW mode', async () => {
      await page.locator('.base-overlay.overlay--visible vaadin-button')
        .filter({ hasText: /зберегти|save/i }).click();
      await page.locator('.overlay__view-title').waitFor({ timeout: 5000 });
    });
    await screenshot(page, 'gallery-refresh-02-view-after-delete');

    await test.step('Close VIEW overlay via breadcrumb back', async () => {
      await page.locator('.overlay__breadcrumb-back').click();
      await waitForOverlayClosed(page);
    });
    await screenshot(page, 'gallery-refresh-03-grid-after-close');

    await test.step('Grid card no longer shows image thumbnail', async () => {
      const card = page.locator('.advertisement-card')
        .filter({ has: page.locator('.advertisement-title', { hasText: adTitle }) }).first();
      await expect(card).toBeVisible({ timeout: 5000 });
      const imageItem = card.locator('.advertisement-thumbnail-wrapper');
      await expect(imageItem).toBeHidden({ timeout: 5000 });
    });
    await screenshot(page, 'gallery-refresh-04-card-no-image');

    const fs = require('fs');
    if (fs.existsSync(imgPath)) fs.unlinkSync(imgPath);
  });
});
