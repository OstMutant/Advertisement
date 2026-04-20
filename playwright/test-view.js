const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  await page.setViewportSize({ width: 1280, height: 900 });

  await page.goto('http://localhost:8080');
  await page.fill('vaadin-text-field[name="username"] input', 'user1@example.com');
  await page.fill('vaadin-password-field[name="password"] input', 'password123');
  await page.click('vaadin-button[theme~="primary"]');
  await page.waitForTimeout(2000);

  // Click first advertisement card
  await page.click('.advertisement-card');
  await page.waitForTimeout(1500);
  await page.screenshot({ path: '/tmp/view-mode.png', fullPage: false });

  await browser.close();
})();
