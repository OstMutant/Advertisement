const { test, expect, screenshot, closeNotification, TEST_USERS } = require('./_helpers');
const { runFillLoginFormFlow, runSubmitLoginFlow, runLogoutFlow } = require('./_flows/auth.flow');
const { runSwitchToUkrainianLoggedInFlow } = require('./_flows/language-switch.flow');
const { runNavigateToUsersTabFlow, runPromoteUserFlow, runOpenUserEditViaListFlow, runOpenUserEditViaViewFlow, runFillUserRoleFlow, runSaveUserEditFlow, clearUserFilter, closeUserOverlay, closeUserOverlayFromEdit } = require('./_flows/user-management.flow');
const { openTimelineTab, assertFeedHasRow, assertTimelineHasRows } = require('./_flows/timeline.flow');
const { runSignUpFlow } = require('./_flows/signup.flow');
const { loginBulk, logoutBulk } = require('./_flows/seed.flow');

// Section 3 helpers — taxon management
async function openRefDataTab(page) {
  await page.locator('vaadin-tab').filter({ hasText: 'Reference Data' }).click();
  await page.locator('.taxon-management-view').waitFor({ timeout: 8000 });
}

async function waitForTaxonOverlay(page) {
  await page.locator('.taxon-overlay.overlay--visible').waitFor({ timeout: 8000 });
}

async function closeTaxonOverlay(page) {
  await page.locator('.taxon-overlay vaadin-button')
    .filter({ has: page.locator('vaadin-icon[icon="vaadin:close"]') })
    .click();
  await page.locator('.taxon-overlay.overlay--visible').waitFor({ state: 'hidden', timeout: 8000 });
}

async function fillTaxonLocale(page, locale, name, desc) {
  const overlay = page.locator('.taxon-overlay');
  const idx     = locale === 'EN' ? 0 : 1;
  const content = overlay.locator('.taxon-locale-content').nth(idx);
  await content.locator('vaadin-text-field input').fill(name);
  await content.locator('vaadin-text-area textarea').fill(desc);
}

async function createCategory(page, { nameEn, descEn, nameUk, descUk }) {
  await page.locator('.taxon-add-button').click();
  await waitForTaxonOverlay(page);
  await fillTaxonLocale(page, 'EN', nameEn, descEn);
  await fillTaxonLocale(page, 'UK', nameUk, descUk);
  await page.locator('.taxon-overlay vaadin-button').filter({ hasText: 'Save' }).click();
  await expect(page.locator('vaadin-notification-card')).toBeVisible({ timeout: 5000 });
  await closeNotification(page);
  await closeTaxonOverlay(page);
}

async function openTaxonEdit(page, name) {
  const row = page.locator('.taxon-row-wrapper')
    .filter({ has: page.locator('.taxon-row-name', { hasText: name }) });
  await row.locator('vaadin-button')
    .filter({ has: page.locator('vaadin-icon[icon="vaadin:pencil"]') })
    .click();
  await waitForTaxonOverlay(page);
}

const CAT1 = { nameEn: 'Electronics', descEn: 'Electronic devices and accessories',         nameUk: 'Електроніка', descUk: 'Електронні пристрої та аксесуари' };
const CAT2 = { nameEn: 'Vehicles',    descEn: 'Cars, motorcycles and other vehicles',       nameUk: 'Транспорт',   descUk: 'Автомобілі, мотоцикли та інший транспорт' };

test.describe.configure({ mode: 'serial' });

test.describe('Promotion flow', () => {
  let page;

  test.beforeAll(async ({ browser }) => {
    page = await browser.newPage();
    await page.goto('/');
  });

  test.afterAll(async () => {
    await page.close();
  });

  // === Section 1: Role promotion ===

  test('adminEn promotes moderatorUk to MODERATOR — activity shows updated role, role badge in view and grid', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.adminEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.adminEn);
    await runNavigateToUsersTabFlow(page, expect);
    await runPromoteUserFlow(page, expect, TEST_USERS.moderatorUk, { role: 'MODERATOR' });
    await openTimelineTab(page);
    await assertFeedHasRow(page, expect, { action: 'updated', entityType: 'user', screenshotName: 'timeline-moderatoruk-promoted' });
    await runLogoutFlow(page, expect);
  });

  test('adminEn promotes moderatorEn to MODERATOR — activity shows updated role, role badge in view and grid', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.adminEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.adminEn);
    await runNavigateToUsersTabFlow(page, expect);
    await runPromoteUserFlow(page, expect, TEST_USERS.moderatorEn, { role: 'MODERATOR' });
    await openTimelineTab(page);
    await assertFeedHasRow(page, expect, { action: 'updated', entityType: 'user', screenshotName: 'timeline-moderatoren-promoted' });
    await runLogoutFlow(page, expect);
  });

  test('adminEn promotes adminUk to ADMIN — activity shows updated role, role badge in view and grid', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.adminEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.adminEn);
    await runNavigateToUsersTabFlow(page, expect);
    await runPromoteUserFlow(page, expect, TEST_USERS.adminUk, { role: 'ADMIN' });
    await openTimelineTab(page);
    await assertFeedHasRow(page, expect, { action: 'updated', entityType: 'user', screenshotName: 'timeline-adminuk-promoted' });
    await runLogoutFlow(page, expect);
  });

  // === Section 2: Set UK locales ===

  test('userUk — first login in English, switches to Ukrainian locale', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.userUk);
    await runSubmitLoginFlow(page, expect, { ...TEST_USERS.userUk, locale: 'en' });
    await runSwitchToUkrainianLoggedInFlow(page, expect);
    await runLogoutFlow(page, expect);
  });

  test('moderatorUk — first login in English, switches to Ukrainian locale', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.moderatorUk);
    await runSubmitLoginFlow(page, expect, { ...TEST_USERS.moderatorUk, locale: 'en' });
    await runSwitchToUkrainianLoggedInFlow(page, expect);
    await runLogoutFlow(page, expect);
  });

  test('adminEn edits userEn name — activity diff, grid updated, restore reverts name, userEn relogin after edit', async () => {
    const editedName = `AdminCrossEdit-${Date.now()}`;
    let originalName;

    await runFillLoginFormFlow(page, TEST_USERS.adminEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.adminEn);
    await runNavigateToUsersTabFlow(page, expect);

    await test.step('admin renames userEn — grid reflects new name', async () => {
      await runOpenUserEditViaListFlow(page, TEST_USERS.userEn.email);
      originalName = await page.locator('.user-overlay vaadin-text-field input').first().inputValue();
      await runFillUserRoleFlow(page, { name: editedName });
      await runSaveUserEditFlow(page, expect, 'cross-actor');
      await closeUserOverlay(page);
      await expect(page.locator('.user-grid-name:visible').filter({ hasText: editedName })).toHaveCount(1, { timeout: 5000 });
      await screenshot(page, 'cross-actor-01-user-renamed');
      await clearUserFilter(page);
    });

    await test.step('cross-actor edit visible in userEn profile activity', async () => {
      await runOpenUserEditViaViewFlow(page, TEST_USERS.userEn.email);
      await page.locator('.user-overlay vaadin-tab').filter({ hasText: /activity|активність/i }).click();
      const activityList = page.locator('.user-overlay .entity-activity-list');
      await activityList.waitFor({ timeout: 5000 });
      const latestRow = activityList.locator('.entity-activity-row').nth(0);
      await expect(latestRow.locator('.entity-activity-action')).toContainText(/updated|оновлено/i);
      await expect(latestRow.locator('.entity-activity-changes')).toContainText(editedName);
      await expect(activityList.locator('.entity-activity-restore-btn').first()).toBeVisible({ timeout: 5000 });
      await screenshot(page, 'cross-actor-02-profile-activity');
    });

    await test.step('restore reverts name — activity records restore entry', async () => {
      const activityList = page.locator('.user-overlay .entity-activity-list');
      await activityList.locator('.entity-activity-restore-btn').first().click();
      await runSaveUserEditFlow(page, expect, 'cross-actor-restore');
      await page.locator('.user-overlay vaadin-tab').filter({ hasText: /activity|активність/i }).click();
      await activityList.waitFor({ timeout: 5000 });
      const rowCount = await activityList.locator('.entity-activity-row').count();
      expect(rowCount).toBeGreaterThanOrEqual(2);
      await expect(activityList.locator('.entity-activity-row').nth(0).locator('.entity-activity-changes')).toContainText(originalName);
      await screenshot(page, 'cross-actor-03-activity-after-restore');
      await closeUserOverlayFromEdit(page);
    });

    await test.step('grid shows original name after restore', async () => {
      await expect(page.locator('.user-grid-name:visible').filter({ hasText: editedName })).toHaveCount(0, { timeout: 5000 });
      await expect(page.locator('.user-grid-name:visible').filter({ hasText: originalName })).toHaveCount(1, { timeout: 5000 });
      await screenshot(page, 'cross-actor-04-name-reverted');
      await clearUserFilter(page);
    });

    await openTimelineTab(page);
    await assertFeedHasRow(page, expect, { action: 'updated', entityType: 'user', screenshotName: 'timeline-cross-actor-restore' });
    await runLogoutFlow(page, expect);

    await test.step('userEn logs in with original password — profile edit and restore did not touch password hash', async () => {
      await runFillLoginFormFlow(page, TEST_USERS.userEn);
      await runSubmitLoginFlow(page, expect, TEST_USERS.userEn);
      await runLogoutFlow(page, expect);
    });
  });

  // === Section 3: Reference Data — Category management ===

  test('adminEn creates categories Electronics and Vehicles — both in list, create discard clears form', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.adminEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.adminEn);
    await openRefDataTab(page);
    await screenshot(page, 'taxon-00-ref-data-tab');

    await test.step('discard in CREATE mode — fields clear, save button disabled', async () => {
      await page.locator('.taxon-add-button').click();
      await waitForTaxonOverlay(page);
      await fillTaxonLocale(page, 'EN', 'Temp Name', 'Temp description long enough to be valid here');
      const overlay    = page.locator('.taxon-overlay');
      const saveBtn    = overlay.locator('vaadin-button').filter({ hasText: 'Save' });
      const discardBtn = overlay.locator('vaadin-button').filter({ hasText: 'Discard changes' });
      await expect(saveBtn).toBeEnabled({ timeout: 5000 });
      await expect(discardBtn).toBeEnabled();
      await screenshot(page, 'taxon-01-create-discard-filled');
      await discardBtn.click();
      await expect(saveBtn).toBeDisabled({ timeout: 3000 });
      await expect(overlay.locator('.taxon-locale-content').nth(0).locator('vaadin-text-field input')).toHaveValue('', { timeout: 3000 });
      await screenshot(page, 'taxon-01-create-discard-cleared');
      await closeTaxonOverlay(page);
    });

    await test.step('discard after CREATE save — fields revert to saved values, not empty', async () => {
      await page.locator('.taxon-add-button').click();
      await waitForTaxonOverlay(page);
      await fillTaxonLocale(page, 'EN', 'TempCat', 'Temporary category for discard test');
      await fillTaxonLocale(page, 'UK', 'ТимчасCat', 'Тимчасова категорія для тесту');
      const overlay    = page.locator('.taxon-overlay');
      const saveBtn    = overlay.locator('vaadin-button').filter({ hasText: 'Save' });
      const discardBtn = overlay.locator('vaadin-button').filter({ hasText: 'Discard changes' });
      await saveBtn.click();
      await expect(page.locator('vaadin-notification-card')).toBeVisible({ timeout: 5000 });
      await closeNotification(page);
      await expect(saveBtn).toBeDisabled({ timeout: 3000 });
      // modify after save — then discard must revert to saved values, not empty
      await overlay.locator('.taxon-locale-content').nth(0).locator('vaadin-text-field input').fill('TempCat MODIFIED');
      await expect(saveBtn).toBeEnabled({ timeout: 3000 });
      await discardBtn.click();
      await expect(overlay.locator('.taxon-locale-content').nth(0).locator('vaadin-text-field input'))
        .toHaveValue('TempCat', { timeout: 3000 });
      await expect(saveBtn).toBeDisabled({ timeout: 3000 });
      await closeTaxonOverlay(page);
    });

    await test.step('create Electronics', async () => {
      await createCategory(page, CAT1);
      await expect(page.locator('.taxon-row-name', { hasText: CAT1.nameEn })).toBeVisible({ timeout: 5000 });
    });

    await test.step('create Vehicles', async () => {
      await createCategory(page, CAT2);
      await expect(page.locator('.taxon-row-name', { hasText: CAT2.nameEn })).toBeVisible({ timeout: 5000 });
    });

    await expect(page.locator('.taxon-row-name', { hasText: CAT1.nameEn })).toBeVisible();
    await expect(page.locator('.taxon-row-name', { hasText: CAT2.nameEn })).toBeVisible();
    await screenshot(page, 'taxon-02-two-categories-in-list');

    await runLogoutFlow(page, expect);
  });

  test('adminEn edits Electronics — edit discard reverts, save records activity, restore reverts name, all fields in timeline diff, delete and restore recorded in activity', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.adminEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.adminEn);
    await openRefDataTab(page);

    await test.step('discard in EDIT mode — modified name reverts to original', async () => {
      await openTaxonEdit(page, CAT1.nameEn);
      const overlay = page.locator('.taxon-overlay');
      await expect(overlay.locator('.taxon-locale-content').nth(0).locator('vaadin-text-field input')).toHaveValue(CAT1.nameEn, { timeout: 5000 });
      await overlay.locator('.taxon-locale-content').nth(0).locator('vaadin-text-field input').fill('Electronics MODIFIED');
      const saveBtn    = overlay.locator('vaadin-button').filter({ hasText: 'Save' });
      const discardBtn = overlay.locator('vaadin-button').filter({ hasText: 'Discard changes' });
      await expect(saveBtn).toBeEnabled({ timeout: 5000 });
      await screenshot(page, 'taxon-03-edit-discard-modified');
      await discardBtn.click();
      await expect(overlay.locator('.taxon-locale-content').nth(0).locator('vaadin-text-field input')).toHaveValue(CAT1.nameEn, { timeout: 5000 });
      await expect(saveBtn).toBeDisabled({ timeout: 3000 });
      await screenshot(page, 'taxon-03-edit-discard-reverted');
      await closeTaxonOverlay(page);
    });

    await test.step('edit Electronics name and save — activity shows v2 update row', async () => {
      await openTaxonEdit(page, CAT1.nameEn);
      const overlay = page.locator('.taxon-overlay');
      await overlay.locator('.taxon-locale-content').nth(0).locator('vaadin-text-field input').fill('Electronics v2');
      const saveBtn = overlay.locator('vaadin-button').filter({ hasText: 'Save' });
      await expect(saveBtn).toBeEnabled({ timeout: 5000 });
      await saveBtn.click();
      await expect(page.locator('vaadin-notification-card')).toBeVisible({ timeout: 5000 });
      await closeNotification(page);
      await screenshot(page, 'taxon-04-edit-saved');

      await overlay.locator('vaadin-tab').filter({ hasText: 'Activity' }).click();
      const activityList = overlay.locator('.entity-activity-list');
      await activityList.waitFor({ timeout: 5000 });
      await expect(activityList.locator('.entity-activity-row')).toHaveCount(2, { timeout: 8000 });
      await expect(activityList.locator('.entity-activity-row').nth(0).locator('.entity-activity-action')).toContainText(/updated|оновлено/i);
      await expect(activityList.locator('.entity-activity-row').nth(0).locator('.entity-activity-version')).toContainText('v2');
      await expect(activityList.locator('.entity-activity-row').nth(1).locator('.entity-activity-version')).toContainText('v1');
      await screenshot(page, 'taxon-04-activity-two-rows');
    });

    await test.step('restore from activity v1 — form reverts to original name, save applies restore', async () => {
      const overlay      = page.locator('.taxon-overlay');
      const activityList = overlay.locator('.entity-activity-list');
      const v1Row        = activityList.locator('.entity-activity-row').nth(1);
      await v1Row.locator('.entity-activity-restore-btn').click();
      await expect(page.locator('vaadin-notification-card')).toBeVisible({ timeout: 5000 });
      await closeNotification(page);
      // form switches back to Edit tab with original name
      await expect(overlay.locator('.taxon-locale-content').nth(0).locator('vaadin-text-field input')).toHaveValue(CAT1.nameEn, { timeout: 5000 });
      await screenshot(page, 'taxon-05-restored-form');
      const saveBtn = overlay.locator('vaadin-button').filter({ hasText: 'Save' });
      await expect(saveBtn).toBeEnabled();
      await saveBtn.click();
      await expect(page.locator('vaadin-notification-card')).toBeVisible({ timeout: 5000 });
      await closeNotification(page);
      await closeTaxonOverlay(page);
      await expect(page.locator('.taxon-row-name', { hasText: CAT1.nameEn })).toBeVisible({ timeout: 5000 });
      await screenshot(page, 'taxon-05-restored-in-list');
    });

    await test.step('timeline — taxon created and updated entries visible, single-field edit shows all 4 fields in diff', async () => {
      await openTimelineTab(page);
      await assertTimelineHasRows(page, expect, { entityType: 'taxon', action: 'created', minCount: 1, screenshotName: 'taxon-06-timeline-created' });
      await assertTimelineHasRows(page, expect, { entityType: 'taxon', action: 'updated', minCount: 1, screenshotName: 'taxon-06-timeline-updated' });

      // TaxonActivityFieldsHookImpl must expand all 4 fields even when only one changed
      const taxonUpdateRow = page.locator('.activity-feed .activity-feed-row')
        .filter({ has: page.locator('.activity-feed-action--updated') })
        .filter({ has: page.locator('.activity-feed-type--taxon') })
        .first();
      const changesDiv = taxonUpdateRow.locator('.activity-feed-changes');
      await expect(changesDiv).toContainText('Name (EN)', { timeout: 5000 });
      await expect(changesDiv).toContainText('Description (EN)');
      await expect(changesDiv).toContainText('Name (UK)');
      await expect(changesDiv).toContainText('Description (UK)');
      await screenshot(page, 'taxon-06-timeline-all-fields-in-diff');
    });

    await test.step('delete Electronics and restore — deleted and restore events recorded in overlay activity', async () => {
      await openRefDataTab(page);

      // Delete
      const activeRow = page.locator('.taxon-row-wrapper')
        .filter({ has: page.locator('.taxon-row-name', { hasText: CAT1.nameEn }) })
        .filter({ hasNot: page.locator('.taxon-deleted-badge:visible') });
      await activeRow.locator('vaadin-button')
        .filter({ has: page.locator('vaadin-icon[icon="vaadin:trash"]') })
        .click();
      await page.locator('vaadin-dialog-overlay').waitFor({ timeout: 5000 });
      await page.evaluate(() => {
        const dialog = document.querySelector('vaadin-dialog[opened]');
        [...dialog.querySelectorAll('vaadin-button')]
          .find(b => /^delete$|^видалити$/i.test(b.textContent?.trim()))
          ?.click();
      });
      await page.locator('vaadin-dialog-overlay').waitFor({ state: 'hidden', timeout: 5000 });
      await expect(page.locator('vaadin-notification-card')).toBeVisible({ timeout: 5000 });
      await closeNotification(page);
      await expect(page.locator('.taxon-row-wrapper').filter({ has: page.locator('.taxon-deleted-badge') })
        .filter({ has: page.locator('.taxon-row-name', { hasText: CAT1.nameEn }) })).toBeVisible({ timeout: 5000 });
      await screenshot(page, 'taxon-07-electronics-deleted');

      // Restore
      const deletedRow = page.locator('.taxon-row-wrapper')
        .filter({ has: page.locator('.taxon-row-name', { hasText: CAT1.nameEn }) });
      await deletedRow.locator('vaadin-button')
        .filter({ has: page.locator('vaadin-icon[icon="vaadin:arrow-backward"]') })
        .click();
      await expect(page.locator('vaadin-notification-card')).toBeVisible({ timeout: 5000 });
      await closeNotification(page);
      await expect(page.locator('.taxon-row-name', { hasText: CAT1.nameEn })).toBeVisible({ timeout: 5000 });
      await screenshot(page, 'taxon-07-electronics-restored');

      // Open edit overlay → Activity tab → verify deleted and restored events are present
      await openTaxonEdit(page, CAT1.nameEn);
      const overlay = page.locator('.taxon-overlay');
      await overlay.locator('vaadin-tab').filter({ hasText: 'Activity' }).click();
      const activityList = overlay.locator('.entity-activity-list');
      await activityList.waitFor({ timeout: 5000 });
      await expect(
        activityList.locator('.entity-activity-row')
          .filter({ has: page.locator('.entity-activity-action--deleted') })
          .first()
      ).toBeVisible({ timeout: 5000 });
      await expect(
        activityList.locator('.entity-activity-row')
          .filter({ has: page.locator('.entity-activity-action--restored') })
          .first()
      ).toBeVisible({ timeout: 5000 });
      await screenshot(page, 'taxon-07-activity-shows-deleted-and-restored');
      await closeTaxonOverlay(page);
    });

    await runLogoutFlow(page, expect);
  });
});

// ─── Boundary: max-content users + seed categories (PW_FULL only) ────────────

const MAX_NAME_100   = 'MaxBoundaryUserNameForValidationTest'.repeat(3).substring(0, 100);
const _emailLocal    = '0'.repeat(48);
const _emailSeg1     = 'max-domain-seg1-' + '0'.repeat(47);
const _emailSeg2     = 'max-domain-seg2-' + '0'.repeat(47);
const _emailSeg3     = 'max-domain-seg3-' + '0'.repeat(45);
const MAX_EMAIL_EN   = `max-boundary-en-${_emailLocal}@${_emailSeg1}.${_emailSeg2}.${_emailSeg3}`;
const MAX_EMAIL_UK   = `max-boundary-uk-${_emailLocal}@${_emailSeg1}.${_emailSeg2}.${_emailSeg3}`;
const MAX_EN         = { name: MAX_NAME_100, email: MAX_EMAIL_EN, password: 'password' };
const MAX_UK         = { name: MAX_NAME_100, email: MAX_EMAIL_UK, password: 'password' };

test.describe('Max-boundary users and categories', () => {
  test.skip(!process.env.PW_FULL, 'Skipped by default — run with --full for boundary tests');

  let page;

  test.beforeAll(async ({ browser }) => {
    page = await browser.newPage();
    await page.goto('/');
  });

  test.afterAll(async () => {
    await page.close();
  });

  test('maxEn signs up — 100-char name accepted, admin verifies user created', async () => {
    await runSignUpFlow(page, expect, MAX_EN);
    await loginBulk(page, TEST_USERS.adminEn);
    await runNavigateToUsersTabFlow(page, expect);
    await runOpenUserEditViaListFlow(page, MAX_EN.email);
    const nameInput = page.locator('.user-overlay vaadin-text-field input').first();
    await expect(nameInput).toHaveValue(MAX_NAME_100, { timeout: 5000 });
    await screenshot(page, 'max-01-en-user-name-100');
    await page.locator('.user-overlay vaadin-tab').filter({ hasText: /activity|активність/i }).click();
    const activityList = page.locator('.user-overlay .entity-activity-list');
    await activityList.waitFor({ timeout: 5000 });
    await expect(activityList.locator('.entity-activity-action--created').first()).toBeVisible({ timeout: 5000 });
    await screenshot(page, 'max-02-en-user-activity-created');
    await closeUserOverlay(page);
    await clearUserFilter(page);
    await logoutBulk(page);
  });

  test('maxUk signs up — 100-char name accepted, admin verifies user created', async () => {
    await runSignUpFlow(page, expect, MAX_UK);
    await loginBulk(page, TEST_USERS.adminEn);
    await runNavigateToUsersTabFlow(page, expect);
    await runOpenUserEditViaListFlow(page, MAX_UK.email);
    const nameInput = page.locator('.user-overlay vaadin-text-field input').first();
    await expect(nameInput).toHaveValue(MAX_NAME_100, { timeout: 5000 });
    await screenshot(page, 'max-03-uk-user-name-100');
    await page.locator('.user-overlay vaadin-tab').filter({ hasText: /activity|активність/i }).click();
    const activityList = page.locator('.user-overlay .entity-activity-list');
    await activityList.waitFor({ timeout: 5000 });
    await expect(activityList.locator('.entity-activity-action--created').first()).toBeVisible({ timeout: 5000 });
    await screenshot(page, 'max-04-uk-user-activity-created');
    await closeUserOverlay(page);
    await clearUserFilter(page);
    await logoutBulk(page);
  });

  test('adminEn seeds 10 boundary categories — for max category selection in spec 04', async () => {
    await loginBulk(page, TEST_USERS.adminEn);
    await openRefDataTab(page);
    for (let i = 1; i <= 10; i++) {
      const label = `Boundary-${String(i).padStart(2, '0')}`;
      await createCategory(page, { nameEn: label, descEn: `Boundary category ${i}`, nameUk: label, descUk: `Гранична категорія ${i}` });
    }
    await screenshot(page, 'max-05-boundary-categories-seeded');
    await logoutBulk(page);
  });
});
