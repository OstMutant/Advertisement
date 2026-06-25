const { test, expect, screenshot, TEST_USERS } = require('./_helpers');
const { runFillLoginFormFlow, runSubmitLoginFlow, runLogoutFlow } = require('./_flows/auth.flow');
const { runSwitchToUkrainianLoggedInFlow } = require('./_flows/language-switch.flow');
const { runNavigateToUsersTabFlow, runPromoteUserFlow, runOpenUserEditViaListFlow, runOpenUserEditViaViewFlow, runFillUserRoleFlow, runSaveUserEditFlow, clearUserFilter, closeUserOverlay, closeUserOverlayFromEdit } = require('./_flows/user-management.flow');
const { openTimelineTab, assertFeedHasRow } = require('./_flows/timeline.flow');

test.describe.configure({ mode: 'serial' });

test.describe('Promotion flow', () => {
  let page;

  test.beforeAll(async ({ browser }) => {
    page = await browser.newPage();
    await page.goto('/');
  });

  test.afterAll(async () => {
    await page.close();
  });

  // === Section 1: Role promotion ===

  test('adminEn promotes moderatorUk to MODERATOR — activity shows updated role, role badge in view and grid', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.adminEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.adminEn);
    await runNavigateToUsersTabFlow(page, expect);
    await runPromoteUserFlow(page, expect, TEST_USERS.moderatorUk, { role: 'MODERATOR' });
    await openTimelineTab(page);
    await assertFeedHasRow(page, expect, { action: 'updated', entityType: 'user', screenshotName: 'timeline-moderatoruk-promoted' });
    await runLogoutFlow(page, expect);
  });

  test('adminEn promotes moderatorEn to MODERATOR — activity shows updated role, role badge in view and grid', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.adminEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.adminEn);
    await runNavigateToUsersTabFlow(page, expect);
    await runPromoteUserFlow(page, expect, TEST_USERS.moderatorEn, { role: 'MODERATOR' });
    await openTimelineTab(page);
    await assertFeedHasRow(page, expect, { action: 'updated', entityType: 'user', screenshotName: 'timeline-moderatoren-promoted' });
    await runLogoutFlow(page, expect);
  });

  test('adminEn promotes adminUk to ADMIN — activity shows updated role, role badge in view and grid', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.adminEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.adminEn);
    await runNavigateToUsersTabFlow(page, expect);
    await runPromoteUserFlow(page, expect, TEST_USERS.adminUk, { role: 'ADMIN' });
    await openTimelineTab(page);
    await assertFeedHasRow(page, expect, { action: 'updated', entityType: 'user', screenshotName: 'timeline-adminuk-promoted' });
    await runLogoutFlow(page, expect);
  });

  // === Section 2: Set UK locales ===

  test('userUk — first login in English, switches to Ukrainian locale', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.userUk);
    await runSubmitLoginFlow(page, expect, { ...TEST_USERS.userUk, locale: 'en' });
    await runSwitchToUkrainianLoggedInFlow(page, expect);
    await runLogoutFlow(page, expect);
  });

  test('moderatorUk — first login in English, switches to Ukrainian locale', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.moderatorUk);
    await runSubmitLoginFlow(page, expect, { ...TEST_USERS.moderatorUk, locale: 'en' });
    await runSwitchToUkrainianLoggedInFlow(page, expect);
    await runLogoutFlow(page, expect);
  });

  test('adminEn edits userEn name — activity diff, grid updated, restore reverts name', async () => {
    const editedName = `AdminCrossEdit-${Date.now()}`;
    let originalName;

    await runFillLoginFormFlow(page, TEST_USERS.adminEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.adminEn);
    await runNavigateToUsersTabFlow(page, expect);

    await test.step('admin renames userEn — grid reflects new name', async () => {
      await runOpenUserEditViaListFlow(page, TEST_USERS.userEn.email);
      originalName = await page.locator('.user-overlay vaadin-text-field input').first().inputValue();
      await runFillUserRoleFlow(page, { name: editedName });
      await runSaveUserEditFlow(page, expect, 'cross-actor');
      await closeUserOverlay(page);
      await expect(page.locator('.user-grid-name:visible').filter({ hasText: editedName })).toHaveCount(1, { timeout: 5000 });
      await screenshot(page, 'cross-actor-01-user-renamed');
      await clearUserFilter(page);
    });

    await test.step('cross-actor edit visible in userEn profile activity', async () => {
      await runOpenUserEditViaViewFlow(page, TEST_USERS.userEn.email);
      await page.locator('.user-overlay vaadin-tab').filter({ hasText: /activity|активність/i }).click();
      const activityList = page.locator('.user-overlay .entity-activity-list');
      await activityList.waitFor({ timeout: 5000 });
      const latestRow = activityList.locator('.entity-activity-row').nth(0);
      await expect(latestRow.locator('.entity-activity-action')).toContainText(/updated|оновлено/i);
      await expect(latestRow.locator('.entity-activity-changes')).toContainText(editedName);
      await expect(activityList.locator('.entity-activity-restore-btn').first()).toBeVisible({ timeout: 5000 });
      await screenshot(page, 'cross-actor-02-profile-activity');
    });

    await test.step('restore reverts name — activity records restore entry', async () => {
      const activityList = page.locator('.user-overlay .entity-activity-list');
      await activityList.locator('.entity-activity-restore-btn').first().click();
      await runSaveUserEditFlow(page, expect, 'cross-actor-restore');
      await page.locator('.user-overlay vaadin-tab').filter({ hasText: /activity|активність/i }).click();
      await activityList.waitFor({ timeout: 5000 });
      const rowCount = await activityList.locator('.entity-activity-row').count();
      expect(rowCount).toBeGreaterThanOrEqual(2);
      await expect(activityList.locator('.entity-activity-row').nth(0).locator('.entity-activity-changes')).toContainText(originalName);
      await screenshot(page, 'cross-actor-03-activity-after-restore');
      await closeUserOverlayFromEdit(page);
    });

    await test.step('grid shows original name after restore', async () => {
      await expect(page.locator('.user-grid-name:visible').filter({ hasText: editedName })).toHaveCount(0, { timeout: 5000 });
      await expect(page.locator('.user-grid-name:visible').filter({ hasText: originalName })).toHaveCount(1, { timeout: 5000 });
      await screenshot(page, 'cross-actor-04-name-reverted');
      await clearUserFilter(page);
    });

    await openTimelineTab(page);
    await assertFeedHasRow(page, expect, { action: 'updated', entityType: 'user', screenshotName: 'timeline-cross-actor-restore' });
    await runLogoutFlow(page, expect);
  });
});
