/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Shared flow helpers for the Users tab -- navigating to it, filtering by email,
 *   opening a user's view/edit overlay (via the list row or via View), filling and saving the
 *   role/name edit form, closing the overlay from either mode, and the full role-promotion flow
 *   (edit + save + activity-list verification of the v1/v2 diff + role-badge color check in VIEW
 *   mode + grid badge check). Also verifies the outer breadcrumb link closes all the way to the list.
 * Usage: None -- a library only, required by spec files (see Input).
 * Uses: @playwright/test (expect), ../_helpers (screenshot, assertComputedColor, assertRightAligned,
 *   closeNotification), ./entity-activity.flow (openEntityActivity, closeEntityActivity).
 * Env: None.
 * Input: required by 02-marketplace-authentication-flow.spec.js, 03-marketplace-promotion-flow.spec.js,
 *   04-provider-profile-flow.spec.js, 07-marketplace-delete-flow.spec.js, and by
 *   ./_flows/audit.flow.js (runOpenUserEditViaListFlow, runOpenUserViewDialogFlow,
 *   closeUserOverlay, clearUserFilter).
 * Outputs: exports closeUserOverlay, closeUserOverlayFromEdit, clearUserFilter,
 *   runNavigateToUsersTabFlow, runFilterUserByEmailFlow, runOpenUserViewDialogFlow,
 *   runOpenUserEditViaViewFlow, runOpenUserEditViaListFlow, runFillUserRoleFlow,
 *   runSaveUserEditFlow, runPromoteUserFlow, runVerifyOuterLinkClosesToListFlow.
 * Returns: N/A
 * ──────────────────────────────────────────────────────────────────────────── */
const { expect } = require('@playwright/test');
const { screenshot, assertComputedColor, assertRightAligned } = require('../_helpers');
const { closeNotification } = require('../_helpers');
const { openEntityActivity, closeEntityActivity } = require('./entity-activity.flow');

// Expected computed colors per role -- must match user-grid.css's role badge colors exactly.
const ROLE_COLOR = {
  admin:     'rgb(29, 78, 216)',
  user:      'rgb(21, 128, 61)',
  moderator: 'rgb(194, 65, 12)',
};

/**
 * Closes the currently-open user overlay (view or edit mode) via its close (X) button.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<void>}
 */
async function closeUserOverlay(page) {
  await closeEntityActivity(page);
  await page.locator('.account-overlay vaadin-button')
    .filter({ has: page.locator('vaadin-icon[icon="vaadin:close"]') })
    .first()
    .click();
  await page.locator('.account-overlay.overlay--visible').waitFor({ state: 'hidden', timeout: 5000 });
}

/**
 * Clears the active filter on the Users grid via the query block's Clear button.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<void>}
 */
async function clearUserFilter(page) {
  await page.locator('.user-query-block vaadin-button[title*="Clear"], .user-query-block vaadin-button[title*="Очистити"]').click();
  await page.locator('.user-list-layout .query-status-bar').click();
  await page.locator('.user-query-block').waitFor({ state: 'hidden', timeout: 3000 });
}

/**
 * Clicks the Users tab and waits for the user list layout to render.
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Expect} expect
 * @returns {Promise<void>}
 */
async function runNavigateToUsersTabFlow(page, expect) {
  await page.locator('vaadin-tab').filter({ hasText: /Users|Користувачі/i }).first().click();
  await page.locator('.user-list-layout').waitFor({ timeout: 5000 });
  await screenshot(page, 'user-management-users-tab');
}

/**
 * Opens the Users query panel, fills the Email filter, and applies it.
 * @param {import('@playwright/test').Page} page
 * @param {string} email
 * @returns {Promise<void>}
 */
async function runFilterUserByEmailFlow(page, email) {
  await page.locator('.user-list-layout .query-status-bar').click();
  await page.locator('.user-query-block').waitFor({ timeout: 5000 });
  await page.locator('.user-query-block vaadin-text-field[placeholder="Email"] input').fill(email);
  await page.locator('.user-query-block vaadin-button[title*="Apply"], .user-query-block vaadin-button[title*="Застосувати"]').click();
}

/**
 * Filters the grid by email, then clicks the matching row's name to open the user in VIEW mode.
 * @param {import('@playwright/test').Page} page
 * @param {string} email
 * @returns {Promise<void>}
 */
async function runOpenUserViewDialogFlow(page, email) {
  await runFilterUserByEmailFlow(page, email);
  await page.locator('.user-grid-name:visible').first().waitFor({ timeout: 5000 });
  await page.locator('.user-grid-name:visible').first().click();
  await page.locator('.account-overlay.overlay--visible').waitFor({ timeout: 5000 });
  await screenshot(page, 'user-management-view-dialog-opened');
}

/**
 * Opens a user in VIEW mode then clicks Edit, landing in EDIT mode with a 2-deep breadcrumb.
 * @param {import('@playwright/test').Page} page
 * @param {string} email
 * @returns {Promise<void>}
 */
async function runOpenUserEditViaViewFlow(page, email) {
  await runOpenUserViewDialogFlow(page, email);
  await page.locator('.account-overlay vaadin-button').filter({ hasText: /Edit|Редагувати/ }).click();
  await page.locator('.account-overlay vaadin-combo-box').waitFor({ timeout: 5000 });
  await expect(page.locator('.account-overlay .overlay__breadcrumb-back')).toHaveCount(2, { timeout: 3000 });
  await screenshot(page, 'user-management-promote-dialog-opened');
}

/**
 * Filters the grid by email, then clicks the row's own Edit action, landing directly in EDIT
 * mode with a 1-deep breadcrumb (no intermediate VIEW step).
 * @param {import('@playwright/test').Page} page
 * @param {string} email
 * @returns {Promise<void>}
 */
async function runOpenUserEditViaListFlow(page, email) {
  await runFilterUserByEmailFlow(page, email);
  const editButton = page.locator('.user-grid-actions vaadin-button[title="Edit"], .user-grid-actions vaadin-button[title="Редагувати"]').first();
  await editButton.waitFor({ timeout: 5000 });
  await editButton.click();
  await page.locator('.account-overlay.overlay--visible').waitFor({ timeout: 5000 });
  await expect(page.locator('.account-overlay .overlay__breadcrumb-back')).toHaveCount(1, { timeout: 3000 });
  await page.locator('.account-overlay vaadin-combo-box').waitFor({ timeout: 5000 });
  await screenshot(page, 'user-management-promote-dialog-opened');
}

/**
 * Fills the user edit form's name field and/or role combo-box, leaving either untouched if null.
 * @param {import('@playwright/test').Page} page
 * @param {{role?: string, name?: string}} fields
 * @returns {Promise<void>}
 */
async function runFillUserRoleFlow(page, { role, name }) {
  if (name != null) {
    const nameField = page.locator('.account-overlay vaadin-text-field input');
    await nameField.fill(name);
  }
  if (role != null) {
    const roleCombo = page.locator('.account-overlay vaadin-combo-box');
    await roleCombo.locator('input').click();
    await roleCombo.locator('input').fill(role);
    await page.keyboard.press('ArrowDown');
    await page.keyboard.press('Enter');
  }
  await screenshot(page, 'user-management-promote-dialog-filled');
}


/**
 * Saves the user edit form, waits for the success notification, screenshots, and dismisses it.
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Expect} expect
 * @param {string} role used only to name the screenshot.
 * @returns {Promise<void>}
 */
async function runSaveUserEditFlow(page, expect, role) {
  await expect(page.locator('.account-overlay vaadin-button').filter({ hasText: /Save|Зберегти/i })).toBeEnabled({ timeout: 5000 });
  await page.locator('.account-overlay vaadin-button').filter({ hasText: /Save|Зберегти/i }).click();
  await expect(page.locator('vaadin-notification-container')).toContainText(
    /User updated successfully|Користувача успішно оновлено/i,
    { timeout: 5000 }
  );
  await screenshot(page, `user-management-promoted-${role.toLowerCase()}`);
  await closeNotification(page);
}

/**
 * Closes the user overlay from EDIT mode, waiting for it to settle back to VIEW first.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<void>}
 */
async function closeUserOverlayFromEdit(page) {
  await closeEntityActivity(page);
  await page.locator('.account-overlay vaadin-button')
    .filter({ has: page.locator('vaadin-icon[icon="vaadin:close"]') })
    .first().click();
  await page.locator('.account-overlay vaadin-button').filter({ hasText: /Edit|Редагувати/ }).waitFor({ state: 'visible', timeout: 5000 });
  await closeUserOverlay(page);
}

/**
 * End-to-end role-promotion flow: opens edit via View, fills role/name, saves, verifies the
 * activity list's v2 updated / v1 created rows (diff fields, actor/timestamp alignment), verifies
 * the role badge and its border color in VIEW mode, verifies the grid's role badge, then clears
 * the filter.
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Expect} expect
 * @param {{name: string, email: string}} user
 * @param {{role?: string|null, name?: string|null}} [options]
 * @returns {Promise<void>}
 */
async function runPromoteUserFlow(page, expect, user, { role = null, name = null } = {}) {
  await runOpenUserEditViaViewFlow(page, user.email);
  await runFillUserRoleFlow(page, { role, name });
  await runSaveUserEditFlow(page, expect, role);

  // Check activity in EDIT overlay — v2 updated + v1 created
  const activityList = await openEntityActivity(page, '.user-history-button');
  await expect(activityList.locator('.entity-activity-row')).toHaveCount(2, { timeout: 5000 });

  const row0 = activityList.locator('.entity-activity-row').nth(0);
  await expect(row0.locator('.entity-activity-action')).toContainText(/updated/i);
  await expect(row0.locator('.entity-activity-version')).toContainText('v2');
  // The actor name and the timestamp must both sit flush against the row's right edge.
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
  await closeEntityActivity(page);
  await page.locator('.account-overlay vaadin-button')
    .filter({ has: page.locator('vaadin-icon[icon="vaadin:close"]') })
    .first().click();
  await page.locator('.account-overlay vaadin-button').filter({ hasText: /Edit|Редагувати/ }).waitFor({ state: 'visible', timeout: 5000 });

  // Check role in VIEW mode
  await expect(page.locator('.account-overlay .user-role-badge')).toContainText(role);
  // The view-card's accent border must match the role badge color -- header text stays static.
  const roleClass = role.toLowerCase();
  const expectedColor = ROLE_COLOR[roleClass];
  const viewCard = page.locator(`.account-overlay .user-view-card.user-view-card--${roleClass}`);
  await expect(viewCard).toHaveCount(1);
  await assertComputedColor(expect, viewCard, 'borderTopColor', expectedColor);
  await expect(page.locator(`.account-overlay .overlay__view-card-header.overlay__view-card-header--${roleClass}`)).toHaveCount(1);
  await screenshot(page, `user-management-promoted-${roleClass}-view`);

  // VIEW → close
  await closeUserOverlay(page);

  // Check role in grid (filter still active)
  await expect(page.locator('.user-list-layout .user-role-badge:visible').first()).toContainText(role);
  await screenshot(page, `user-management-promoted-${role.toLowerCase()}-grid`);

  await clearUserFilter(page);
}

/**
 * Verifies the outer breadcrumb link closes all the way to the Users list, even when the overlay
 * was entered via View (not directly via Edit).
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Expect} expect
 * @param {string} email
 * @returns {Promise<void>}
 */
async function runVerifyOuterLinkClosesToListFlow(page, expect, email) {
  await runOpenUserEditViaViewFlow(page, email);
  await openEntityActivity(page, '.user-history-button');
  await closeEntityActivity(page, 'outer');
  await expect(page.locator('.base-overlay.overlay--visible')).toHaveCount(0, { timeout: 5000 });
  await expect(page.locator('.user-list-layout')).toBeVisible({ timeout: 5000 });
  await screenshot(page, 'user-management-outer-link-to-list');
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
  runVerifyOuterLinkClosesToListFlow,
};
