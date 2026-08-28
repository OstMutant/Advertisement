/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Flow helpers for the City reference-data domain -- switching to the Cities sub-tab,
 *   creating a city through its overlay form, selecting a city in the advertisement form, and
 *   asserting a city name is shown on a card / view overlay.
 * Usage: None -- a library only, required by spec files (see Input).
 * Uses: ../_helpers (screenshot, assertCardHasText, assertOverlayHasText), ./category.flow
 *   (openReferenceDataTab, shared with the Categories sub-tab since both live under Reference Data).
 * Env: None.
 * Input: required by 05-seed-filter-sort-pagination.spec.js (runCreateCityFlow); also required
 *   internally by advertisement.flow.js and seed.flow.js (selectCityInAdForm).
 * Outputs: exports openCitiesSubTab, runCreateCityFlow, selectCityInAdForm, assertCardHasCity,
 *   assertViewOverlayHasCity.
 * Returns: N/A
 * ──────────────────────────────────────────────────────────────────────────── */
const { screenshot, assertCardHasText, assertOverlayHasText } = require('../_helpers');
const { openReferenceDataTab } = require('./category.flow');

/**
 * Navigates to Reference Data, then switches to the Cities sub-tab.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<void>}
 */
async function openCitiesSubTab(page) {
  await openReferenceDataTab(page);
  await page.locator('.reference-data-sub-tabs vaadin-tab').filter({ hasText: /Cities|Міста/i }).click();
  await page.locator('.city-management-view').waitFor({ timeout: 5000 });
}

/**
 * Creates a city with EN/UK name and description through the city overlay form, then verifies it
 * appears in the city management list.
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Expect} expect
 * @param {object} params
 * @param {string} params.nameEn
 * @param {string} params.descriptionEn
 * @param {string} params.nameUk
 * @param {string} params.descriptionUk
 * @param {string} [params.screenshotPrefix] when set, screenshots are taken before/after save.
 * @returns {Promise<void>}
 */
async function runCreateCityFlow(page, expect, { nameEn, descriptionEn, nameUk, descriptionUk, screenshotPrefix }) {
  await openCitiesSubTab(page);
  await page.locator('.city-add-button').click();
  const overlay = page.locator('.city-overlay');
  await overlay.waitFor({ timeout: 5000 });

  const localeContents = overlay.locator('.taxon-locale-content');
  await localeContents.nth(0).locator('vaadin-text-field input').fill(nameEn);
  await localeContents.nth(0).locator('vaadin-text-area textarea').fill(descriptionEn);
  await localeContents.nth(1).locator('vaadin-text-field input').fill(nameUk);
  await localeContents.nth(1).locator('vaadin-text-area textarea').fill(descriptionUk);

  if (screenshotPrefix) await screenshot(page, `${screenshotPrefix}-form`);

  await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
  await expect(page.locator('vaadin-notification-card')).toBeVisible({ timeout: 5000 });
  await page.locator('vaadin-notification-card vaadin-button').click();
  await overlay.locator('vaadin-button')
    .filter({ has: page.locator('vaadin-icon[icon="vaadin:close"]') })
    .click();
  await overlay.waitFor({ state: 'hidden', timeout: 8000 });
  await expect(page.locator('.city-management-view .taxon-row-name', { hasText: nameEn })).toBeVisible({ timeout: 5000 });

  if (screenshotPrefix) await screenshot(page, `${screenshotPrefix}-created`);
}

/**
 * Selects a city in the advertisement form's city combo box by typing its name and confirming
 * the first suggestion.
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Locator} overlay the advertisement overlay.
 * @param {string} cityName
 * @returns {Promise<void>}
 */
async function selectCityInAdForm(page, overlay, cityName) {
  const comboBox = overlay.locator('[data-testid="advertisement-overlay-field-city"]');
  await comboBox.locator('input').click();
  await comboBox.locator('input').fill(cityName);
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Enter');
}

/**
 * Asserts an advertisement card shows the expected city name.
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Expect} expect
 * @param {import('@playwright/test').Locator} card
 * @param {string} cityName
 * @param {string} [screenshotName] when set, takes a screenshot after the assertion passes.
 * @returns {Promise<void>}
 */
async function assertCardHasCity(page, expect, card, cityName, screenshotName) {
  return assertCardHasText(page, expect, card, '.advertisement-city', cityName, screenshotName);
}

/**
 * Asserts the advertisement view overlay shows the expected city chip.
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Expect} expect
 * @param {import('@playwright/test').Locator} overlay
 * @param {string} cityName
 * @param {string} [screenshotName] when set, takes a screenshot after the assertion passes.
 * @returns {Promise<void>}
 */
async function assertViewOverlayHasCity(page, expect, overlay, cityName, screenshotName) {
  return assertOverlayHasText(page, expect, overlay, '.advertisement-city-chip', cityName, screenshotName);
}

module.exports = {
  openCitiesSubTab,
  runCreateCityFlow,
  selectCityInAdForm,
  assertCardHasCity,
  assertViewOverlayHasCity,
};
