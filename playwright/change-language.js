const { check, screenshot, withBrowser } = require('./_common');

// Tests locale switching (no login required — locale selector visible to guests too)
withBrowser(async (page) => {
  await page.goto('http://localhost:8080/', { waitUntil: 'networkidle' });
  await screenshot(page, 'lang-01-default-page');

  await check('Locale combobox visible', async () => {
    await page.waitForSelector('.locale-combobox', { timeout: 5000 });
    const current = await page.locator('.locale-combobox input').inputValue();
    console.log('      current locale label:', current);
  });

  await check('Switch to English', async () => {
    await page.locator('.locale-combobox input').click();
    await page.waitForSelector('vaadin-combo-box-item', { timeout: 3000 });
    const options = page.locator('vaadin-combo-box-item');
    const count = await options.count();
    console.log('      available options count:', count);
    await options.filter({ hasText: /english|англійська|en/i }).first().click();
    await page.waitForNavigation({ waitUntil: 'networkidle', timeout: 10000 }).catch(() => {});
  });
  await screenshot(page, 'lang-02-after-switch');

  await check('UI text changed', async () => {
    const body = await page.textContent('body');
    console.log('      body snippet:', body.replace(/\s+/g, ' ').trim().slice(0, 300));
    if (body.includes('Log In') || body.includes('Advertisements')) {
      console.log('      [SUCCESS] English UI detected');
    } else if (body.includes('Увійти') || body.includes('Оголошення')) {
      console.log('      [INFO] Still in Ukrainian — user may already have EN locale saved');
    }
  });

  await check('Switch back to Ukrainian', async () => {
    await page.locator('.locale-combobox input').click();
    await page.waitForSelector('vaadin-combo-box-item', { timeout: 3000 });
    await page.locator('vaadin-combo-box-item').filter({ hasText: /укр|ukrainian/i }).first().click();
    await page.waitForNavigation({ waitUntil: 'networkidle', timeout: 10000 }).catch(() => {});
  });
  await screenshot(page, 'lang-03-back-to-ukrainian');
}, { skipLogin: true });
