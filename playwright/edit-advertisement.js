const { chromium } = require('playwright');
const { check, screenshot, login, waitForOverlay, waitForGrid } = require('./_common');

const UX = process.argv.includes('--ux');

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });

  await login(page, 'user2@example.com', 'password');
  await screenshot(page, 'edit-01-advertisement-list', UX);

  await check('Advertisement list loaded', async () => {
    await page.waitForSelector('.advertisement-card', { timeout: 8000 });
    const items = await page.locator('.advertisement-card').count();
    console.log(`      found ${items} items in list`);
  });

  await check('Open first advertisement detail', async () => {
    await page.locator('.advertisement-card').first().click();
    await waitForOverlay(page);
  });
  await screenshot(page, 'edit-02-advertisement-detail', UX);

  await check('Click Редагувати', async () => {
    await page.locator('vaadin-button').filter({ hasText: /редагувати|edit/i }).first().click();
    await page.waitForSelector('.base-overlay.overlay--visible vaadin-text-field input', { timeout: 5000 });
  });
  await screenshot(page, 'edit-03-edit-form', UX);

  await check('Update title field', async () => {
    const titleInput = page.locator('vaadin-text-field input').first();
    await titleInput.clear();
    await titleInput.fill('Updated by Playwright');
    console.log('      title updated');
  });

  await check('Save changes', async () => {
    await page.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).first().click();
    // After save from VIEW mode → returns to VIEW; wait for overlay to show view content
    await page.waitForSelector('.base-overlay.overlay--visible', { timeout: 5000 });
    await page.waitForTimeout(500);
    const body = await page.textContent('body');
    if (body.includes('Updated by Playwright')) {
      console.log('      [SUCCESS] Title updated in list');
    } else {
      console.log('      body snippet:', body.replace(/\s+/g, ' ').trim().slice(0, 300));
    }
  });
  await screenshot(page, 'edit-04-saved-result', UX);

  await browser.close();
})();
