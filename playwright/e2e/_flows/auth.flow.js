const { screenshot } = require('../_test-helpers');

async function runFillLoginFormFlow(page, user) {
  await page.locator('vaadin-button').filter({ hasText: /log in|увійти/i }).first().click();
  await page.locator('[data-testid="login-email-label"]').waitFor({ timeout: 5000 });
  await page.locator('[data-testid="login-email-label"] input').fill(user.email);
  await page.locator('[data-testid="login-password-label"] input').fill(user.password);
  await screenshot(page, 'auth-fill-credentials-entered');
}

const TAB_LABELS = {
  en: { advertisements: 'Advertisements', users: 'Users' },
  uk: { advertisements: 'Оголошення',     users: 'Користувачі' },
};

async function runSubmitLoginFlow(page, expect, user) {
  await page.locator('vaadin-button').filter({ hasText: /log in|увійти/i }).last().click();
  await expect(page.locator('.header-settings-button')).toBeVisible({ timeout: 8000 });

  const labels = TAB_LABELS[user.locale];
  await expect(page.locator('vaadin-tab').filter({ hasText: labels.advertisements }).first()).toBeVisible();

  if (user.role === 'MODERATOR' || user.role === 'ADMIN') {
    await expect(page.locator('vaadin-tab').filter({ hasText: labels.users }).first()).toBeVisible();
  } else {
    await expect(page.locator('vaadin-tab').filter({ hasText: labels.users }).first()).not.toBeVisible();
  }

  await screenshot(page, 'auth-submit-logged-in');
}

async function runCancelLogoutFlow(page, expect) {
  await page.locator('.header-logout-button').click();
  await page.getByRole('button', { name: /cancel|скасувати/i }).waitFor({ timeout: 5000 });
  await screenshot(page, 'auth-logout-cancel-dialog');

  await page.getByRole('button', { name: /cancel|скасувати/i }).click();
  await page.locator('vaadin-confirm-dialog-overlay').waitFor({ state: 'hidden', timeout: 5000 });
  await expect(page.locator('.header-settings-button')).toBeVisible();
  await screenshot(page, 'auth-logout-cancel-stays-logged-in');
}

async function runLogoutFlow(page, expect) {
  await page.locator('.header-logout-button').click();
  await page.getByRole('button', { name: /^yes$|^так$/i }).waitFor({ timeout: 5000 });
  await screenshot(page, 'auth-logout-confirm-dialog');

  await page.getByRole('button', { name: /^yes$|^так$/i }).click();

  await expect(page.locator('.header-settings-button')).not.toBeVisible({ timeout: 8000 });
  await expect(page.locator('vaadin-button').filter({ hasText: /log in|увійти/i }).first()).toBeVisible({ timeout: 5000 });
  await screenshot(page, 'auth-logout-logged-out');
}

module.exports = { runFillLoginFormFlow, runSubmitLoginFlow, runCancelLogoutFlow, runLogoutFlow };
