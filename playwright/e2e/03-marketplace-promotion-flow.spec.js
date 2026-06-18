const { test, expect, TEST_USERS } = require('./_helpers');
const { runFillLoginFormFlow, runSubmitLoginFlow, runLogoutFlow } = require('./_flows/auth.flow');
const { runSwitchToUkrainianLoggedInFlow } = require('./_flows/language-switch.flow');
const { runNavigateToUsersTabFlow, runPromoteUserFlow } = require('./_flows/user-management.flow');

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
});
