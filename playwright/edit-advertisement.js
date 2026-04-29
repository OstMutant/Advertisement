const { check, screenshot, withBrowser, waitForOverlay } = require('./_common');

withBrowser(async (page) => {
  await screenshot(page, 'edit-01-advertisement-list');

  await check('Advertisement list loaded', async () => {
    await page.waitForSelector('.advertisement-card', { timeout: 8000 });
    const items = await page.locator('.advertisement-card').count();
    console.log(`      found ${items} items in list`);
  });

  await check('Open first advertisement detail', async () => {
    await page.locator('.advertisement-card').first().click();
    await waitForOverlay(page);
  });
  await screenshot(page, 'edit-02-advertisement-detail');

  await check('Click Редагувати', async () => {
    await page.locator('vaadin-button').filter({ hasText: /редагувати|edit/i }).first().click();
    await page.waitForSelector('.base-overlay.overlay--visible vaadin-text-field input', { timeout: 5000 });
  });
  await screenshot(page, 'edit-03-edit-form');

  await check('Update title field', async () => {
    const titleInput = page.locator('vaadin-text-field input').first();
    await titleInput.clear();
    await titleInput.fill('Updated by Playwright');
    console.log('      title updated');
  });

  await check('Save changes', async () => {
    await page.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).first().click();
    await page.waitForSelector('.overlay__view-title', { timeout: 5000 });
    const body = await page.textContent('body');
    if (body.includes('Updated by Playwright')) {
      console.log('      [SUCCESS] Title updated');
    } else {
      console.log('      body snippet:', body.replace(/\s+/g, ' ').trim().slice(0, 300));
    }
  });
  await screenshot(page, 'edit-04-saved-result');
}, { email: 'user2@example.com' });
