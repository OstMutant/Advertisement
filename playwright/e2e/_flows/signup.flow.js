const { screenshot } = require('../_helpers');
const { closeNotification } = require('../_helpers');
const { runFillLoginFormFlow, runSubmitLoginFlow, runLogoutFlow } = require('./auth.flow');

async function runSignUpFlow(page, expect, user, registeredRole = 'USER', afterLoginHook = null, sessionLocale = 'en') {
  await page.locator('vaadin-button').filter({ hasText: /sign up|зареєструватися/i }).first().click();
  await page.locator('[data-testid="signup-name-label"]').waitFor({ timeout: 5000 });
  await screenshot(page, 'signup-dialog-open');

  await page.locator('[data-testid="signup-name-label"] input').fill(user.name);
  await page.locator('[data-testid="signup-email-label"] input').fill(user.email);
  await page.locator('[data-testid="signup-password-label"] input').fill(user.password);
  await screenshot(page, 'signup-form-filled');

  await page.getByRole('button', { name: /sign up|зареєструватися/i }).last().click();
  await expect(page.locator('vaadin-notification-container')).toContainText(
    /User registered successfully|Користувача успішно зареєстровано/i,
    { timeout: 8000 }
  );
  await page.locator('vaadin-dialog-overlay[theme~="signup-dialog"]').waitFor({ state: 'hidden', timeout: 5000 });
  await expect(page.locator('.header-auth-row span').first()).toContainText(/Not signed in|Не увійшли/i);
  await screenshot(page, 'signup-success');
  await closeNotification(page);

  await runFillLoginFormFlow(page, user);
  await runSubmitLoginFlow(page, expect, { ...user, role: registeredRole }, sessionLocale);
  await closeNotification(page);
  if (afterLoginHook) await afterLoginHook();
  await runLogoutFlow(page, expect);
}

module.exports = { runSignUpFlow };
