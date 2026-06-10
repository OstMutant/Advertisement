const { test, expect, loginAs,
        waitForOverlay, waitForOverlayClosed, openHistory,
        openSettings, openActivityTab, closeOverlay, screenshot } = require('./_test-helpers');

const ADMIN_EMAIL = 'user3@example.com';

test.describe('Restore content correctness', () => {

  // ── Advertisement history restore ────────────────────────────────────────────

  test.describe('Advertisement history', () => {
    test.beforeEach(async ({ page }) => { await loginAs(page); });

    test('restore to v1 sets content to initial creation value', async ({ page }) => {
      const title = `Restore Content Test ${Date.now()}`;
      const overlay = page.locator('.advertisement-overlay');

      await test.step('Create ad with initial description', async () => {
        await page.locator('vaadin-button').filter({ hasText: /додати|add/i }).first().click();
        await waitForOverlay(page);
        await overlay.locator('[data-testid="advertisement-overlay-field-title"] input').fill(title);
        await overlay.locator('[data-testid="advertisement-overlay-field-description"] textarea').fill('First Version');
        await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
        await waitForOverlayClosed(page);
      });

      await test.step('Edit description → Second Version', async () => {
        await page.locator('.advertisement-card')
          .filter({ has: page.locator('.advertisement-title', { hasText: title }) })
          .first().click();
        await waitForOverlay(page);
        await overlay.locator('vaadin-button').filter({ hasText: /edit|редагувати/i }).first().click();
        await overlay.locator('[data-testid="advertisement-overlay-field-description"] textarea').fill('Second Version');
        await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
        await page.locator('.overlay__view-title').waitFor();
      });

      await test.step('Edit description → Third Version', async () => {
        await overlay.locator('vaadin-button').filter({ hasText: /edit|редагувати/i }).first().click();
        await overlay.locator('[data-testid="advertisement-overlay-field-description"] textarea').fill('Third Version');
        await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
        await page.locator('.overlay__view-title').waitFor();
      });

      await openHistory(page);
      await screenshot(page, 'restore-content-01-history-v3');

      await test.step('Restore button on v1 (oldest) is present', async () => {
        await expect(page.locator('.entity-activity-restore-btn').last()).toBeVisible();
      });

      await page.locator('.entity-activity-restore-btn').last().click();
      await expect(overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i })).toBeEnabled({ timeout: 5000 });
      await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
      await page.locator('.overlay__view-title').waitFor({ timeout: 8000 });

      await test.step('Switch to edit and verify description = First Version', async () => {
        await overlay.locator('vaadin-button').filter({ hasText: /edit|редагувати/i }).first().click();
        await page.locator('[data-testid="advertisement-overlay-field-description"] textarea').waitFor();
        const desc = await overlay.locator('[data-testid="advertisement-overlay-field-description"] textarea').inputValue();
        if (desc !== 'First Version')
          throw new Error(`Expected "First Version" after v1 restore, got "${desc}"`);
      });
      await screenshot(page, 'restore-content-02-restored-v1');
    });

    test('restore to v2 sets content to v2 value, not v3', async ({ page }) => {
      const title = `Restore V2 Test ${Date.now()}`;
      const overlay = page.locator('.advertisement-overlay');

      await test.step('Create + edit twice', async () => {
        await page.locator('vaadin-button').filter({ hasText: /додати|add/i }).first().click();
        await waitForOverlay(page);
        await overlay.locator('[data-testid="advertisement-overlay-field-title"] input').fill(title);
        await overlay.locator('[data-testid="advertisement-overlay-field-description"] textarea').fill('v1 content');
        await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
        await waitForOverlayClosed(page);

        await page.locator('.advertisement-card')
          .filter({ has: page.locator('.advertisement-title', { hasText: title }) })
          .first().click();
        await waitForOverlay(page);

        for (const desc of ['v2 content', 'v3 content']) {
          await overlay.locator('vaadin-button').filter({ hasText: /edit|редагувати/i }).first().click();
          await overlay.locator('[data-testid="advertisement-overlay-field-description"] textarea').fill(desc);
          await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
          await page.locator('.overlay__view-title').waitFor();
        }
      });

      await openHistory(page);

      await test.step('Restore to v2 (second restore button from bottom)', async () => {
        const btns = page.locator('.entity-activity-restore-btn');
        const count = await btns.count();
        await btns.nth(count - 2).click();
        await expect(overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i })).toBeEnabled({ timeout: 5000 });
        await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
        await page.locator('.overlay__view-title').waitFor({ timeout: 8000 });
      });

      await test.step('Description is v2 content', async () => {
        await overlay.locator('vaadin-button').filter({ hasText: /edit|редагувати/i }).first().click();
        await page.locator('[data-testid="advertisement-overlay-field-description"] textarea').waitFor();
        const desc = await overlay.locator('[data-testid="advertisement-overlay-field-description"] textarea').inputValue();
        if (desc !== 'v2 content')
          throw new Error(`Expected "v2 content" after v2 restore, got "${desc}"`);
      });
      await screenshot(page, 'restore-content-03-restored-v2');
    });
  });

  // ── Settings activity restore ─────────────────────────────────────────────────

  test.describe('Settings activity', () => {
    test.beforeEach(async ({ page }) => { await loginAs(page); });

    test('restore reverts settings to the snapshot value of the clicked entry', async ({ page }) => {
      await openSettings(page);

      const getSize = async () => parseInt(
        await page.locator('.settings-overlay-content vaadin-integer-field').first().locator('input').inputValue(), 10
      );
      const setSize = async (n) => {
        const inp = page.locator('.settings-overlay-content vaadin-integer-field').first().locator('input');
        await inp.click({ clickCount: 3 });
        await inp.fill(String(n));
      };
      const save = () => page.locator('.base-overlay.overlay--visible vaadin-button')
        .filter({ hasText: /зберегти|save/i }).click();

      const current = await getSize();
      const step1 = current === 10 ? 15 : 10;
      const step2 = current === 10 ? 20 : 5;
      const step3 = current === 10 ? 25 : 8;

      await setSize(step1);
      await save();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(1100);

      await setSize(step2);
      await save();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(1100);

      await setSize(step3);
      await save();
      await page.waitForLoadState('networkidle');

      await openActivityTab(page);
      await page.locator('.entity-activity-list .entity-activity-row').nth(1).waitFor({ timeout: 10000 });

      await test.step('Restore button present on non-current entry', async () => {
        await expect(page.locator('.entity-activity-list .entity-activity-restore-btn').first())
          .toBeVisible({ timeout: 5000 });
      });

      await page.locator('.entity-activity-list .entity-activity-restore-btn').first().click();
      await expect(page.locator('.base-overlay.overlay--visible vaadin-button').filter({ hasText: /зберегти|save/i })).toBeEnabled({ timeout: 5000 });
      await page.locator('.base-overlay.overlay--visible vaadin-button')
        .filter({ hasText: /зберегти|save/i }).click();
      await page.waitForLoadState('networkidle');
      await page.locator('.settings-overlay-content vaadin-integer-field').first().waitFor({ timeout: 5000 });

      await test.step('Size matches snapshot value of the restored entry', async () => {
        const restored = await getSize();
        if (restored !== step2)
          throw new Error(`Expected ${step2} (snapshot value) after restore, got ${restored}`);
      });
      await screenshot(page, 'restore-content-04-settings-restored');
    });
  });

  // ── User profile activity restore ────────────────────────────────────────────

  test.describe('User activity', () => {
    test.beforeEach(async ({ page }) => { await loginAs(page, ADMIN_EMAIL); });

    test('restore reverts user to the snapshot value of the clicked entry', async ({ page }) => {
      const name1 = `RestoreContentUser1 ${Date.now()}`;
      const name2 = `RestoreContentUser2 ${Date.now()}`;
      const name3 = `RestoreContentUser3 ${Date.now()}`;

      const renameFirstUser = async (name) => {
        await page.locator('vaadin-grid.user-grid .user-grid-actions vaadin-button').first().click();
        await waitForOverlay(page);
        const field = page.locator('.base-overlay.overlay--visible vaadin-text-field').first().locator('input');
        await field.click({ clickCount: 3 });
        await field.fill(name);
        await page.locator('.base-overlay.overlay--visible vaadin-button')
          .filter({ hasText: /зберегти|save/i }).click();
        await waitForOverlayClosed(page);
        await page.waitForTimeout(600);
      };

      await test.step('Rename user three times to guarantee non-current entries with diffs', async () => {
        await page.locator('vaadin-tab').filter({ hasText: /users|користувач/i }).click();
        await page.locator('vaadin-grid.user-grid').waitFor({ timeout: 8000 });
        await renameFirstUser(name1);
        await renameFirstUser(name2);
        await renameFirstUser(name3);
      });

      await test.step('Open user profile → enter edit mode → Activity tab', async () => {
        await page.locator('vaadin-grid.user-grid .user-grid-name', { hasText: name3 }).first().click();
        await waitForOverlay(page);
        const overlay = page.locator('.base-overlay.overlay--visible');
        const editBtn = overlay.locator('vaadin-button').filter({ hasText: /edit|редагувати/i }).first();
        if (await editBtn.isVisible()) await editBtn.click();
        await page.locator('.user-form-tabs').waitFor({ timeout: 5000 });
        const activityTab = overlay.locator('.user-form-tabs vaadin-tab').filter({ hasText: /activ|активн/i });
        await activityTab.click();
        await overlay.locator('.entity-activity-list').first().waitFor({ timeout: 8000 });
        await page.locator('.entity-activity-list .entity-activity-row').nth(1).waitFor({ timeout: 8000 });
      });

      await test.step('Restore button present on non-current entry', async () => {
        await expect(page.locator('.entity-activity-list .entity-activity-restore-btn').first())
          .toBeVisible({ timeout: 5000 });
      });
      await screenshot(page, 'restore-content-05-user-before-restore');

      await page.locator('.entity-activity-list .entity-activity-restore-btn').first().click();
      await expect(page.locator('.base-overlay.overlay--visible vaadin-button').filter({ hasText: /зберегти|save/i })).toBeEnabled({ timeout: 5000 });
      await page.locator('.base-overlay.overlay--visible vaadin-button')
        .filter({ hasText: /зберегти|save/i }).click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(600);

      await test.step('Name is the snapshot value of the restored entry (name2)', async () => {
        await page.keyboard.press('Escape');
        await waitForOverlayClosed(page).catch(() => {});
        await page.waitForTimeout(500);
        const names = await page.locator('vaadin-grid.user-grid .user-grid-name').allTextContents();
        if (!names.some(n => n.trim() === name2))
          throw new Error(`Expected "${name2}" in grid after restore, but not found`);
        if (names.some(n => n.trim() === name3))
          throw new Error(`"${name3}" still present — restore used wrong snapshot`);
      });
      await screenshot(page, 'restore-content-06-user-restored');
    });
  });
});
