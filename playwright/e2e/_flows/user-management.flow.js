const { screenshot, assertComputedColor, assertRightAligned } = require('../_helpers');
const { closeNotification } = require('../_helpers');

// Expected computed colors per role -- must match user-grid.css's role badge colors exactly.
const ROLE_COLOR = {
  admin:     'rgb(29, 78, 216)',
  user:      'rgb(21, 128, 61)',
  moderator: 'rgb(194, 65, 12)',
};

async function closeUserOverlay(page) {
  await page.locator('.user-overlay vaadin-button')
    .filter({ has: page.locator('vaadin-icon[icon="vaadin:close"]') })
    .first()
    .click();
  await page.locator('.user-overlay.overlay--visible').waitFor({ state: 'hidden', timeout: 5000 });
}

async function clearUserFilter(page) {
  await page.locator('.user-query-block vaadin-button[title*="Clear"], .user-query-block vaadin-button[title*="Очистити"]').click();
  await page.locator('.user-list-layout .query-status-bar').click();
  await page.locator('.user-query-block').waitFor({ state: 'hidden', timeout: 3000 });
}

async function runNavigateToUsersTabFlow(page, expect) {
  await page.locator('vaadin-tab').filter({ hasText: /Users|Користувачі/i }).first().click();
  await page.locator('.user-list-layout').waitFor({ timeout: 5000 });
  await screenshot(page, 'user-management-users-tab');
}

async function runFilterUserByEmailFlow(page, email) {
  await page.locator('.user-list-layout .query-status-bar').click();
  await page.locator('.user-query-block').waitFor({ timeout: 5000 });
  await page.locator('.user-query-block vaadin-text-field[placeholder="Email"] input').fill(email);
  await page.locator('.user-query-block vaadin-button[title*="Apply"], .user-query-block vaadin-button[title*="Застосувати"]').click();
}

async function runOpenUserViewDialogFlow(page, email) {
  await runFilterUserByEmailFlow(page, email);
  await page.locator('.user-grid-name:visible').first().waitFor({ timeout: 5000 });
  await page.locator('.user-grid-name:visible').first().click();
  await page.locator('.user-overlay.overlay--visible').waitFor({ timeout: 5000 });
  await screenshot(page, 'user-management-view-dialog-opened');
}

async function runOpenUserEditViaViewFlow(page, email) {
  await runOpenUserViewDialogFlow(page, email);
  await page.locator('.user-overlay vaadin-button').filter({ hasText: /Edit|Редагувати/ }).click();
  await page.locator('.user-overlay vaadin-combo-box').waitFor({ timeout: 5000 });
  await screenshot(page, 'user-management-promote-dialog-opened');
}

async function runOpenUserEditViaListFlow(page, email) {
  await runFilterUserByEmailFlow(page, email);
  const editButton = page.locator('.user-grid-actions vaadin-button[title="Edit"], .user-grid-actions vaadin-button[title="Редагувати"]').first();
  await editButton.waitFor({ timeout: 5000 });
  await editButton.click();
  await page.locator('.user-overlay.overlay--visible').waitFor({ timeout: 5000 });
  await page.locator('.user-overlay vaadin-combo-box').waitFor({ timeout: 5000 });
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
  await expect(page.locator('.user-overlay vaadin-button').filter({ hasText: /Save|Зберегти/i })).toBeEnabled({ timeout: 5000 });
  await page.locator('.user-overlay vaadin-button').filter({ hasText: /Save|Зберегти/i }).click();
  await expect(page.locator('vaadin-notification-container')).toContainText(
    /User updated successfully|Користувача успішно оновлено/i,
    { timeout: 5000 }
  );
  await screenshot(page, `user-management-promoted-${role.toLowerCase()}`);
  await closeNotification(page);
}

async function closeUserOverlayFromEdit(page) {
  await page.locator('.user-overlay vaadin-button')
    .filter({ has: page.locator('vaadin-icon[icon="vaadin:close"]') })
    .first().click();
  await page.locator('.user-overlay vaadin-button').filter({ hasText: /Edit|Редагувати/ }).waitFor({ state: 'visible', timeout: 5000 });
  await closeUserOverlay(page);
}

async function runPromoteUserFlow(page, expect, user, { role = null, name = null } = {}) {
  await runOpenUserEditViaViewFlow(page, user.email);
  await runFillUserRoleFlow(page, { role, name });
  await runSaveUserEditFlow(page, expect, role);

  // Check activity in EDIT overlay — v2 updated + v1 created
  await page.locator('.user-overlay vaadin-tab').filter({ hasText: /Activity|Активність/i }).click();
  const activityList = page.locator('.user-overlay .entity-activity-list');
  await activityList.waitFor({ timeout: 5000 });
  await expect(activityList.locator('.entity-activity-row')).toHaveCount(2, { timeout: 5000 });

  const row0 = activityList.locator('.entity-activity-row').nth(0);
  await expect(row0.locator('.entity-activity-action')).toContainText(/updated/i);
  await expect(row0.locator('.entity-activity-version')).toContainText('v2');
  // The actor name and the timestamp must both sit flush against the row's right edge (improvement-126).
  const meta0 = row0.locator('.entity-activity-meta');
  await assertRightAligned(expect, meta0.locator('.entity-activity-time'), meta0);
  const userBox = await meta0.locator('.entity-activity-user').boundingBox();
  const timeBox = await meta0.locator('.entity-activity-time').boundingBox();
  expect(userBox.x, 'actor name must sit to the left of the timestamp, both on the right side of the row').toBeLessThan(timeBox.x);
  expect(timeBox.x - (userBox.x + userBox.width), 'actor name and timestamp must be adjacent, not far apart').toBeLessThan(20);
  const changes0 = row0.locator('.entity-activity-changes');
  await expect(changes0).toContainText(/Role/i);
  await expect(changes0).toContainText('USER');
  await expect(changes0).toContainText(role);
  await expect(changes0).toContainText(user.name);
  await expect(changes0).toContainText(user.email);
  await expect(row0.locator('.entity-activity-changes-item--unchanged').first()).toBeVisible();

  const row1 = activityList.locator('.entity-activity-row').nth(1);
  await expect(row1.locator('.entity-activity-action')).toContainText(/created/i);
  await expect(row1.locator('.entity-activity-version')).toContainText('v1');
  const changes1 = row1.locator('.entity-activity-changes');
  await expect(changes1).toContainText('USER');
  await expect(changes1).toContainText(user.name);
  await expect(changes1).toContainText(user.email);

  await screenshot(page, `user-management-promoted-${role.toLowerCase()}-edit-activity`);

  // EDIT → VIEW
  await page.locator('.user-overlay vaadin-button')
    .filter({ has: page.locator('vaadin-icon[icon="vaadin:close"]') })
    .first().click();
  await page.locator('.user-overlay vaadin-button').filter({ hasText: /Edit|Редагувати/ }).waitFor({ state: 'visible', timeout: 5000 });

  // Check role in VIEW mode
  await expect(page.locator('.user-overlay .user-role-badge')).toContainText(role);
  // The view-card's accent border must match the role badge color -- header text stays static.
  const roleClass = role.toLowerCase();
  const expectedColor = ROLE_COLOR[roleClass];
  const viewCard = page.locator(`.user-overlay .user-view-card.user-view-card--${roleClass}`);
  await expect(viewCard).toHaveCount(1);
  await assertComputedColor(expect, viewCard, 'borderTopColor', expectedColor);
  await expect(page.locator(`.user-overlay .overlay__view-card-header.overlay__view-card-header--${roleClass}`)).toHaveCount(1);
  await screenshot(page, `user-management-promoted-${roleClass}-view`);

  // VIEW → close
  await closeUserOverlay(page);

  // Check role in grid (filter still active)
  await expect(page.locator('.user-list-layout .user-role-badge:visible').first()).toContainText(role);
  await screenshot(page, `user-management-promoted-${role.toLowerCase()}-grid`);

  await clearUserFilter(page);
}

module.exports = {
  closeUserOverlay,
  closeUserOverlayFromEdit,
  clearUserFilter,
  runNavigateToUsersTabFlow,
  runFilterUserByEmailFlow,
  runOpenUserViewDialogFlow,
  runOpenUserEditViaViewFlow,
  runOpenUserEditViaListFlow,
  runFillUserRoleFlow,
  runSaveUserEditFlow,
  runPromoteUserFlow,
};
