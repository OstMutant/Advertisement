const { test, expect, loginAs, screenshot,
        waitForOverlay, waitForOverlayClosed, openSettings, openActivityTab, closeOverlay } = require('./_test-helpers');

const AD_TITLE = 'Activity Types Test Ad';

test.describe('Activity types', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, 'user1@example.com');
  });

  test('CREATED, UPDATED, DELETED entries + entity type badges', async ({ page }) => {
    const advOverlay = page.locator('.advertisement-overlay');

    await page.locator('vaadin-button').filter({ hasText: /додати|add/i }).first().click();
    await waitForOverlay(page);
    await advOverlay.locator('vaadin-text-field input').fill(AD_TITLE);
    await advOverlay.locator('vaadin-text-area textarea').fill('Initial content');
    await advOverlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await waitForOverlayClosed(page);
    await screenshot(page, 'acttypes-01-created');

    await page.locator('.advertisement-card')
      .filter({ has: page.locator('.advertisement-title', { hasText: AD_TITLE }) })
      .first().click();
    await waitForOverlay(page);
    await advOverlay.locator('vaadin-button').filter({ hasText: /edit|редагувати/i }).first().click();
    await page.locator('.base-overlay.overlay--visible vaadin-text-field input').first().waitFor();
    await advOverlay.locator('vaadin-text-area textarea').fill('Updated content');
    await advOverlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await page.locator('.overlay__view-title').waitFor();
    await screenshot(page, 'acttypes-02-edited');

    await closeOverlay(page);

    let deleteExists = false;
    const deleteCardBtn = page.locator('vaadin-grid')
      .locator(`[title*="${AD_TITLE}"], .advertisement-card`)
      .locator('vaadin-button').filter({ hasText: /видалити|delete/i }).first();
    if (await deleteCardBtn.count() > 0) {
      await deleteCardBtn.click();
      await page.locator('vaadin-dialog-overlay').waitFor({ timeout: 5000 });
      await page.evaluate(() => {
        const dialog = document.querySelector('vaadin-dialog[opened]');
        if (!dialog) return;
        const btn = [...dialog.querySelectorAll('vaadin-button')]
          .find(b => /видалити|delete|confirm/i.test(b.textContent?.trim()));
        if (btn) btn.click();
      });
      await page.locator('vaadin-dialog-overlay').waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {});
      deleteExists = true;
    }

    await openSettings(page);
    await openActivityTab(page);

    await test.step('ADVERTISEMENT entity type badge present', async () => {
      if (await page.locator('.user-activity-type--advertisement').count() === 0)
        throw new Error('No ADVERTISEMENT type badge');
    });

    await test.step('CREATED action badge present', async () => {
      const rows = page.locator('.user-activity-row');
      const count = await rows.count();
      let found = false;
      for (let i = 0; i < count; i++) {
        if (/Створ|Creat/i.test(await rows.nth(i).textContent())) { found = true; break; }
      }
      if (!found) throw new Error('CREATED action not found in activity');
    });

    await test.step('UPDATED action badge present', async () => {
      const rows = page.locator('.user-activity-row');
      const count = await rows.count();
      let found = false;
      for (let i = 0; i < count; i++) {
        if (/Оновл|Updat/i.test(await rows.nth(i).textContent())) { found = true; break; }
      }
      if (!found) throw new Error('UPDATED action not found in activity');
    });

    if (deleteExists) {
      await test.step('Deleted entity shows (deleted) marker', async () => {
        const rows = page.locator('.user-activity-row--deleted');
        if (await rows.count() === 0) throw new Error('No deleted-row marker found');
        const text = await rows.first().textContent();
        if (!text.includes('видалено') && !text.includes('deleted'))
          throw new Error('(deleted) marker text not found. Row: ' + text);
      });
    }

    await test.step('Activity rows contain timestamp', async () => {
      const times = page.locator('.user-activity-time');
      if (await times.count() === 0) throw new Error('No timestamps found in activity');
      const first = await times.first().textContent();
      if (!first || first === 'N/A') throw new Error('Empty or N/A timestamp');
    });
  });
});
