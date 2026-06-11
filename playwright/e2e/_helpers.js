const TEST_USERS = {
  userEn:      { name: 'User EN',      email: 'user.en@example.com',      role: 'USER',      locale: 'en', password: 'password' },
  userUk:      { name: 'User UK',      email: 'user.uk@example.com',      role: 'USER',      locale: 'uk', password: 'password' },
  moderatorEn: { name: 'Moderator EN', email: 'moderator.en@example.com', role: 'MODERATOR', locale: 'en', password: 'password' },
  moderatorUk: { name: 'Moderator UK', email: 'moderator.uk@example.com', role: 'MODERATOR', locale: 'uk', password: 'password' },
  adminEn:     { name: 'Admin EN',     email: 'admin.en@example.com',     role: 'ADMIN',     locale: 'en', password: 'password' },
  adminUk:     { name: 'Admin UK',     email: 'admin.uk@example.com',     role: 'ADMIN',     locale: 'uk', password: 'password' },
};

async function closeNotification(page) {
  const card = page.locator('vaadin-notification-card').first();
  const visible = await card.isVisible().catch(() => false);
  if (!visible) return;
  await card.locator('vaadin-button').click();
  await card.waitFor({ state: 'hidden', timeout: 3000 });
}

module.exports = { TEST_USERS, closeNotification };
