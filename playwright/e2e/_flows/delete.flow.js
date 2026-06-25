const { screenshot } = require('../_helpers');

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

async function runCreateSimpleAdvertisementFlow(page, { title, description, screenshotPrefix }) {
  await page.locator('.add-advertisement-button').click();
  const overlay = page.locator('.advertisement-overlay');
  await overlay.waitFor({ timeout: 5000 });
  await overlay.locator('[data-testid="advertisement-overlay-field-title"] input').fill(title);
  await overlay.locator('[data-testid="advertisement-overlay-field-description"] textarea').fill(description);
  await screenshot(page, `${screenshotPrefix}-form-filled`);
  await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
  await page.locator('.base-overlay.overlay--visible').waitFor({ state: 'hidden', timeout: 10000 });
  await screenshot(page, `${screenshotPrefix}-saved`);
}

module.exports = { cancelDeleteDialog, confirmDeleteDialog, runCreateSimpleAdvertisementFlow };
