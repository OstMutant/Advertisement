/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Role promotion, user editing and Reference Data e2e coverage. An administrator
 *   promotes freshly registered users through the Users tab (activity diff, role badge in view and
 *   grid, outer breadcrumb), a user's first login in English switches to Ukrainian and keeps it on
 *   relogin, an administrator renames a user and restores the name from activity, and Reference
 *   Data category and city management is covered end to end: create, discard in create and edit,
 *   edit with activity diff, restore from activity, outer breadcrumb, and delete/restore with the
 *   deleted category struck through in an advertisement's view overlay and activity diff. A
 *   `PW_FULL`-gated block signs up two users with 100-character names. Every test is independent:
 *   it creates its own users, categories and cities with run-id names and filters grids, lists and
 *   the timeline to its own data before asserting.
 *   Per test:
 *   - "adminEn promotes a user to MODERATOR -- ...": edit via View -> activity (v2 updated role, v1
 *     created) -> role badge in view -> grid badge -> outer breadcrumb -> timeline check.
 *   - "adminEn promotes a second user to MODERATOR -- ...": same promotion on another user, timeline check.
 *   - "adminEn promotes a user to ADMIN -- ...": same promotion to ADMIN, timeline check.
 *   - "user logs in for the first time in English -- ...": login (EN) -> switch to Ukrainian -> logout
 *     -> relogin shows Ukrainian tabs.
 *   - "adminEn renames a user -- ...": rename -> grid (new name) -> activity (updated, restore button)
 *     -> restore -> activity (restore entry) -> grid (original name) -> timeline check -> the user
 *     logs in with the original password.
 *   - "adminEn creates categories and cities -- ...": discard in create, discard after save, two
 *     categories and two cities in their lists.
 *   - "adminEn edits a category -- ...": discard in edit, save with activity v2, restore from v1,
 *     outer breadcrumb, timeline created/updated entries with all 4 fields in the diff.
 *   - "adminEn edits a city -- ...": save with activity v2, restore from v1, outer breadcrumb.
 *   - "adminEn deletes and restores a category -- ...": delete, struck-through chip in an
 *     advertisement's view and activity diff, restore, deleted and restored events in activity.
 *   - boundary (PW_FULL): "maxEn/maxUk signs up -- 100-char name accepted, ...".
 * Usage: run via the Playwright test runner -- `bash /app/playwright/run.sh 03-marketplace-
 *   promotion-flow --ux` (runs alone or with any other spec), or as part of the full e2e suite
 *   (`bash /app/playwright/run.sh e2e --ux`). The boundary describe block additionally needs
 *   `--full` (sets PW_FULL) to run.
 * Uses: @playwright/test.
 * Env: PW_FULL -- when unset/falsy, the 'Max-boundary users' describe block is skipped entirely.
 * Input: ./_fixtures (test, expect, unique, createUser, adminAccount, pageAs, request),
 *   ./_helpers (screenshot, closeNotification), ./_flows/auth.flow, ./_flows/language-switch.flow,
 *   ./_flows/user-management.flow, ./_flows/timeline.flow, ./_flows/filter.flow,
 *   ./_flows/signup.flow, ./_flows/seed.flow, ./_flows/advertisement.flow,
 *   ./_flows/advertisement-filter.flow, ./_flows/category.flow, ./_flows/city.flow,
 *   ./_flows/entity-activity.flow.
 * Outputs: Playwright HTML report entries (one per test/test.step), PNG screenshots attached to
 *   the report when PW_SCREENSHOTS is set. Creates only its own uniquely named users, categories,
 *   cities and advertisements.
 * Returns: exit code from the Playwright test runner -- 0 when every test in this file passes,
 *   non-zero otherwise.
 * ──────────────────────────────────────────────────────────────────────────── */
const { test, expect } = require('./_fixtures');
const { screenshot, closeNotification } = require('./_helpers');
const { runFillLoginFormFlow, runSubmitLoginFlow, runLogoutFlow } = require('./_flows/auth.flow');
const { runSwitchToUkrainianLoggedInFlow } = require('./_flows/language-switch.flow');
const { runNavigateToUsersTabFlow, runPromoteUserFlow, runOpenUserEditViaListFlow, runOpenUserEditViaViewFlow, runFillUserRoleFlow, runSaveUserEditFlow, clearUserFilter, closeUserOverlay, closeUserOverlayFromEdit, runVerifyOuterLinkClosesToListFlow } = require('./_flows/user-management.flow');
const { openTimelineTab, openTimelineFilter, closeTimelineFilter, fillEntityType, fillActorPicker, assertTimelineHasRows, TIMELINE_BLOCK } = require('./_flows/timeline.flow');
const { applyFilter } = require('./_flows/filter.flow');
const { runSignUpFlow } = require('./_flows/signup.flow');
const { issueApiKeyViaApi, postViaApi, seedTaxonsViaApi } = require('./_flows/seed.flow');
const { cardByTitle, openCardOverlay, switchToEditMode, openActivityTab, closeOverlayToList } = require('./_flows/advertisement.flow');
const { runOpenFilterPanelFlow, runFillTitleFilterFlow, runApplyFilterFlow } = require('./_flows/advertisement-filter.flow');
const { openReferenceDataTab, assertViewOverlayHasDeletedCategory, assertActivityDiffHasStruckThroughCategory } = require('./_flows/category.flow');
const { openCitiesSubTab } = require('./_flows/city.flow');
const { openEntityActivity, closeEntityActivity } = require('./_flows/entity-activity.flow');

const LONG_TEST_TIMEOUT = 2 * 60 * 1000;

/** Matches a text exactly, so a name never also matches a longer name that starts with it. */
function exactText(text) {
  return new RegExp(`^${text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`);
}

/** Opens the Timeline tab narrowed to one actor and one entity type and waits for the first row. */
async function openTimelineOf(page, actorName, entityType) {
  await openTimelineTab(page);
  await openTimelineFilter(page);
  await fillActorPicker(page, actorName, { useSearch: true });
  await fillEntityType(page, entityType);
  await applyFilter(page, TIMELINE_BLOCK);
  await closeTimelineFilter(page);
  await expect(page.locator('.activity-feed .activity-feed-row').first()).toBeVisible({ timeout: 8000 });
}

/** Builds a category or city with run-id names in both locales. */
function taxonData(unique, labelEn, labelUk, descEn, descUk) {
  return { nameEn: unique(labelEn), descEn, nameUk: unique(labelUk), descUk };
}

/** Creates one category or city through the REST API as the given administrator and returns its id. */
async function seedTaxon(request, admin, type, { nameEn, descEn, nameUk, descUk }) {
  const key = await issueApiKeyViaApi(request, admin);
  const [id] = await seedTaxonsViaApi(request, key, type, [{ nameEn, descriptionEn: descEn, nameUk, descriptionUk: descUk }]);
  return id;
}

// Section 3 helpers — taxon management
async function waitForTaxonOverlay(page) {
  await page.locator('.category-overlay.overlay--visible').waitFor({ timeout: 8000 });
}

async function closeTaxonOverlay(page) {
  await closeEntityActivity(page);
  await page.locator('.category-overlay').getByRole('button', { name: /cancel|скасувати/i }).click();
  await page.locator('.category-overlay.overlay--visible').waitFor({ state: 'hidden', timeout: 8000 });
}

async function fillTaxonLocale(page, locale, name, desc) {
  const overlay = page.locator('.category-overlay');
  const idx     = locale === 'EN' ? 0 : 1;
  const content = overlay.locator('.taxon-locale-content').nth(idx);
  await content.getByRole('textbox', { name: /name|назва/i }).fill(name);
  await content.getByRole('textbox', { name: /description|опис/i }).fill(desc);
}

async function createCategory(page, { nameEn, descEn, nameUk, descUk }) {
  await page.locator('.category-management-view').getByRole('button', { name: /add new|додати/i }).click();
  await waitForTaxonOverlay(page);
  await fillTaxonLocale(page, 'EN', nameEn, descEn);
  await fillTaxonLocale(page, 'UK', nameUk, descUk);
  await page.locator('.category-overlay').getByRole('button', { name: /зберегти|save/i }).click();
  await expect(page.locator('vaadin-notification-card')).toBeVisible({ timeout: 5000 });
  await closeNotification(page);
  await closeTaxonOverlay(page);
}

function taxonRow(page, name) {
  return page.locator('.taxon-row-wrapper')
    .filter({ has: page.locator('.taxon-row-name', { hasText: exactText(name) }) });
}

async function openTaxonEdit(page, name) {
  await taxonRow(page, name).getByRole('button', { name: /^(edit|редагувати)$/i }).click();
  await waitForTaxonOverlay(page);
  await expect(page.locator('.category-overlay .overlay__breadcrumb-back')).toHaveCount(1, { timeout: 3000 });
}

// Section 3 helpers — city management (mirrors taxon helpers above, scoped to the Cities sub-tab)
async function waitForCityOverlay(page) {
  await page.locator('.city-overlay.overlay--visible').waitFor({ timeout: 8000 });
}

async function closeCityOverlay(page) {
  await closeEntityActivity(page);
  await page.locator('.city-overlay').getByRole('button', { name: /cancel|скасувати/i }).click();
  await page.locator('.city-overlay.overlay--visible').waitFor({ state: 'hidden', timeout: 8000 });
}

async function fillCityLocale(page, locale, name, desc) {
  const overlay = page.locator('.city-overlay');
  const idx     = locale === 'EN' ? 0 : 1;
  const content = overlay.locator('.taxon-locale-content').nth(idx);
  await content.getByRole('textbox', { name: /name|назва/i }).fill(name);
  await content.getByRole('textbox', { name: /description|опис/i }).fill(desc);
}

async function createCity(page, { nameEn, descEn, nameUk, descUk }) {
  await page.locator('.city-management-view').getByRole('button', { name: /add new|додати/i }).click();
  await waitForCityOverlay(page);
  await fillCityLocale(page, 'EN', nameEn, descEn);
  await fillCityLocale(page, 'UK', nameUk, descUk);
  await page.locator('.city-overlay').getByRole('button', { name: /зберегти|save/i }).click();
  await expect(page.locator('vaadin-notification-card')).toBeVisible({ timeout: 5000 });
  await closeNotification(page);
  await closeCityOverlay(page);
}

function cityRowName(page, name) {
  return page.locator('.city-management-view .taxon-row-name', { hasText: exactText(name) });
}

async function openCityEdit(page, name) {
  await page.locator('.city-management-view .taxon-row-wrapper')
    .filter({ has: page.locator('.taxon-row-name', { hasText: exactText(name) }) })
    .getByRole('button', { name: /^(edit|редагувати)$/i })
    .click();
  await waitForCityOverlay(page);
  await expect(page.locator('.city-overlay .overlay__breadcrumb-back')).toHaveCount(1, { timeout: 3000 });
}

test.describe('Promotion flow', () => {

  // === Section 1: Role promotion ===

  test('adminEn promotes a user to MODERATOR — activity shows updated role, role badge in view and grid, outer breadcrumb link closes directly to list, timeline check', async ({ pageAs, adminAccount, createUser }) => {
    test.setTimeout(LONG_TEST_TIMEOUT);
    const target = await createUser();
    const page = await pageAs(adminAccount);

    await runNavigateToUsersTabFlow(page, expect);
    await runPromoteUserFlow(page, expect, target, { role: 'MODERATOR' });
    await runVerifyOuterLinkClosesToListFlow(page, expect, target.email, target.name);
    await openTimelineOf(page, adminAccount.name, 'USER');
    await assertTimelineHasRows(page, expect, { action: 'updated', entityType: 'user', titleText: target.email, screenshotName: 'timeline-moderatoruk-promoted' });
  });

  test('adminEn promotes a second user to MODERATOR — activity shows updated role, role badge in view and grid, timeline check', async ({ pageAs, adminAccount, createUser }) => {
    test.setTimeout(LONG_TEST_TIMEOUT);
    const target = await createUser();
    const page = await pageAs(adminAccount);

    await runNavigateToUsersTabFlow(page, expect);
    await runPromoteUserFlow(page, expect, target, { role: 'MODERATOR' });
    await openTimelineOf(page, adminAccount.name, 'USER');
    await assertTimelineHasRows(page, expect, { action: 'updated', entityType: 'user', titleText: target.email, screenshotName: 'timeline-moderatoren-promoted' });
  });

  test('adminEn promotes a user to ADMIN — activity shows updated role, role badge in view and grid, timeline check', async ({ pageAs, adminAccount, createUser }) => {
    test.setTimeout(LONG_TEST_TIMEOUT);
    const target = await createUser();
    const page = await pageAs(adminAccount);

    await runNavigateToUsersTabFlow(page, expect);
    await runPromoteUserFlow(page, expect, target, { role: 'ADMIN' });
    await openTimelineOf(page, adminAccount.name, 'USER');
    await assertTimelineHasRows(page, expect, { action: 'updated', entityType: 'user', titleText: target.email, screenshotName: 'timeline-adminuk-promoted' });
  });

  // === Section 2: User locale ===

  test('user logs in for the first time in English — switches to Ukrainian, logs out, relogin keeps Ukrainian', async ({ page, createUser }) => {
    const user = await createUser();
    await page.goto('/');

    await test.step('first login in English, switch to Ukrainian, logout', async () => {
      await runFillLoginFormFlow(page, user);
      await runSubmitLoginFlow(page, expect, user);
      await runSwitchToUkrainianLoggedInFlow(page, expect);
      await runLogoutFlow(page, expect);
    });

    await test.step('relogin shows the Ukrainian locale', async () => {
      await runFillLoginFormFlow(page, user);
      await runSubmitLoginFlow(page, expect, { ...user, locale: 'uk' });
      await runLogoutFlow(page, expect);
    });
  });

  // === Section 2b: Cross-actor user edit ===

  test('adminEn renames a user — activity diff, grid updated, restore reverts name, timeline check, user relogin after edit', async ({ pageAs, adminAccount, createUser, unique }) => {
    test.setTimeout(LONG_TEST_TIMEOUT);
    const target = await createUser();
    const originalName = target.name;
    const editedName = unique('AdminCrossEdit');
    const page = await pageAs(adminAccount);

    await runNavigateToUsersTabFlow(page, expect);

    await test.step('admin renames the user — grid reflects new name', async () => {
      await runOpenUserEditViaListFlow(page, target.email, originalName);
      await runFillUserRoleFlow(page, { name: editedName });
      await runSaveUserEditFlow(page, expect, 'cross-actor');
      await closeUserOverlay(page);
      await expect(page.locator('.user-grid-name:visible').filter({ hasText: editedName })).toHaveCount(1, { timeout: 5000 });
      await screenshot(page, 'cross-actor-01-user-renamed');
      await clearUserFilter(page);
    });

    await test.step('cross-actor edit visible in the user profile activity', async () => {
      await runOpenUserEditViaViewFlow(page, target.email, editedName);
      const activityList = await openEntityActivity(page, '.user-history-button');
      const latestRow = activityList.locator('.entity-activity-row').nth(0);
      await expect(latestRow.locator('.entity-activity-action')).toContainText(/updated|оновлено/i);
      await expect(latestRow.locator('.entity-activity-changes')).toContainText(editedName);
      await expect(activityList.locator('.entity-activity-restore-btn').first()).toBeVisible({ timeout: 5000 });
      await screenshot(page, 'cross-actor-02-profile-activity');
    });

    await test.step('restore reverts name — activity records restore entry', async () => {
      await page.locator('.entity-activity-overlay .entity-activity-list .entity-activity-restore-btn').first().click();
      await page.locator('.entity-activity-overlay.overlay--visible').waitFor({ state: 'hidden', timeout: 8000 });
      await runSaveUserEditFlow(page, expect, 'cross-actor-restore');
      const activityList = await openEntityActivity(page, '.user-history-button');
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

    await openTimelineOf(page, adminAccount.name, 'USER');
    await assertTimelineHasRows(page, expect, { action: 'updated', entityType: 'user', titleText: target.email, screenshotName: 'timeline-cross-actor-restore' });

    await test.step('the user logs in with the original password — profile edit and restore did not touch the password hash', async () => {
      const userPage = await pageAs(target);
      await expect(userPage.getByRole('button', { name: /^(settings|налаштування)$/i })).toBeVisible();
    });
  });

  // === Section 3: Reference Data — Category and city management ===

  test('adminEn creates categories and cities — discard in create clears form, discard after save reverts to saved values, both categories and both cities in their lists', async ({ pageAs, adminAccount, unique }) => {
    test.setTimeout(LONG_TEST_TIMEOUT);
    const cat1 = taxonData(unique, 'Electronics', 'Електроніка', 'Electronic devices and accessories', 'Електронні пристрої та аксесуари');
    const cat2 = taxonData(unique, 'Vehicles', 'Транспорт', 'Cars, motorcycles and other vehicles', 'Автомобілі, мотоцикли та інший транспорт');
    const city1 = taxonData(unique, 'Lviv', 'Львів', 'Western Ukraine cultural hub', 'Культурний центр Західної України');
    const city2 = taxonData(unique, 'Kyiv', 'Київ', 'Capital of Ukraine', 'Столиця України');
    const tempName = unique('TempCat');
    const page = await pageAs(adminAccount);

    await openReferenceDataTab(page);
    await screenshot(page, 'taxon-00-ref-data-tab');

    await test.step('discard in CREATE mode — fields clear, save button disabled', async () => {
      await page.locator('.category-management-view').getByRole('button', { name: /add new|додати/i }).click();
      await waitForTaxonOverlay(page);
      await fillTaxonLocale(page, 'EN', 'Temp Name', 'Temp description long enough to be valid here');
      const overlay    = page.locator('.category-overlay');
      const saveBtn    = overlay.getByRole('button', { name: /зберегти|save/i });
      const discardBtn = overlay.getByRole('button', { name: /скинути зміни|discard changes/i });
      await expect(saveBtn).toBeEnabled({ timeout: 5000 });
      await expect(discardBtn).toBeEnabled();
      await screenshot(page, 'taxon-01-create-discard-filled');
      await discardBtn.click();
      await expect(saveBtn).toBeDisabled({ timeout: 3000 });
      await expect(overlay.locator('.taxon-locale-content').nth(0).getByRole('textbox', { name: /name|назва/i })).toHaveValue('', { timeout: 3000 });
      await screenshot(page, 'taxon-01-create-discard-cleared');
      await closeTaxonOverlay(page);
    });

    await test.step('discard after CREATE save — fields revert to saved values, not empty', async () => {
      await page.locator('.category-management-view').getByRole('button', { name: /add new|додати/i }).click();
      await waitForTaxonOverlay(page);
      await fillTaxonLocale(page, 'EN', tempName, 'Temporary category for discard test');
      await fillTaxonLocale(page, 'UK', unique('ТимчасCat'), 'Тимчасова категорія для тесту');
      const overlay    = page.locator('.category-overlay');
      const saveBtn    = overlay.getByRole('button', { name: /зберегти|save/i });
      const discardBtn = overlay.getByRole('button', { name: /скинути зміни|discard changes/i });
      await saveBtn.click();
      await expect(page.locator('vaadin-notification-card')).toBeVisible({ timeout: 5000 });
      await closeNotification(page);
      await expect(saveBtn).toBeDisabled({ timeout: 3000 });
      // modify after save — then discard must revert to saved values, not empty
      await overlay.locator('.taxon-locale-content').nth(0).getByRole('textbox', { name: /name|назва/i }).fill(`${tempName} MODIFIED`);
      await expect(saveBtn).toBeEnabled({ timeout: 3000 });
      await discardBtn.click();
      await expect(overlay.locator('.taxon-locale-content').nth(0).getByRole('textbox', { name: /name|назва/i }))
        .toHaveValue(tempName, { timeout: 3000 });
      await expect(saveBtn).toBeDisabled({ timeout: 3000 });
      await closeTaxonOverlay(page);
    });

    await test.step('create the first category', async () => {
      await createCategory(page, cat1);
      await expect(page.locator('.taxon-row-name', { hasText: exactText(cat1.nameEn) })).toBeVisible({ timeout: 5000 });
    });

    await test.step('create the second category', async () => {
      await createCategory(page, cat2);
      await expect(page.locator('.taxon-row-name', { hasText: exactText(cat2.nameEn) })).toBeVisible({ timeout: 5000 });
    });

    await expect(page.locator('.taxon-row-name', { hasText: exactText(cat1.nameEn) })).toBeVisible();
    await expect(page.locator('.taxon-row-name', { hasText: exactText(cat2.nameEn) })).toBeVisible();
    await screenshot(page, 'taxon-02-two-categories-in-list');

    await test.step('create two cities — both in Cities sub-tab list', async () => {
      await openCitiesSubTab(page);
      await createCity(page, city1);
      await createCity(page, city2);
      await expect(cityRowName(page, city1.nameEn)).toBeVisible({ timeout: 5000 });
      await expect(cityRowName(page, city2.nameEn)).toBeVisible({ timeout: 5000 });
      await screenshot(page, 'city-01-two-cities-in-list');
    });
  });

  test('adminEn edits a category — edit discard reverts, save records activity, restore reverts name, outer breadcrumb link closes directly to list, timeline shows created and updated entries with all fields in the diff', async ({ pageAs, adminAccount, request, unique }) => {
    test.setTimeout(LONG_TEST_TIMEOUT);
    const cat = taxonData(unique, 'Electronics', 'Електроніка', 'Electronic devices and accessories', 'Електронні пристрої та аксесуари');
    await seedTaxon(request, adminAccount, 'CATEGORY', cat);
    const page = await pageAs(adminAccount);
    await openReferenceDataTab(page);

    await test.step('discard in EDIT mode — modified name reverts to original', async () => {
      await openTaxonEdit(page, cat.nameEn);
      const overlay = page.locator('.category-overlay');
      await expect(overlay.locator('.taxon-locale-content').nth(0).getByRole('textbox', { name: /name|назва/i })).toHaveValue(cat.nameEn, { timeout: 5000 });
      await overlay.locator('.taxon-locale-content').nth(0).getByRole('textbox', { name: /name|назва/i }).fill(`${cat.nameEn} MODIFIED`);
      const saveBtn    = overlay.getByRole('button', { name: /зберегти|save/i });
      const discardBtn = overlay.getByRole('button', { name: /скинути зміни|discard changes/i });
      await expect(saveBtn).toBeEnabled({ timeout: 5000 });
      await screenshot(page, 'taxon-03-edit-discard-modified');
      await discardBtn.click();
      await expect(overlay.locator('.taxon-locale-content').nth(0).getByRole('textbox', { name: /name|назва/i })).toHaveValue(cat.nameEn, { timeout: 5000 });
      await expect(saveBtn).toBeDisabled({ timeout: 3000 });
      await screenshot(page, 'taxon-03-edit-discard-reverted');
      await closeTaxonOverlay(page);
    });

    await test.step('edit the name and save — activity shows v2 update row', async () => {
      await openTaxonEdit(page, cat.nameEn);
      const overlay = page.locator('.category-overlay');
      await overlay.locator('.taxon-locale-content').nth(0).getByRole('textbox', { name: /name|назва/i }).fill(`${cat.nameEn} v2`);
      const saveBtn = overlay.getByRole('button', { name: /зберегти|save/i });
      await expect(saveBtn).toBeEnabled({ timeout: 5000 });
      await saveBtn.click();
      await expect(page.locator('vaadin-notification-card')).toBeVisible({ timeout: 5000 });
      await closeNotification(page);
      await screenshot(page, 'taxon-04-edit-saved');

      const activityList = await openEntityActivity(page, '.category-history-button');
      await expect(activityList.locator('.entity-activity-row')).toHaveCount(2, { timeout: 8000 });
      await expect(activityList.locator('.entity-activity-row').nth(0).locator('.entity-activity-action')).toContainText(/updated|оновлено/i);
      await expect(activityList.locator('.entity-activity-row').nth(0).locator('.entity-activity-version')).toContainText('v2');
      await expect(activityList.locator('.entity-activity-row').nth(1).locator('.entity-activity-version')).toContainText('v1');
      await screenshot(page, 'taxon-04-activity-two-rows');
    });

    await test.step('restore from activity v1 — form reverts to original name, save applies restore', async () => {
      const overlay      = page.locator('.category-overlay');
      const activityList = page.locator('.entity-activity-overlay .entity-activity-list');
      const v1Row        = activityList.locator('.entity-activity-row').nth(1);
      await v1Row.locator('.entity-activity-restore-btn').click();
      await page.locator('.entity-activity-overlay.overlay--visible').waitFor({ state: 'hidden', timeout: 8000 });
      await expect(page.locator('vaadin-notification-card')).toBeVisible({ timeout: 5000 });
      await closeNotification(page);
      // history overlay closes itself, revealing the edit form with the original name
      await expect(overlay.locator('.taxon-locale-content').nth(0).getByRole('textbox', { name: /name|назва/i })).toHaveValue(cat.nameEn, { timeout: 5000 });
      await screenshot(page, 'taxon-05-restored-form');
      const saveBtn = overlay.getByRole('button', { name: /зберегти|save/i });
      await expect(saveBtn).toBeEnabled();
      await saveBtn.click();
      await expect(page.locator('vaadin-notification-card')).toBeVisible({ timeout: 5000 });
      await closeNotification(page);
      await closeTaxonOverlay(page);
      await expect(page.locator('.taxon-row-name', { hasText: exactText(cat.nameEn) })).toBeVisible({ timeout: 5000 });
      await screenshot(page, 'taxon-05-restored-in-list');
    });

    await test.step('outer breadcrumb link in nested history closes directly to list, even after View→Edit', async () => {
      await page.locator('.taxon-row-name', { hasText: exactText(cat.nameEn) }).click();
      await waitForTaxonOverlay(page);
      await page.locator('.category-overlay').getByRole('button', { name: /edit|редагувати/i }).first().click();
      await page.locator('.category-overlay .taxon-locale-content').nth(0).getByRole('textbox', { name: /name|назва/i }).waitFor({ timeout: 5000 });
      await expect(page.locator('.category-overlay .overlay__breadcrumb-back')).toHaveCount(2, { timeout: 3000 });
      await openEntityActivity(page, '.category-history-button');
      await closeEntityActivity(page, 'outer');
      await expect(page.locator('.base-overlay.overlay--visible')).toHaveCount(0, { timeout: 5000 });
      await expect(page.locator('.category-management-view')).toBeVisible({ timeout: 5000 });
      await screenshot(page, 'taxon-05b-outer-link-to-list');
    });

    await test.step('timeline — taxon created and updated entries visible, single-field edit shows all 4 fields in diff', async () => {
      await openTimelineOf(page, adminAccount.name, 'Category');
      await assertTimelineHasRows(page, expect, { entityType: 'taxon', action: 'created', titleText: cat.nameEn, screenshotName: 'taxon-06-timeline-created' });
      await assertTimelineHasRows(page, expect, { entityType: 'taxon', action: 'updated', titleText: cat.nameEn, screenshotName: 'taxon-06-timeline-updated' });

      // all 4 fields are expanded in the diff even when only one changed
      const taxonUpdateRow = page.locator('.activity-feed .activity-feed-row')
        .filter({ has: page.locator('.activity-feed-action--updated') })
        .filter({ has: page.locator('.activity-feed-type--taxon') })
        .filter({ has: page.locator('.activity-feed-changes', { hasText: `${cat.nameEn} v2` }) })
        .first();
      const changesDiv = taxonUpdateRow.locator('.activity-feed-changes');
      await expect(changesDiv).toContainText('Name (EN)', { timeout: 5000 });
      await expect(changesDiv).toContainText('Description (EN)');
      await expect(changesDiv).toContainText('Name (UK)');
      await expect(changesDiv).toContainText('Description (UK)');
      await screenshot(page, 'taxon-06-timeline-all-fields-in-diff');
    });
  });

  test('adminEn edits a city — save records activity v2, restore from v1 reverts name, outer breadcrumb link closes directly to list', async ({ pageAs, adminAccount, request, unique }) => {
    test.setTimeout(LONG_TEST_TIMEOUT);
    const city = taxonData(unique, 'Lviv', 'Львів', 'Western Ukraine cultural hub', 'Культурний центр Західної України');
    await seedTaxon(request, adminAccount, 'CITY', city);
    const page = await pageAs(adminAccount);
    await openCitiesSubTab(page);

    await test.step('edit the city name and save — activity shows v2 update row, restore reverts to v1', async () => {
      await openCityEdit(page, city.nameEn);
      const overlay = page.locator('.city-overlay');
      await overlay.locator('.taxon-locale-content').nth(0).getByRole('textbox', { name: /name|назва/i }).fill(`${city.nameEn} v2`);
      const saveBtn = overlay.getByRole('button', { name: /зберегти|save/i });
      await expect(saveBtn).toBeEnabled({ timeout: 5000 });
      await saveBtn.click();
      await expect(page.locator('vaadin-notification-card')).toBeVisible({ timeout: 5000 });
      await closeNotification(page);
      await screenshot(page, 'city-02-edit-saved');

      const activityList = await openEntityActivity(page, '.city-history-button');
      await expect(activityList.locator('.entity-activity-row')).toHaveCount(2, { timeout: 8000 });
      await expect(activityList.locator('.entity-activity-row').nth(0).locator('.entity-activity-version')).toContainText('v2');
      await screenshot(page, 'city-02-activity-two-rows');

      const v1Row = activityList.locator('.entity-activity-row').nth(1);
      await v1Row.locator('.entity-activity-restore-btn').click();
      await page.locator('.entity-activity-overlay.overlay--visible').waitFor({ state: 'hidden', timeout: 8000 });
      await expect(page.locator('vaadin-notification-card')).toBeVisible({ timeout: 5000 });
      await closeNotification(page);
      await expect(overlay.locator('.taxon-locale-content').nth(0).getByRole('textbox', { name: /name|назва/i })).toHaveValue(city.nameEn, { timeout: 5000 });
      await saveBtn.click();
      await expect(page.locator('vaadin-notification-card')).toBeVisible({ timeout: 5000 });
      await closeNotification(page);
      await closeCityOverlay(page);
      await expect(cityRowName(page, city.nameEn)).toBeVisible({ timeout: 5000 });
      await screenshot(page, 'city-02-restored-in-list');
    });

    await test.step('outer breadcrumb link in nested history closes directly to list, even after View→Edit', async () => {
      await cityRowName(page, city.nameEn).click();
      await waitForCityOverlay(page);
      await page.locator('.city-overlay').getByRole('button', { name: /edit|редагувати/i }).first().click();
      await page.locator('.city-overlay .taxon-locale-content').nth(0).getByRole('textbox', { name: /name|назва/i }).waitFor({ timeout: 5000 });
      await expect(page.locator('.city-overlay .overlay__breadcrumb-back')).toHaveCount(2, { timeout: 3000 });
      await openEntityActivity(page, '.city-history-button');
      await closeEntityActivity(page, 'outer');
      await expect(page.locator('.base-overlay.overlay--visible')).toHaveCount(0, { timeout: 5000 });
      await expect(page.locator('.city-management-view')).toBeVisible({ timeout: 5000 });
      await screenshot(page, 'city-02b-outer-link-to-list');
    });
  });

  test('adminEn deletes and restores a category — advertisement view and activity diff show the category struck through while deleted, deleted and restored events recorded in activity', async ({ pageAs, adminAccount, createUser, request, unique }) => {
    test.setTimeout(LONG_TEST_TIMEOUT);
    const cat = taxonData(unique, 'Electronics', 'Електроніка', 'Electronic devices and accessories', 'Електронні пристрої та аксесуари');
    const categoryId = await seedTaxon(request, adminAccount, 'CATEGORY', cat);
    const owner = await createUser();
    const adTitle = unique('Strikethrough Test Ad');
    const ownerKey = await issueApiKeyViaApi(request, owner);
    await postViaApi(request, ownerKey, '/api/advertisements', {
      title: adTitle,
      description: 'Category strikethrough test advertisement.',
      adKind: 'OFFER',
      categoryIds: [categoryId],
    });
    const page = await pageAs(adminAccount);
    await openReferenceDataTab(page);

    await test.step('delete the category — deleted badge shown', async () => {
      const activeRow = taxonRow(page, cat.nameEn).filter({ hasNot: page.locator('.taxon-deleted-badge:visible') });
      await activeRow.getByRole('button', { name: /^(delete|видалити)$/i }).click();
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
      await expect(taxonRow(page, cat.nameEn).filter({ has: page.locator('.taxon-deleted-badge') })).toBeVisible({ timeout: 5000 });
      await screenshot(page, 'taxon-07-electronics-deleted');
    });

    await test.step('advertisement view and activity diff render the deleted category struck through, not dropped or shown as a bare id', async () => {
      await page.getByRole('tab', { name: /Advertisements|Оголошення/i }).first().click();
      await runOpenFilterPanelFlow(page, expect);
      await runFillTitleFilterFlow(page, adTitle);
      await runApplyFilterFlow(page, expect);
      const adOverlay = await openCardOverlay(page, cardByTitle(page, adTitle), 'taxon-07-strike-ad-view');
      await assertViewOverlayHasDeletedCategory(page, expect, adOverlay, cat.nameEn, 'taxon-07-strike-ad-view-chip-deleted');
      await switchToEditMode(page, adOverlay);
      const adActivityList = await openActivityTab(adOverlay);
      const catChangeRow = adActivityList.locator('.entity-activity-row').filter({ hasText: cat.nameEn }).first();
      await assertActivityDiffHasStruckThroughCategory(page, expect, catChangeRow.locator('.entity-activity-changes'), cat.nameEn, 'taxon-07-strike-ad-activity-diff-deleted');
      await closeOverlayToList(page, adOverlay);
    });

    await test.step('restore the category — back in the list, deleted and restored events recorded in activity', async () => {
      await openReferenceDataTab(page);
      await taxonRow(page, cat.nameEn).getByRole('button', { name: /^(restore|відновити)$/i }).click();
      await expect(page.locator('vaadin-notification-card')).toBeVisible({ timeout: 5000 });
      await closeNotification(page);
      await expect(taxonRow(page, cat.nameEn)).toBeVisible({ timeout: 5000 });
      await expect(taxonRow(page, cat.nameEn).locator('.taxon-deleted-badge:visible')).toHaveCount(0, { timeout: 5000 });
      await screenshot(page, 'taxon-07-electronics-restored');

      await openTaxonEdit(page, cat.nameEn);
      const activityList = await openEntityActivity(page, '.category-history-button');
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
  });
});

// ─── Boundary: max-content users (PW_FULL only) ──────────────────────────────

/** Builds a user whose name is exactly 100 characters and whose e-mail is at its maximum length, both unique to this run. */
function maxBoundaryUser(unique, kind) {
  const token = unique('m').replace(/\W/g, '').toLowerCase();
  const name = `${unique('MaxBoundary')} ${'BoundaryUserNameForValidationTest'.repeat(3)}`.substring(0, 100);
  const domain = [`max-domain-seg1-${'0'.repeat(47)}`, `max-domain-seg2-${'0'.repeat(47)}`, `max-domain-seg3-${'0'.repeat(45)}`].join('.');
  return { name, email: `max-boundary-${kind}-${token.padEnd(48, '0')}@${domain}`, password: 'password' };
}

test.describe('Max-boundary users', () => {
  test.skip(!process.env.PW_FULL, 'Skipped by default — run with --full for boundary tests');

  for (const [kind, prefix, label] of [['en', 'max-01', 'maxEn'], ['uk', 'max-03', 'maxUk']]) {
    test(`${label} signs up — 100-char name accepted, admin verifies user created with a created activity entry`, async ({ page, pageAs, adminAccount, unique }) => {
      test.setTimeout(LONG_TEST_TIMEOUT);
      const user = maxBoundaryUser(unique, kind);
      expect(user.name).toHaveLength(100);
      await page.goto('/');
      await runSignUpFlow(page, expect, user);

      const adminPage = await pageAs(adminAccount);
      await runNavigateToUsersTabFlow(adminPage, expect);
      await runOpenUserEditViaListFlow(adminPage, user.email, user.name);
      const nameInput = adminPage.locator('.account-overlay').getByRole('textbox').first();
      await expect(nameInput).toHaveValue(user.name, { timeout: 5000 });
      await screenshot(adminPage, `${prefix}-${kind}-user-name-100`);
      const activityList = await openEntityActivity(adminPage, '.user-history-button');
      await expect(activityList.locator('.entity-activity-action--created').first()).toBeVisible({ timeout: 5000 });
      await screenshot(adminPage, `${prefix}-${kind}-user-activity-created`);
      await closeUserOverlay(adminPage);
      await clearUserFilter(adminPage);
    });
  }
});
