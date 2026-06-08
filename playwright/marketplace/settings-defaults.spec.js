const { test, expect, loginAs, waitForOverlay, waitForOverlayClosed, closeOverlay,
        openSettings, openActivityTab, confirmDialog, screenshot } = require('./_test-helpers');

async function signUpAndLogin(page, email, password = 'password123') {
  await page.goto('/');
  await page.locator('.header-signup-button').click();
  await page.locator('[data-testid="signup-name-label"] input').waitFor({ timeout: 5000 });

  await page.locator('[data-testid="signup-name-label"] input').fill('Test User');
  await page.locator('[data-testid="signup-email-label"] input').fill(email);
  await page.locator('[data-testid="signup-password-label"] input').fill(password);

  await page.locator('vaadin-button').filter({ hasText: /sign up|зареєструватися/i }).last().click();
  await page.locator('vaadin-notification').waitFor({ timeout: 5000 }).catch(() => {});

  await loginAs(page, email, password);
}

async function getAdsPageSizeValue(page) {
  const val = await page
    .locator('.settings-overlay-content vaadin-integer-field')
    .first()
    .locator('input')
    .inputValue();
  return parseInt(val, 10);
}

test.describe('Settings defaults', () => {

  test('new user can restore settings to defaults after first change', async ({ page }) => {
    const email = `newuser-${Date.now()}@example.com`;
    await signUpAndLogin(page, email);

    await openSettings(page);
    const defaultSize = await getAdsPageSizeValue(page);
    expect(defaultSize).toBe(20);

    const newSize = 10;
    const sizeInput = () =>
      page.locator('.settings-overlay-content vaadin-integer-field').first().locator('input');

    await sizeInput().click({ clickCount: 3 });
    await sizeInput().fill(String(newSize));
    await page.locator('.base-overlay.overlay--visible vaadin-button')
      .filter({ hasText: /зберегти|save/i }).click();
    await page.waitForLoadState('networkidle');
    await screenshot(page, 'settings-defaults-01-changed');

    await closeOverlay(page);
    await openSettings(page);
    await openActivityTab(page);

    await test.step('Restore button is visible after first change', async () => {
      await expect(
        page.locator('.activity-feed-list .entity-activity-restore-btn').first()
      ).toBeVisible({ timeout: 5000 });
    });
    await screenshot(page, 'settings-defaults-02-restore-visible');

    await page.locator('.activity-feed-list .entity-activity-restore-btn').first().click();
    await confirmDialog(page, 'Оновити|Update');
    await page.waitForLoadState('networkidle');

    await page.locator('.settings-overlay-content vaadin-integer-field').first().waitFor({ timeout: 5000 });

    await test.step('adsPageSize restored to default (20)', async () => {
      const restoredSize = await getAdsPageSizeValue(page);
      expect(restoredSize).toBe(defaultSize);
    });
    await screenshot(page, 'settings-defaults-03-restored');
  });

});
