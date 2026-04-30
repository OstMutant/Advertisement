const { test, expect, loginAs,
        waitForOverlay, waitForOverlayClosed, openSettings, openActivityTab, confirmDialog } = require('./_test-helpers');

test.describe('Settings activity', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, 'user3@example.com');
  });

  test('page size change creates activity entry, restore reverts it', async ({ page }) => {
    await openSettings(page);

    const overlay = page.locator('.base-overlay.overlay--visible');
    const getAdsPageSizeValue = async (p) => {
      const val = await p.locator('.settings-overlay-content vaadin-integer-field').first().locator('input').inputValue();
      return parseInt(val, 10);
    };

    const originalSize = await getAdsPageSizeValue(page);
    const newSize = originalSize === 10 ? 15 : 10;

    const sizeInput = () => page.locator('.settings-overlay-content vaadin-integer-field').first().locator('input');
    const saveBtn = () => overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i });

    await sizeInput().click({ clickCount: 3 });
    await sizeInput().fill(String(newSize));
    await saveBtn().click();
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1100);

    await sizeInput().fill(String(originalSize));
    await saveBtn().click();
    await page.waitForLoadState('networkidle');

    await test.step('Activity tab present in settings', async () => {
      const tabs = await overlay.locator('vaadin-tab').allTextContents();
      if (!tabs.some(t => /activ|активн/i.test(t))) throw new Error('Activity tab not found');
    });

    await openActivityTab(page);

    await test.step('Settings change appears in activity', async () => {
      if (await page.locator('.user-activity-row').count() === 0) throw new Error('No activity rows');
      const body = await page.locator('.user-activity-list').first().textContent();
      if (!body.includes('сторінці') && !body.includes('page'))
        throw new Error('Settings change summary not found in activity');
    });

    await test.step('Page size diff shown in activity', async () => {
      if (await page.locator('.user-activity-changes').count() === 0) throw new Error('No changes summary found');
      if (!(await page.locator('.user-activity-list').first().textContent()).includes('→'))
        throw new Error('No diff arrow → found in activity');
    });

    await test.step('Restore settings button present', async () => {
      if (await page.locator('.user-activity-list .adv-history-restore-btn').count() === 0)
        throw new Error('No restore button for settings');
    });

    await page.locator('.user-activity-list .adv-history-restore-btn').nth(0).click();
    await confirmDialog(page, 'Оновити|Update');
    await page.waitForLoadState('networkidle');

    await page.locator('.settings-overlay-content vaadin-integer-field').first().waitFor({ timeout: 5000 });

    await test.step('Settings overlay stays open after restore', async () => {
      await expect(page.locator('.base-overlay.overlay--visible')).toBeVisible();
      await expect(page.locator('.overlay__form-fields-card')).toBeVisible();
    });

    await test.step('Page size changed after restore', async () => {
      const currentSize = await getAdsPageSizeValue(page);
      if (currentSize !== newSize) throw new Error(`Expected ${newSize} after restore, got ${currentSize}`);
    });
  });
});
