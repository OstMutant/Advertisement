/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Authentication e2e coverage -- sign-up through the UI (English and Ukrainian
 *   variants), login/logout, locale persistence across re-login, a rejected wrong password and the
 *   login rate limiter. Every test is independent and uses its own freshly created account.
 *   Per test -- account creation:
 *   - "new user signs up in English -- USER role assigned, settings open, activity created": sign
 *     up a unique e-mail -> login as USER -> settings defaults -> activity (v1 created) -> logout.
 *   - "new user signs up in Ukrainian -- USER role assigned, settings open, activity created":
 *     switch to Ukrainian -> sign up a unique e-mail -> login as USER with Ukrainian session
 *     locale -> settings defaults -> activity (v1 created) -> logout.
 *   Per test -- authentication:
 *   - "user logs in -- cancel logout keeps session, confirm logout works": login -> cancel
 *     logout dialog -> confirm logout.
 *   - "user -- locale persists across logout and re-login": login -> switch to UK -> logout ->
 *     login -> UK locale active -> switch to EN -> logout -> login -> EN locale active -> logout.
 *   - "wrong password -- login rejected with an error, user stays logged out": fill wrong password
 *     -> submit -> invalid-credentials notification -> header settings button not visible.
 *   - "new user exceeds login attempts -- 5 wrong passwords rejected, 6th blocked with
 *     too-many-attempts message, correct password still blocked during lockout": sign up a unique
 *     e-mail -> 5 rejected attempts -> 6th blocked -> correct password blocked.
 *   Per test -- API key self-service:
 *   - "user reviews and revokes own API keys": open the account API keys tab (empty state) -> two
 *     REST-issued keys listed by label and prefix, never the key value -> cancel keeps the key ->
 *     confirm revokes it -> revoked key gets 401 on REST, other key still 200 -> show-revoked toggle.
 * Usage: run via the Playwright test runner -- bash /app/playwright/run.sh
 *   02-marketplace-authentication-flow --ux (runs alone or with any other spec), or as part of the
 *   full suite: bash /app/playwright/run.sh e2e --ux.
 * Uses: @playwright/test.
 * Env: None.
 * Input: ./_fixtures (test, expect, createUser, unique), ./_helpers (screenshot,
 *   closeNotification), ./_flows/auth.flow (runFillLoginFormFlow, runSubmitLoginFlow,
 *   runCancelLogoutFlow, runLogoutFlow), ./_flows/signup.flow (runSignUpFlow),
 *   ./_flows/language-switch.flow (runSwitchToUkrainianFlow, runSwitchToUkrainianLoggedInFlow,
 *   runSwitchToEnglishLoggedInFlow), ./_flows/audit.flow (runVerifySettingsAfterSignupFlow), ./_flows/seed.flow (issueApiKeyViaApi).
 * Outputs: Playwright HTML report entries for each test. Creates only its own uniquely named
 *   accounts (signed up through the UI or created through the REST API) and the API keys issued
 *   for them, no other data.
 * Returns: Playwright's own pass/fail exit code convention -- 0 when every test in this file
 *   passes, non-zero otherwise.
 * ──────────────────────────────────────────────────────────────────────────── */
const { test, expect } = require('./_fixtures');
const { screenshot, closeNotification } = require('./_helpers');
const { runFillLoginFormFlow, runSubmitLoginFlow, runCancelLogoutFlow, runLogoutFlow } = require('./_flows/auth.flow');
const { runSignUpFlow } = require('./_flows/signup.flow');
const { runSwitchToUkrainianFlow, runSwitchToUkrainianLoggedInFlow, runSwitchToEnglishLoggedInFlow } = require('./_flows/language-switch.flow');
const { runVerifySettingsAfterSignupFlow } = require('./_flows/audit.flow');
const { issueApiKeyViaApi } = require('./_flows/seed.flow');

const SETTINGS_BUTTON = /^(settings|налаштування)$/i;
const INVALID_CREDENTIALS = /Invalid email or password|Невірна пошта або пароль/i;
const TOO_MANY_ATTEMPTS = /Too many login attempts|Забагато спроб входу/i;

// Builds a sign-up account whose name and lower-case e-mail both derive from one unique value.
function newSignUpAccount(unique, prefix) {
  const name = unique(prefix);
  const email = `${name.toLowerCase().replace(/\s+/g, '.')}@example.com`;
  return { name, email, password: 'password', role: 'USER', locale: 'en' };
}

// Opens the login dialog from the signed-out header.
async function openLoginDialog(page) {
  await page.getByRole('button', { name: /log in|увійти/i }).first().click();
  await page.getByTestId('login-email-label').waitFor({ timeout: 5000 });
}

// Submits the already-open login dialog with the given credentials.
async function submitLogin(page, email, password) {
  await page.getByTestId('login-email-label').getByRole('textbox').fill(email);
  await page.getByTestId('login-password-label').locator('input').fill(password);
  await page.getByRole('button', { name: /log in|увійти/i }).last().click();
}

test.describe('Authentication flow', () => {
  test.describe.configure({ timeout: 90000 });

  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  // === Section 1: Account creation ===

  test('new user signs up in English — USER role assigned, settings open, activity created', async ({ page, unique }) => {
    const user = newSignUpAccount(unique, 'Signup En');

    await runSignUpFlow(page, expect, user, 'USER', async () => {
      await runVerifySettingsAfterSignupFlow(page, expect, { screenshotName: 'signup-en-settings' });
    });
  });

  test('new user signs up in Ukrainian — USER role assigned, settings open, activity created', async ({ page, unique }) => {
    const user = newSignUpAccount(unique, 'Signup Uk');

    await runSwitchToUkrainianFlow(page, expect);
    await runSignUpFlow(page, expect, user, 'USER', async () => {
      await runVerifySettingsAfterSignupFlow(page, expect, { screenshotName: 'signup-uk-settings' });
    }, 'uk');
  });

  // === Section 2: Authentication tests ===

  test('user logs in — cancel logout keeps session, confirm logout works', async ({ page, createUser }) => {
    const user = await createUser();

    await runFillLoginFormFlow(page, user);
    await runSubmitLoginFlow(page, expect, user);
    await runCancelLogoutFlow(page, expect);
    await runLogoutFlow(page, expect);
  });

  test('user — locale persists across logout and re-login', async ({ page, createUser }) => {
    const user = await createUser();

    await test.step('login, switch to Ukrainian, logout', async () => {
      await runFillLoginFormFlow(page, user);
      await runSubmitLoginFlow(page, expect, user);
      await runSwitchToUkrainianLoggedInFlow(page, expect);
      await runLogoutFlow(page, expect);
    });

    await test.step('re-login keeps Ukrainian, switch to English, logout', async () => {
      await runFillLoginFormFlow(page, user);
      await runSubmitLoginFlow(page, expect, { ...user, locale: 'uk' });
      await screenshot(page, 'auth-locale-persist-uk-after-relogin');
      await runSwitchToEnglishLoggedInFlow(page, expect);
      await runLogoutFlow(page, expect);
    });

    await test.step('re-login keeps English, logout', async () => {
      await runFillLoginFormFlow(page, user);
      await runSubmitLoginFlow(page, expect, user);
      await screenshot(page, 'auth-locale-persist-en-after-relogin');
      await runLogoutFlow(page, expect);
    });
  });

  test('wrong password — login rejected with an error, user stays logged out', async ({ page, createUser }) => {
    const user = await createUser();

    await openLoginDialog(page);
    await submitLogin(page, user.email, 'wrongpassword123');
    await screenshot(page, 'auth-failure-submitted');

    await expect(page.locator('vaadin-notification-container')).toContainText(INVALID_CREDENTIALS, { timeout: 5000 });
    await expect(page.getByRole('button', { name: SETTINGS_BUTTON })).not.toBeVisible();
    await screenshot(page, 'auth-failure-not-logged-in');
    await closeNotification(page);

    await page.keyboard.press('Escape');
    await page.getByTestId('login-email-label').waitFor({ state: 'hidden', timeout: 5000 });
  });

  test('new user exceeds login attempts — 5 wrong passwords rejected, 6th blocked with too-many-attempts message, correct password still blocked during lockout', async ({ page, unique }) => {
    const rateLimitUser = newSignUpAccount(unique, 'Rate Limit User');
    await runSignUpFlow(page, expect, rateLimitUser, 'USER');

    await openLoginDialog(page);

    for (let attempt = 1; attempt <= 5; attempt++) {
      await submitLogin(page, rateLimitUser.email, 'wrongpassword123');
      await expect(page.locator('vaadin-notification-container')).toContainText(INVALID_CREDENTIALS, { timeout: 5000 });
      await closeNotification(page);
    }
    await screenshot(page, 'login-rate-limit-attempts-exhausted');

    // 6th attempt — limiter now blocks before checking credentials at all
    await submitLogin(page, rateLimitUser.email, 'wrongpassword123');
    await expect(page.locator('vaadin-notification-container')).toContainText(TOO_MANY_ATTEMPTS, { timeout: 5000 });
    await closeNotification(page);
    await screenshot(page, 'login-rate-limit-message');

    // Correct password is also blocked while the lockout window is active
    await submitLogin(page, rateLimitUser.email, rateLimitUser.password);
    await expect(page.locator('vaadin-notification-container')).toContainText(TOO_MANY_ATTEMPTS, { timeout: 5000 });
    await closeNotification(page);

    await page.keyboard.press('Escape');
    await page.getByTestId('login-email-label').waitFor({ state: 'hidden', timeout: 5000 });
  });

  // === Section 3: API key self-service ===

  test('user reviews and revokes own API keys — empty state, two REST-issued keys listed by label and prefix with no key value, cancel keeps the key, confirm revokes it, revoked key gets 401 on REST, show-revoked toggle lists it as revoked, other key still works', async ({ pageAs, createUser, request, unique }) => {
    const user = await createUser();
    const page = await pageAs(user);
    const grid = page.getByRole('treegrid');
    const labelA = unique('laptop');
    const labelB = unique('ci');
    const dialog = page.getByRole('dialog', { name: 'Revoke API key?' });
    let rawA;
    let rawB;

    await test.step('empty state before any key is issued', async () => {
      await page.getByRole('button', { name: SETTINGS_BUTTON }).click();
      await expect(page.getByRole('tab', { name: 'API keys' })).toBeVisible({ timeout: 5000 });
      await page.getByRole('tab', { name: 'API keys' }).click();
      await expect(page.getByText('No API keys to show.')).toBeVisible();
    });

    await test.step('two REST-issued keys are listed by label and prefix, never by value', async () => {
      rawA = await issueApiKeyViaApi(request, user, labelA);
      rawB = await issueApiKeyViaApi(request, user, labelB);

      await page.getByRole('tab', { name: /^settings$/i }).click();
      await page.getByRole('tab', { name: 'API keys' }).click();
      await expect(grid.getByRole('gridcell', { name: labelA, exact: true })).toBeVisible();
      await expect(grid.getByRole('gridcell', { name: labelB, exact: true })).toBeVisible();
      await expect(grid.getByRole('gridcell', { name: rawA.slice(0, 10) + '...' })).toBeVisible();
      await expect(page.getByText(rawA)).toHaveCount(0);
      await screenshot(page, 'api-keys-listed');
    });

    await test.step('cancel in the confirm dialog keeps the key', async () => {
      await page.getByRole('button', { name: 'Revoke key ' + labelA }).click();
      await expect(dialog.getByRole('heading', { name: 'Revoke API key?' })).toBeVisible();
      await expect(dialog).toContainText(labelA);
      await dialog.getByRole('button', { name: 'Cancel' }).click();
      await dialog.waitFor({ state: 'hidden', timeout: 5000 });
      await expect(grid.getByRole('gridcell', { name: labelA, exact: true })).toBeVisible();
    });

    await test.step('confirm revokes the key and hides it from the default list', async () => {
      await page.getByRole('button', { name: 'Revoke key ' + labelA }).click();
      await dialog.getByRole('button', { name: 'Revoke key', exact: true }).click();
      await expect(page.getByText('API key revoked')).toBeVisible({ timeout: 5000 });
      await closeNotification(page);
      await expect(grid.getByRole('gridcell', { name: labelA, exact: true })).toHaveCount(0);
      await expect(grid.getByRole('gridcell', { name: labelB, exact: true })).toBeVisible();
    });

    await test.step('REST rejects the revoked key and still accepts the other one', async () => {
      const revoked = await request.get('/api/api-keys', { headers: { Authorization: 'Bearer ' + rawA } });
      expect(revoked.status()).toBe(401);
      const active = await request.get('/api/api-keys', { headers: { Authorization: 'Bearer ' + rawB } });
      expect(active.status()).toBe(200);
    });

    await test.step('show-revoked toggle lists the revoked key with its status', async () => {
      await page.getByRole('checkbox', { name: 'Show revoked keys' }).check();
      await expect(grid.getByRole('gridcell', { name: labelA, exact: true })).toBeVisible();
      await expect(grid.getByRole('gridcell', { name: /^Revoked / })).toBeVisible();
      await screenshot(page, 'api-keys-show-revoked');
    });
  });

});
