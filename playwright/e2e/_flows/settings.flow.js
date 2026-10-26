/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Flow helpers for the Settings overlay -- changing/reading pagination page sizes
 *   with optional moderation-queue-size parameter, and a thin Settings-specific wrapper around
 *   the shared entity-activity history overlay helpers, and a by-label locator for each page size field.
 * Usage: None -- a library only, required by spec files (see Input).
 * Uses: @playwright/test (expect), ./entity-activity.flow (openEntityActivity, closeEntityActivity,
 *   restoreFromEntityActivity).
 * Env: None.
 * Input: required directly by 06-seed-filter-sort-pagination.spec.js and 04-provider-profile-
 *   flow.spec.js, and indirectly by every spec that goes through ./audit.flow.
 * Outputs: exports changePageSizes, openHistory, closeHistory, restoreLatestFromActivity, getPageSizes,
 *   pageSizeField.
 * Returns: N/A
 * ──────────────────────────────────────────────────────────────────────────── */
const { expect } = require('@playwright/test');
const { openEntityActivity, closeEntityActivity, restoreFromEntityActivity } = require('./entity-activity.flow');

const PAGE_SIZE_LABELS = {
  ads:              /ads per page|оголошень на сторінці/i,
  providerProfiles: /provider profiles per page|постачальників на сторінку/i,
  feedback:         /feedback entries per page|відгуків на сторінці/i,
  users:            /users per page|користувачів на сторінці/i,
  timeline:         /timeline entries per page|подій на сторінці/i,
  moderation:       /moderation queue items per page|елементів черги модерації на сторінці/i,
};

/**
 * Locates one page size field of the open Settings overlay by its label. The users, timeline and
 * moderation fields exist only for privileged actors.
 * @param {import('@playwright/test').Page} page
 * @param {'ads'|'providerProfiles'|'feedback'|'users'|'timeline'|'moderation'} key
 * @returns {import('@playwright/test').Locator} the field's number input.
 */
function pageSizeField(page, key) {
  return page.locator('.settings-overlay-content').getByRole('spinbutton', { name: PAGE_SIZE_LABELS[key] });
}

/**
 * Opens the Settings overlay, sets page size fields, and saves. All parameters optional (only
 * specified ones are filled).
 * @param {import('@playwright/test').Page} page
 * @param {number} [adsSize] advertisements page size.
 * @param {number} [providerProfilesSize] provider profiles page size.
 * @param {number} [usersSize] users page size.
 * @param {number} [moderationSize] moderation queue page size (privileged actors only).
 * @returns {Promise<void>}
 */
async function changePageSizes(page, adsSize, providerProfilesSize, usersSize, moderationSize) {
  await page.getByRole('button', { name: /^(settings|налаштування)$/i }).click();
  await page.locator('.base-overlay.overlay--visible').waitFor({ timeout: 5000 });
  const adsInput        = pageSizeField(page, 'ads');
  const providersInput  = pageSizeField(page, 'providerProfiles');
  const usersInput      = pageSizeField(page, 'users');
  const moderationInput = pageSizeField(page, 'moderation');
  await adsInput.click({ clickCount: 3 });
  await adsInput.fill(String(adsSize));
  await providersInput.click({ clickCount: 3 });
  await providersInput.fill(String(providerProfilesSize));
  await usersInput.click({ clickCount: 3 });
  await usersInput.fill(String(usersSize));
  if (moderationSize !== undefined) {
    await moderationInput.click({ clickCount: 3 });
    await moderationInput.fill(String(moderationSize));
  }
  await page.locator('.base-overlay.overlay--visible').getByRole('button', { name: /зберегти|save/i }).click();
}

/**
 * Opens the Settings history overlay -- thin Settings-specific wrapper over the shared
 * entity-activity helper (Settings uses the same EntityActivityOverlay as every other domain now).
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<import('@playwright/test').Locator>} the entity-activity-list locator.
 */
async function openHistory(page) {
  return openEntityActivity(page, '.settings-history-button');
}

/**
 * Closes the history overlay. `via: 'settings'` (default) and `via: 'x'` both go back to
 * Settings; `via: 'home'` exits all the way out. Maps onto the shared helper's generic
 * 'parent'/'x'/'outer'.
 * @param {import('@playwright/test').Page} page
 * @param {'settings'|'x'|'home'} [via='settings']
 * @returns {Promise<void>}
 */
async function closeHistory(page, via = 'settings') {
  const mapped = { settings: 'parent', x: 'x', home: 'outer' }[via];
  await closeEntityActivity(page, mapped);
}

/**
 * Restores the first history entry, then saves the staged values -- same two-step contract as
 * the underlying shared helper.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<void>}
 */
async function restoreLatestFromActivity(page) {
  await restoreFromEntityActivity(page);
  const saveButton = page.locator('.base-overlay.overlay--visible').getByRole('button', { name: /зберегти|save/i });
  await expect(saveButton).toBeEnabled({ timeout: 5000 });
  await saveButton.click();
}

/**
 * Opens the Settings overlay, reads all three page size fields, closes the overlay, and returns them.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<{adsPageSize: number, providerProfilesPageSize: number, usersPageSize: number}>}
 */
async function getPageSizes(page) {
  await page.getByRole('button', { name: /^(settings|налаштування)$/i }).click();
  await page.locator('.base-overlay.overlay--visible').waitFor({ timeout: 5000 });
  const adsPageSize              = parseInt(await pageSizeField(page, 'ads').inputValue(), 10);
  const providerProfilesPageSize = parseInt(await pageSizeField(page, 'providerProfiles').inputValue(), 10);
  const usersPageSize            = parseInt(await pageSizeField(page, 'users').inputValue(), 10);
  const moderationPageSize       = parseInt(await pageSizeField(page, 'moderation').inputValue(), 10);
  await page.locator('.base-overlay.overlay--visible .overlay__breadcrumb-back').click();
  await page.locator('.base-overlay.overlay--visible').waitFor({ state: 'hidden', timeout: 5000 });
  return { adsPageSize, providerProfilesPageSize, usersPageSize, moderationPageSize };
}

module.exports = { changePageSizes, openHistory, closeHistory, restoreLatestFromActivity, getPageSizes, pageSizeField };
