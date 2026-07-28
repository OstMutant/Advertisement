const { expect } = require('@playwright/test');

// Opens settings overlay, sets both page size fields, and saves.
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

// Opens the Settings history overlay (a nested overlay stacked on top of the already-open
// Settings overlay, not a tab) via the header history icon. Requires Settings to be open already.
async function openHistory(page) {
  await page.locator('.settings-history-button').click();
  await page.locator('.settings-activity-overlay.overlay--visible').waitFor({ timeout: 5000 });
}

// Closes the history overlay. Breadcrumb chain is Home / Settings / Activity(current); X behaves
// like the "Settings" link -- both go back to the screen history was opened from, not Home.
// `via: 'settings'` (default) and `via: 'x'` land back on Settings (never actually closed
// underneath). `via: 'home'` is the one path that exits all the way out. No path changes any data
// (read-only panel).
async function closeHistory(page, via = 'settings') {
  const selector = {
    x: '.settings-activity-close-button',
    settings: '.settings-activity-breadcrumb-settings',
    home: '.settings-activity-breadcrumb-home',
  }[via];
  await page.locator(selector).click();
  await page.locator('.settings-activity-overlay.overlay--visible').waitFor({ state: 'hidden', timeout: 5000 });
  if (via === 'home') {
    await page.locator('.base-overlay.overlay--visible').waitFor({ state: 'hidden', timeout: 5000 });
  }
}

// Inside an open history overlay, clicks the first restore button. Restoring stages the values
// into the Settings form and closes the history overlay by itself (no separate close() call
// needed) -- this then explicitly saves the staged values, same two-step contract as before.
async function restoreLatestFromActivity(page) {
  await page.locator('.settings-activity-overlay .entity-activity-list .entity-activity-restore-btn').first().click();
  await page.locator('.settings-activity-overlay.overlay--visible').waitFor({ state: 'hidden', timeout: 5000 });
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
  await page.locator('.base-overlay.overlay--visible .overlay__breadcrumb-back').click();
  await page.locator('.base-overlay.overlay--visible').waitFor({ state: 'hidden', timeout: 5000 });
  return { adsPageSize, usersPageSize };
}

module.exports = { changePageSizes, openHistory, closeHistory, restoreLatestFromActivity, getPageSizes };
