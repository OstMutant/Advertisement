const TEST_USERS = {
  userEn:      { name: 'User EN',      email: 'user.en@example.com',      role: 'USER',      locale: 'en', password: 'password' },
  userUk:      { name: 'User UK',      email: 'user.uk@example.com',      role: 'USER',      locale: 'uk', password: 'password' },
  moderatorEn: { name: 'Moderator EN', email: 'moderator.en@example.com', role: 'MODERATOR', locale: 'en', password: 'password' },
  moderatorUk: { name: 'Moderator UK', email: 'moderator.uk@example.com', role: 'MODERATOR', locale: 'uk', password: 'password' },
  adminEn:     { name: 'Admin EN',     email: 'admin.en@example.com',     role: 'ADMIN',     locale: 'en', password: 'password' },
  adminUk:     { name: 'Admin UK',     email: 'admin.uk@example.com',     role: 'ADMIN',     locale: 'uk', password: 'password' },
};

const { test } = require('@playwright/test');

async function closeNotification(page) {
  // Loop to handle multiple stacked notifications (e.g. restore + auto-save triggers 2 cards).
  for (let attempt = 0; attempt < 5; attempt++) {
    const card = page.locator('vaadin-notification-card').first();
    if (!await card.isVisible().catch(() => false)) return;
    await card.locator('vaadin-button').click().catch(() => {});
    // Wait until no notification cards remain in DOM (covers the case where a new card
    // replaces the closed one — .first() would otherwise shift to the new card and loop endlessly).
    const cleared = await page.waitForFunction(
      () => !document.querySelector('vaadin-notification-card'),
      { timeout: 8000 }
    ).then(() => true).catch(() => false);
    if (cleared) return;
  }
}

async function screenshotThenClose(page, name) {
  if (process.env.PW_SCREENSHOTS) {
    const buffer = await page.screenshot({ fullPage: false });
    await test.info().attach(name, { body: buffer, contentType: 'image/png' });
  }
  await closeNotification(page);
}

module.exports = { TEST_USERS, closeNotification, screenshotThenClose };
