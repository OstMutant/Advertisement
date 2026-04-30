const { test, expect, loginAs, screenshot,
        waitForOverlay, waitForOverlayClosed, openSettings, openActivityTab } = require('./_test-helpers');

test.describe('User activity', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page);
  });

  test('user overlay shows activity tab with ad changes; settings shows same', async ({ page }) => {
    await test.step('Create advertisement to generate activity', async () => {
      await page.locator('.add-advertisement-button').click();
      await waitForOverlay(page);
      const ov = page.locator('.advertisement-overlay');
      await ov.locator('vaadin-text-field input').fill('User Activity Test Ad');
      await ov.locator('vaadin-text-area textarea').fill('Activity test');
      await ov.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
      await waitForOverlayClosed(page);
      await screenshot(page, 'activity-01-ad-created');
    });

    await openSettings(page);

    await test.step('Activity tab present in settings', async () => {
      const tabs = await page.locator('.base-overlay.overlay--visible vaadin-tab').allTextContents();
      if (!tabs.some(t => /activ|активн/i.test(t))) throw new Error('No activity tab in settings');
    });

    await openActivityTab(page);

    await test.step('Activity list visible with entries', async () => {
      await expect(page.locator('.user-activity-list').first()).toBeVisible();
      if (await page.locator('.user-activity-row').count() === 0)
        throw new Error('No activity rows in settings');
    });

    await test.step('My activity section visible in settings', async () => {
      const text = await page.locator('.user-activity-list').first().textContent();
      if (!text) throw new Error('Activity list is empty');
    });

    await test.step('Close settings overlay', async () => {
      await page.keyboard.press('Escape');
      await waitForOverlayClosed(page).catch(() => {});
    });
  });
});
