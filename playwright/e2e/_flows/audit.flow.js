const { screenshot } = require('../_helpers');
const { runOpenUserEditViaListFlow, runOpenUserViewDialogFlow } = require('./user-management.flow');

async function runOpenSettingsFlow(page) {
  await page.locator('.header-settings-button').click();
  await page.locator('.settings-overlay').waitFor({ timeout: 5000 });
}

async function runCloseSettingsFlow(page) {
  await page.locator('.settings-overlay vaadin-button')
    .filter({ has: page.locator('vaadin-icon[icon="vaadin:close"]') })
    .first()
    .click();
  await page.locator('.settings-overlay').waitFor({ state: 'hidden', timeout: 5000 });
}

// rows: [{ action, version, actor }] — all fields optional
async function runVerifyEntityActivityFlow(page, expect, scope, { screenshotName, rows }) {
  const list = scope.locator('.entity-activity-list');
  await list.waitFor({ timeout: 5000 });
  await expect(list.locator('.entity-activity-row')).toHaveCount(rows.length, { timeout: 5000 });
  for (const [i, row] of rows.entries()) {
    const r = list.locator('.entity-activity-row').nth(i);
    if (row.action != null)  await expect(r.locator('.entity-activity-action')).toContainText(row.action);
    if (row.version != null) await expect(r.locator('.entity-activity-version')).toContainText(row.version);
    if (row.actor != null)   await expect(r.locator('.entity-activity-user')).toContainText(row.actor);
  }
  await screenshot(page, screenshotName);
}


async function runVerifySettingsActivityFlow(page, expect, { screenshotName, rows }) {
  await runOpenSettingsFlow(page);
  await page.locator('.settings-overlay vaadin-tab').filter({ hasText: /activity|активність/i }).click();
  await runVerifyEntityActivityFlow(page, expect, page.locator('.settings-overlay'), { screenshotName, rows });
  await runCloseSettingsFlow(page);
}

async function runVerifySettingsAfterSignupFlow(page, expect, { screenshotName }) {
  await runOpenSettingsFlow(page);

  const fields = page.locator('.settings-overlay-content vaadin-integer-field');
  const adsValue = await fields.nth(0).locator('input').inputValue();
  const usersValue = await fields.nth(1).locator('input').inputValue();
  await screenshot(page, `${screenshotName}-defaults`);

  await page.locator('.settings-overlay vaadin-tab').filter({ hasText: /activity|активність/i }).click();
  const activityList = page.locator('.settings-overlay .entity-activity-list');
  await activityList.waitFor({ timeout: 5000 });
  await expect(activityList.locator('.entity-activity-row')).toHaveCount(1, { timeout: 5000 });
  const row = activityList.locator('.entity-activity-row').nth(0);
  await expect(row.locator('.entity-activity-action')).toContainText(/created|створено/i);
  await expect(row.locator('.entity-activity-version')).toContainText('v1');
  await expect(row).toContainText(adsValue);
  await expect(row).toContainText(usersValue);
  await screenshot(page, `${screenshotName}-activity`);

  await runCloseSettingsFlow(page);
}

// Caller must navigate to Users tab before calling this
async function runVerifyUserAuditActivityFlow(page, expect, email, { screenshotName, rows }) {
  await runOpenUserEditViaListFlow(page, email);
  await page.locator('.user-overlay vaadin-tab').filter({ hasText: /activity|активність/i }).click();
  await runVerifyEntityActivityFlow(page, expect, page.locator('.user-overlay'), { screenshotName, rows });

  await page.locator('.user-overlay vaadin-button')
    .filter({ has: page.locator('vaadin-icon[icon="vaadin:close"]') })
    .first()
    .click();
  await page.locator('.user-overlay.overlay--visible').waitFor({ state: 'hidden', timeout: 5000 });

  await page.locator('.user-query-block vaadin-button[title*="Clear"], .user-query-block vaadin-button[title*="Очистити"]').click();
  await page.locator('.user-list-layout .query-status-bar').click();
  await page.locator('.user-query-block').waitFor({ state: 'hidden', timeout: 3000 });
}

module.exports = {
  runVerifySettingsActivityFlow,
  runVerifySettingsAfterSignupFlow,
  runVerifyUserAuditActivityFlow,
};
