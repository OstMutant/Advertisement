const { check, screenshot, withBrowser, waitForOverlay } = require('./_common');

// Tests the settings overlay — ads page size change
withBrowser(async (page) => {
  await screenshot(page, 'settings-01-home');

  await check('Settings button visible', async () => {
    await page.waitForSelector('.header-settings-button', { timeout: 5000 });
  });

  await check('Open settings overlay', async () => {
    await page.locator('.header-settings-button').click();
    await waitForOverlay(page);
    const body = await page.textContent('body');
    console.log('      settings snippet:', body.replace(/\s+/g, ' ').trim().slice(0, 300));
  });
  await screenshot(page, 'settings-02-overlay');

  await check('Ads page size field present', async () => {
    const fields = page.locator('vaadin-integer-field');
    const count = await fields.count();
    console.log(`      found ${count} integer fields`);
    if (count === 0) throw new Error('No integer fields found');
    const val = await fields.first().locator('input').inputValue();
    console.log('      current ads page size:', val);
  });

  await check('Change ads page size', async () => {
    const adsField = page.locator('vaadin-integer-field').first().locator('input');
    await adsField.click({ clickCount: 3 });
    await adsField.fill('15');
    console.log('      set to 15');
  });

  await check('Save settings', async () => {
    await page.locator('.base-overlay.overlay--visible vaadin-button')
      .filter({ hasText: /зберегти|save/i }).click();
    await page.waitForLoadState('networkidle');
    console.log('      [SUCCESS] Settings saved');
  });
  await screenshot(page, 'settings-03-saved');
});
