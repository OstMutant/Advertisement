const { test, expect, loginAs,
        waitForOverlay, waitForOverlayClosed, downloadPng } = require('./_test-helpers');
const fs = require('fs');

const avatar = seed =>
  `https://api.dicebear.com/9.x/adventurer/png?seed=${seed}&size=256&backgroundColor=b6e3f4`;

async function openEditOverlay(page, title) {
  await page.locator('.advertisement-card')
    .filter({ has: page.locator('.advertisement-title', { hasText: title }) })
    .first().click();
  await page.locator('.base-overlay.overlay--visible').waitFor({ timeout: 5000 });
  await page.locator('vaadin-button').filter({ hasText: /редагувати|edit/i }).first().click();
  await page.locator('.base-overlay.overlay--visible vaadin-text-field input').first().waitFor();
}

test.describe('Upload gallery', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page);
  });

  test('upload multiple images and verify gallery', async ({ page }) => {
    const seeds = ['gallery-a', 'gallery-b', 'gallery-c'];
    const paths = seeds.map(s => `/tmp/upload-gallery-${s}.png`);
    await Promise.all(seeds.map((s, i) => downloadPng(avatar(s), paths[i])));

    await test.step('Create advertisement', async () => {
      await page.locator('.add-advertisement-button').click();
      await waitForOverlay(page);
      const ov = page.locator('.advertisement-overlay');
      await ov.locator('vaadin-text-field input').fill('Gallery Upload Test');
      await ov.locator('vaadin-text-area textarea').fill('Testing multi upload');
      await ov.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
      await waitForOverlayClosed(page);
    });

    await test.step('Upload 3 images', async () => {
      await openEditOverlay(page, 'Gallery Upload Test');
      await page.locator('vaadin-upload input[type="file"]').setInputFiles(paths);
      await page.locator('.attachment-gallery__item').first().waitFor({ timeout: 10000 });
      await page.locator('.base-overlay.overlay--visible vaadin-button')
        .filter({ hasText: /зберегти|save/i }).click();
      await page.locator('.overlay__view-title').waitFor();
    });

    await test.step('Gallery shows multiple images', async () => {
      const imgs = await page.locator('.base-overlay.overlay--visible img').count();
      if (imgs < 2) throw new Error(`Expected multiple images, got ${imgs}`);
    });

    await test.step('Delete one image and save', async () => {
      await page.locator('vaadin-button').filter({ hasText: /редагувати|edit/i }).first().click();
      await page.locator('.base-overlay.overlay--visible vaadin-text-field input').first().waitFor();
      const deleteBtn = page.locator('.attachment-gallery__item .attachment-delete-btn, .attachment-gallery__item button').first();
      if (await deleteBtn.count() > 0) {
        await deleteBtn.click();
        await page.locator('.base-overlay.overlay--visible vaadin-button')
          .filter({ hasText: /зберегти|save/i }).click();
        await page.locator('.overlay__view-title').waitFor();
      }
    });

    paths.forEach(p => { if (fs.existsSync(p)) fs.unlinkSync(p); });
  });
});
