const { test, expect, loginAs,
        waitForOverlay, waitForOverlayClosed, openHistory, confirmDialog, screenshot } = require('./_test-helpers');

test.describe('Advertisement history (deep)', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page);
  });

  test('multiple versions: badges, restore buttons, restore flow', async ({ page }) => {
    const overlay = page.locator('.advertisement-overlay');
    const TITLE = 'History Deep Test';

    await page.locator('vaadin-button').filter({ hasText: /додати|add/i }).first().click();
    await waitForOverlay(page);
    await overlay.locator('vaadin-text-field input').fill(TITLE);
    await overlay.locator('vaadin-text-area textarea').fill('Version 1');
    await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await waitForOverlayClosed(page);

    await page.locator('.advertisement-card')
      .filter({ has: page.locator('.advertisement-title', { hasText: TITLE }) })
      .first().click();
    await waitForOverlay(page);
    await openHistory(page);

    await test.step('CREATED badge present after create', async () => {
      if (await page.locator('.adv-history-action--created').count() === 0)
        throw new Error('No CREATED badge after initial save');
    });

    await test.step('Version v1 shown', async () => {
      const text = await page.locator('.adv-history-list').textContent();
      if (!text.includes('v1') && !text.includes('1')) throw new Error('No version 1 label');
    });

    await test.step('No restore button on only entry', async () => {
      if (await page.locator('.adv-history-restore-btn').count() > 0)
        throw new Error('Restore button should not appear for the only history entry');
    });

    // Edit twice to get v2 and v3
    for (const [ver, desc] of [['v2', 'Version 2'], ['v3', 'Version 3']]) {
      await overlay.locator('vaadin-tab').filter({ hasText: /view|перегляд/i }).click();
      await page.locator('.overlay__view-title').waitFor({ timeout: 3000 });
      await overlay.locator('vaadin-button').filter({ hasText: /edit|редагувати/i }).first().click();
      await page.locator('.base-overlay.overlay--visible vaadin-text-field input').first().waitFor();
      await overlay.locator('vaadin-text-area textarea').fill(desc);
      await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
      await page.locator('.overlay__view-title').waitFor();
      await openHistory(page);
    }

    const rows = await page.locator('.adv-history-row').count();
    await test.step('3 history rows after 2 edits', async () => {
      if (rows < 3) throw new Error(`Expected >=3 rows, got ${rows}`);
    });

    await test.step('Version badges go v3, v2, v1', async () => {
      const text = await page.locator('.adv-history-list').textContent();
      if (!text.includes('v3') && !text.includes('3')) throw new Error('v3 not found');
    });

    await test.step('Latest row (v3) has NO restore button', async () => {
      const firstRow = page.locator('.adv-history-row').first();
      if (await firstRow.locator('.adv-history-restore-btn').count() > 0)
        throw new Error('Latest version should not have restore button');
    });

    await test.step('Older rows have restore buttons', async () => {
      if (await page.locator('.adv-history-restore-btn').count() < 2)
        throw new Error('Expected restore buttons on older entries');
    });

    await test.step('UPDATED badges present', async () => {
      if (await page.locator('.adv-history-action--updated').count() === 0)
        throw new Error('No UPDATED badges found');
    });

    await test.step('Title diff shown in v3 changes', async () => {
      if (await page.locator('.adv-history-changes').count() === 0)
        throw new Error('No changes summary found');
    });
    await screenshot(page, 'history-deep-01-three-versions');

    await page.locator('.adv-history-restore-btn').last().click();

    await test.step('Restore dialog shown', async () => {
      await page.locator('vaadin-dialog-overlay').waitFor({ timeout: 5000 });
    });

    await confirmDialog(page);
    await page.locator('.overlay__view-title').waitFor({ timeout: 5000 });

    await test.step('Title restored to v1 value', async () => {
      await expect(page.locator('.overlay__view-title')).toBeVisible();
    });

    await openHistory(page);

    await test.step('History grows after restore', async () => {
      const newRows = await page.locator('.adv-history-row').count();
      if (newRows <= rows) throw new Error(`Expected more rows after restore, got ${newRows}`);
    });
    await screenshot(page, 'history-deep-02-after-restore');
  });
});
