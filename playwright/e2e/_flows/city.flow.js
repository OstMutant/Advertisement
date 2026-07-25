const { screenshot } = require('../_helpers');
const { openReferenceDataTab } = require('./category.flow');

async function openCitiesSubTab(page) {
  await openReferenceDataTab(page);
  await page.locator('.reference-data-sub-tabs vaadin-tab').filter({ hasText: /Cities|Міста/i }).click();
  await page.locator('.city-management-view').waitFor({ timeout: 5000 });
}

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

async function selectCityInAdForm(page, overlay, cityName) {
  const comboBox = overlay.locator('[data-testid="advertisement-overlay-field-city"]');
  await comboBox.locator('input').click();
  await comboBox.locator('input').fill(cityName);
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Enter');
}

async function assertCardHasCity(page, expect, card, cityName, screenshotName) {
  const line = card.locator('.advertisement-city');
  await expect(line).toBeVisible({ timeout: 5000 });
  await expect(line).toContainText(cityName, { timeout: 5000 });
  if (screenshotName) await screenshot(page, screenshotName);
}

async function assertViewOverlayHasCity(page, expect, overlay, cityName, screenshotName) {
  const chip = overlay.locator('.advertisement-city-chip', { hasText: cityName });
  await expect(chip).toBeVisible({ timeout: 5000 });
  if (screenshotName) await screenshot(page, screenshotName);
}

module.exports = {
  openCitiesSubTab,
  runCreateCityFlow,
  selectCityInAdForm,
  assertCardHasCity,
  assertViewOverlayHasCity,
};
