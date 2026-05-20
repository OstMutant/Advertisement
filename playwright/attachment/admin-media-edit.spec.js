const { test, expect, loginAs,
        waitForOverlay, waitForOverlayClosed, openHistory, downloadPng, screenshot } = require('./_test-helpers');

const AD_TITLE = 'Admin Media Edit Bug Test';
const avatar   = seed => `https://api.dicebear.com/9.x/adventurer/png?seed=${seed}&size=256&backgroundColor=b6e3f4`;

test.describe('Admin media edit — single current-state badge', () => {

  test('editing another user ad media as admin shows exactly one current-state badge', async ({ page }) => {
    const img1 = '/tmp/admin-media-1.png';
    const img2 = '/tmp/admin-media-2.png';
    await downloadPng(avatar('admin-media-a'), img1);
    await downloadPng(avatar('admin-media-b'), img2);

    // ── Step 1: user creates ad (text only, no image) ──────────────────────
    await test.step('User creates ad and edits text', async () => {
      await loginAs(page, 'user1@example.com', 'password');
      const overlay = page.locator('.advertisement-overlay');

      await page.locator('.add-advertisement-button').click();
      await waitForOverlay(page);
      await overlay.locator('[data-testid="advertisement-overlay-field-title"] input').fill(AD_TITLE);
      await overlay.locator('[data-testid="advertisement-overlay-field-description"] textarea').fill('Initial description');
      await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
      await waitForOverlayClosed(page);

      // Edit text so we get v2 in audit_log (text-only change, no image)
      await page.locator('.advertisement-card')
        .filter({ has: page.locator('.advertisement-title', { hasText: AD_TITLE }) })
        .first().click();
      await waitForOverlay(page);
      await overlay.locator('vaadin-button').filter({ hasText: /редагувати|edit/i }).first().click();
      await page.locator('[data-testid="advertisement-overlay-field-title"] input').waitFor();
      await overlay.locator('[data-testid="advertisement-overlay-field-description"] textarea').fill('Updated description v2');
      await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
      await page.locator('.overlay__view-title').waitFor();
      // Close overlay via breadcrumb back link
      await page.locator('.overlay__breadcrumb-back').click();
      await waitForOverlayClosed(page);
    });

    // ── Step 2: admin adds an image ────────────────────────────────────────
    await test.step('Admin adds image (audit v3 + attachment snapshot rn=1)', async () => {
      // Logout current user: click Вийти → confirm dialog → Так
      await page.locator('vaadin-button').filter({ hasText: /вийти/i }).first().click();
      await page.locator('vaadin-confirm-dialog-overlay').waitFor({ timeout: 5000 });
      // Confirm button is in the confirm-button slot of vaadin-confirm-dialog
      await page.evaluate(() => {
        const dialog = document.querySelector('vaadin-confirm-dialog[opened]');
        const btn = dialog && dialog.querySelector('[slot="confirm-button"]');
        if (btn) btn.click();
      });
      await page.locator('.header-settings-button').waitFor({ state: 'hidden', timeout: 10000 });
      await loginAs(page, 'user3@example.com', 'password');
      const overlay = page.locator('.advertisement-overlay');

      await page.locator('.advertisement-card')
        .filter({ has: page.locator('.advertisement-title', { hasText: AD_TITLE }) })
        .first().click();
      await waitForOverlay(page);
      await overlay.locator('vaadin-button').filter({ hasText: /редагувати|edit/i }).first().click();
      await page.locator('[data-testid="advertisement-overlay-field-title"] input').waitFor();

      await page.locator('vaadin-upload input[type="file"]').setInputFiles(img1);
      await page.locator('.attachment-gallery__item').first().waitFor({ timeout: 10000 });

      await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
      await page.locator('.overlay__view-title').waitFor();
    });

    // ── Step 3: admin replaces the image ──────────────────────────────────
    await test.step('Admin replaces image (audit v4 + attachment snapshot rn=2)', async () => {
      const overlay = page.locator('.advertisement-overlay');

      await overlay.locator('vaadin-button').filter({ hasText: /редагувати|edit/i }).first().click();
      await page.locator('[data-testid="advertisement-overlay-field-title"] input').waitFor();

      // Delete existing image and upload a new one
      await page.locator('.attachment-gallery__delete-btn').first().click();
      await page.locator('vaadin-upload input[type="file"]').setInputFiles(img2);
      await page.locator('.attachment-gallery__item').first().waitFor({ timeout: 10000 });

      await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
      await page.locator('.overlay__view-title').waitFor();
    });

    // ── Step 4: check history ─────────────────────────────────────────────
    await test.step('History shows exactly one current-state badge', async () => {
      const opened = await openHistory(page);
      if (!opened) throw new Error('History tab not available');

      await screenshot(page, 'admin-media-edit-history');

      const badgeCount = await page.locator('.entity-history-current-badge').count();
      if (badgeCount !== 1) {
        throw new Error(`Expected exactly 1 current-state badge, got ${badgeCount} — bug: multiple versions incorrectly marked as current`);
      }
    });

    const fs = require('fs');
    [img1, img2].forEach(p => { try { fs.unlinkSync(p); } catch (_) {} });
  });
});
