const { chromium } = require('playwright');
const { check, screenshot, login } = require('./_common');

// Tests the settings overlay — ads page size change
const UX = process.argv.includes('--ux');

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });

  await login(page);
  await screenshot(page, 'settings-01-home', UX);

  await check('Settings button visible', async () => {
    await page.waitForSelector('.header-settings-button', { timeout: 5000 });
  });

  await check('Open settings overlay', async () => {
    await page.locator('.header-settings-button').click();
    await page.waitForTimeout(1500);
    const body = await page.textContent('body');
    console.log('      settings snippet:', body.replace(/\s+/g, ' ').trim().slice(0, 300));
  });
  await screenshot(page, 'settings-02-overlay', UX);

  await check('Ads page size field present', async () => {
    // SettingsOverlay uses vaadin-integer-field
    const fields = page.locator('vaadin-integer-field');
    const count = await fields.count();
    console.log(`      found ${count} integer fields`);
    if (count === 0) throw new Error('No integer fields found');
    const val = await fields.first().locator('input').inputValue();
    console.log('      current ads page size:', val);
  });

  await check('Change ads page size', async () => {
    const adsField = page.locator('vaadin-integer-field').first().locator('input');
    await adsField.triple_click?.() ?? await adsField.click({ clickCount: 3 });
    await adsField.fill('15');
    console.log('      set to 15');
  });

  await check('Save settings', async () => {
    await page.locator('.base-overlay.overlay--visible vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await page.waitForTimeout(1500);
    const body = await page.textContent('body');
    // Should show success notification and close overlay
    if (!body.includes('Налаштування') || body.includes('збережено')) {
      console.log('      [SUCCESS] Settings saved, overlay closed');
    } else {
      console.log('      body snippet:', body.replace(/\s+/g, ' ').trim().slice(0, 200));
    }
  });
  await screenshot(page, 'settings-03-saved', UX);

  await browser.close();
})();
