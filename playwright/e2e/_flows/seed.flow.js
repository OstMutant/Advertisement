const { closeNotification } = require('../_helpers');

async function signUpBulk(page, { name, email, password }) {
  await page.locator('vaadin-button').filter({ hasText: /sign up/i }).first().click();
  await page.locator('[data-testid="signup-name-label"]').waitFor({ timeout: 5000 });
  await page.locator('[data-testid="signup-name-label"] input').fill(name);
  await page.locator('[data-testid="signup-email-label"] input').fill(email);
  await page.locator('[data-testid="signup-password-label"] input').fill(password);
  await page.getByRole('button', { name: /sign up/i }).last().click();
  await page.locator('vaadin-dialog-overlay[theme~="signup-dialog"]').waitFor({ state: 'hidden', timeout: 8000 });
  await closeNotification(page);
}

async function loginBulk(page, { email, password }) {
  await page.locator('vaadin-button').filter({ hasText: /log in/i }).first().click();
  await page.locator('[data-testid="login-email-label"]').waitFor({ timeout: 5000 });
  await page.locator('[data-testid="login-email-label"] input').fill(email);
  await page.locator('[data-testid="login-password-label"] input').fill(password);
  await page.locator('vaadin-button').filter({ hasText: /log in/i }).last().click();
  await page.locator('.header-settings-button').waitFor({ timeout: 15000 });
}

async function logoutBulk(page) {
  await page.locator('.header-logout-button').click();
  await page.locator('vaadin-confirm-dialog-overlay[opened]:not([opening])').waitFor({ state: 'attached', timeout: 5000 });
  await page.getByRole('button', { name: /^yes$|^так$/i }).click();
  await page.locator('vaadin-button').filter({ hasText: /log in/i }).first().waitFor({ timeout: 5000 });
}

async function createAdvertisementBulk(page, { title, description }) {
  await page.locator('.add-advertisement-button').click();
  const overlay = page.locator('.advertisement-overlay');
  await overlay.waitFor({ timeout: 5000 });
  await overlay.locator('[data-testid="advertisement-overlay-field-title"] input').fill(title);
  await overlay.locator('[data-testid="advertisement-overlay-field-description"] textarea').fill(description);
  await overlay.locator('vaadin-button').filter({ hasText: /save|зберегти/i }).click();
  await page.locator('.base-overlay.overlay--visible').waitFor({ state: 'hidden', timeout: 10000 });
}

module.exports = { signUpBulk, loginBulk, logoutBulk, createAdvertisementBulk };
