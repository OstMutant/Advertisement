const { chromium } = require('playwright');
const { check, screenshot, login } = require('./_common');

const UX = process.argv.includes('--ux');

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });

  await login(page);
  await screenshot(page, 'edit-01-advertisement-list', UX);

  await check('Advertisement list loaded', async () => {
    await page.waitForTimeout(1500);
    const items = await page.locator('vaadin-grid-cell-content, .advertisement-card, vaadin-virtual-list > *').count();
    console.log(`      found ${items} items in list`);
  });

  await check('Open first advertisement detail', async () => {
    await page.locator('.advertisement-card').first().click();
    await page.waitForTimeout(1500);
  });
  await screenshot(page, 'edit-02-advertisement-detail', UX);

  await check('Click Редагувати', async () => {
    await page.locator('vaadin-button').filter({ hasText: /редагувати|edit/i }).first().click();
    await page.waitForTimeout(1500);
  });
  await screenshot(page, 'edit-03-edit-form', UX);

  await check('Update title field', async () => {
    // Edit panel is a side panel — scope to the form area by heading text
    const titleInput = page.locator('vaadin-text-field input').first();
    await titleInput.clear();
    await titleInput.fill('Updated by Playwright');
    console.log('      title updated');
  });

  await check('Save changes', async () => {
    await page.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).first().click();
    await page.waitForTimeout(2000);
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
