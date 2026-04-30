const { test, expect, loginAs,
        waitForOverlay, waitForOverlayClosed, openHistory, confirmDialog, screenshot } = require('./_test-helpers');

test.describe('Advertisement history', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page);
  });

  test('create → edit → restore shows correct history', async ({ page }) => {
    const overlay = page.locator('.advertisement-overlay');

    await page.locator('vaadin-button').filter({ hasText: /new|add|create|нове|додати/i }).first().click();
    await waitForOverlay(page);
    await overlay.locator('vaadin-text-field input').fill('History Test Ad');
    await overlay.locator('vaadin-text-area textarea').fill('Original description v1');
    await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save|submit/i }).click();
    await waitForOverlayClosed(page);

    await page.locator('.advertisement-card')
      .filter({ has: page.locator('.advertisement-title', { hasText: 'History Test Ad' }) })
      .first().click();
    await waitForOverlay(page);

    await openHistory(page);

    await test.step('History list visible', async () => {
      await expect(page.locator('.adv-history-list')).toBeVisible();
    });

    const rowCountInit = await page.locator('.adv-history-row').count();
    await test.step('History has entries after create', async () => {
      if (rowCountInit === 0) throw new Error('No history rows');
    });

    await overlay.locator('vaadin-tab').filter({ hasText: /view|перегляд/i }).click();
    await page.locator('.overlay__view-title').waitFor({ timeout: 3000 });

    await overlay.locator('vaadin-button').filter({ hasText: /edit|редагувати/i }).first().click();
    await page.locator('.base-overlay.overlay--visible vaadin-text-field input').first().waitFor();

    await overlay.locator('vaadin-text-area textarea').fill('Updated description v2');
    await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save|submit/i }).click();
    await page.locator('.overlay__view-title').waitFor();

    await openHistory(page);

    const rowCountAfter = await page.locator('.adv-history-row').count();
    await test.step('History has 2+ entries after edit', async () => {
      if (rowCountAfter < 2) throw new Error(`Expected >=2 rows, got ${rowCountAfter}`);
    });

    await test.step('Changes summary shown', async () => {
      if (await page.locator('.adv-history-changes').count() === 0) throw new Error('No changes summary found');
    });
    await screenshot(page, 'adv-history-01-after-edit');

    const restoreBtns = page.locator('.adv-history-restore-btn');
    await test.step('Restore buttons present', async () => {
      if (await restoreBtns.count() === 0) throw new Error('No restore buttons');
    });

    await restoreBtns.last().click();

    await test.step('Restore confirm dialog shown', async () => {
      await page.locator('vaadin-dialog-overlay').waitFor({ timeout: 5000 });
    });

    await confirmDialog(page);
    await page.locator('.overlay__view-title').waitFor();

    await test.step('View shown after restore', async () => {
      await expect(page.locator('.overlay__view-title')).toBeVisible();
    });
    await screenshot(page, 'adv-history-02-after-restore');

    await openHistory(page);

    await test.step('No RESTORED badge (all entries are UPDATED)', async () => {
      if (await page.locator('.adv-history-action--restored').count() > 0)
        throw new Error('RESTORED badge found but should not exist');
      if (await page.locator('.adv-history-action--updated').count() === 0)
        throw new Error('No UPDATED badge found after restore');
    });
  });
});
