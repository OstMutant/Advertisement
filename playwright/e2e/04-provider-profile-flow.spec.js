/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Provider Profile e2e coverage -- both the AccountOverlay self-service/admin tab
 *   (create/edit/moderator-readonly/admin-on-behalf, kind/about/categories/city fields) and the
 *   public Providers catalog (anonymous browsing/filtering, deep link + sitemap.xml + crawler meta
 *   tags, delete from the catalog card, SUPPORT-kind disabled-not-removed for a non-privileged
 *   actor already holding that kind). Per test:
 *   - "userEn creates provider profile" / "userEn edits provider profile" / "moderatorEn views
 *     userEn's account" / "adminEn creates and edits userUk's provider profile via the Users grid":
 *     unchanged AccountOverlay tab coverage, see individual test names for detail.
 *   - "anonymous visitor browses the public Providers catalog": first seeds two more profiles
 *     (moderatorEn as MASTER, adminUk as SHOP, with moderatorEn re-edited after adminUk's creation
 *     so its updated_at becomes the latest of all four -- real, distinct created_at/updated_at
 *     timestamps for every provider), then Providers tab -> lists all four -> filter by kind (Shop
 *     matches two, Support and Master each match one) -> filter by category (Vehicles matches
 *     userEn+userUk+moderatorEn, Electronics matches userEn+adminUk -- userEn holds both categories
 *     from its own earlier edit test) -> filter by city (Kyiv matches userUk+adminUk) -> clear ->
 *     the Updated/Created rows' own sort icons each cycle DESC -> NEUTRAL -> ASC -> DESC, asserting
 *     the icon's aria-label at every state and the real four-card order at the DESC/ASC states
 *     (NEUTRAL issues no ORDER BY at all, so its row order is Postgres physical storage order, not
 *     a documented contract -- not asserted).
 *   - "userEn opens a provider deep link": direct navigation to /providers/:id -> catalog overlay
 *     opens -> share button copies link -> sitemap.xml lists it -> crawler-facing og:type=profile/
 *     JSON-LD ProfilePage -> card click updates URL -> browser Back closes overlay.
 *   - "userEn deletes their own provider profile from the public catalog": delete confirm dialog ->
 *     card removed -> AccountOverlay Provider Profile tab shows the empty state again.
 *   - "userUk (non-privileged) edits their SUPPORT provider profile": SUPPORT radio option stays
 *     visible but disabled (the actor's own existing kind), MASTER/SHOP remain enabled.
 * Usage: run via the Playwright test runner -- `bash /app/playwright/run.sh 04-provider-profile-
 *   flow --ux`, or as part of the full e2e suite (`bash /app/playwright/run.sh e2e --ux`).
 * Uses: @playwright/test.
 * Env: None.
 * Input: ./_helpers (test, expect, screenshot, closeNotification, TEST_USERS), ./_flows/auth.flow
 *   (runFillLoginFormFlow, runSubmitLoginFlow, runLogoutFlow), ./_flows/audit.flow
 *   (runOpenSettingsFlow, runCloseSettingsFlow), ./_flows/entity-activity.flow (openEntityActivity,
 *   closeEntityActivity), ./_flows/user-management.flow (runNavigateToUsersTabFlow,
 *   runOpenUserViewDialogFlow, closeUserOverlay, clearUserFilter), ./_flows/delete.flow
 *   (confirmDeleteDialog). Depends on spec 02 having signed up all TEST_USERS, and spec 03 having
 *   created the Electronics/Vehicles categories and Lviv/Kyiv cities.
 * Outputs: Playwright HTML report entries for each test. userEn's provider profile is deleted by
 *   the end of this file's own serial sequence; userUk, moderatorEn and adminUk each keep a saved
 *   provider profile (userUk: SUPPORT/Vehicles/Kyiv, moderatorEn: MASTER/Vehicles/Lviv, adminUk:
 *   SHOP/Electronics/Kyiv) used only within this file.
 * Returns: exit code from the Playwright test runner -- 0 when every test in this file passes,
 *   non-zero otherwise.
 * ──────────────────────────────────────────────────────────────────────────── */
const { test, expect, screenshot, closeNotification, closeOverlay, TEST_USERS } = require('./_helpers');
const { runFillLoginFormFlow, runSubmitLoginFlow, runLogoutFlow } = require('./_flows/auth.flow');
const { runOpenSettingsFlow, runCloseSettingsFlow } = require('./_flows/audit.flow');
const { openEntityActivity, closeEntityActivity } = require('./_flows/entity-activity.flow');
const { runNavigateToUsersTabFlow, runOpenUserEditViaListFlow, runOpenUserViewDialogFlow, closeUserOverlay, clearUserFilter } = require('./_flows/user-management.flow');
const { confirmDeleteDialog } = require('./_flows/delete.flow');
const { verifyDateRangeFilters, waitForVaadin } = require('./_flows/filter.flow');

test.describe.configure({ mode: 'serial' });

async function openProviderProfileTab(page) {
  await page.locator('.account-overlay .account-overlay-tabs vaadin-tab').filter({ hasText: /provider profile|профіль провайдера/i }).click();
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

  test('anonymous visitor browses the public Providers catalog — lists userEn (SHOP), userUk (SUPPORT), moderatorEn (MASTER) and adminUk (SHOP), filters by kind/category/city, sorts by updated date', async () => {
    await test.step('seed two more provider profiles with staggered timestamps — moderatorEn (MASTER) created first, adminUk (SHOP) created second, then moderatorEn is re-edited so its updatedAt becomes the latest of all four, deliberately decoupling id order from updatedAt order', async () => {
      await runFillLoginFormFlow(page, TEST_USERS.moderatorEn);
      await runSubmitLoginFlow(page, expect, TEST_USERS.moderatorEn);
      await runOpenSettingsFlow(page);
      await openProviderProfileTab(page);
      await page.locator('.account-overlay vaadin-button').filter({ hasText: 'Create Profile' }).click();
      await page.waitForTimeout(300);
      await page.locator('.account-overlay vaadin-radio-button').filter({ hasText: 'MASTER' }).first().click();
      await fillAbout(page, 'Master craftsman offering on-site vehicle repair.');
      await selectCategory(page, 'Vehicles');
      await selectCity(page, 'Lviv');
      await page.locator('.account-overlay vaadin-button').filter({ hasText: 'Save' }).click();
      await expect(page.locator('vaadin-notification-container')).toContainText('Provider profile saved', { timeout: 5000 });
      await closeNotification(page);
      await page.locator('.account-overlay vaadin-button[title="Cancel"]').click();
      await page.waitForTimeout(300);
      await runCloseSettingsFlow(page);
      await runLogoutFlow(page, expect);

      // adminUk's TEST_USERS locale metadata says 'uk', but unlike userUk/moderatorUk this actor
      // never actually switched the UI to Ukrainian in an earlier spec -- the real rendered UI is
      // still English at this point, so the login assertion below uses that real locale. Button/
      // radio lookups still use bilingual selectors defensively.
      await runFillLoginFormFlow(page, TEST_USERS.adminUk);
      await runSubmitLoginFlow(page, expect, TEST_USERS.adminUk, 'en');
      await runOpenSettingsFlow(page);
      await openProviderProfileTab(page);
      await page.locator('.account-overlay vaadin-button').filter({ hasText: /create profile|створити профіль/i }).click();
      await page.waitForTimeout(300);
      await page.locator('.account-overlay vaadin-radio-button').filter({ hasText: /shop|магазин/i }).first().click();
      await fillAbout(page, 'Retail shop for electronics and accessories.');
      await selectCategory(page, 'Electronics');
      await selectCity(page, 'Kyiv');
      await page.locator('.account-overlay vaadin-button').filter({ hasText: /save|зберегти/i }).click();
      await expect(page.locator('vaadin-notification-container')).toContainText(/provider profile saved|профіль провайдера збережено/i, { timeout: 5000 });
      await closeNotification(page);
      await page.locator('.account-overlay vaadin-button[title="Cancel"], .account-overlay vaadin-button[title="Скасувати"]').click();
      await page.waitForTimeout(300);
      await runCloseSettingsFlow(page);
      await runLogoutFlow(page, expect);

      // Re-edit moderatorEn's profile after adminUk's was created -- moderatorEn keeps the lower
      // id (created first) but this bumps its updatedAt past adminUk's, so id order and updatedAt
      // order genuinely diverge for the resulting 4-provider set.
      await runFillLoginFormFlow(page, TEST_USERS.moderatorEn);
      await runSubmitLoginFlow(page, expect, TEST_USERS.moderatorEn);
      await runOpenSettingsFlow(page);
      await openProviderProfileTab(page);
      await page.locator('.account-overlay vaadin-button').filter({ hasText: 'Edit' }).click();
      await page.waitForTimeout(300);
      await fillAbout(page, 'Master craftsman offering on-site vehicle repair, now also on weekends.');
      await page.locator('.account-overlay vaadin-button').filter({ hasText: 'Save' }).click();
      await expect(page.locator('vaadin-notification-container')).toContainText('Provider profile saved', { timeout: 5000 });
      await closeNotification(page);
      await page.locator('.account-overlay vaadin-button[title="Cancel"]').click();
      await page.waitForTimeout(300);
      await runCloseSettingsFlow(page);
      await runLogoutFlow(page, expect);
    });

    await page.goto('/');
    await page.locator('vaadin-tab').filter({ hasText: 'Providers' }).click();
    await page.waitForTimeout(300);

    const container = page.locator('.provider-profile-container');
    await expect(container.locator('.provider-profile-card')).toHaveCount(4, { timeout: 10000 });
    await expect(container.locator('.provider-profile-kind-badge--shop')).toHaveCount(2);
    await expect(container.locator('.provider-profile-kind-badge--support')).toHaveCount(1);
    await expect(container.locator('.provider-profile-kind-badge--master')).toHaveCount(1);
    await expect(container.locator('.provider-profile-share').first()).toBeVisible();
    await screenshot(page, 'provider-catalog-list');

    await page.locator('.providers-content-wrapper .query-status-bar').click();
    await expect(page.locator('.provider-profile-query-block')).toBeVisible({ timeout: 5000 });

    await test.step('kind filter — multi-match Shop (userEn + adminUk), single-match Support (userUk) and Master (moderatorEn)', async () => {
      await page.locator('.provider-profile-query-block').locator('vaadin-multi-select-combo-box[data-testid="provider-profile-filter-kind"]').click();
      await page.locator('vaadin-multi-select-combo-box-item').filter({ hasText: 'Shop' }).first().click();
      await page.keyboard.press('Escape');
      await page.locator('.query-action-block vaadin-button[title*="Apply"]').click();
      await page.waitForTimeout(300);
      await expect(container.locator('.provider-profile-card')).toHaveCount(2, { timeout: 10000 });
      await expect(container.locator('.provider-profile-kind-badge--shop')).toHaveCount(2);
      await screenshot(page, 'provider-catalog-filter-kind-shop');

      await page.locator('.query-action-block vaadin-button[title*="Clear"]').click();
      await page.waitForTimeout(300);
      await expect(container.locator('.provider-profile-card')).toHaveCount(4, { timeout: 10000 });

      await page.locator('.provider-profile-query-block').locator('vaadin-multi-select-combo-box[data-testid="provider-profile-filter-kind"]').click();
      await page.locator('vaadin-multi-select-combo-box-item').filter({ hasText: 'Support' }).first().click();
      await page.keyboard.press('Escape');
      await page.locator('.query-action-block vaadin-button[title*="Apply"]').click();
      await page.waitForTimeout(300);
      await expect(container.locator('.provider-profile-card')).toHaveCount(1, { timeout: 10000 });
      await expect(container.locator('.provider-profile-kind-badge--support')).toBeVisible();
      await screenshot(page, 'provider-catalog-filter-kind-support');

      await page.locator('.query-action-block vaadin-button[title*="Clear"]').click();
      await page.waitForTimeout(300);
      await expect(container.locator('.provider-profile-card')).toHaveCount(4, { timeout: 10000 });

      await page.locator('.provider-profile-query-block').locator('vaadin-multi-select-combo-box[data-testid="provider-profile-filter-kind"]').click();
      await page.locator('vaadin-multi-select-combo-box-item').filter({ hasText: 'Master' }).first().click();
      await page.keyboard.press('Escape');
      await page.locator('.query-action-block vaadin-button[title*="Apply"]').click();
      await page.waitForTimeout(300);
      await expect(container.locator('.provider-profile-card')).toHaveCount(1, { timeout: 10000 });
      await expect(container.locator('.provider-profile-kind-badge--master')).toBeVisible();
      await screenshot(page, 'provider-catalog-filter-kind-master');

      await page.locator('.query-action-block vaadin-button[title*="Clear"]').click();
      await page.waitForTimeout(300);
      await expect(container.locator('.provider-profile-card')).toHaveCount(4, { timeout: 10000 });
    });

    await test.step('category filter — Vehicles matches userEn (holds both categories since the earlier edit test) + userUk + moderatorEn, Electronics matches userEn + adminUk', async () => {
      await page.locator('.provider-profile-query-block').locator('[data-testid="provider-profile-filter-categories"]').click();
      await page.locator('vaadin-multi-select-combo-box-item').filter({ hasText: 'Vehicles' }).first().click();
      await page.keyboard.press('Escape');
      await page.locator('.query-action-block vaadin-button[title*="Apply"]').click();
      await page.waitForTimeout(300);
      // userEn's own "edits provider profile" test earlier in this file adds Vehicles on top of
      // its original Electronics (never replacing it), so userEn also matches this filter.
      await expect(container.locator('.provider-profile-card')).toHaveCount(3, { timeout: 10000 });
      await expect(container.locator('.provider-profile-kind-badge--shop')).toHaveCount(1);
      await expect(container.locator('.provider-profile-kind-badge--support')).toBeVisible();
      await expect(container.locator('.provider-profile-kind-badge--master')).toBeVisible();
      await screenshot(page, 'provider-catalog-filter-category-vehicles');

      await page.locator('.query-action-block vaadin-button[title*="Clear"]').click();
      await page.waitForTimeout(300);
      await expect(container.locator('.provider-profile-card')).toHaveCount(4, { timeout: 10000 });

      await page.locator('.provider-profile-query-block').locator('[data-testid="provider-profile-filter-categories"]').click();
      await page.locator('vaadin-multi-select-combo-box-item').filter({ hasText: 'Electronics' }).first().click();
      await page.keyboard.press('Escape');
      await page.locator('.query-action-block vaadin-button[title*="Apply"]').click();
      await page.waitForTimeout(300);
      await expect(container.locator('.provider-profile-card')).toHaveCount(2, { timeout: 10000 });
      await expect(container.locator('.provider-profile-kind-badge--shop')).toHaveCount(2);
      await screenshot(page, 'provider-catalog-filter-category-electronics');

      await page.locator('.query-action-block vaadin-button[title*="Clear"]').click();
      await page.waitForTimeout(300);
      await expect(container.locator('.provider-profile-card')).toHaveCount(4, { timeout: 10000 });
    });

    await page.locator('.provider-profile-query-block').locator('vaadin-combo-box[data-testid="provider-profile-filter-city"] input').click();
    await page.locator('.provider-profile-query-block').locator('vaadin-combo-box[data-testid="provider-profile-filter-city"] input').fill('Kyiv');
    await page.locator('vaadin-combo-box-item').filter({ hasText: 'Kyiv' }).first().click();
    await page.locator('.query-action-block vaadin-button[title*="Apply"]').click();
    await page.waitForTimeout(300);
    await expect(container.locator('.provider-profile-card')).toHaveCount(2, { timeout: 10000 });
    await expect(container.locator('.provider-profile-kind-badge--support')).toBeVisible();
    await expect(container.locator('.provider-profile-kind-badge--shop')).toBeVisible();
    await screenshot(page, 'provider-catalog-filter-city-kyiv');

    await page.locator('.query-action-block vaadin-button[title*="Clear"]').click();
    await page.waitForTimeout(300);
    await expect(container.locator('.provider-profile-card')).toHaveCount(4, { timeout: 10000 });

    const cardOrder = () => container.locator('.provider-profile-card').evaluateAll(
      els => els.map(el => el.getAttribute('data-provider-id')));
    const cardIdByOwner = async name => container.locator('.provider-profile-card')
      .filter({ has: page.locator('.provider-profile-card-title', { hasText: name }) })
      .first().getAttribute('data-provider-id');
    const userEnId = await cardIdByOwner(TEST_USERS.userEn.name);
    const userUkId = await cardIdByOwner(TEST_USERS.userUk.name);
    const moderatorEnId = await cardIdByOwner(TEST_USERS.moderatorEn.name);
    const adminUkId = await cardIdByOwner(TEST_USERS.adminUk.name);

    await test.step('Kind row no longer carries a sort icon — regression guard for the fix that moved sorting onto its own Created/Updated rows', async () => {
      const kindRow = page.locator('.provider-profile-query-block .query-inline-row')
        .filter({ has: page.locator('.query-inline-label-sort', { hasText: 'Kind' }) });
      await expect(kindRow.locator('.sort-icon')).toHaveCount(0);
    });

    const createdIcon = page.locator('.provider-profile-query-block .query-inline-row')
      .filter({ has: page.locator('.query-inline-label-sort', { hasText: 'Created' }) })
      .locator('.sort-icon');
    const updatedIcon = page.locator('.provider-profile-query-block .query-inline-row')
      .filter({ has: page.locator('.query-inline-label-sort', { hasText: 'Updated' }) })
      .locator('.sort-icon');

    await test.step('sort by updated date — Updated row\'s own sort icon cycles DESC -> NEUTRAL -> ASC -> DESC, real card order asserted at each state', async () => {
      // Default sort is updatedAt DESC, createdAt DESC -- moderatorEn (re-edited last, so its
      // updatedAt is the latest of all four) is deterministically first without any click needed.
      await expect(updatedIcon).toHaveAttribute('aria-label', 'Descending');
      expect(await cardOrder()).toEqual([moderatorEnId, adminUkId, userUkId, userEnId]);
      await screenshot(page, 'provider-catalog-sort-updated-desc');

      // Neutralize createdAt (the silent tie-breaker) first -- updatedAt is appended after
      // createdAt in the underlying multi-field sort, so createdAt would otherwise stay the
      // dominant sort key and mask updatedAt's own direction changes below.
      await createdIcon.click();
      await expect(createdIcon).toHaveAttribute('aria-label', 'No sorting');
      await page.locator('.query-action-block vaadin-button[title*="Apply"]').click();
      await waitForVaadin(page);

      // DESC -> NEUTRAL: no sort criterion left at all -- no ORDER BY clause is issued, so
      // Postgres returns rows in whatever physical order it finds them. That order is not a
      // documented contract (an UPDATE can move a row's physical position), so only the count and
      // icon state are asserted here, not a specific card order.
      await updatedIcon.click();
      await expect(updatedIcon).toHaveAttribute('aria-label', 'No sorting');
      await page.locator('.query-action-block vaadin-button[title*="Apply"]').click();
      await waitForVaadin(page);
      await expect(container.locator('.provider-profile-card')).toHaveCount(4, { timeout: 10000 });

      // NEUTRAL -> ASC: order becomes fully determined by real updated_at timestamps (a real
      // ORDER BY, not a coincidental physical-storage order) -- the real proof this row's own sort
      // drives the result.
      await updatedIcon.click();
      await expect(updatedIcon).toHaveAttribute('aria-label', 'Ascending');
      await page.locator('.query-action-block vaadin-button[title*="Apply"]').click();
      await waitForVaadin(page);
      await expect(container.locator('.provider-profile-card')).toHaveCount(4, { timeout: 10000 });
      expect(await cardOrder()).toEqual([userEnId, userUkId, adminUkId, moderatorEnId]);
      await screenshot(page, 'provider-catalog-sort-updated-asc');

      // ASC -> DESC, back to the default state.
      await updatedIcon.click();
      await expect(updatedIcon).toHaveAttribute('aria-label', 'Descending');
      await page.locator('.query-action-block vaadin-button[title*="Apply"]').click();
      await waitForVaadin(page);
      await expect(container.locator('.provider-profile-card')).toHaveCount(4, { timeout: 10000 });
      expect(await cardOrder()).toEqual([moderatorEnId, adminUkId, userUkId, userEnId]);
    });

    await test.step('sort by created date — Created row\'s own sort icon cycles independently of the Updated row, real card order asserted at each state', async () => {
      // Neutralize Updated first (currently DESC, the default) so Created's own direction is
      // what actually drives the visible order below, not masked by Updated staying dominant.
      await updatedIcon.click();
      await expect(updatedIcon).toHaveAttribute('aria-label', 'No sorting');
      await page.locator('.query-action-block vaadin-button[title*="Apply"]').click();
      await waitForVaadin(page);

      // Created row is currently NEUTRAL (cleared in the previous step). Clicking it does not
      // disturb Updated's own icon state, proving the two rows are independently tracked.
      await expect(createdIcon).toHaveAttribute('aria-label', 'No sorting');
      await createdIcon.click();
      await expect(createdIcon).toHaveAttribute('aria-label', 'Ascending');
      await expect(updatedIcon).toHaveAttribute('aria-label', 'No sorting');
      await page.locator('.query-action-block vaadin-button[title*="Apply"]').click();
      await waitForVaadin(page);
      await expect(container.locator('.provider-profile-card')).toHaveCount(4, { timeout: 10000 });
      // Real creation order throughout this file's own test order: userEn, userUk, moderatorEn,
      // adminUk -- createdAt ASC reproduces it exactly, unaffected by moderatorEn's later re-edit
      // since createdAt never changes after creation.
      expect(await cardOrder()).toEqual([userEnId, userUkId, moderatorEnId, adminUkId]);
      await screenshot(page, 'provider-catalog-sort-created-asc');

      await createdIcon.click();
      await expect(createdIcon).toHaveAttribute('aria-label', 'Descending');
      await page.locator('.query-action-block vaadin-button[title*="Apply"]').click();
      await waitForVaadin(page);
      await expect(container.locator('.provider-profile-card')).toHaveCount(4, { timeout: 10000 });
      expect(await cardOrder()).toEqual([adminUkId, moderatorEnId, userUkId, userEnId]);
      await screenshot(page, 'provider-catalog-sort-created-desc');

      await createdIcon.click();
      await expect(createdIcon).toHaveAttribute('aria-label', 'No sorting');
      await page.locator('.query-action-block vaadin-button[title*="Apply"]').click();
      await waitForVaadin(page);
    });

    await test.step('created/updated date-range filters — reuses the shared verifyDateRangeFilters helper', async () => {
      await verifyDateRangeFilters(page, '.provider-profile-query-block', 'provider', 4);
      await expect(container.locator('.provider-profile-card')).toHaveCount(4, { timeout: 10000 });
    });
  });

  test('userEn opens a provider deep link — direct navigation to /providers/:id opens the catalog overlay, share button copies link, sitemap.xml lists it', async () => {
    await page.goto('/');
    await page.locator('vaadin-tab').filter({ hasText: 'Providers' }).click();
    await page.waitForTimeout(300);

    const card = page.locator('.provider-profile-card--shop')
      .filter({ has: page.locator('.provider-profile-card-title', { hasText: TEST_USERS.userEn.name }) });
    await card.waitFor({ timeout: 5000 });
    const providerId = await card.getAttribute('data-provider-id');
    expect(providerId).toBeTruthy();

    await page.goto(`/providers/${providerId}-userEn`);
    const overlay = page.locator('.provider-profile-catalog-overlay');
    await overlay.waitFor({ timeout: 10000 });
    await expect(overlay.locator('.provider-profile-kind-badge')).toContainText('Shop');
    await screenshot(page, 'provider-catalog-deep-link-opened');

    await test.step('share button — copies link to clipboard, shows confirmation notification', async () => {
      await page.evaluate(() => {
        navigator.clipboard.writeText = () => Promise.resolve();
      });
      await overlay.locator('.overlay__view-share').click();
      await page.locator('vaadin-notification-card').filter({ hasText: /link copied/i }).first().waitFor({ timeout: 5000 });
      await closeNotification(page);
    });

    await closeOverlay(page);

    await test.step('sitemap.xml — valid XML, lists this provider profile\'s deep link', async () => {
      const response = await page.request.get('/sitemap.xml');
      expect(response.ok()).toBeTruthy();
      expect(response.headers()['content-type']).toContain('xml');
      const body = await response.text();
      expect(body).toContain('<urlset');
      expect(body).toContain(`/providers/${providerId}</loc>`);
    });

    await test.step('crawler-facing HTML — og:title/description/url, og:type=profile, twitter:card uses name= not property=, JSON-LD ProfilePage block present', async () => {
      const response = await page.request.get(`/providers/${providerId}`);
      expect(response.ok()).toBeTruthy();
      const html = await response.text();
      expect(html).toContain('<meta property="og:type" content="profile">');
      expect(html).toContain('<meta name="twitter:card" content="summary_large_image">');
      expect(html).not.toContain('property="twitter:card"');
      expect(html).toMatch(/<script type="application\/ld\+json">\{.*"@type":"ProfilePage".*\}<\/script>/);
    });

    await test.step('card click — updates URL to /providers/:id, browser Back closes overlay and returns to /', async () => {
      await card.click();
      await overlay.waitFor({ timeout: 10000 });
      await expect(page).toHaveURL(new RegExp(`/providers/${providerId}$`));
      await page.goBack();
      await overlay.waitFor({ state: 'hidden', timeout: 10000 });
      await expect(page).toHaveURL(/\/$/);
    });
  });

  test('userEn deletes their own provider profile from the public catalog — confirm dialog, card removed, empty AccountOverlay state', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.userEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.userEn);
    await page.locator('vaadin-tab').filter({ hasText: 'Providers' }).click();
    await page.waitForTimeout(300);

    const card = page.locator('.provider-profile-card--shop')
      .filter({ has: page.locator('.provider-profile-card-title', { hasText: TEST_USERS.userEn.name }) });
    await card.waitFor({ timeout: 5000 });
    await card.hover();
    await card.locator('.provider-profile-delete').click();
    await confirmDeleteDialog(page);

    await expect(page.locator('vaadin-notification-container')).toContainText('deleted', { timeout: 5000 });
    await closeNotification(page);
    // adminUk's SHOP card remains -- only userEn's own card is gone, so the catalog drops from
    // 4 to 3 and exactly one SHOP card (adminUk's) survives.
    await expect(page.locator('.provider-profile-card')).toHaveCount(3, { timeout: 5000 });
    await expect(page.locator('.provider-profile-card--shop')).toHaveCount(1, { timeout: 5000 });
    await screenshot(page, 'provider-catalog-deleted');

    await runOpenSettingsFlow(page);
    await openProviderProfileTab(page);
    await expect(page.locator('.account-overlay .provider-profile-view-empty-text')).toBeVisible({ timeout: 5000 });
    await runCloseSettingsFlow(page);

    await runLogoutFlow(page, expect);
  });

  test('userUk (non-privileged) edits their SUPPORT provider profile — SUPPORT stays visible but disabled, MASTER/SHOP remain enabled', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.userUk);
    await runSubmitLoginFlow(page, expect, TEST_USERS.userUk);
    await runOpenSettingsFlow(page);
    await openProviderProfileTab(page);

    // userUk already has a SUPPORT profile from the earlier admin-on-behalf test -- edit it to
    // confirm SUPPORT stays selectable-as-is (disabled, not removed) so the Binder can still
    // represent the actor's real current value.
    await page.locator('.account-overlay vaadin-button').filter({ hasText: /edit|редагувати/i }).click();
    await page.waitForTimeout(300);

    await expect(page.locator('.account-overlay vaadin-radio-button').filter({ hasText: 'MASTER' })).toHaveCount(1, { timeout: 5000 });
    await expect(page.locator('.account-overlay vaadin-radio-button').filter({ hasText: 'SHOP' })).toHaveCount(1, { timeout: 5000 });
    await expect(page.locator('.account-overlay vaadin-radio-button').filter({ hasText: 'MASTER' })).not.toHaveJSProperty('disabled', true);
    await expect(page.locator('.account-overlay vaadin-radio-button').filter({ hasText: 'SHOP' })).not.toHaveJSProperty('disabled', true);
    const supportRadio = page.locator('.account-overlay vaadin-radio-button').filter({ hasText: 'SUPPORT' });
    await expect(supportRadio).toHaveCount(1, { timeout: 5000 });
    await expect(supportRadio).toHaveJSProperty('disabled', true);
    await screenshot(page, 'provider-catalog-support-disabled-not-offered');

    await page.locator('.account-overlay vaadin-button[title="Cancel"], .account-overlay vaadin-button[title="Скасувати"]').click();
    await page.waitForTimeout(300);
    await runCloseSettingsFlow(page);
    await runLogoutFlow(page, expect);
  });
});
