const { test, expect, loginAs,
        waitForOverlay, waitForOverlayClosed, openHistory, screenshot,
        waitForSaved, returnToViewAfterSave } = require('./_test-helpers');

test.describe('Advertisement history', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page);
  });

  test('create → edit → restore shows correct history', async ({ page }) => {
    const overlay = page.locator('.advertisement-overlay');

    await page.locator('vaadin-button').filter({ hasText: /new|add|create|нове|додати/i }).first().click();
    await waitForOverlay(page);
    await overlay.locator('[data-testid="advertisement-overlay-field-title"] input').fill('History Test Ad');
    await overlay.locator('[data-testid="advertisement-overlay-field-description"] textarea').fill('Original description v1');
    await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save|submit/i }).click();
    await waitForOverlayClosed(page);

    await page.locator('.advertisement-card')
      .filter({ has: page.locator('.advertisement-title', { hasText: 'History Test Ad' }) })
      .first().click();
    await waitForOverlay(page);

    // openHistory enters edit mode then clicks Activity tab
    await openHistory(page);

    await test.step('History list visible', async () => {
      await expect(page.locator('.entity-activity-list')).toBeVisible();
    });

    const rowCountInit = await page.locator('.entity-activity-row').count();
    await test.step('History has entries after create', async () => {
      if (rowCountInit === 0) throw new Error('No history rows');
    });

    // Switch back to edit form tab to make a change
    await overlay.locator('.adv-form-tabs vaadin-tab').first().click();
    await page.locator('[data-testid="advertisement-overlay-field-title"] input').waitFor();

    await overlay.locator('[data-testid="advertisement-overlay-field-description"] textarea').fill('Updated description v2');
    await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save|submit/i }).click();
    await waitForSaved(page);

    await openHistory(page);

    const rowCountAfter = await page.locator('.entity-activity-row').count();
    await test.step('History has 2+ entries after edit', async () => {
      if (rowCountAfter < 2) throw new Error(`Expected >=2 rows, got ${rowCountAfter}`);
    });

    await test.step('Changes summary shown', async () => {
      if (await page.locator('.entity-activity-changes').count() === 0) throw new Error('No changes summary found');
    });
    await screenshot(page, 'entity-activity-01-after-edit');

    const restoreBtns = page.locator('.entity-activity-restore-btn');
    await test.step('Restore buttons present', async () => {
      if (await restoreBtns.count() === 0) throw new Error('No restore buttons');
    });

    await test.step('Restore button is left-aligned in history row', async () => {
      const row = page.locator('.entity-activity-row').filter({ has: page.locator('.entity-activity-restore-btn') }).first();
      const btn = row.locator('.entity-activity-restore-btn');
      const rowBox = await row.boundingBox();
      const btnBox = await btn.boundingBox();
      if (btnBox.x - rowBox.x > 48)
        throw new Error(`Restore button not left-aligned in history: offset=${btnBox.x - rowBox.x}px`);
    });

    await restoreBtns.last().click();

    await expect(overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i })).toBeEnabled({ timeout: 5000 });

    // Save to apply the restore
    await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await returnToViewAfterSave(page);

    await test.step('View shown after restore', async () => {
      await expect(page.locator('.overlay__view-title')).toBeVisible();
    });
    await screenshot(page, 'entity-activity-02-after-restore');

    await openHistory(page);

    await test.step('No RESTORED badge (all entries are UPDATED)', async () => {
      if (await page.locator('.entity-activity-action--restored').count() > 0)
        throw new Error('RESTORED badge found but should not exist');
      if (await page.locator('.entity-activity-action--updated').count() === 0)
        throw new Error('No UPDATED badge found after restore');
    });
  });
});
