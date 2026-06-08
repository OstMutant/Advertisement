const { screenshot } = require('../_test-helpers');

async function runSignUpFlow(page, expect, user) {
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
  await screenshot(page, 'signup-success');
}

module.exports = { runSignUpFlow };
