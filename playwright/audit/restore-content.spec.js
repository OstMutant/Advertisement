const { test, expect, loginAs,
        waitForOverlay, waitForOverlayClosed, openHistory, confirmDialog,
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
        await expect(page.locator('.entity-history-restore-btn').last()).toBeVisible();
      });

      await page.locator('.entity-history-restore-btn').last().click();
      await confirmDialog(page);
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
        const btns = page.locator('.entity-history-restore-btn');
        const count = await btns.count();
        await btns.nth(count - 2).click();
        await confirmDialog(page);
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

    test('restore reverts to state BEFORE the change, not to state after', async ({ page }) => {
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

      const before = await getSize();
      const after  = before === 10 ? 15 : 10;

      await setSize(after);
      await save();
      await page.waitForLoadState('networkidle');

      await openActivityTab(page);

      await test.step('Restore button present', async () => {
        await expect(page.locator('.activity-feed-list .entity-history-restore-btn').first())
          .toBeVisible({ timeout: 10000 });
      });

      await page.locator('.activity-feed-list .entity-history-restore-btn').first().click();
      await page.locator('vaadin-confirm-dialog').waitFor({ timeout: 5000 }).catch(async () => {
        await page.locator('vaadin-dialog-overlay').waitFor({ timeout: 5000 });
      });
      await confirmDialog(page, 'Оновити|Update');
      await page.waitForLoadState('networkidle');
      await page.locator('.settings-overlay-content vaadin-integer-field').first().waitFor({ timeout: 5000 });

      await test.step('Size is reverted to BEFORE value', async () => {
        const restored = await getSize();
        if (restored !== before)
          throw new Error(`Expected size ${before} (before value) after restore, got ${restored} — restore used wrong snapshot`);
      });
      await screenshot(page, 'restore-content-04-settings-reverted');
    });
  });

  // ── User profile activity restore ────────────────────────────────────────────

  test.describe('User activity', () => {
    test.beforeEach(async ({ page }) => { await loginAs(page, ADMIN_EMAIL); });

    test('restore reverts user name to state BEFORE the edit', async ({ page }) => {
      const newName = `RestoreContentUser ${Date.now()}`;
      let originalName;

      await test.step('Open first user for edit', async () => {
        await page.locator('vaadin-tab').filter({ hasText: /users|користувач/i }).click();
        await page.locator('vaadin-grid.user-grid').waitFor({ timeout: 8000 });
        await page.locator('vaadin-grid.user-grid .user-grid-actions vaadin-button').first().click();
        await waitForOverlay(page);
        const nameField = page.locator('.base-overlay.overlay--visible vaadin-text-field').first().locator('input');
        originalName = await nameField.inputValue();
        await nameField.click({ clickCount: 3 });
        await nameField.fill(newName);
        await page.locator('.base-overlay.overlay--visible vaadin-button')
          .filter({ hasText: /зберегти|save/i }).click();
        await waitForOverlayClosed(page);
        await page.waitForTimeout(500);
      });

      await test.step('Open user profile → Activity tab', async () => {
        await page.locator('vaadin-grid.user-grid .user-grid-name', { hasText: newName }).first().click();
        await waitForOverlay(page);
        const overlay = page.locator('.base-overlay.overlay--visible');
        const activityTab = overlay.locator('vaadin-tab').filter({ hasText: /activ|активн/i });
        await activityTab.click();
        await overlay.locator('.activity-feed-list').first().waitFor({ timeout: 8000 });
      });

      await test.step('Restore button present', async () => {
        await expect(page.locator('.activity-feed-list .entity-history-restore-btn').first())
          .toBeVisible({ timeout: 5000 });
      });
      await screenshot(page, 'restore-content-05-user-before-restore');

      await page.locator('.activity-feed-list .entity-history-restore-btn').first().click();
      await page.evaluate(() => {
        const dialog = document.querySelector('vaadin-confirm-dialog[opened]');
        if (dialog) { const btn = dialog.querySelector('[slot="confirm-button"]'); if (btn) btn.click(); }
      });
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(600);

      await test.step('Name reverted to original (BEFORE value)', async () => {
        await page.keyboard.press('Escape');
        await waitForOverlayClosed(page).catch(() => {});
        await page.waitForTimeout(500);
        const names = await page.locator('vaadin-grid.user-grid .user-grid-name').allTextContents();
        if (names.some(n => n.trim() === newName))
          throw new Error(`Name "${newName}" still present — restore used AFTER snapshot instead of BEFORE`);
      });
      await screenshot(page, 'restore-content-06-user-restored');
    });
  });
});
