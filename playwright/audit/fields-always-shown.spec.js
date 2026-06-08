const { test, expect, loginAs,
        waitForOverlay, waitForOverlayClosed, openHistory, openSettings, openActivityTab, screenshot } = require('./_test-helpers');

test.describe('All fields always shown in history and activity', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, 'user1@example.com');
  });

  test('history: edit description only — unchanged title shown in every row', async ({ page }) => {
    const TITLE = `Fields Always Shown ${Date.now()}`;
    const DESC_V1 = 'Original description v1';
    const DESC_V2 = 'Updated description v2';

    await page.locator('.add-advertisement-button').click();
    await waitForOverlay(page);
    const ov = page.locator('.advertisement-overlay');
    await ov.locator('[data-testid="advertisement-overlay-field-title"] input').fill(TITLE);
    await ov.locator('[data-testid="advertisement-overlay-field-description"] textarea').fill(DESC_V1);
    await ov.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await waitForOverlayClosed(page);

    await page.locator('.advertisement-card')
      .filter({ has: page.locator('.advertisement-title', { hasText: TITLE }) })
      .first().click();
    await waitForOverlay(page);

    await test.step('CREATED row shows both title and description fields', async () => {
      await openHistory(page);
      const createdRow = page.locator('.entity-activity-row').first();
      const items = createdRow.locator('.entity-activity-changes-item');
      const count = await items.count();
      if (count < 2) throw new Error(`CREATED row should have >=2 field items (title + description), got ${count}`);
      const texts = await items.allTextContents();
      const hasTitle = texts.some(t => t.includes(TITLE));
      if (!hasTitle) throw new Error(`Title "${TITLE}" not found in CREATED row items: ${JSON.stringify(texts)}`);
      const hasDesc = texts.some(t => t.includes(DESC_V1));
      if (!hasDesc) throw new Error(`Description "${DESC_V1}" not found in CREATED row items: ${JSON.stringify(texts)}`);
    });
    await screenshot(page, 'fields-01-history-created-both-fields');

    await ov.locator('vaadin-tab').filter({ hasText: /view|перегляд/i }).click();
    await page.locator('.overlay__view-title').waitFor({ timeout: 3000 });
    await ov.locator('vaadin-button').filter({ hasText: /edit|редагувати/i }).first().click();
    await page.locator('[data-testid="advertisement-overlay-field-title"] input').waitFor();
    await ov.locator('[data-testid="advertisement-overlay-field-description"] textarea').fill(DESC_V2);
    await ov.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await page.locator('.overlay__view-title').waitFor();

    await openHistory(page);

    await test.step('UPDATED row (description only) shows unchanged title and changed description', async () => {
      const updatedRow = page.locator('.entity-activity-row').first();
      const items = updatedRow.locator('.entity-activity-changes-item');
      const count = await items.count();
      if (count < 2) throw new Error(`UPDATED row should have >=2 field items (title unchanged + description diff), got ${count}`);

      const texts = await items.allTextContents();
      const hasTitle = texts.some(t => t.includes(TITLE));
      if (!hasTitle) throw new Error(`Unchanged title "${TITLE}" not found in UPDATED row: ${JSON.stringify(texts)}`);

      const hasDiff = texts.some(t => t.includes('→'));
      if (!hasDiff) throw new Error(`Diff arrow → not found in UPDATED row — changed description should show diff: ${JSON.stringify(texts)}`);

      const unchangedItems = updatedRow.locator('.entity-activity-changes-item--unchanged');
      const unchangedCount = await unchangedItems.count();
      if (unchangedCount === 0) throw new Error('No unchanged-styled items in UPDATED row — title should be styled as unchanged');
    });
    await screenshot(page, 'fields-02-history-updated-title-unchanged');
  });

  test('activity: create — both fields shown; update title only — description shown as unchanged', async ({ page }) => {
    const TITLE_V1 = `Activity Fields Test ${Date.now()}`;
    const TITLE_V2 = TITLE_V1 + ' edited';
    const DESC = 'Description stays the same';

    await page.locator('.add-advertisement-button').click();
    await waitForOverlay(page);
    const ov = page.locator('.advertisement-overlay');
    await ov.locator('[data-testid="advertisement-overlay-field-title"] input').fill(TITLE_V1);
    await ov.locator('[data-testid="advertisement-overlay-field-description"] textarea').fill(DESC);
    await ov.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await waitForOverlayClosed(page);

    await openSettings(page);
    await openActivityTab(page);

    await test.step('CREATED activity row shows both title and description', async () => {
      const createdRow = page.locator('.activity-feed-row')
        .filter({ hasText: TITLE_V1 }).first();
      const items = createdRow.locator('.activity-feed-changes-item');
      const count = await items.count();
      if (count < 2) throw new Error(`CREATED activity row should have >=2 field items, got ${count}`);
      const texts = await items.allTextContents();
      const hasTitle = texts.some(t => t.includes(TITLE_V1));
      if (!hasTitle) throw new Error(`Title not found in CREATED activity row: ${JSON.stringify(texts)}`);
      const hasDesc = texts.some(t => t.includes(DESC));
      if (!hasDesc) throw new Error(`Description not found in CREATED activity row: ${JSON.stringify(texts)}`);
    });
    await screenshot(page, 'fields-03-activity-created-both-fields');

    await page.keyboard.press('Escape');
    await waitForOverlayClosed(page).catch(() => {});

    await page.locator('.advertisement-card')
      .filter({ has: page.locator('.advertisement-title', { hasText: TITLE_V1 }) })
      .first().click();
    await waitForOverlay(page);
    await ov.locator('vaadin-button').filter({ hasText: /edit|редагувати/i }).first().click();
    await page.locator('[data-testid="advertisement-overlay-field-title"] input').waitFor();
    const titleInput = page.locator('[data-testid="advertisement-overlay-field-title"] input');
    await titleInput.click({ clickCount: 3 });
    await titleInput.fill(TITLE_V2);
    await ov.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await page.locator('.overlay__view-title').waitFor();
    await ov.locator('.overlay__breadcrumb-back').first().click();
    await waitForOverlayClosed(page);

    await openSettings(page);
    await openActivityTab(page);

    await test.step('UPDATED activity row (title only) shows title diff and unchanged description', async () => {
      const updatedRow = page.locator('.activity-feed-row')
        .filter({ hasText: TITLE_V2 }).first();
      const items = updatedRow.locator('.activity-feed-changes-item');
      const count = await items.count();
      if (count < 2) throw new Error(`UPDATED activity row should have >=2 field items, got ${count}`);

      const texts = await items.allTextContents();
      const hasDiff = texts.some(t => t.includes('→'));
      if (!hasDiff) throw new Error(`Diff arrow → not found in UPDATED row — title change should show diff: ${JSON.stringify(texts)}`);

      const hasDesc = texts.some(t => t.includes(DESC));
      if (!hasDesc) throw new Error(`Unchanged description "${DESC}" not found in UPDATED activity row: ${JSON.stringify(texts)}`);

      const unchangedItems = updatedRow.locator('.activity-feed-changes-item--unchanged');
      const unchangedCount = await unchangedItems.count();
      if (unchangedCount === 0) throw new Error('No unchanged-styled items — description should be styled as unchanged');
    });
    await screenshot(page, 'fields-04-activity-updated-desc-unchanged');
  });
});
