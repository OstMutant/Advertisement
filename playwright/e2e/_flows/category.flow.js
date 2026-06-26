const { screenshot } = require('../_helpers');

async function openReferenceDataTab(page) {
  await page.locator('.main-tabs vaadin-tab').filter({ hasText: /Reference Data|Довідникові дані/i }).click();
  await page.locator('.taxon-management-view').waitFor({ timeout: 5000 });
}

async function runCreateCategoryFlow(page, expect, { nameEn, descriptionEn, nameUk, descriptionUk, screenshotPrefix }) {
  await openReferenceDataTab(page);
  await page.locator('.taxon-add-button').click();
  const overlay = page.locator('.taxon-overlay');
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
  await expect(page.locator('.taxon-row-name', { hasText: nameEn })).toBeVisible({ timeout: 5000 });

  if (screenshotPrefix) await screenshot(page, `${screenshotPrefix}-created`);
}

// Waits for Vaadin server round-trips to settle (same pattern as waitForVaadin in filter.flow.js).
async function waitForVaadinIdle(page) {
  await page.evaluate(() => new Promise(r => setTimeout(r, 0)));
  await page.waitForFunction(() => {
    if (window.Vaadin?.Flow?.clients) {
      const clients = Object.values(window.Vaadin.Flow.clients);
      if (clients.some(c => typeof c.isActive === 'function' && c.isActive())) return false;
    }
    return true;
  }, { timeout: 8000 });
}

async function selectCategoryInAdForm(page, overlay, categoryName) {
  const comboBox = overlay.locator('[data-testid="advertisement-overlay-field-categories"]');
  // Click input to open the dropdown — this loads items into el.items AND blurs any
  // previously focused field (title/description) so Vaadin can sync those values to
  // the server before we trigger combo box events.
  await comboBox.locator('input').click();
  await waitForVaadinIdle(page);
  await page.locator('vaadin-multi-select-combo-box-overlay').waitFor({ state: 'visible', timeout: 5000 });
  // Select via JS — avoids keyboard-based filter events that cause server round-trips
  // resetting title/description to empty values.
  const found = await page.evaluate((name) => {
    const el = document.querySelector('[data-testid="advertisement-overlay-field-categories"] vaadin-multi-select-combo-box');
    if (!el) return false;
    const items = el.items || el.filteredItems || [];
    const match = items.find(i => i.name === name);
    if (!match) return false;
    el.selectedItems = [...(el.selectedItems || []), match];
    return true;
  }, categoryName);
  if (!found) throw new Error(`Category "${categoryName}" not found in combo box items`);
  await page.evaluate(() => {
    const el = document.querySelector('[data-testid="advertisement-overlay-field-categories"] vaadin-multi-select-combo-box');
    if (el) el.opened = false;
  });
  await page.locator('vaadin-multi-select-combo-box-overlay').waitFor({ state: 'hidden', timeout: 5000 });
  await waitForVaadinIdle(page);
}

async function deselectCategoryInAdForm(page, overlay, categoryName) {
  const comboBox = overlay.locator('[data-testid="advertisement-overlay-field-categories"]');
  await comboBox.locator('input').click();
  await waitForVaadinIdle(page);
  await page.locator('vaadin-multi-select-combo-box-overlay').waitFor({ state: 'visible', timeout: 5000 });
  const found = await page.evaluate((name) => {
    const el = document.querySelector('[data-testid="advertisement-overlay-field-categories"] vaadin-multi-select-combo-box');
    if (!el) return false;
    const items = el.items || el.filteredItems || [];
    const match = items.find(i => i.name === name);
    if (!match) return false;
    el.selectedItems = (el.selectedItems || []).filter(i => i.name !== name);
    return true;
  }, categoryName);
  if (!found) throw new Error(`Category "${categoryName}" not found`);
  await page.evaluate(() => {
    const el = document.querySelector('[data-testid="advertisement-overlay-field-categories"] vaadin-multi-select-combo-box');
    if (el) el.opened = false;
  });
  await page.locator('vaadin-multi-select-combo-box-overlay').waitFor({ state: 'hidden', timeout: 5000 });
  await waitForVaadinIdle(page);
}

async function assertCardHasCategoryStripe(page, expect, card, segmentCount, screenshotName) {
  const stripe = card.locator('.advertisement-category-stripe');
  await expect(stripe).toBeVisible({ timeout: 5000 });
  const segments = stripe.locator('.advertisement-category-stripe-segment');
  await expect(segments).toHaveCount(segmentCount, { timeout: 5000 });
  if (screenshotName) await screenshot(page, screenshotName);
}

async function assertViewOverlayHasCategories(page, expect, overlay, categoryNames, screenshotName) {
  const chips = overlay.locator('.advertisement-category-chip');
  await expect(chips).toHaveCount(categoryNames.length, { timeout: 5000 });
  for (const name of categoryNames) {
    await expect(overlay.locator('.advertisement-category-chip', { hasText: name })).toBeVisible({ timeout: 5000 });
  }
  if (screenshotName) await screenshot(page, screenshotName);
}

module.exports = {
  openReferenceDataTab,
  runCreateCategoryFlow,
  selectCategoryInAdForm,
  deselectCategoryInAdForm,
  assertCardHasCategoryStripe,
  assertViewOverlayHasCategories,
};
