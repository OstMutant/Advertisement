/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Second spec in the e2e suite -- signs up all six TEST_USERS accounts (adminEn first,
 *   auto-promoted to ADMIN by the app; the rest start as USER), verifying settings/timeline/user
 *   audit activity are created on signup, then exercises the login/logout flow: cancel-logout keeps
 *   the session, logout works, locale choice persists across logout and re-login, a wrong password
 *   is rejected without logging in, and 5 wrong-password attempts trigger the login rate limiter
 *   (6th attempt blocked before credential check, correct password also blocked during lockout).
 *   Per test -- account creation:
 *   - "adminEn signs up -- first user auto-promoted to ADMIN, settings open, timeline and user
 *     audit created": signup -> settings open -> activity (v1 created with defaults) -> timeline
 *     (actor picker visible) -> users tab -> user audit (v1 created).
 *   - "userEn/userUk/moderatorUk/moderatorEn/adminUk signs up -- USER role assigned, settings
 *     open, activity created": signup (switching locale first for the UK-locale accounts) ->
 *     settings -> activity (v1 created).
 *   Per test -- authentication:
 *   - "userEn logs in -- cancel logout keeps session, confirm logout works": login -> cancel
 *     logout dialog -> confirm logout.
 *   - "userEn -- locale persists across logout and re-login": login -> switch to UK -> logout ->
 *     login -> UK locale active -> switch to EN -> logout -> login -> EN locale active.
 *   - "wrong password -- login rejected, user stays logged out": fill wrong password -> submit ->
 *     header settings button not visible.
 * Usage: run via the Playwright test runner -- bash /app/playwright/run.sh
 *   02-marketplace-authentication-flow --ux, or as part of the full suite: bash
 *   /app/playwright/run.sh e2e --ux (depends on spec 01 having run first in the same browser page
 *   sequence, though it does not depend on any state spec 01 leaves behind).
 * Uses: @playwright/test (test, expect) via ./_helpers.
 * Env: None.
 * Input: ./_helpers (test, expect, screenshot, TEST_USERS, closeNotification),
 *   ./_flows/auth.flow (runFillLoginFormFlow, runSubmitLoginFlow, runCancelLogoutFlow,
 *   runLogoutFlow), ./_flows/signup.flow (runSignUpFlow), ./_flows/language-switch.flow
 *   (runSwitchToUkrainianFlow, runSwitchToEnglishFlow, runSwitchToUkrainianLoggedInFlow,
 *   runSwitchToEnglishLoggedInFlow), ./_flows/user-management.flow (runNavigateToUsersTabFlow),
 *   ./_flows/audit.flow (runVerifySettingsAfterSignupFlow, runVerifyUserAuditActivityFlow),
 *   ./_flows/timeline.flow (openTimelineTab, openTimelineFilter, closeTimelineFilter,
 *   assertFeedHasRow, assertActorPickerVisible -- exercised only for adminEn, since the Timeline
 *   tab is ADMIN/MODERATOR only).
 * Outputs: Playwright HTML report entries for each test. Registers all six TEST_USERS accounts
 *   (userEn, userUk, moderatorEn, moderatorUk, adminEn, adminUk) plus a throwaway
 *   "Rate Limit User" account -- every later spec (03 onward) logs in as one of these TEST_USERS
 *   and assumes they already exist. moderatorEn/moderatorUk/adminUk are left at USER role here;
 *   spec 03 promotes them to their final role.
 * Returns: Playwright's own pass/fail exit code convention -- 0 when every test in this file
 *   passes, non-zero otherwise.
 * ──────────────────────────────────────────────────────────────────────────── */
const { test, expect, screenshot, TEST_USERS, closeNotification } = require('./_helpers');
const { runFillLoginFormFlow, runSubmitLoginFlow, runCancelLogoutFlow, runLogoutFlow } = require('./_flows/auth.flow');
const { runSignUpFlow } = require('./_flows/signup.flow');
const { runSwitchToUkrainianFlow, runSwitchToEnglishFlow, runSwitchToUkrainianLoggedInFlow, runSwitchToEnglishLoggedInFlow } = require('./_flows/language-switch.flow');
const { runNavigateToUsersTabFlow } = require('./_flows/user-management.flow');
const { runVerifySettingsAfterSignupFlow, runVerifyUserAuditActivityFlow } = require('./_flows/audit.flow');
const { openTimelineTab, openTimelineFilter, closeTimelineFilter, assertFeedHasRow, assertActorPickerVisible } = require('./_flows/timeline.flow'); // adminEn only — Timeline tab is ADMIN/MOD only

// Local helper — submits the already-open login dialog with the given credentials.
async function submitLogin(page, email, password) {
  await page.locator('[data-testid="login-email-label"] input').fill(email);
  await page.locator('[data-testid="login-password-label"] input').fill(password);
  await page.locator('vaadin-button').filter({ hasText: /log in|увійти/i }).last().click();
}

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

  test('adminEn signs up — first user auto-promoted to ADMIN, settings open, timeline and user audit created', async () => {
    await runSignUpFlow(page, expect, TEST_USERS.adminEn, 'ADMIN', async () => {
      await runVerifySettingsAfterSignupFlow(page, expect, { screenshotName: 'admin-signup-settings', privileged: true });
      await openTimelineTab(page);
      await openTimelineFilter(page);
      await assertActorPickerVisible(page, expect, true);
      await closeTimelineFilter(page);
      await assertFeedHasRow(page, expect, { action: 'created', entityType: 'user', screenshotName: 'admin-signup-timeline' });
      await runNavigateToUsersTabFlow(page, expect);
      await runVerifyUserAuditActivityFlow(page, expect, TEST_USERS.adminEn.email, {
        screenshotName: 'admin-signup-user-audit',
        rows: [{ action: /created/i, version: 'v1', changesIncludes: [TEST_USERS.adminEn.name, TEST_USERS.adminEn.email] }],
      });
    });
  });

  test('userEn signs up — USER role assigned, settings open, activity created', async () => {
    await runSignUpFlow(page, expect, TEST_USERS.userEn, 'USER', async () => {
      await runVerifySettingsAfterSignupFlow(page, expect, { screenshotName: 'useren-signup-settings' });
    });
  });

  test('userUk signs up — USER role assigned, settings open, activity created', async () => {
    await runSwitchToUkrainianFlow(page, expect);
    await runSignUpFlow(page, expect, TEST_USERS.userUk, 'USER', async () => {
      await runVerifySettingsAfterSignupFlow(page, expect, { screenshotName: 'useruk-signup-settings' });
    }, 'uk');
  });

  test('moderatorUk signs up — USER role assigned, activity created', async () => {
    await runSignUpFlow(page, expect, TEST_USERS.moderatorUk, 'USER', async () => {
      await runVerifySettingsAfterSignupFlow(page, expect, { screenshotName: 'moderatoruk-signup-settings' });
    });
  });

  test('moderatorEn signs up — USER role assigned, activity created', async () => {
    await runSwitchToEnglishFlow(page, expect);
    await runSignUpFlow(page, expect, TEST_USERS.moderatorEn, 'USER', async () => {
      await runVerifySettingsAfterSignupFlow(page, expect, { screenshotName: 'moderatoren-signup-settings' });
    });
  });

  test('adminUk signs up — USER role assigned, activity created', async () => {
    await runSignUpFlow(page, expect, TEST_USERS.adminUk, 'USER', async () => {
      await runVerifySettingsAfterSignupFlow(page, expect, { screenshotName: 'adminuk-signup-settings' });
    });
  });

  // === Section 2: Authentication tests ===

  test('userEn logs in — cancel logout keeps session, confirm logout works', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.userEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.userEn);
    await runCancelLogoutFlow(page, expect);
    await runLogoutFlow(page, expect);
  });

  test('userEn — locale persists across logout and re-login', async () => {
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

  test('rateLimitUser exceeds login attempts — 5 wrong passwords rejected, 6th blocked with too-many-attempts message, correct password still blocked during lockout', async () => {
    const rateLimitUser = { name: 'Rate Limit User', email: 'ratelimit.user@example.com', role: 'USER', locale: 'en', password: 'password' };
    await runSignUpFlow(page, expect, rateLimitUser, 'USER');

    await page.locator('vaadin-button').filter({ hasText: /log in|увійти/i }).first().click();
    await page.locator('[data-testid="login-email-label"]').waitFor({ timeout: 5000 });

    for (let attempt = 1; attempt <= 5; attempt++) {
      await submitLogin(page, rateLimitUser.email, 'wrongpassword123');
      await expect(page.locator('vaadin-notification-container')).toContainText(
        /Invalid email or password|Невірна пошта або пароль/i,
        { timeout: 5000 }
      );
      await closeNotification(page);
    }
    await screenshot(page, 'login-rate-limit-attempts-exhausted');

    // 6th attempt — limiter now blocks before checking credentials at all
    await submitLogin(page, rateLimitUser.email, 'wrongpassword123');
    await expect(page.locator('vaadin-notification-container')).toContainText(
      /Too many login attempts|Забагато спроб входу/i,
      { timeout: 5000 }
    );
    await closeNotification(page);
    await screenshot(page, 'login-rate-limit-message');

    // Correct password is also blocked while the lockout window is active
    await submitLogin(page, rateLimitUser.email, rateLimitUser.password);
    await expect(page.locator('vaadin-notification-container')).toContainText(
      /Too many login attempts|Забагато спроб входу/i,
      { timeout: 5000 }
    );
    await closeNotification(page);

    await page.keyboard.press('Escape');
    await page.locator('[data-testid="login-email-label"]').waitFor({ state: 'hidden', timeout: 5000 });
  });

});
