const { expect } = require('@playwright/test');

// Opens settings overlay, sets both page size fields, and saves.
// Leaves the overlay open on the settings tab after save.
async function changePageSizes(page, adsSize, usersSize) {
  await page.locator('.header-settings-button').click();
  await page.locator('.base-overlay.overlay--visible').waitFor({ timeout: 5000 });
  const adsInput   = page.locator('.settings-overlay-content vaadin-integer-field').nth(0).locator('input');
  const usersInput = page.locator('.settings-overlay-content vaadin-integer-field').nth(1).locator('input');
  await adsInput.click({ clickCount: 3 });
  await adsInput.fill(String(adsSize));
  await usersInput.click({ clickCount: 3 });
  await usersInput.fill(String(usersSize));
  await page.locator('.base-overlay.overlay--visible vaadin-button')
    .filter({ hasText: /зберегти|save/i }).click();
}

// Inside an open settings overlay with the activity tab already shown,
// clicks the first restore button, waits for Save to become enabled, and saves.
// After save the overlay resets to the settings tab (afterSave behaviour).
async function restoreLatestFromActivity(page) {
  await page.locator('.entity-activity-list .entity-activity-restore-btn').first().click();
  await expect(
    page.locator('.base-overlay.overlay--visible vaadin-button').filter({ hasText: /зберегти|save/i })
  ).toBeEnabled({ timeout: 5000 });
  await page.locator('.base-overlay.overlay--visible vaadin-button')
    .filter({ hasText: /зберегти|save/i }).click();
}

// Opens settings overlay, reads both page size fields, closes overlay,
// and returns { adsPageSize, usersPageSize } as integers.
async function getPageSizes(page) {
  await page.locator('.header-settings-button').click();
  await page.locator('.base-overlay.overlay--visible').waitFor({ timeout: 5000 });
  const adsPageSize   = parseInt(await page.locator('.settings-overlay-content vaadin-integer-field').nth(0).locator('input').inputValue(), 10);
  const usersPageSize = parseInt(await page.locator('.settings-overlay-content vaadin-integer-field').nth(1).locator('input').inputValue(), 10);
  await page.locator('.overlay__breadcrumb-back').click();
  await page.locator('.base-overlay.overlay--visible').waitFor({ state: 'hidden', timeout: 5000 });
  return { adsPageSize, usersPageSize };
}

module.exports = { changePageSizes, restoreLatestFromActivity, getPageSizes };
