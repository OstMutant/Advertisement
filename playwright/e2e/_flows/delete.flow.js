/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Flow helpers for the advertisement delete confirm dialog (cancel/confirm) and a
 *   shared "create a simple advertisement" flow used to set up an ad for later delete/edit tests.
 * Usage: None -- a library only, required by spec files (see Input).
 * Uses: ../_helpers (screenshot), ./category.flow (selectCategoryInAdForm).
 * Env: None.
 * Input: required by 03-marketplace-promotion-flow.spec.js, 05-marketplace-advertisement-flow.spec.js,
 *   07-marketplace-delete-flow.spec.js.
 * Outputs: exports cancelDeleteDialog, confirmDeleteDialog, runCreateSimpleAdvertisementFlow.
 * Returns: N/A
 * ──────────────────────────────────────────────────────────────────────────── */
const { screenshot } = require('../_helpers');
const { selectCategoryInAdForm } = require('./category.flow');

/**
 * Opens the delete confirm dialog's Cancel button and waits for the dialog to close.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<void>}
 */
async function cancelDeleteDialog(page) {
  await page.locator('vaadin-dialog-overlay').waitFor({ timeout: 5000 });
  await screenshot(page, 'delete-dialog-cancel-open');
  await page.evaluate(() => {
    const dialog = document.querySelector('vaadin-dialog[opened]');
    [...dialog.querySelectorAll('vaadin-button')]
      .find(b => /cancel|скасувати/i.test(b.textContent?.trim()))
      ?.click();
  });
  await page.locator('vaadin-dialog-overlay').waitFor({ state: 'hidden', timeout: 5000 });
}

/**
 * Opens the delete confirm dialog's Delete button and waits for the dialog to close.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<void>}
 */
async function confirmDeleteDialog(page) {
  await page.locator('vaadin-dialog-overlay').waitFor({ timeout: 5000 });
  await screenshot(page, 'delete-dialog-confirm-open');
  await page.evaluate(() => {
    const dialog = document.querySelector('vaadin-dialog[opened]');
    [...dialog.querySelectorAll('vaadin-button')]
      .find(b => /^delete$|^видалити$/i.test(b.textContent?.trim()))
      ?.click();
  });
  await page.locator('vaadin-dialog-overlay').waitFor({ state: 'hidden', timeout: 5000 });
}

/**
 * Creates a new advertisement via the "add" button, filling title/description/categories and saving.
 * @param {import('@playwright/test').Page} page
 * @param {Object} params
 * @param {string} params.title
 * @param {string} params.description
 * @param {string} params.screenshotPrefix prefix used for the form-filled/saved screenshots.
 * @param {string[]} [params.categories] category names to select in the form, in order.
 * @returns {Promise<void>}
 */
async function runCreateSimpleAdvertisementFlow(page, { title, description, screenshotPrefix, categories = [] }) {
  await page.locator('.add-advertisement-button').click();
  const overlay = page.locator('.advertisement-overlay');
  await overlay.waitFor({ timeout: 5000 });
  await overlay.locator('[data-testid="advertisement-overlay-field-title"] input').fill(title);
  await overlay.locator('[data-testid="advertisement-overlay-field-description"] .ql-editor').fill(description);
  for (const cat of categories) await selectCategoryInAdForm(page, overlay, cat);
  await screenshot(page, `${screenshotPrefix}-form-filled`);
  await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
  await page.locator('.base-overlay.overlay--visible').waitFor({ state: 'hidden', timeout: 10000 });
  await screenshot(page, `${screenshotPrefix}-saved`);
}

module.exports = { cancelDeleteDialog, confirmDeleteDialog, runCreateSimpleAdvertisementFlow };
