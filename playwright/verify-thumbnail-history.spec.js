const { test, expect, loginAs,
        waitForOverlay, waitForOverlayClosed, openHistory, downloadPng } = require('./_test-helpers');
const fs = require('fs');

const avatar = seed =>
  `https://api.dicebear.com/9.x/adventurer/png?seed=${seed}&size=256&backgroundColor=b6e3f4`;

test.describe('Thumbnail and photo history', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page);
  });

  test('cards show real thumbnails; history tab shows photo changes', async ({ page }) => {
    const imgPath = '/tmp/vth-thumb.png';
    await downloadPng(avatar('thumbnail-test'), imgPath);

    await page.locator('.add-advertisement-button').click();
    await waitForOverlay(page);
    const ov = page.locator('.advertisement-overlay');
    await ov.locator('vaadin-text-field input').fill('Thumbnail Verify Ad');
    await ov.locator('vaadin-text-area textarea').fill('Thumbnail test');
    await ov.locator('vaadin-upload input[type="file"]').setInputFiles(imgPath);
    await page.locator('.attachment-gallery__item').first().waitFor({ timeout: 10000 });
    await ov.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await waitForOverlayClosed(page);

    await test.step('At least one card has a real thumbnail image', async () => {
      const thumbs = page.locator('.advertisement-card img');
      const count = await thumbs.count();
      if (count === 0) throw new Error('No thumbnail images found on cards');
      const src = await thumbs.first().getAttribute('src');
      if (!src) throw new Error('Thumbnail img has no src');
    });

    await page.locator('.advertisement-card')
      .filter({ has: page.locator('.advertisement-title', { hasText: 'Thumbnail Verify Ad' }) })
      .first().click();
    await waitForOverlay(page);
    await openHistory(page);

    await test.step('History tab shows photo changes', async () => {
      const text = await page.locator('.adv-history-list').textContent();
      if (!/(фото|photo)/i.test(text))
        throw new Error('No photo entry in history for thumbnail ad: ' + text.slice(0, 200));
    });

    if (fs.existsSync(imgPath)) fs.unlinkSync(imgPath);
  });
});
