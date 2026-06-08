const { test, expect, screenshot } = require('./_test-helpers');
const { runFillLoginFormFlow, runSubmitLoginFlow, runCancelLogoutFlow, runLogoutFlow } = require('./_flows/auth.flow');
const { runSignUpFlow } = require('./_flows/signup.flow');
const { runSwitchToUkrainianFlow, runSwitchToEnglishFlow, runSwitchToUkrainianLoggedInFlow, runSwitchToEnglishLoggedInFlow } = require('./_flows/language-switch.flow');
const { runNavigateToUsersTabFlow, runPromoteUserFlow } = require('./_flows/user-management.flow');
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

  test('register admin EN — first user becomes ADMIN', async () => {
    await runSignUpFlow(page, expect, TEST_USERS.adminEn);
  });

  test('register user EN', async () => {
    await runSignUpFlow(page, expect, TEST_USERS.userEn);
  });

  test('register user UK and moderator UK candidates', async () => {
    await runSwitchToUkrainianFlow(page, expect);
    await runSignUpFlow(page, expect, TEST_USERS.userUk);
    await runSignUpFlow(page, expect, TEST_USERS.moderatorUk);
  });

  test('register moderator EN and admin UK candidates', async () => {
    await runSwitchToEnglishFlow(page, expect);
    await runSignUpFlow(page, expect, TEST_USERS.moderatorEn);
    await runSignUpFlow(page, expect, TEST_USERS.adminUk);
  });

  // === Section 2: Role promotion ===

  test('admin EN promotes moderator and admin accounts', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.adminEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.adminEn);
    await runNavigateToUsersTabFlow(page, expect);
    await runPromoteUserFlow(page, expect, TEST_USERS.moderatorUk.email, { role: 'MODERATOR' });
    await runPromoteUserFlow(page, expect, TEST_USERS.moderatorEn.email, { role: 'MODERATOR' });
    await runPromoteUserFlow(page, expect, TEST_USERS.adminUk.email, { role: 'ADMIN' });
    await runLogoutFlow(page, expect);
  });

  // === Section 3: Set UK locales ===

  test('set UK locale for userUk and moderatorUk', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.userUk);
    await runSubmitLoginFlow(page, expect, { ...TEST_USERS.userUk, locale: 'en' });
    await runSwitchToUkrainianLoggedInFlow(page, expect);
    await runLogoutFlow(page, expect);

    await runFillLoginFormFlow(page, TEST_USERS.moderatorUk);
    await runSubmitLoginFlow(page, expect, { ...TEST_USERS.moderatorUk, locale: 'en' });
    await runSwitchToUkrainianLoggedInFlow(page, expect);
    await runLogoutFlow(page, expect);
  });

  // === Section 4: Authentication tests ===

  test('login — English locale (userEn)', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.userEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.userEn);
  });

  test('cancel logout — stays logged in', async () => {
    await runCancelLogoutFlow(page, expect);
  });

  test('confirm logout — logged out', async () => {
    await runLogoutFlow(page, expect);
  });

  test('login — Ukrainian locale (userUk)', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.userUk);
    await runSubmitLoginFlow(page, expect, TEST_USERS.userUk);
    await runLogoutFlow(page, expect);
  });

  test('login — Admin role (adminEn)', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.adminEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.adminEn);
    await runLogoutFlow(page, expect);
  });

  test('login — Moderator role (moderatorUk)', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.moderatorUk);
    await runSubmitLoginFlow(page, expect, TEST_USERS.moderatorUk);
    await runLogoutFlow(page, expect);
  });

  test('locale persists across sessions', async () => {
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

  test('wrong password — user not logged in', async () => {
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
});
