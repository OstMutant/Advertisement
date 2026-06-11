const { screenshot } = require('../_test-helpers');
const { closeNotification } = require('../_helpers');

async function runNavigateToUsersTabFlow(page, expect) {
  await page.locator('vaadin-tab').filter({ hasText: /Users|Користувачі/i }).first().click();
  await page.locator('.user-list-layout').waitFor({ timeout: 5000 });
  await screenshot(page, 'user-management-users-tab');
}

async function runOpenUserEditDialogFlow(page, email) {
  await page.locator('.user-list-layout .query-status-bar').click();
  await page.locator('.user-query-block').waitFor({ timeout: 5000 });

  await page.locator('.user-query-block vaadin-text-field[placeholder="Email"] input').fill(email);
  await page.locator('.user-query-block vaadin-button[title*="Apply"], .user-query-block vaadin-button[title*="Застосувати"]').click();

  await page.locator('vaadin-button[title="Edit"], vaadin-button[title="Редагувати"]').first().waitFor({ timeout: 5000 });
  await page.locator('vaadin-button[title="Edit"], vaadin-button[title="Редагувати"]').first().click();

  await page.locator('.user-overlay.overlay--visible').waitFor({ timeout: 5000 });
  await screenshot(page, 'user-management-promote-dialog-opened');
}

async function runFillUserRoleFlow(page, { role, name }) {
  if (name != null) {
    const nameField = page.locator('.user-overlay vaadin-text-field input');
    await nameField.fill(name);
  }
  if (role != null) {
    const roleCombo = page.locator('.user-overlay vaadin-combo-box');
    await roleCombo.locator('input').click();
    await roleCombo.locator('input').fill(role);
    await page.keyboard.press('ArrowDown');
    await page.keyboard.press('Enter');
  }
  await screenshot(page, 'user-management-promote-dialog-filled');
}

async function runSaveUserEditFlow(page, expect, role) {
  await page.locator('.user-overlay vaadin-button').filter({ hasText: /Save|Зберегти/i }).click();
  await expect(page.locator('vaadin-notification-container')).toContainText(
    /User updated successfully|Користувача успішно оновлено/i,
    { timeout: 5000 }
  );

  await page.locator('.user-overlay vaadin-button')
    .filter({ has: page.locator('vaadin-icon[icon="vaadin:close"]') })
    .first()
    .click();
  await page.locator('.user-overlay.overlay--visible').waitFor({ state: 'hidden', timeout: 5000 });

  await page.locator('.user-query-block vaadin-button[title*="Clear"], .user-query-block vaadin-button[title*="Очистити"]').click();
  await page.locator('.user-list-layout .query-status-bar').click();
  await page.locator('.user-query-block').waitFor({ state: 'hidden', timeout: 3000 });

  await screenshot(page, `user-management-promoted-${role.toLowerCase()}`);
  await closeNotification(page);
}

async function runPromoteUserFlow(page, expect, email, { role = null, name = null } = {}) {
  await runOpenUserEditDialogFlow(page, email);
  await runFillUserRoleFlow(page, { role, name });
  await runSaveUserEditFlow(page, expect, role);
}

module.exports = {
  runNavigateToUsersTabFlow,
  runOpenUserEditDialogFlow,
  runFillUserRoleFlow,
  runSaveUserEditFlow,
  runPromoteUserFlow,
};
