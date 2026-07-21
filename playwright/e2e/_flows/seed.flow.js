const { closeNotification } = require('../_helpers');
const { selectCategoryInAdForm } = require('./category.flow');

async function signUpBulk(page, { name, email, password }) {
  await page.locator('vaadin-button').filter({ hasText: /sign up/i }).first().click();
  // Wait for Vaadin to set [opened] on the dialog overlay — the element is attached but hidden
  // during the opening animation, so waiting for state:'visible' races with the CSS transition.
  await page.locator('vaadin-dialog-overlay[theme~="signup-dialog"][opened]').waitFor({ state: 'attached', timeout: 12000 });
  // fill() auto-waits for interactability, handling any remaining CSS animation on inner inputs.
  await page.locator('[data-testid="signup-name-label"] input').fill(name);
  await page.locator('[data-testid="signup-email-label"] input').fill(email);
  await page.locator('[data-testid="signup-password-label"] input').fill(password);
  await page.getByRole('button', { name: /sign up/i }).last().click();
  // On serial retry the user may already exist — dialog stays open with an error notification.
  // Dismiss the notification and close the dialog so the loop can continue.
  const dialog = page.locator('vaadin-dialog-overlay[theme~="signup-dialog"]');
  const closed = await dialog.waitFor({ state: 'hidden', timeout: 8000 }).then(() => true).catch(() => false);
  if (!closed) {
    await closeNotification(page);
    // Close the dialog via JS — keyboard is unreliable when page may be detaching on retry
    await page.evaluate(() => {
      const d = document.querySelector('vaadin-dialog[theme~="signup-dialog"]');
      if (d) d.opened = false;
    });
    await dialog.waitFor({ state: 'hidden', timeout: 3000 }).catch(() => {});
  } else {
    await closeNotification(page);
  }
}

async function loginBulk(page, { email, password }) {
  await page.locator('vaadin-button').filter({ hasText: /log in/i }).first().click();
  await page.locator('[data-testid="login-email-label"]').waitFor({ timeout: 5000 });
  await page.locator('[data-testid="login-email-label"] input').fill(email);
  await page.locator('[data-testid="login-password-label"] input').fill(password);
  await page.locator('vaadin-button').filter({ hasText: /log in/i }).last().click();
  await page.locator('.header-settings-button').waitFor({ timeout: 15000 });
}

async function logoutBulk(page) {
  await page.locator('.header-logout-button').click();
  await page.locator('vaadin-confirm-dialog-overlay[opened]:not([opening])').waitFor({ state: 'attached', timeout: 5000 });
  await page.getByRole('button', { name: /^log out$|^вийти$/i }).last().click();
  await page.locator('vaadin-button').filter({ hasText: /log in/i }).first().waitFor({ timeout: 5000 });
}

async function createAdvertisementBulk(page, { title, description, category = null }) {
  // Wait for button to be stable — Vaadin may reconnect after heavy prior load, temporarily detaching DOM
  await page.locator('.add-advertisement-button').waitFor({ state: 'visible', timeout: 20000 });
  await page.locator('.add-advertisement-button').click();
  const overlay = page.locator('.advertisement-overlay');
  await overlay.waitFor({ timeout: 5000 });
  await overlay.locator('[data-testid="advertisement-overlay-field-title"] input').fill(title);
  await overlay.locator('[data-testid="advertisement-overlay-field-description"] .ql-editor').fill(description);
  if (category) await selectCategoryInAdForm(page, overlay, category);
  await overlay.locator('vaadin-button').filter({ hasText: /save|зберегти/i }).click();
  await overlay.waitFor({ state: 'hidden', timeout: 10000 });
}

async function signUpBulkParallel(browser, users, poolSize = 3) {
  const groups = Array.from({ length: poolSize }, () => []);
  users.forEach((user, i) => groups[i % poolSize].push(user));

  const delay = ms => new Promise(r => setTimeout(r, ms));
  const failed = [];

  await Promise.all(
    groups.filter(g => g.length > 0).map(async (groupUsers, idx) => {
      // Stagger context startups to avoid hammering the Vaadin server simultaneously
      await delay(idx * 500);
      const context = await browser.newContext();
      const ctxPage = await context.newPage();
      try {
        await ctxPage.goto('/');
        await ctxPage.locator('vaadin-tab').filter({ hasText: /Advertisements|Оголошення/i }).first().waitFor({ timeout: 15000 });
        for (const user of groupUsers) {
          try {
            await signUpBulk(ctxPage, user);
          } catch (_) {
            failed.push(user);
          }
        }
      } finally {
        await context.close();
      }
    })
  );

  // Retry any users that failed in the parallel phase — run serially for stability
  if (failed.length > 0) {
    const context = await browser.newContext();
    const ctxPage = await context.newPage();
    try {
      await ctxPage.goto('/');
      await ctxPage.locator('vaadin-tab').filter({ hasText: /Advertisements|Оголошення/i }).first().waitFor({ timeout: 15000 });
      for (const user of failed) {
        await signUpBulk(ctxPage, user);
      }
    } finally {
      await context.close();
    }
  }
}

module.exports = { signUpBulk, signUpBulkParallel, loginBulk, logoutBulk, createAdvertisementBulk };
