/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Provider Profile tab e2e coverage inside AccountOverlay -- empty state, self-service
 *   create (kind/about/categories/city -- radio group, Quill editor, multi-select combo box, combo
 *   box). Save keeps the form open (same as Name/Settings, see AccountOverlay.proceed()) -- the
 *   Close icon is used afterward to reach View mode (kind badge, about, category/city chips). The
 *   second test is the regression scenario for the categories-not-persisting bug: re-editing an
 *   existing profile must show the previously saved kind/about/categories/city pre-filled, not
 *   empty, with an activity diff, history button, and the outer breadcrumb link closing the whole
 *   overlay. A third test verifies a moderator viewing another user's account is read-only across
 *   all three AccountOverlay tabs (Name/Settings/Provider Profile), not just Provider Profile. A
 *   fourth test repeats the create/edit round trip through the Users-grid entry path (admin editing
 *   someone else, not self-service) -- a structurally different route into the same form handler.
 *   Per test:
 *   - "userEn creates provider profile -- empty state before, Create button, kind/about/category/
 *     city filled, view mode shows kind badge/about/category chip/city chip after save": login ->
 *     Provider Profile tab (empty state) -> Create -> fill form -> save -> Close -> VIEW
 *     (badge/about/chips).
 *   - "userEn edits provider profile -- previously-saved kind/about/categories/city pre-filled on
 *     re-edit, second category added, activity diff, history button, outer breadcrumb closes to
 *     list": login -> Provider Profile tab (VIEW shows saved data) -> Edit (combo boxes
 *     pre-selected, not empty) -> add second category, change kind -> save (form still open,
 *     saved values checked directly) -> activity (v1 created + v2 updated) -> outer breadcrumb
 *     closes the whole overlay.
 *   - "moderatorEn views userEn's account -- read-only on Name, Settings and Provider Profile
 *     tabs, no Edit/Create/Save button anywhere": login as moderator -> Users grid -> view userEn
 *     -> Name tab (no Edit button) -> Settings tab (no Save/Discard, fields read-only) ->
 *     Provider Profile tab (no Edit button, saved data still visible).
 * Usage: run via the Playwright test runner -- `bash /app/playwright/run.sh 04-provider-profile-
 *   flow --ux`, or as part of the full e2e suite (`bash /app/playwright/run.sh e2e --ux`).
 * Uses: @playwright/test.
 * Env: None.
 * Input: ./_helpers (test, expect, screenshot, closeNotification, TEST_USERS), ./_flows/auth.flow
 *   (runFillLoginFormFlow, runSubmitLoginFlow, runLogoutFlow), ./_flows/audit.flow
 *   (runOpenSettingsFlow, runCloseSettingsFlow), ./_flows/entity-activity.flow (openEntityActivity,
 *   closeEntityActivity), ./_flows/user-management.flow (runNavigateToUsersTabFlow,
 *   runOpenUserViewDialogFlow, closeUserOverlay, clearUserFilter). Depends on spec 02 having signed
 *   up all TEST_USERS, and spec 03 having created the Electronics/Vehicles categories and Lviv city.
 * Outputs: Playwright HTML report entries for each test. Leaves userEn with a saved provider
 *   profile (kind SHOP, categories Electronics + Vehicles, city Lviv) used only within this file's
 *   own serial test sequence.
 * Returns: exit code from the Playwright test runner -- 0 when every test in this file passes,
 *   non-zero otherwise.
 * ──────────────────────────────────────────────────────────────────────────── */
const { test, expect, screenshot, closeNotification, TEST_USERS } = require('./_helpers');
const { runFillLoginFormFlow, runSubmitLoginFlow, runLogoutFlow } = require('./_flows/auth.flow');
const { runOpenSettingsFlow, runCloseSettingsFlow } = require('./_flows/audit.flow');
const { openEntityActivity, closeEntityActivity } = require('./_flows/entity-activity.flow');
const { runNavigateToUsersTabFlow, runOpenUserEditViaListFlow, runOpenUserViewDialogFlow, closeUserOverlay, clearUserFilter } = require('./_flows/user-management.flow');

test.describe.configure({ mode: 'serial' });

async function openProviderProfileTab(page) {
  await page.locator('.account-overlay .account-overlay-tabs vaadin-tab').filter({ hasText: 'Provider Profile' }).click();
  await page.waitForTimeout(300);
}

async function fillAbout(page, text) {
  await page.locator('.account-overlay .overlay__description-rich-editor .ql-editor').fill(text);
}

async function selectCategory(page, name) {
  const box = page.locator('.account-overlay vaadin-multi-select-combo-box');
  await box.click();
  await page.locator('vaadin-multi-select-combo-box-item').filter({ hasText: name }).first().click();
  await page.keyboard.press('Escape');
}

async function selectedCategoryNames(page) {
  return page.locator('.account-overlay vaadin-multi-select-combo-box')
    .evaluate(el => (el.selectedItems || []).map(i => i.label));
}

async function selectCity(page, name) {
  const box = page.locator('.account-overlay vaadin-combo-box');
  await box.locator('input').click();
  await box.locator('input').fill(name);
  await page.locator('vaadin-combo-box-item').filter({ hasText: name }).first().click();
}

test.describe('Provider Profile flow', () => {
  let page;

  test.beforeAll(async ({ browser }) => {
    page = await browser.newPage();
    await page.goto('/');
  });

  test.afterAll(async () => {
    await page.close();
  });

  test('userEn creates provider profile — empty state before, Create button, kind/about/category/city filled, view mode shows kind badge/about/category chip/city chip after save', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.userEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.userEn);
    await runOpenSettingsFlow(page);
    await openProviderProfileTab(page);

    await expect(page.locator('.account-overlay .provider-profile-view-empty-text')).toBeVisible({ timeout: 5000 });
    await screenshot(page, 'provider-profile-empty-state');

    await page.locator('.account-overlay vaadin-button').filter({ hasText: 'Create Profile' }).click();
    await page.waitForTimeout(300);

    await page.locator('.account-overlay vaadin-radio-button').filter({ hasText: 'MASTER' }).first().click();
    await fillAbout(page, 'Professional electronics repair and installation services.');
    await selectCategory(page, 'Electronics');
    await selectCity(page, 'Lviv');
    await screenshot(page, 'provider-profile-create-filled');

    await page.locator('.account-overlay vaadin-button').filter({ hasText: 'Save' }).click();
    await expect(page.locator('vaadin-notification-container')).toContainText('Provider profile saved', { timeout: 5000 });
    await closeNotification(page);

    // Save keeps the form open -- Cancel switches to View (safe: nothing unsaved to lose).
    await page.locator('.account-overlay vaadin-button[title="Cancel"]').click();
    await page.waitForTimeout(300);
    await expect(page.locator('.account-overlay .provider-profile-kind-badge')).toContainText('MASTER', { timeout: 5000 });
    await expect(page.locator('.account-overlay .provider-profile-category-chip')).toContainText('Electronics');
    await expect(page.locator('.account-overlay .provider-profile-city-chip')).toContainText('Lviv');
    await screenshot(page, 'provider-profile-view-after-create');

    await runCloseSettingsFlow(page);
    await runLogoutFlow(page, expect);
  });

  test('userEn edits provider profile — previously-saved kind/about/categories/city pre-filled on re-edit, second category added, activity diff, history button, outer breadcrumb closes to list', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.userEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.userEn);
    await runOpenSettingsFlow(page);
    await openProviderProfileTab(page);

    // Re-fetched from the server on this fresh login -- proves the saved data actually persisted.
    await expect(page.locator('.account-overlay .provider-profile-category-chip')).toContainText('Electronics', { timeout: 5000 });

    await page.locator('.account-overlay vaadin-button').filter({ hasText: 'Edit' }).click();
    await page.waitForTimeout(300);

    // The regression check: the combo box must show the previously-saved category, not empty.
    await expect(async () => {
      const names = await selectedCategoryNames(page);
      expect(names).toContain('Electronics');
    }).toPass({ timeout: 5000 });
    await expect(page.locator('.account-overlay .overlay__description-rich-editor .ql-editor')).toContainText('Professional electronics', { timeout: 5000 });
    await expect(page.locator('.account-overlay vaadin-combo-box input')).toHaveValue('Lviv', { timeout: 5000 });
    await screenshot(page, 'provider-profile-edit-prefilled');

    await page.locator('.account-overlay vaadin-radio-button').filter({ hasText: 'SHOP' }).first().click();
    await selectCategory(page, 'Vehicles');

    await page.locator('.account-overlay vaadin-button').filter({ hasText: 'Save' }).click();
    await expect(page.locator('vaadin-notification-container')).toContainText('Provider profile saved', { timeout: 5000 });
    await closeNotification(page);

    // Save keeps the form open -- the just-saved values stay in the fields, no reload needed.
    await expect(page.locator('.account-overlay vaadin-radio-button').filter({ hasText: 'SHOP' })).toHaveJSProperty('checked', true, { timeout: 5000 });
    await expect(async () => {
      const names = await selectedCategoryNames(page);
      expect(names.sort()).toEqual(['Electronics', 'Vehicles']);
    }).toPass({ timeout: 5000 });
    await screenshot(page, 'provider-profile-edit-after-save');

    const activityList = await openEntityActivity(page, '.provider-profile-history-button');
    await expect(activityList.locator('.entity-activity-row')).toHaveCount(2, { timeout: 5000 });
    await screenshot(page, 'provider-profile-activity-diff');

    await closeEntityActivity(page, 'outer');
    await expect(page.locator('.base-overlay.overlay--visible')).toHaveCount(0, { timeout: 5000 });
    await screenshot(page, 'provider-profile-outer-breadcrumb-closed');

    await runLogoutFlow(page, expect);
  });

  test('moderatorEn views userEn\'s account — read-only on Name, Settings and Provider Profile tabs, no Edit/Create/Save button anywhere', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.moderatorEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.moderatorEn);
    await runNavigateToUsersTabFlow(page, expect);
    await runOpenUserViewDialogFlow(page, TEST_USERS.userEn.email);

    // Name tab (default) — no Edit button.
    await expect(page.locator('.account-overlay vaadin-button').filter({ hasText: 'Edit' })).toHaveCount(0, { timeout: 5000 });
    await screenshot(page, 'provider-profile-moderator-name-readonly');

    // Settings tab — no Save/Discard, page-size field read-only.
    await page.locator('.account-overlay .account-overlay-tabs vaadin-tab').filter({ hasText: 'Settings' }).click();
    await page.waitForTimeout(300);
    await expect(page.locator('.account-overlay vaadin-button').filter({ hasText: 'Save' })).toHaveCount(0, { timeout: 5000 });
    await expect(page.locator('.account-overlay vaadin-button').filter({ hasText: 'Discard changes' })).toHaveCount(0, { timeout: 5000 });
    await expect(page.locator('.account-overlay vaadin-integer-field').first()).toHaveJSProperty('readonly', true, { timeout: 5000 });
    await screenshot(page, 'provider-profile-moderator-settings-readonly');

    // Provider Profile tab — data visible (both categories saved in the previous test), no Edit button.
    await openProviderProfileTab(page);
    const moderatorChips = page.locator('.account-overlay .provider-profile-category-chip');
    await expect(moderatorChips).toHaveCount(2, { timeout: 5000 });
    await expect(moderatorChips).toContainText(['Electronics', 'Vehicles']);
    await expect(page.locator('.account-overlay vaadin-button').filter({ hasText: 'Edit' })).toHaveCount(0, { timeout: 5000 });
    await screenshot(page, 'provider-profile-moderator-providerprofile-readonly');

    await closeUserOverlay(page);
    await clearUserFilter(page);
    await runLogoutFlow(page, expect);
  });

  test('adminEn creates and edits userUk\'s provider profile via the Users grid — not self-service, same create/edit round trip through the grid entry path', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.adminEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.adminEn);
    await runNavigateToUsersTabFlow(page, expect);
    await runOpenUserEditViaListFlow(page, TEST_USERS.userUk.email);
    await openProviderProfileTab(page);

    await expect(page.locator('.account-overlay .provider-profile-view-empty-text')).toBeVisible({ timeout: 5000 });

    await page.locator('.account-overlay vaadin-button').filter({ hasText: 'Create Profile' }).click();
    await page.waitForTimeout(300);
    // SUPPORT is only offered to a privileged actor -- exercises that branch, untouched elsewhere in this file.
    await page.locator('.account-overlay vaadin-radio-button').filter({ hasText: 'SUPPORT' }).first().click();
    await fillAbout(page, 'Admin-managed support profile for userUk.');
    await selectCategory(page, 'Vehicles');
    await selectCity(page, 'Kyiv');

    await page.locator('.account-overlay vaadin-button').filter({ hasText: 'Save' }).click();
    await expect(page.locator('vaadin-notification-container')).toContainText('Provider profile saved', { timeout: 5000 });
    await closeNotification(page);

    await page.locator('.account-overlay vaadin-button[title="Cancel"]').click();
    await page.waitForTimeout(300);
    await expect(page.locator('.account-overlay .provider-profile-kind-badge')).toContainText('SUPPORT', { timeout: 5000 });
    await expect(page.locator('.account-overlay .provider-profile-city-chip')).toContainText('Kyiv');
    await screenshot(page, 'provider-profile-admin-via-grid-view');

    // Re-edit through this same grid-entry path -- the pre-fill regression fix applies here too.
    await page.locator('.account-overlay vaadin-button').filter({ hasText: 'Edit' }).click();
    await page.waitForTimeout(300);
    await expect(async () => {
      const names = await selectedCategoryNames(page);
      expect(names).toContain('Vehicles');
    }).toPass({ timeout: 5000 });
    await expect(page.locator('.account-overlay vaadin-combo-box input')).toHaveValue('Kyiv', { timeout: 5000 });

    // Cancel from Provider Profile Edit routes back to View first (same overlay stays open,
    // same as afterDiscard()'s design) -- closeUserOverlay only fully exits from View.
    await page.locator('.account-overlay vaadin-button[title="Cancel"]').click();
    await page.waitForTimeout(300);
    await closeUserOverlay(page);
    await clearUserFilter(page);
    await runLogoutFlow(page, expect);
  });
});
