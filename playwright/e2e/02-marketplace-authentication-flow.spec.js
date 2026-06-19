const { test, expect, screenshot } = require('./_helpers');
const { runFillLoginFormFlow, runSubmitLoginFlow, runCancelLogoutFlow, runLogoutFlow } = require('./_flows/auth.flow');
const { runSignUpFlow } = require('./_flows/signup.flow');
const { runSwitchToUkrainianFlow, runSwitchToEnglishFlow, runSwitchToUkrainianLoggedInFlow, runSwitchToEnglishLoggedInFlow } = require('./_flows/language-switch.flow');
const { runNavigateToUsersTabFlow } = require('./_flows/user-management.flow');
const { runVerifySettingsAfterSignupFlow, runVerifyUserAuditActivityFlow } = require('./_flows/audit.flow');
const { TEST_USERS } = require('./_helpers');

test.describe.configure({ mode: 'serial' });

test.describe('Authentication flow', () => {
  let page;

  test.beforeAll(async ({ browser }) => {
    page = await browser.newPage();
    await page.goto('/');
  });

  test.afterAll(async () => {
    await page.close();
  });

  // === Section 1: Account creation ===

  test('adminEn signs up — first user is auto-promoted to ADMIN', async () => {
    await runSignUpFlow(page, expect, TEST_USERS.adminEn, 'ADMIN', async () => {
      await runVerifySettingsAfterSignupFlow(page, expect, { screenshotName: 'admin-signup-settings' });
      await runNavigateToUsersTabFlow(page, expect);
      await runVerifyUserAuditActivityFlow(page, expect, TEST_USERS.adminEn.email, {
        screenshotName: 'admin-signup-user-audit',
        rows: [{ action: /created/i, version: 'v1', changesIncludes: [TEST_USERS.adminEn.name, TEST_USERS.adminEn.email] }],
      });
    });
  });

  test('userEn signs up — gets USER role', async () => {
    await runSignUpFlow(page, expect, TEST_USERS.userEn, 'USER', async () => {
      await runVerifySettingsAfterSignupFlow(page, expect, { screenshotName: 'useren-signup-settings' });
    });
  });

  test('userUk signs up — gets USER role', async () => {
    await runSwitchToUkrainianFlow(page, expect);
    await runSignUpFlow(page, expect, TEST_USERS.userUk, 'USER', async () => {
      await runVerifySettingsAfterSignupFlow(page, expect, { screenshotName: 'useruk-signup-settings' });
    }, 'uk');
  });

  test('moderatorUk signs up — MODERATOR candidate', async () => {
    await runSignUpFlow(page, expect, TEST_USERS.moderatorUk, 'USER', async () => {
      await runVerifySettingsAfterSignupFlow(page, expect, { screenshotName: 'moderatoruk-signup-settings' });
    });
  });

  test('moderatorEn signs up — MODERATOR candidate', async () => {
    await runSwitchToEnglishFlow(page, expect);
    await runSignUpFlow(page, expect, TEST_USERS.moderatorEn, 'USER', async () => {
      await runVerifySettingsAfterSignupFlow(page, expect, { screenshotName: 'moderatoren-signup-settings' });
    });
  });

  test('adminUk signs up — ADMIN candidate', async () => {
    await runSignUpFlow(page, expect, TEST_USERS.adminUk, 'USER', async () => {
      await runVerifySettingsAfterSignupFlow(page, expect, { screenshotName: 'adminuk-signup-settings' });
    });
  });

  // === Section 2: Authentication tests ===

  test('userEn logs in, cancels logout — remains authenticated', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.userEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.userEn);
    await runCancelLogoutFlow(page, expect);
    await runLogoutFlow(page, expect);
  });

  test('userEn — switched locale persists across logout and re-login', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.userEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.userEn);
    await runSwitchToUkrainianLoggedInFlow(page, expect);
    await runLogoutFlow(page, expect);

    await runFillLoginFormFlow(page, TEST_USERS.userEn);
    await runSubmitLoginFlow(page, expect, { ...TEST_USERS.userEn, locale: 'uk' });
    await screenshot(page, 'auth-locale-persist-uk-after-relogin');
    await runSwitchToEnglishLoggedInFlow(page, expect);
    await runLogoutFlow(page, expect);

    await runFillLoginFormFlow(page, TEST_USERS.userEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.userEn);
    await screenshot(page, 'auth-locale-persist-en-after-relogin');
    await runLogoutFlow(page, expect);
  });

  test('wrong password — login rejected, user stays logged out', async () => {
    await page.locator('vaadin-button').filter({ hasText: /log in|увійти/i }).first().click();
    await page.locator('[data-testid="login-email-label"]').waitFor({ timeout: 5000 });

    await page.locator('[data-testid="login-email-label"] input').fill(TEST_USERS.userEn.email);
    await page.locator('[data-testid="login-password-label"] input').fill('wrongpassword123');
    await page.locator('vaadin-button').filter({ hasText: /log in|увійти/i }).last().click();
    await screenshot(page, 'auth-failure-submitted');

    await page.waitForTimeout(2000);
    await expect(page.locator('.header-settings-button')).not.toBeVisible({ timeout: 3000 });
    await screenshot(page, 'auth-failure-not-logged-in');

    await page.keyboard.press('Escape');
    await page.locator('[data-testid="login-email-label"]').waitFor({ state: 'hidden', timeout: 5000 });
  });

  test('adminEn: settings activity tracks change, both fields shown, restore reverts, save returns to form', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.adminEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.adminEn);

    await page.locator('.header-settings-button').click();
    const settingsOverlay = page.locator('.settings-overlay');
    await settingsOverlay.waitFor({ timeout: 5000 });

    const sizeInput  = () => settingsOverlay.locator('.settings-overlay-content vaadin-integer-field').first().locator('input');
    const saveBtn    = () => settingsOverlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).first();
    const activityList = settingsOverlay.locator('.entity-activity-list');

    const originalSize = parseInt(await sizeInput().inputValue(), 10);
    const changedSize  = originalSize === 10 ? 15 : 10;

    await test.step('change page size and confirm audit entry with diff and restore button', async () => {
      await sizeInput().click({ clickCount: 3 });
      await sizeInput().fill(String(changedSize));
      await saveBtn().click();
      await page.waitForLoadState('networkidle');

      // Switch to activity tab to confirm the entry was committed before continuing
      await settingsOverlay.locator('vaadin-tab').filter({ hasText: /activity|активність/i }).click();
      await activityList.waitFor({ timeout: 5000 });
      await expect(activityList.locator('.entity-activity-row').first()).toBeVisible();
      await expect(activityList).toContainText('→');
      await expect(activityList.locator('.entity-activity-restore-btn').first()).toBeVisible();
      await screenshot(page, 'settings-activity-list');
    });

    await test.step('both settings fields shown even when only one changed', async () => {
      const firstRow = activityList.locator('.entity-activity-row').first();
      await expect(firstRow.locator('.entity-activity-changes-item').filter({ hasText: /adsPageSize|Оголошень/i }).first()).toBeVisible();
      await expect(firstRow.locator('.entity-activity-changes-item').filter({ hasText: /usersPageSize|Користувач/i }).first()).toBeVisible();
    });

    await test.step('restore reverts size to snapshot value', async () => {
      await activityList.locator('.entity-activity-restore-btn').first().click();
      await expect(saveBtn()).toBeEnabled({ timeout: 5000 });
      await saveBtn().click();
      await page.waitForLoadState('networkidle');
      await settingsOverlay.locator('.settings-overlay-content vaadin-integer-field').first().waitFor({ timeout: 5000 });
      const restoredSize = parseInt(await sizeInput().inputValue(), 10);
      expect(restoredSize).toBe(originalSize);
      await screenshot(page, 'settings-after-restore');
    });

    await test.step('save while on activity tab returns to settings form', async () => {
      await sizeInput().click({ clickCount: 3 });
      await sizeInput().fill(String(changedSize));
      await settingsOverlay.locator('vaadin-tab').filter({ hasText: /activity|активність/i }).click();
      await activityList.waitFor({ timeout: 5000 });
      await saveBtn().click();
      await page.waitForLoadState('networkidle');
      await expect(settingsOverlay.locator('.overlay__form-fields-card')).toBeVisible();
      await expect(activityList).toBeHidden();
      await screenshot(page, 'settings-tab-switch-after-save');
    });

    await settingsOverlay.locator('vaadin-button')
      .filter({ has: page.locator('vaadin-icon[icon="vaadin:close"]') })
      .first().click();
    await settingsOverlay.waitFor({ state: 'hidden', timeout: 5000 });

    await runLogoutFlow(page, expect);
  });
});
