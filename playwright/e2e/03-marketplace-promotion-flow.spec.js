const { test, expect, screenshot, closeNotification, TEST_USERS } = require('./_helpers');
const { runFillLoginFormFlow, runSubmitLoginFlow, runLogoutFlow } = require('./_flows/auth.flow');
const { runSwitchToUkrainianLoggedInFlow } = require('./_flows/language-switch.flow');
const { runNavigateToUsersTabFlow, runPromoteUserFlow, runFilterUserByEmailFlow, clearUserFilter, closeUserOverlay } = require('./_flows/user-management.flow');

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

  test('adminEn promotes moderatorUk to MODERATOR', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.adminEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.adminEn);
    await runNavigateToUsersTabFlow(page, expect);
    await runPromoteUserFlow(page, expect, TEST_USERS.moderatorUk, { role: 'MODERATOR' });
    await runLogoutFlow(page, expect);
  });

  test('adminEn promotes moderatorEn to MODERATOR', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.adminEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.adminEn);
    await runNavigateToUsersTabFlow(page, expect);
    await runPromoteUserFlow(page, expect, TEST_USERS.moderatorEn, { role: 'MODERATOR' });
    await runLogoutFlow(page, expect);
  });

  test('adminEn promotes adminUk to ADMIN', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.adminEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.adminEn);
    await runNavigateToUsersTabFlow(page, expect);
    await runPromoteUserFlow(page, expect, TEST_USERS.adminUk, { role: 'ADMIN' });
    await runLogoutFlow(page, expect);
  });

  // === Section 2: Set UK locales ===

  test('userUk — first login defaults to EN, switches locale to Ukrainian', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.userUk);
    await runSubmitLoginFlow(page, expect, { ...TEST_USERS.userUk, locale: 'en' });
    await runSwitchToUkrainianLoggedInFlow(page, expect);
    await runLogoutFlow(page, expect);
  });

  test('moderatorUk — first login defaults to EN, switches locale to Ukrainian', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.moderatorUk);
    await runSubmitLoginFlow(page, expect, { ...TEST_USERS.moderatorUk, locale: 'en' });
    await runSwitchToUkrainianLoggedInFlow(page, expect);
    await runLogoutFlow(page, expect);
  });

  test('adminEn: user edit visible in profile activity, restore from profile reverts user name', async () => {
    const editedName = `AdminCrossEdit-${Date.now()}`;
    let originalName;

    await runFillLoginFormFlow(page, TEST_USERS.adminEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.adminEn);
    await runNavigateToUsersTabFlow(page, expect);

    await test.step('admin renames userEn', async () => {
      await runFilterUserByEmailFlow(page, TEST_USERS.userEn.email);
      await page.locator('vaadin-button[title="Edit"], vaadin-button[title="Редагувати"]').first().waitFor({ timeout: 5000 });
      await page.locator('vaadin-button[title="Edit"], vaadin-button[title="Редагувати"]').first().click();
      await page.locator('.user-overlay.overlay--visible').waitFor({ timeout: 5000 });
      const nameField = page.locator('.user-overlay vaadin-text-field').first().locator('input');
      await nameField.waitFor({ timeout: 5000 });
      originalName = await nameField.inputValue();
      await nameField.click({ clickCount: 3 });
      await nameField.fill(editedName);
      await page.locator('.user-overlay vaadin-button').filter({ hasText: /Save|Зберегти/i }).click();
      await expect(page.locator('vaadin-notification-container')).toContainText(/updated|оновлено/i, { timeout: 5000 });
      await closeNotification(page);
      await closeUserOverlay(page);
      await clearUserFilter(page);
      await screenshot(page, 'cross-actor-01-user-renamed');
    });

    await test.step('admin edit visible in userEn profile activity', async () => {
      await runFilterUserByEmailFlow(page, TEST_USERS.userEn.email);
      await page.locator('.user-grid-name:visible').first().waitFor({ timeout: 5000 });
      await page.locator('.user-grid-name:visible').first().click();
      await page.locator('.user-overlay.overlay--visible').waitFor({ timeout: 5000 });
      await page.locator('.user-overlay vaadin-button').filter({ hasText: /edit|редагувати/i }).first().click();
      await page.locator('.user-overlay vaadin-combo-box').waitFor({ timeout: 5000 });
      await page.locator('.user-overlay vaadin-tab').filter({ hasText: /activity|активність/i }).click();
      const activityList = page.locator('.user-overlay .entity-activity-list');
      await activityList.waitFor({ timeout: 5000 });
      const latestRow = activityList.locator('.entity-activity-row').nth(0);
      await expect(latestRow.locator('.entity-activity-action')).toContainText(/updated|оновлено/i);
      await expect(latestRow.locator('.entity-activity-changes')).toContainText(editedName);
      await screenshot(page, 'cross-actor-02-profile-activity');
    });

    await test.step('restore from user profile reverts userEn name', async () => {
      const activityList = page.locator('.user-overlay .entity-activity-list');
      await expect(activityList.locator('.entity-activity-restore-btn').first()).toBeVisible({ timeout: 5000 });
      await activityList.locator('.entity-activity-restore-btn').first().click();
      await expect(page.locator('.user-overlay vaadin-button').filter({ hasText: /Save|Зберегти/i })).toBeEnabled({ timeout: 5000 });
      await page.locator('.user-overlay vaadin-button').filter({ hasText: /Save|Зберегти/i }).click();
      await expect(page.locator('vaadin-notification-container')).toContainText(/updated|оновлено/i, { timeout: 5000 });
      await closeNotification(page);
      // Edit → View
      await page.locator('.user-overlay vaadin-button')
        .filter({ has: page.locator('vaadin-icon[icon="vaadin:close"]') })
        .first().click();
      await page.locator('.user-overlay vaadin-button').filter({ hasText: /edit|редагувати/i }).first().waitFor({ state: 'visible', timeout: 5000 });
      await closeUserOverlay(page);
      await screenshot(page, 'cross-actor-03-after-restore');
    });

    await test.step('userEn name reverted in grid', async () => {
      await expect(
        page.locator('.user-grid-name:visible').filter({ hasText: editedName })
      ).toHaveCount(0, { timeout: 5000 });
      await expect(
        page.locator('.user-grid-name:visible').filter({ hasText: originalName })
      ).toHaveCount(1, { timeout: 5000 });
      await clearUserFilter(page);
      await screenshot(page, 'cross-actor-04-name-reverted');
    });

    await runLogoutFlow(page, expect);
  });
});
