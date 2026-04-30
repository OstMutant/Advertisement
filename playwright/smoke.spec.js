const { test, expect, loginAs, screenshot,
        waitForOverlay, waitForOverlayClosed, downloadPng } = require('./_test-helpers');
const fs = require('fs');

const avatar = seed =>
  `https://api.dicebear.com/9.x/adventurer/png?seed=${seed}&size=256&backgroundColor=b6e3f4,c0aede,d1d4f9,ffd5dc,ffdfbf`;

test.describe('Smoke: language (no auth)', () => {
  test('locale combobox switches UI language', async ({ page }) => {
    await page.goto('/');

    await test.step('Locale combobox visible', async () => {
      await expect(page.locator('.locale-combobox')).toBeVisible();
    });

    await test.step('Switch to English', async () => {
      await page.locator('.locale-combobox input').click();
      await page.locator('vaadin-combo-box-item').filter({ hasText: /english|en/i }).first().click();
      await page.waitForLoadState('networkidle').catch(() => {});
    });

    await test.step('Switch back to Ukrainian', async () => {
      await page.locator('.locale-combobox input').click();
      await page.locator('vaadin-combo-box-item').filter({ hasText: /укр|ukrainian/i }).first().click();
      await page.waitForLoadState('networkidle').catch(() => {});
    });
  });
});

test.describe('Smoke: user flow', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page);
  });

  test('add + edit + upload single + upload multi + filter + settings', async ({ page }) => {
    await test.step('Open add form', async () => {
      await page.locator('.add-advertisement-button').click();
      await waitForOverlay(page);
    });

    await test.step('Save new advertisement', async () => {
      const overlay = page.locator('.advertisement-overlay');
      await overlay.locator('vaadin-text-field input').fill('Smoke Test Ad');
      await overlay.locator('vaadin-text-area textarea').fill('Created by smoke test');
      await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
      await waitForOverlayClosed(page);
      const body = await page.textContent('body');
      if (!body.includes('Smoke Test Ad')) throw new Error('Ad not visible after save');
    });

    await test.step('Open ad and edit title', async () => {
      await page.locator('.advertisement-card')
        .filter({ has: page.locator('.advertisement-title', { hasText: 'Smoke Test Ad' }) })
        .first().click();
      await waitForOverlay(page);
      await page.locator('vaadin-button').filter({ hasText: /редагувати|edit/i }).first().click();
      await page.locator('.base-overlay.overlay--visible vaadin-text-field input').first().waitFor();
      const input = page.locator('.base-overlay.overlay--visible vaadin-text-field input').first();
      await input.clear();
      await input.fill('Smoke Test Ad (edited)');
      await page.locator('.base-overlay.overlay--visible vaadin-button')
        .filter({ hasText: /зберегти|save/i }).click();
      await page.locator('.overlay__view-title').waitFor();
      const body = await page.textContent('body');
      if (!body.includes('edited')) throw new Error('Edited title not visible');
    });

    const backBtn = page.locator('.overlay__breadcrumb-back');
    if (await backBtn.isVisible().catch(() => false)) {
      await backBtn.click();
      await waitForOverlayClosed(page).catch(() => {});
    }

    const singlePath = '/tmp/smoke-avatar-sunny.png';
    await test.step('Create ad and upload single image', async () => {
      await downloadPng(avatar('sunny'), singlePath);
      await page.locator('.add-advertisement-button').click();
      await waitForOverlay(page);
      const ov = page.locator('.advertisement-overlay');
      await ov.locator('vaadin-text-field input').fill('Single Image House');
      await ov.locator('vaadin-text-area textarea').fill('A cozy house');
      await ov.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
      await waitForOverlayClosed(page);

      await page.locator('.advertisement-card')
        .filter({ has: page.locator('.advertisement-title', { hasText: 'Single Image House' }) })
        .first().click();
      await waitForOverlay(page);
      await page.locator('vaadin-button').filter({ hasText: /редагувати|edit/i }).first().click();
      await page.locator('.base-overlay.overlay--visible vaadin-text-field input').first().waitFor();
      await page.locator('vaadin-upload input[type="file"]').setInputFiles(singlePath);
      await page.locator('.attachment-gallery__item').first().waitFor({ timeout: 10000 });
      await page.locator('.base-overlay.overlay--visible vaadin-button')
        .filter({ hasText: /зберегти|save/i }).click();
      await page.locator('.overlay__view-title').waitFor();
      const imgs = await page.locator('.base-overlay.overlay--visible img').count();
      if (imgs !== 1) throw new Error(`Expected 1 image, got ${imgs}`);
    });
    await page.locator('.overlay__breadcrumb-back').click();
    await waitForOverlayClosed(page).catch(() => {});
    if (fs.existsSync(singlePath)) fs.unlinkSync(singlePath);

    const multiSeeds = ['rocky', 'misty', 'comet'];
    const multiPaths = multiSeeds.map(s => `/tmp/smoke-avatar-${s}.png`);
    await test.step('Create ad and upload 3 images', async () => {
      await Promise.all(multiSeeds.map((s, i) => downloadPng(avatar(s), multiPaths[i])));
      await page.locator('.add-advertisement-button').click();
      await waitForOverlay(page);
      const ov = page.locator('.advertisement-overlay');
      await ov.locator('vaadin-text-field input').fill('Gallery Mansion');
      await ov.locator('vaadin-text-area textarea').fill('A luxurious mansion');
      await ov.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
      await waitForOverlayClosed(page);

      await page.locator('.advertisement-card')
        .filter({ has: page.locator('.advertisement-title', { hasText: 'Gallery Mansion' }) })
        .first().click();
      await waitForOverlay(page);
      await page.locator('vaadin-button').filter({ hasText: /редагувати|edit/i }).first().click();
      await page.locator('.base-overlay.overlay--visible vaadin-text-field input').first().waitFor();
      await page.locator('vaadin-upload input[type="file"]').setInputFiles(multiPaths);
      await page.locator('.attachment-gallery__item').first().waitFor({ timeout: 10000 });
      await page.locator('.base-overlay.overlay--visible vaadin-button')
        .filter({ hasText: /зберегти|save/i }).click();
      await page.locator('.overlay__view-title').waitFor();
      const imgs = await page.locator('.base-overlay.overlay--visible img').count();
      if (imgs < 2) throw new Error(`Expected multiple images, got ${imgs}`);
    });
    await page.locator('.overlay__breadcrumb-back').click();
    await waitForOverlayClosed(page).catch(() => {});
    multiPaths.forEach(p => { if (fs.existsSync(p)) fs.unlinkSync(p); });

    await test.step('Filter and clear advertisements', async () => {
      await page.locator('.query-status-bar').first().click();
      await expect(page.locator('.advertisement-query-block')).toBeVisible({ timeout: 3000 });
      await page.locator('.advertisement-query-block .query-text input').first().fill('Smoke');
      await page.locator('.advertisement-query-block vaadin-button[title*="Застосувати"]').first().click();
      await page.locator('.advertisement-query-block vaadin-button[title*="Очистити"]').first().click();
    });

    await test.step('Open settings and save page size', async () => {
      await page.locator('.header-settings-button').click();
      await waitForOverlay(page);
      const field = page.locator('vaadin-integer-field').first().locator('input');
      await field.click({ clickCount: 3 });
      await field.fill('15');
      await page.locator('.base-overlay.overlay--visible vaadin-button')
        .filter({ hasText: /зберегти|save/i }).click();
      await page.waitForLoadState('networkidle');
    });
  });
});

test.describe('Smoke: admin flow', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, 'user3@example.com');
  });

  test('users grid + open detail + edit + filter users', async ({ page }) => {
    await test.step('Switch to Users tab', async () => {
      await page.locator('vaadin-tab').filter({ hasText: /users|користувач/i }).first().click();
      await page.locator('vaadin-grid.user-grid').waitFor({ timeout: 8000 });
      const cells = await page.locator('vaadin-grid.user-grid vaadin-grid-cell-content').count();
      if (cells === 0) throw new Error('User grid is empty');
    });

    await test.step('Open user detail', async () => {
      await page.locator('vaadin-grid.user-grid vaadin-grid-cell-content .user-grid-name').first().click();
      await waitForOverlay(page);
      await page.locator('.overlay__breadcrumb-back').click();
      await waitForOverlayClosed(page);
    });

    await test.step('Open user edit overlay', async () => {
      await page.locator('vaadin-grid.user-grid .user-grid-actions vaadin-button').first().click();
      await waitForOverlay(page);
      await page.locator('.base-overlay.overlay--visible vaadin-button')
        .filter({ hasText: /скасувати|cancel|закрити|close/i }).first().click();
      await waitForOverlayClosed(page).catch(() => {});
    });

    await test.step('Filter users by name and clear', async () => {
      await page.locator('.query-status-bar').filter({ visible: true }).first().click();
      await expect(page.locator('.user-query-block')).toBeVisible({ timeout: 3000 });
      await page.locator('.user-query-block .query-text input').first().fill('User 1');
      await page.locator('.user-query-block vaadin-button[title*="Застосувати"]').first().click();
      await page.locator('vaadin-grid.user-grid .user-grid-name').first().waitFor({ timeout: 5000 });
      await page.locator('.user-query-block vaadin-button[title*="Очистити"]').first().click();
      await page.locator('vaadin-grid.user-grid .user-grid-name').first().waitFor({ timeout: 5000 });
    });
  });
});
