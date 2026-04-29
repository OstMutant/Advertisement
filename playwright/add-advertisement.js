const { check, screenshot, login, withBrowser, waitForOverlay, waitForOverlayClosed } = require('./_common');

withBrowser(async (page) => {
  await screenshot(page, 'add-01-advertisement-list');

  await check('New advertisement button visible', async () => {
    await page.waitForSelector('vaadin-button', { timeout: 5000 });
    const buttons = await page.locator('vaadin-button').allTextContents();
    console.log('      buttons:', buttons.join(' | '));
  });

  await check('Open new advertisement form', async () => {
    await page.locator('vaadin-button').filter({ hasText: /new|add|create|нове|додати/i }).first().click();
    await waitForOverlay(page);
    const body = await page.textContent('body');
    console.log('      form snippet:', body.replace(/\s+/g, ' ').trim().slice(0, 300));
  });
  await screenshot(page, 'add-02-new-advertisement-form');

  await check('Fill form fields', async () => {
    const overlay = page.locator('.advertisement-overlay');
    await overlay.locator('vaadin-text-field input').fill('Test from Playwright');
    await overlay.locator('vaadin-text-area textarea').fill('Auto-generated test description');
    console.log('      fields filled');
  });

  await check('Save advertisement', async () => {
    const overlay = page.locator('.advertisement-overlay');
    await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save|submit/i }).click();
    await waitForOverlayClosed(page);
    const body = await page.textContent('body');
    if (body.includes('Test from Playwright')) {
      console.log('      [SUCCESS] Advertisement appears in list');
    } else {
      console.log('      body after save:', body.replace(/\s+/g, ' ').trim().slice(0, 400));
    }
  });
  await screenshot(page, 'add-03-saved-result');
});
