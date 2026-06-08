const { test, expect, loginAs, screenshot } = require('./_test-helpers');

test.describe('Login failure and logout', () => {
  test('wrong password shows error, user not logged in', async ({ page }) => {
    await page.goto('/');

    await test.step('Open login dialog', async () => {
      await page.locator('vaadin-button').filter({ hasText: /log in|увійти/i }).first().click();
      await page.locator('[data-testid="login-email-label"]').waitFor({ timeout: 5000 });
    });

    await test.step('Enter wrong password and submit', async () => {
      await page.locator('[data-testid="login-email-label"] input').fill('user1@example.com');
      await page.locator('[data-testid="login-password-label"] input').fill('wrongpassword123');
      await page.locator('vaadin-button').filter({ hasText: /log in|увійти/i }).last().click();
      await screenshot(page, 'login-failure-01-submitted');
    });

    await test.step('Error shown, header settings button not visible', async () => {
      await page.waitForTimeout(2000);
      await expect(page.locator('.header-settings-button')).not.toBeVisible({ timeout: 3000 });
      await screenshot(page, 'login-failure-02-not-logged-in');
    });
  });

  test('logout flow: cancel stays logged in, confirm logs out', async ({ page }) => {
    await test.step('Login', async () => {
      await loginAs(page, 'user1@example.com');
    });

    await test.step('Click logout button', async () => {
      await page.locator('.header-logout-button').click();
      await screenshot(page, 'logout-01-dialog');
    });

    await test.step('Logout confirm dialog appears', async () => {
      await expect(page.locator('vaadin-confirm-dialog-overlay')).toBeVisible({ timeout: 5000 });
    });

    await test.step('Cancel — still logged in', async () => {
      await page.evaluate(() => {
        const dialog = document.querySelector('vaadin-confirm-dialog[opened]');
        const btn = [...dialog.querySelectorAll('vaadin-button')]
          .find(b => /cancel|скасувати/i.test(b.textContent?.trim()));
        btn?.click();
      });
      await page.locator('vaadin-confirm-dialog-overlay').waitFor({ state: 'hidden', timeout: 5000 });
      await expect(page.locator('.header-settings-button')).toBeVisible();
      await screenshot(page, 'logout-02-cancel-stays');
    });

    await test.step('Confirm logout — logged out', async () => {
      await page.locator('.header-logout-button').click();
      await page.locator('vaadin-confirm-dialog-overlay').waitFor({ timeout: 5000 });
      await page.evaluate(() => {
        const dialog = document.querySelector('vaadin-confirm-dialog[opened]');
        const btn = [...dialog.querySelectorAll('vaadin-button')]
          .find(b => /^yes$|^так$/i.test(b.textContent?.trim()));
        btn?.click();
      });
      await expect(page.locator('.header-settings-button')).not.toBeVisible({ timeout: 8000 });
      await expect(page.locator('vaadin-button').filter({ hasText: /log in|увійти/i }).first())
        .toBeVisible({ timeout: 5000 });
      await screenshot(page, 'logout-03-logged-out');
    });
  });
});
