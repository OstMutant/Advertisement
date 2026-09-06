/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Flow helpers for verifying audit/activity behavior -- opening/closing the Settings
 *   overlay, asserting an entity-activity list's rows match expected action/version/actor/changes,
 *   verifying the Settings audit trail right after signup, and verifying a given user's own audit
 *   activity from the Users tab.
 * Usage: None -- a library only, required by spec files (see Input).
 * Uses: ../_helpers (screenshot), ./user-management.flow, ./settings.flow, ./entity-activity.flow.
 * Env: None.
 * Input: required by 02-marketplace-authentication-flow.spec.js
 *   (runVerifySettingsAfterSignupFlow, runVerifyUserAuditActivityFlow), and by
 *   04-provider-profile-flow.spec.js (runOpenSettingsFlow, runCloseSettingsFlow).
 * Outputs: exports runOpenSettingsFlow, runCloseSettingsFlow, runVerifyEntityActivityFlow,
 *   runVerifySettingsAfterSignupFlow, runVerifyUserAuditActivityFlow.
 * Returns: N/A
 * ──────────────────────────────────────────────────────────────────────────── */
const { screenshot } = require('../_helpers');
const { runOpenUserEditViaListFlow, runOpenUserViewDialogFlow, closeUserOverlay, clearUserFilter } = require('./user-management.flow');
const { openHistory, closeHistory } = require('./settings.flow');
const { openEntityActivity } = require('./entity-activity.flow');

async function runOpenSettingsFlow(page) {
  await page.locator('.header-settings-button').click();
  await page.locator('.account-overlay').waitFor({ timeout: 5000 });
}

async function runCloseSettingsFlow(page) {
  await page.locator('.account-overlay vaadin-button')
    .filter({ has: page.locator('vaadin-icon[icon="vaadin:close"]') })
    .first()
    .click();
  await page.locator('.account-overlay').waitFor({ state: 'hidden', timeout: 5000 });
}

// rows: [{ action, version, actor, changesIncludes, hasUnchangedItem, hasDiff }] — all fields optional
async function runVerifyEntityActivityFlow(page, expect, scope, { screenshotName, rows }) {
  const list = scope.locator('.entity-activity-list');
  await list.waitFor({ timeout: 5000 });
  await expect(list.locator('.entity-activity-row')).toHaveCount(rows.length, { timeout: 5000 });
  for (const [i, row] of rows.entries()) {
    const r = list.locator('.entity-activity-row').nth(i);
    if (row.action != null)  await expect(r.locator('.entity-activity-action')).toContainText(row.action);
    if (row.version != null) await expect(r.locator('.entity-activity-version')).toContainText(row.version);
    if (row.actor != null)   await expect(r.locator('.entity-activity-user')).toContainText(row.actor);
    if (row.changesIncludes != null) {
      for (const text of row.changesIncludes) {
        await expect(r.locator('.entity-activity-changes-item').filter({ hasText: text }).first()).toBeVisible();
      }
    }
    if (row.hasUnchangedItem) {
      await expect(r.locator('.entity-activity-changes-item--unchanged').first()).toBeVisible();
    }
    if (row.hasDiff) {
      await expect(r.locator('.entity-activity-changes')).toContainText('→');
    }
  }
  await screenshot(page, screenshotName);
}


/**
 * Verifies the Settings overlay's default values right after signup, then opens its nested
 * history overlay and asserts the single "created" activity row reflects those same values.
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Expect} expect
 * @param {object} params
 * @param {string} params.screenshotName base name for the screenshots taken along the flow.
 * @param {boolean} [params.privileged=false] whether the actor sees moderator/admin-only settings.
 * @returns {Promise<void>}
 */
async function runVerifySettingsAfterSignupFlow(page, expect, { screenshotName, privileged = false }) {
  await runOpenSettingsFlow(page);

  const fields = page.locator('.settings-overlay-content vaadin-integer-field');
  await expect(fields).toHaveCount(privileged ? 3 : 1, { timeout: 5000 });
  const adsValue = await fields.nth(0).locator('input').inputValue();
  let usersValue, timelineValue;
  if (privileged) {
    usersValue = await fields.nth(1).locator('input').inputValue();
    timelineValue = await fields.nth(2).locator('input').inputValue();
  }
  await screenshot(page, `${screenshotName}-defaults`);

  // history is a nested overlay stacked over Settings now, not a tab inside it
  const activityList = await openHistory(page);
  await expect(activityList.locator('.entity-activity-row')).toHaveCount(1, { timeout: 5000 });
  const row = activityList.locator('.entity-activity-row').nth(0);
  await expect(row.locator('.entity-activity-action')).toContainText(/created|створено/i);
  await expect(row.locator('.entity-activity-version')).toContainText('v1');
  await expect(row).toContainText(adsValue);
  if (privileged) {
    await expect(row).toContainText(usersValue);
    await expect(row).toContainText(timelineValue);
  }
  await screenshot(page, `${screenshotName}-activity`);
  await closeHistory(page);

  await runCloseSettingsFlow(page);
}

/**
 * Opens a given user's edit form and its history overlay, verifies the expected activity rows,
 * then closes the overlay and clears the user filter. Caller must navigate to the Users tab first.
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Expect} expect
 * @param {string} email the target user's email, used to locate them in the Users list.
 * @param {object} params
 * @param {string} params.screenshotName base name for the screenshot taken after verification.
 * @param {Array<object>} params.rows expected activity rows, see runVerifyEntityActivityFlow.
 * @returns {Promise<void>}
 */
async function runVerifyUserAuditActivityFlow(page, expect, email, { screenshotName, rows }) {
  await runOpenUserEditViaListFlow(page, email);
  await openEntityActivity(page, '.user-history-button');
  await runVerifyEntityActivityFlow(page, expect, page.locator('.entity-activity-overlay'), { screenshotName, rows });

  await closeUserOverlay(page);
  await clearUserFilter(page);
}

module.exports = {
  runOpenSettingsFlow,
  runCloseSettingsFlow,
  runVerifyEntityActivityFlow,
  runVerifySettingsAfterSignupFlow,
  runVerifyUserAuditActivityFlow,
};
