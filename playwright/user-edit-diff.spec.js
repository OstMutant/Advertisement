const { test, expect, loginAs,
        waitForOverlay, waitForOverlayClosed, openSettings, openActivityTab } = require('./_test-helpers');

test.describe('User edit diff', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, 'user3@example.com');
  });

  test('editing user name shows diff in settings activity', async ({ page }) => {
    await test.step('Open Users tab', async () => {
      await page.locator('vaadin-tab').filter({ hasText: /users|користувач/i }).first().click();
      await page.locator('vaadin-grid.user-grid').waitFor({ timeout: 8000 });
    });

    await test.step('Open edit overlay for first user', async () => {
      await page.locator('vaadin-grid.user-grid .user-grid-actions vaadin-button').first().click();
      await waitForOverlay(page);
    });

    await test.step('Change user name and save', async () => {
      const nameField = page.locator('.base-overlay.overlay--visible vaadin-text-field input').first();
      await nameField.waitFor();
      const current = await nameField.inputValue();
      await nameField.click({ clickCount: 3 });
      await nameField.fill(current.includes('Edited') ? current.replace(' Edited', '') : current + ' Edited');
      await page.locator('.base-overlay.overlay--visible vaadin-button')
        .filter({ hasText: /зберегти|save/i }).click();
      await waitForOverlayClosed(page).catch(() => {});
    });

    await openSettings(page);
    await openActivityTab(page);

    await test.step('Changes summary in activity', async () => {
      if (await page.locator('.user-activity-changes').count() === 0)
        throw new Error('No changes summary in activity after user edit');
      const text = await page.locator('.user-activity-list').first().textContent();
      if (!text.includes('→')) throw new Error('No diff arrow → in user edit activity');
    });
  });
});
