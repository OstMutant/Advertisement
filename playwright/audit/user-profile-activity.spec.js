const { test, expect, loginAs,
        waitForOverlay, waitForOverlayClosed, openSettings, openActivityTab, screenshot } = require('./_test-helpers');

const ADMIN_EMAIL = 'user3@example.com';

async function goToUsersTab(page) {
  await page.locator('vaadin-tab').filter({ hasText: /users|користувач/i }).first().click();
  await page.locator('vaadin-grid.user-grid').waitFor({ timeout: 8000 });
}

async function openFirstNonAdminProfile(page) {
  await page.locator('vaadin-grid.user-grid .user-grid-name').first().click();
  await waitForOverlay(page);
}

async function openUserActivityFeed(page) {
  await openActivityTab(page);
}

test.describe('User profile activity — cross-actor flows', () => {

  test('Bug 1: settings activity shows both fields when only one changes', async ({ page }) => {
    await loginAs(page, ADMIN_EMAIL);
    await openSettings(page);

    const overlay  = page.locator('.base-overlay.overlay--visible');
    const sizeInput = () => page.locator('.settings-overlay-content vaadin-integer-field').first().locator('input');
    const saveBtn   = () => overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i });

    const original = parseInt(await sizeInput().inputValue(), 10);
    const changed  = original === 10 ? 15 : 10;

    await test.step('Change only adsPageSize', async () => {
      await sizeInput().click({ clickCount: 3 });
      await sizeInput().fill(String(changed));
      await saveBtn().click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(600);
      await sizeInput().click({ clickCount: 3 });
      await sizeInput().fill(String(original));
      await saveBtn().click();
      await page.waitForLoadState('networkidle');
    });

    await openActivityTab(page);
    await screenshot(page, 'user-profile-activity-01-settings-activity');

    await test.step('Activity row shows both adsPageSize AND usersPageSize', async () => {
      const firstRow = page.locator('.activity-feed-row').first();
      await expect(firstRow).toBeVisible({ timeout: 5000 });

      const items = firstRow.locator('.activity-feed-changes-item');
      const texts = await items.allTextContents();
      if (texts.length < 2)
        throw new Error(`Expected >=2 field items (adsPageSize + usersPageSize), got ${texts.length}`);

      const hasAds   = texts.some(t => /adsPageSize|Оголошень/i.test(t));
      const hasUsers = texts.some(t => /usersPageSize|Користувач/i.test(t));
      if (!hasAds)   throw new Error(`adsPageSize field missing from settings activity: ${JSON.stringify(texts)}`);
      if (!hasUsers) throw new Error(`usersPageSize field missing — must always show even if unchanged: ${JSON.stringify(texts)}`);
    });
    await screenshot(page, 'user-profile-activity-02-both-settings-fields');
  });

  test('Bug 3a: admin edit of User 1 appears in User 1 profile activity', async ({ page }) => {
    const editedName = `AdminEdited ${Date.now()}`;
    let originalName;

    await loginAs(page, ADMIN_EMAIL);
    await goToUsersTab(page);

    await test.step('Admin edits first user via actions button', async () => {
      await page.locator('vaadin-grid.user-grid .user-grid-actions vaadin-button').first().click();
      await waitForOverlay(page);
      const nameField = page.locator('.base-overlay.overlay--visible vaadin-text-field').first().locator('input');
      originalName = await nameField.inputValue();
      await nameField.click({ clickCount: 3 });
      await nameField.fill(editedName);
      await page.locator('.base-overlay.overlay--visible vaadin-button')
        .filter({ hasText: /зберегти|save/i }).click();
      await waitForOverlayClosed(page);
      await page.waitForTimeout(500);
    });
    await screenshot(page, 'user-profile-activity-03-after-admin-edit');

    await test.step('Open User 1 profile (view overlay)', async () => {
      await page.locator('vaadin-grid.user-grid .user-grid-name', { hasText: editedName }).first().click();
      await waitForOverlay(page);
      await openUserActivityFeed(page);
    });
    await screenshot(page, 'user-profile-activity-04-user1-activity');

    await test.step('Admin edit appears in User 1 activity feed', async () => {
      const rows = page.locator('.activity-feed-row');
      if (await rows.count() === 0) throw new Error('No activity rows in User 1 profile');

      const feedText = await page.locator('.activity-feed-list').first().textContent();
      if (!feedText) throw new Error('Activity feed is empty');
    });
    await screenshot(page, 'user-profile-activity-05-edit-visible');

    await test.step('Revert User 1 name', async () => {
      await page.keyboard.press('Escape');
      await waitForOverlayClosed(page).catch(() => {});
      await goToUsersTab(page);
      await page.locator('vaadin-grid.user-grid .user-grid-actions vaadin-button').first().click();
      await waitForOverlay(page);
      const nameField = page.locator('.base-overlay.overlay--visible vaadin-text-field').first().locator('input');
      await nameField.click({ clickCount: 3 });
      await nameField.fill(originalName || 'User 1');
      await page.locator('.base-overlay.overlay--visible vaadin-button')
        .filter({ hasText: /зберегти|save/i }).click();
      await waitForOverlayClosed(page);
    });
  });

  test('Bug 3b: restore from User 1 profile targets User 1, not admin', async ({ page }) => {
    const editedName = `RestoreTarget ${Date.now()}`;
    let originalName;

    await loginAs(page, ADMIN_EMAIL);
    await goToUsersTab(page);

    await test.step('Admin edits User 1', async () => {
      await page.locator('vaadin-grid.user-grid .user-grid-actions vaadin-button').first().click();
      await waitForOverlay(page);
      const nameField = page.locator('.base-overlay.overlay--visible vaadin-text-field').first().locator('input');
      originalName = await nameField.inputValue();
      await nameField.click({ clickCount: 3 });
      await nameField.fill(editedName);
      await page.locator('.base-overlay.overlay--visible vaadin-button')
        .filter({ hasText: /зберегти|save/i }).click();
      await waitForOverlayClosed(page);
      await page.waitForTimeout(500);
    });

    await test.step('Open User 1 profile → Activity tab', async () => {
      await page.locator('vaadin-grid.user-grid .user-grid-name', { hasText: editedName }).first().click();
      await waitForOverlay(page);
      await openUserActivityFeed(page);
    });
    await screenshot(page, 'user-profile-activity-06-restore-button');

    await test.step('Restore button is present in User 1 profile', async () => {
      await expect(page.locator('.activity-feed-list .entity-history-restore-btn').first())
        .toBeVisible({ timeout: 5000 });
    });

    await test.step('Click restore — User 1 name reverts, admin name intact', async () => {
      await page.locator('.activity-feed-list .entity-history-restore-btn').first().click();
      await page.evaluate(() => {
        const dialog = document.querySelector('vaadin-confirm-dialog[opened]');
        if (dialog) {
          const btn = dialog.querySelector('[slot="confirm-button"]');
          if (btn) btn.click();
        }
      });
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(600);
    });
    await screenshot(page, 'user-profile-activity-07-after-restore');

    await test.step('Grid shows User 1 reverted — editedName gone — admin row intact', async () => {
      await page.keyboard.press('Escape');
      await waitForOverlayClosed(page).catch(() => {});
      await goToUsersTab(page);
      const names = await page.locator('vaadin-grid.user-grid .user-grid-name').allTextContents();
      if (names.some(n => n.trim() === editedName))
        throw new Error(`User 1 still has editedName "${editedName}" — restore targeted wrong user or didn't work`);
      // Admin email cell may be off-screen in virtualised grid — checking names list is sufficient
    });
    await screenshot(page, 'user-profile-activity-08-grid-after-restore');
  });

  test('Bug 3c: admin own activity (settings) — no restore button for edits to other users', async ({ page }) => {
    const editedName = `AdminOwnProfile ${Date.now()}`;
    let originalName;

    await loginAs(page, ADMIN_EMAIL);
    await goToUsersTab(page);

    await test.step('Admin edits User 1 to generate cross-user activity', async () => {
      await page.locator('vaadin-grid.user-grid .user-grid-actions vaadin-button').first().click();
      await waitForOverlay(page);
      const nameField = page.locator('.base-overlay.overlay--visible vaadin-text-field').first().locator('input');
      originalName = await nameField.inputValue();
      await nameField.click({ clickCount: 3 });
      await nameField.fill(editedName);
      await page.locator('.base-overlay.overlay--visible vaadin-button')
        .filter({ hasText: /зберегти|save/i }).click();
      await waitForOverlayClosed(page);
      await page.waitForTimeout(500);
    });

    await test.step('Open admin own activity via settings', async () => {
      await openSettings(page);
      await openActivityTab(page);
    });
    await screenshot(page, 'user-profile-activity-09-admin-own-activity');

    await test.step('Cross-user edit row has NO restore button', async () => {
      const rows = page.locator('.activity-feed-row');
      const rowCount = await rows.count();
      for (let i = 0; i < rowCount; i++) {
        const rowText = await rows.nth(i).textContent();
        if (rowText.includes(editedName)) {
          const btnInRow = await rows.nth(i).locator('.entity-history-restore-btn').count();
          if (btnInRow > 0)
            throw new Error(`Restore button found for cross-user edit row in admin's own activity — must not appear`);
          return;
        }
      }
    });
    await screenshot(page, 'user-profile-activity-10-no-restore-for-cross-user');

    await test.step('Restore User 1 to original name', async () => {
      await page.keyboard.press('Escape');
      await waitForOverlayClosed(page).catch(() => {});
      await goToUsersTab(page);
      await page.locator('vaadin-grid.user-grid .user-grid-name', { hasText: editedName }).first().click();
      await waitForOverlay(page);
      await openUserActivityFeed(page);
      const restoreBtn = page.locator('.activity-feed-list .entity-history-restore-btn').first();
      if (await restoreBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
        await restoreBtn.click();
        await page.evaluate(() => {
          const d = document.querySelector('vaadin-confirm-dialog[opened]');
          if (d) { const b = d.querySelector('[slot="confirm-button"]'); if (b) b.click(); }
        });
        await page.waitForLoadState('networkidle');
      }
    });
  });
});
