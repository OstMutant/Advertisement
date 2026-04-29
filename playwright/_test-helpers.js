const { test: base, expect } = require('@playwright/test');
const common = require('./_common');

async function loginAs(page, email = 'user1@example.com', password = 'password') {
  await common.login(page, email, password);
}

// Extended test with automatic login fixture
const test = base.extend({
  loggedInPage: async ({ page }, use) => {
    await loginAs(page);
    await use(page);
  },
});

module.exports = { test, expect, loginAs, ...common };
