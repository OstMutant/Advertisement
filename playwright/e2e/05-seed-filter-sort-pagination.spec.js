const { test, expect, screenshot, closeOverlay, closeNotification, TEST_USERS } = require('./_helpers');

async function openSettings(page) {
  await page.locator('.header-settings-button').click();
  await page.locator('.base-overlay.overlay--visible').waitFor({ timeout: 10000 });
}

async function switchToTab(page, tabName, itemSelector) {
  await page.locator('vaadin-tab').filter({ hasText: tabName }).first().click();
  await page.locator(itemSelector).first().waitFor({ timeout: 8000 });
}

async function screenshotThenClose(page, name) {
  if (process.env.PW_SCREENSHOTS) {
    const buffer = await page.screenshot({ fullPage: false });
    await test.info().attach(name, { body: buffer, contentType: 'image/png' });
  }
  await closeNotification(page);
}
const { signUpBulkParallel, loginBulk, logoutBulk, createAdvertisementBulk } = require('./_flows/seed.flow');
const {
  openQueryPanel, clearFilter, applyFilter,
  resetDefaultSorts,
  fillText, fillNumber, fillRole, fillCategory, fillCity, fillAdKind,
  getRow, getTotalCount,
  verifyPagination, verifyDateRangeFilters, verifySortColumn,
} = require('./_flows/filter.flow');
const { changePageSizes, openHistory, closeHistory, restoreLatestFromActivity, getPageSizes } = require('./_flows/settings.flow');
const { openTimelineTab, openTimelineFilter, assertActorPickerVisible, assertAllRowsHaveType, assertAllRowsHaveAction, fillEntityType, fillActionType, fillActorPicker, removeActorChip, actorChipCount, TIMELINE_BLOCK } = require('./_flows/timeline.flow');
const { goToNextPage } = require('./_flows/filter.flow');
const { runCreateCategoryFlow } = require('./_flows/category.flow');
const { runCreateCityFlow } = require('./_flows/city.flow');

test.describe.configure({ mode: 'serial' });

const ADV_BLOCK  = '.advertisement-query-block';
const USER_BLOCK = '.user-query-block';
const ADV_ITEM   = '.advertisement-card';
const USER_ITEM  = '.user-grid-name';

const SEED_COUNT = 60;
// Distinct from spec-03 categories (Electronics, Vehicles) to avoid duplicates in e2e suite mode.
const CATEGORIES = ['Clothing', 'Books', 'Furniture', 'Sports', 'Toys'];
// Distinct from spec-03 cities (Lviv, Kyiv) to avoid duplicates in e2e suite mode.
const CITIES = ['Kharkiv', 'Odesa', 'Dnipro'];
// No seeding needed -- AdKind is a fixed enum, not a taxon dictionary like categories/cities.
const AD_KINDS = ['Offer', 'Request', 'Product'];

// Same generation formula as spec 03's MAX_NAME_100 (maxEn's 100-char boundary name) — used as
// the timeline actor filter's second pick below so the actor chip's own truncation gets exercised
// by a real maximum-length name, not just short "Seed User N" labels.
const MAX_ACTOR_NAME = 'MaxBoundaryUserNameForValidationTest'.repeat(3).substring(0, 100);

const SEED_CATEGORIES = [
  { nameEn: 'Clothing',  descriptionEn: 'Clothes, fashion and apparel.',        nameUk: 'Одяг',    descriptionUk: 'Одяг, мода та аксесуари.' },
  { nameEn: 'Books',     descriptionEn: 'Books, magazines and literature.',      nameUk: 'Книги',   descriptionUk: 'Книги, журнали та література.' },
  { nameEn: 'Furniture', descriptionEn: 'Home and office furniture.',            nameUk: 'Меблі',   descriptionUk: 'Домашні та офісні меблі.' },
  { nameEn: 'Sports',    descriptionEn: 'Sports equipment and accessories.',     nameUk: 'Спорт',   descriptionUk: 'Спортивне обладнання та аксесуари.' },
  { nameEn: 'Toys',      descriptionEn: 'Toys, games and hobbies.',              nameUk: 'Іграшки', descriptionUk: 'Іграшки, ігри та хобі.' },
];

const SEED_CITIES = [
  { nameEn: 'Kharkiv', descriptionEn: 'Major city in eastern Ukraine.',     nameUk: 'Харків', descriptionUk: 'Велике місто на сході України.' },
  { nameEn: 'Odesa',   descriptionEn: 'Port city on the Black Sea.',        nameUk: 'Одеса',  descriptionUk: 'Портове місто на Чорному морі.' },
  { nameEn: 'Dnipro',  descriptionEn: 'Major city on the Dnipro river.',    nameUk: 'Дніпро', descriptionUk: 'Велике місто на річці Дніпро.' },
];

const seedUser = i => ({
  name: `Seed User ${String(i).padStart(2, '0')}`,
  email: `seed.user${String(i).padStart(2, '0')}@example.com`,
  password: 'password',
});

const seedAd = i => ({
  title: `Seed Advertisement ${String(i).padStart(2, '0')}`,
  description: `Description for seed advertisement ${i}.`,
});

// Ensures adminEn exists and is ADMIN before seed tests run.
// In a clean-DB isolated run adminEn won't exist — sign them up here so they become
// the very first user and get auto-promoted to ADMIN by the app.
// In a full e2e run adminEn was created by spec-02; login succeeds and we log out cleanly.
async function ensureAdminEn(page) {
  await page.locator('vaadin-button').filter({ hasText: /log in/i }).first().click();
  await page.locator('[data-testid="login-email-label"]').waitFor({ timeout: 5000 });
  await page.locator('[data-testid="login-email-label"] input').fill(TEST_USERS.adminEn.email);
  await page.locator('[data-testid="login-password-label"] input').fill(TEST_USERS.adminEn.password);
  await page.locator('vaadin-button').filter({ hasText: /log in/i }).last().click();

  const loginOk = await page.locator('.header-settings-button')
    .waitFor({ state: 'visible', timeout: 8000 })
    .then(() => true)
    .catch(() => false);

  if (loginOk) {
    // Already exists — log out so tests start from logged-out state.
    await page.locator('.header-logout-button').click();
    await page.locator('vaadin-confirm-dialog-overlay[opened]:not([opening])').waitFor({ state: 'attached', timeout: 5000 });
    await page.getByRole('button', { name: /^log out$|^вийти$/i }).last().click();
    await page.locator('vaadin-button').filter({ hasText: /log in/i }).first().waitFor({ timeout: 5000 });
  } else {
    // Login failed — adminEn doesn't exist yet; close dialog and sign them up.
    await page.keyboard.press('Escape');
    await page.locator('[data-testid="login-email-label"]').waitFor({ state: 'hidden', timeout: 5000 });
    await page.locator('vaadin-button').filter({ hasText: /sign up/i }).first().click();
    await page.locator('[data-testid="signup-name-label"]').waitFor({ timeout: 5000 });
    await page.locator('[data-testid="signup-name-label"] input').fill(TEST_USERS.adminEn.name);
    await page.locator('[data-testid="signup-email-label"] input').fill(TEST_USERS.adminEn.email);
    await page.locator('[data-testid="signup-password-label"] input').fill(TEST_USERS.adminEn.password);
    await page.getByRole('button', { name: /sign up/i }).last().click();
    await page.locator('vaadin-dialog-overlay[theme~="signup-dialog"]').waitFor({ state: 'hidden', timeout: 8000 });
    // Dismiss all notifications (success + possible error) one by one.
    for (let i = 0; i < 5; i++) {
      const card = page.locator('vaadin-notification-card').first();
      if (!(await card.isVisible().catch(() => false))) break;
      const btn = card.locator('vaadin-button');
      if (await btn.isVisible().catch(() => false)) await btn.click();
      await card.waitFor({ state: 'hidden', timeout: 8000 }).catch(() => {});
    }
  }
}

test.describe('Seed data and query validation', () => {
  test.skip(!process.env.PW_FULL, 'Skipped by default — run with --full to generate seed data');

  let page;

  test.beforeAll(async ({ browser }) => {
    page = await browser.newPage();
    await page.goto('/');
    await page.locator('vaadin-tab').filter({ hasText: 'Advertisements' }).first().waitFor({ timeout: 8000 });
    await ensureAdminEn(page);
  });

  test.afterAll(async () => {
    await page.close();
  });

  // ── Test 1: seed users ────────────────────────────────────────────────────

  test(`seed ${SEED_COUNT} users — parallel signup`, async ({ browser }) => {
    test.setTimeout(5 * 60 * 1000);
    const users = Array.from({ length: SEED_COUNT }, (_, i) => seedUser(i + 1));
    await signUpBulkParallel(browser, users, 1);
  });

  // ── Test 2: seed advertisements ───────────────────────────────────────────

  test(`adminEn seeds ${SEED_COUNT} advertisements — five categories, three cities, three listing types`, async () => {
    test.setTimeout(5 * 60 * 1000);
    await loginBulk(page, TEST_USERS.adminEn);
    for (const cat of SEED_CATEGORIES) await runCreateCategoryFlow(page, expect, cat);
    for (const city of SEED_CITIES) await runCreateCityFlow(page, expect, city);
    await page.locator('vaadin-tab').filter({ hasText: 'Advertisements' }).first().click();
    await page.locator('.add-advertisement-button').waitFor({ timeout: 8000 });
    for (let i = 1; i <= SEED_COUNT; i++) {
      await createAdvertisementBulk(page, {
        ...seedAd(i),
        category: CATEGORIES[(i - 1) % CATEGORIES.length],
        city: CITIES[(i - 1) % CITIES.length],
        adKind: AD_KINDS[(i - 1) % AD_KINDS.length],
      });
    }
    // Force a full page reload to clear 60 stale advertisement overlay DOM elements before logout.
    // Without this, SPA-style logout/login preserves the stale DOM, which causes Vaadin to
    // re-activate a stale overlay when the category filter combo fires a server sync event.
    await page.reload();
    await page.locator('.header-logout-button').waitFor({ timeout: 15000 });
    await logoutBulk(page);
  });

  // ── Test 3: advertisement filters, sort, pagination ───────────────────────

  test('advertisements — title, date, category, city and listing type filters, column sort, pagination', async () => {
    test.setTimeout(5 * 60 * 1000);
    await loginBulk(page, TEST_USERS.adminEn);
    await page.locator('vaadin-tab').filter({ hasText: 'Advertisements' }).first().click();
    await page.locator(ADV_ITEM).first().waitFor({ timeout: 8000 });

    await openQueryPanel(page, ADV_BLOCK);
    await screenshot(page, 'adv-filter-panel-open');

    // ── title exact match ─────────────────────────────────────────────────────
    await fillText(page, ADV_BLOCK, 'Title', 'Seed Advertisement 01');
    await applyFilter(page, ADV_BLOCK);
    await expect(page.locator(ADV_ITEM)).toHaveCount(1, { timeout: 8000 });
    await screenshot(page, 'adv-filter-title-exact');
    await clearFilter(page, ADV_BLOCK);

    // ── title partial match → 60 results ─────────────────────────────────────
    await fillText(page, ADV_BLOCK, 'Title', 'Seed');
    await applyFilter(page, ADV_BLOCK);
    await expect(page.locator('.pagination-count:visible')).toContainText(`of ${SEED_COUNT}`, { timeout: 8000 });
    await screenshot(page, 'adv-filter-title-partial');
    await clearFilter(page, ADV_BLOCK);

    // ── category filter → SEED_COUNT / 5 results per category ────────────────
    await fillCategory(page, ADV_BLOCK, CATEGORIES[0]);
    await applyFilter(page, ADV_BLOCK);
    await expect(page.locator('.pagination-count:visible')).toContainText(`of ${SEED_COUNT / CATEGORIES.length}`, { timeout: 8000 });
    await screenshot(page, 'adv-filter-category');
    await clearFilter(page, ADV_BLOCK);

    // ── city filter → SEED_COUNT / 3 results per city ────────────────────────
    await fillCity(page, ADV_BLOCK, CITIES[0]);
    await applyFilter(page, ADV_BLOCK);
    await expect(page.locator('.pagination-count:visible')).toContainText(`of ${SEED_COUNT / CITIES.length}`, { timeout: 8000 });
    await screenshot(page, 'adv-filter-city');
    await clearFilter(page, ADV_BLOCK);

    // ── listing type filter → at least SEED_COUNT / 3 results per type; unlike category/city,
    // every advertisement always has a listing type, so non-seed ads left behind by earlier specs
    // always add to one of the three buckets -- use >= same as verifyDateRangeFilters, not toBe ──
    await fillAdKind(page, ADV_BLOCK, AD_KINDS[0]);
    await applyFilter(page, ADV_BLOCK);
    await page.locator('.pagination-count:visible').waitFor({ timeout: 8000 });
    expect(await getTotalCount(page)).toBeGreaterThanOrEqual(SEED_COUNT / AD_KINDS.length);
    await screenshot(page, 'adv-filter-ad-kind');
    await clearFilter(page, ADV_BLOCK);

    // ── date range filters (created/updated today + boundary cases) ──────────
    await verifyDateRangeFilters(page, ADV_BLOCK, 'adv', SEED_COUNT);

    // ── sorts ─────────────────────────────────────────────────────────────────
    await verifySortColumn(page, {
      block: ADV_BLOCK, sortCol: 'Title', itemSelector: ADV_ITEM,
      assertSelector: `${ADV_ITEM} .advertisement-title`,
      setup: { reset: 'all', filter: { field: 'Title', value: 'Seed' } },
      firstAsc: 'Seed Advertisement 01', firstDesc: 'Seed Advertisement 60', prefix: 'adv',
    });
    await verifySortColumn(page, {
      block: ADV_BLOCK, sortCol: 'Created At', itemSelector: ADV_ITEM,
      assertSelector: `${ADV_ITEM} .advertisement-title`,
      setup: { reset: 'Updated At', filter: { field: 'Title', value: 'Seed' } },
      startDesc: true,
      firstAsc: 'Seed Advertisement 01', firstDesc: 'Seed Advertisement 60', prefix: 'adv',
    });
    await verifySortColumn(page, {
      block: ADV_BLOCK, sortCol: 'Updated At', itemSelector: ADV_ITEM,
      assertSelector: `${ADV_ITEM} .advertisement-title`,
      setup: { reset: 'Created At', filter: { field: 'Title', value: 'Seed' } },
      startDesc: true,
      firstAsc: 'Seed Advertisement 01', firstDesc: 'Seed Advertisement 60', prefix: 'adv',
    });

    // ── pagination ────────────────────────────────────────────────────────────
    await clearFilter(page, ADV_BLOCK);
    await resetDefaultSorts(page, ADV_BLOCK);
    await fillText(page, ADV_BLOCK, 'Title', 'Seed');
    await applyFilter(page, ADV_BLOCK);
    await verifyPagination(page, 'adv', SEED_COUNT);
    await clearFilter(page, ADV_BLOCK);
    await logoutBulk(page);
  });

  // ── Test 4: user filters, sort, pagination ────────────────────────────────

  test('users — email, role and date filters, invalid fractional ID input, column sort, pagination', async () => {
    test.setTimeout(5 * 60 * 1000);
    await loginBulk(page, TEST_USERS.adminEn);
    await page.locator('vaadin-tab').filter({ hasText: 'Users' }).first().click();
    await page.locator(USER_ITEM).first().waitFor({ timeout: 8000 });

    await openQueryPanel(page, USER_BLOCK);
    await screenshot(page, 'user-filter-panel-open');

    // ── email exact match ─────────────────────────────────────────────────────
    await fillText(page, USER_BLOCK, 'Email', 'seed.user01@example.com');
    await applyFilter(page, USER_BLOCK);
    await expect(page.locator('.pagination-count:visible')).toContainText('1\u20131 of 1', { timeout: 8000 });
    await screenshot(page, 'user-filter-email-exact');
    await clearFilter(page, USER_BLOCK);

    // ── email partial match → 60 results ─────────────────────────────────────
    await fillText(page, USER_BLOCK, 'Email', 'seed.user');
    await applyFilter(page, USER_BLOCK);
    await expect(page.locator('.pagination-count:visible')).toContainText(`of ${SEED_COUNT}`, { timeout: 8000 });
    await screenshot(page, 'user-filter-email-partial');
    await clearFilter(page, USER_BLOCK);

    // ── email nonexistent → 0 results ────────────────────────────────────────
    await fillText(page, USER_BLOCK, 'Email', 'no-such-xyz@example.com');
    await applyFilter(page, USER_BLOCK);
    await expect(page.locator('.pagination-count:visible')).toContainText('0', { timeout: 8000 });
    await screenshot(page, 'user-filter-email-no-results');
    await clearFilter(page, USER_BLOCK);

    // ── name partial match → 60 results ──────────────────────────────────────
    await fillText(page, USER_BLOCK, 'Name', 'Seed');
    await applyFilter(page, USER_BLOCK);
    await expect(page.locator('.pagination-count:visible')).toContainText(`of ${SEED_COUNT}`, { timeout: 8000 });
    await screenshot(page, 'user-filter-name-partial');
    await clearFilter(page, USER_BLOCK);

    // ── role filter (USER) ────────────────────────────────────────────────────
    await fillRole(page, USER_BLOCK, 'USER');
    await applyFilter(page, USER_BLOCK);
    expect(await getTotalCount(page)).toBeGreaterThanOrEqual(SEED_COUNT);
    await screenshot(page, 'user-filter-role-user');
    await clearFilter(page, USER_BLOCK);

    // ── role filter (ADMIN) — at least 1 result (full run may have 2 if adminUk promoted) ──
    await fillRole(page, USER_BLOCK, 'ADMIN');
    await applyFilter(page, USER_BLOCK);
    expect(await getTotalCount(page)).toBeGreaterThanOrEqual(1);
    await screenshot(page, 'user-filter-role-admin');
    await clearFilter(page, USER_BLOCK);

    // ── ID range filter ───────────────────────────────────────────────────────
    await fillNumber(page, USER_BLOCK, 'ID', 1, 10);
    await applyFilter(page, USER_BLOCK);
    const idCount = await getTotalCount(page);
    expect(idCount).toBeGreaterThanOrEqual(1);
    expect(idCount).toBeLessThanOrEqual(10);
    await screenshot(page, 'user-filter-id-range');
    await clearFilter(page, USER_BLOCK);

    // ── ID filter fractional input — flagged invalid, not silently truncated (improvement-061) ──
    const idMinInput = getRow(page, USER_BLOCK, 'ID').locator('.query-number input').first();
    const idMinField = getRow(page, USER_BLOCK, 'ID').locator('.query-number').first();
    await idMinInput.fill('1.5');
    await expect(idMinField).toHaveAttribute('invalid', '', { timeout: 5000 });
    await screenshot(page, 'user-filter-id-fractional-invalid');
    await idMinInput.fill('1');
    await expect(idMinField).not.toHaveAttribute('invalid', '', { timeout: 5000 });
    await clearFilter(page, USER_BLOCK);

    // ── date range filters (created/updated today + boundary cases) ──────────
    await verifyDateRangeFilters(page, USER_BLOCK, 'user', SEED_COUNT);

    // ── sorts ─────────────────────────────────────────────────────────────────
    await verifySortColumn(page, {
      block: USER_BLOCK, sortCol: 'Name', itemSelector: USER_ITEM,
      assertSelector: USER_ITEM,
      setup: { reset: 'all', filter: { field: 'Name', value: 'Seed' } },
      firstAsc: 'Seed User 01', firstDesc: 'Seed User 60', prefix: 'user',
    });
    await verifySortColumn(page, {
      block: USER_BLOCK, sortCol: 'Email', itemSelector: USER_ITEM,
      assertSelector: '.user-grid-email',
      setup: { reset: 'clearAll', filter: { field: 'Email', value: 'seed.user' } },
      firstAsc: 'seed.user01@example.com', firstDesc: 'seed.user60@example.com', prefix: 'user',
    });
    await verifySortColumn(page, {
      block: USER_BLOCK, sortCol: 'ID', itemSelector: USER_ITEM,
      assertSelector: USER_ITEM,
      setup: { reset: 'clearAll', filter: { field: 'Name', value: 'Seed' } },
      firstAsc: 'Seed User 01', firstDesc: 'Seed User 60', prefix: 'user',
    });
    // ADMIN < USER alphabetically: ASC → adminEn (ADMIN) first; DESC → seed users (USER) first.
    await verifySortColumn(page, {
      block: USER_BLOCK, sortCol: 'Role', itemSelector: USER_ITEM,
      assertSelector: '.user-role-badge',
      setup: { reset: 'clearAll' },
      firstAsc: 'ADMIN', firstDesc: 'USER', prefix: 'user',
    });
    // Seed users are created ~1.5-1.9s apart in a strictly serial loop, but the sandboxed
    // container's system clock occasionally makes a small (~200ms) backward adjustment
    // (confirmed via diagnostic logging — not an app bug, see improvement-020 investigation
    // notes). That can swap created_at/updated_at order between immediately-adjacent seed
    // users at either end of the batch, even though id order (real insertion order) never
    // does. Tolerate a 1-position slop at both ends instead of asserting an exact name.
    await verifySortColumn(page, {
      block: USER_BLOCK, sortCol: 'Created At', itemSelector: USER_ITEM,
      assertSelector: USER_ITEM,
      setup: { reset: 'Updated At', filter: { field: 'Name', value: 'Seed' } },
      startDesc: true,
      firstAsc: /Seed User 0[12]/, firstDesc: /Seed User (59|60)/, prefix: 'user',
    });
    await verifySortColumn(page, {
      block: USER_BLOCK, sortCol: 'Updated At', itemSelector: USER_ITEM,
      assertSelector: USER_ITEM,
      setup: { reset: 'Created At', filter: { field: 'Name', value: 'Seed' } },
      startDesc: true,
      firstAsc: /Seed User 0[12]/, firstDesc: /Seed User (59|60)/, prefix: 'user',
    });

    // ── pagination ────────────────────────────────────────────────────────────
    await clearFilter(page, USER_BLOCK);
    await resetDefaultSorts(page, USER_BLOCK);
    await fillText(page, USER_BLOCK, 'Email', 'seed.user');
    await applyFilter(page, USER_BLOCK);
    await verifyPagination(page, 'user', SEED_COUNT);
    await clearFilter(page, USER_BLOCK);
    await logoutBulk(page);
  });

  // ── Test 5: settings page sizes, activity verification, restore ───────────

  test('adminEn changes page sizes — activity diff, ads and users grids reflect sizes, restore defaults, no cross-session bleed', async ({ browser }) => {
    test.setTimeout(3 * 60 * 1000);
    await loginBulk(page, TEST_USERS.adminEn);

    // ── verify page size defaults are 20 before any change ───────────────────
    const { adsPageSize: adsDefault, usersPageSize: usersDefault } = await getPageSizes(page);
    expect(adsDefault).toBe(20);
    expect(usersDefault).toBe(20);

    // ── change both page sizes, verify change appears in activity ─────────────
    await changePageSizes(page, 5, 7);
    await screenshotThenClose(page, 'settings-changed');

    // ── cross-session bleed check: userEn's own session must stay unaffected ──
    const userContext = await browser.newContext();
    const userPage = await userContext.newPage();
    await userPage.goto('/');
    await userPage.locator('vaadin-tab').filter({ hasText: 'Advertisements' }).first().waitFor({ timeout: 8000 });
    await loginBulk(userPage, TEST_USERS.userEn);
    await userPage.locator('vaadin-tab').filter({ hasText: 'Advertisements' }).first().click();
    await userPage.locator(ADV_ITEM).first().waitFor({ timeout: 8000 });
    await expect(userPage.locator('.pagination-count:visible'))
      .toContainText('1\u201320 of', { timeout: 5000 });
    await screenshot(userPage, 'settings-bleed-check-userEn-unaffected');
    await logoutBulk(userPage);
    await userContext.close();

    // ── history is a nested overlay now, not a tab: breadcrumb chain Home / Settings / Activity ─
    await openHistory(page);
    await expect(page.locator('.settings-activity-breadcrumb-home')).toBeVisible();
    await expect(page.locator('.settings-activity-breadcrumb-settings')).toBeVisible();
    await expect(page.locator('.settings-activity-close-button')).toBeVisible();
    // exactly one separator between each of the 3 segments -- catches a doubled-up "›" regression
    await expect(page.locator('.settings-activity-overlay .overlay__breadcrumb-sep')).toHaveCount(2);
    await expect(page.locator('.settings-activity-overlay .overlay__breadcrumb')).not.toContainText('››');
    await expect(page.locator('.settings-activity-overlay .entity-activity-list .entity-activity-row').first())
      .toBeVisible({ timeout: 5000 });
    const firstActivityRow = page.locator('.settings-activity-overlay .entity-activity-list .entity-activity-row').first();
    await expect(firstActivityRow.locator('.entity-activity-changes-item').filter({ hasText: /Ads per page|Оголошень/i }).first()).toBeVisible();
    await expect(firstActivityRow.locator('.entity-activity-changes-item').filter({ hasText: /Users per page|Користувач/i }).first()).toBeVisible();
    await screenshot(page, 'settings-activity-after-change');
    // X goes back to Settings (the screen history was opened from), same as the "Settings" link
    await closeHistory(page, 'x');
    await expect(page.locator('.settings-overlay-content')).toBeVisible();
    // the breadcrumb's "Home" link is the one path that exits all the way out
    await openHistory(page);
    await closeHistory(page, 'home');
    await expect(page.locator('.base-overlay.overlay--visible')).toBeHidden();

    // ── verify new page sizes are applied in both views ───────────────────────
    await switchToTab(page, 'Advertisements', ADV_ITEM);
    await expect(page.locator('.pagination-count:visible'))
      .toContainText('1\u20135 of', { timeout: 5000 });
    await screenshot(page, 'settings-ads-page-size-5');

    await switchToTab(page, 'Users', USER_ITEM);
    await expect(page.locator('.pagination-count:visible'))
      .toContainText('1\u20137 of', { timeout: 5000 });
    await screenshot(page, 'settings-users-page-size-7');

    // ── restore defaults via activity, verify restore entry recorded ──────────
    await openSettings(page);
    // single-link breadcrumb case (Settings itself) -- same regression risk, one separator only
    await expect(page.locator('.settings-overlay .overlay__breadcrumb-sep')).toHaveCount(1);
    await expect(page.locator('.settings-overlay .overlay__breadcrumb')).not.toContainText('››');
    await openHistory(page);
    await restoreLatestFromActivity(page);
    await expect(page.locator('.settings-activity-overlay.overlay--visible')).toBeHidden();
    await expect(page.locator('.settings-overlay-content')).toBeVisible();
    await screenshotThenClose(page, 'settings-restored');

    await openHistory(page);
    const activityCount = await page.locator('.settings-activity-overlay .entity-activity-list .entity-activity-row').count();
    expect(activityCount).toBeGreaterThanOrEqual(2);
    await screenshot(page, 'settings-activity-after-restore');
    // closing via the breadcrumb back-link uncovers Settings again (other path than the X above)
    await closeHistory(page);
    await closeOverlay(page);

    // ── verify default page sizes (20) are restored in both views ─────────────
    await switchToTab(page, 'Advertisements', ADV_ITEM);
    await expect(page.locator('.pagination-count:visible'))
      .toContainText('1\u201320 of', { timeout: 5000 });
    await screenshot(page, 'settings-ads-restored-20');

    await switchToTab(page, 'Users', USER_ITEM);
    await expect(page.locator('.pagination-count:visible'))
      .toContainText('1\u201320 of', { timeout: 5000 });
    await screenshot(page, 'settings-users-restored-20');

    // ── verify an unsaved field edit survives a trip into history and back ────
    // History now covers the whole Settings form (a separate overlay, not a tab), so an
    // in-progress edit must still be there, untouched, once history is closed again.
    await openSettings(page);
    const tabSizeInput = page.locator('.settings-overlay-content vaadin-integer-field').nth(0).locator('input');
    await tabSizeInput.click({ clickCount: 3 });
    await tabSizeInput.fill('15');
    await openHistory(page);
    await closeHistory(page);
    await expect(page.locator('.overlay__form-fields-card')).toBeVisible({ timeout: 5000 });
    await expect(tabSizeInput).toHaveValue('15');
    await page.locator('.base-overlay.overlay--visible vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await page.waitForLoadState('networkidle');
    await screenshot(page, 'settings-history-trip-preserves-unsaved-edit');

    // ── cleanup: restore defaults again before logging out ────────────────────
    await openHistory(page);
    await restoreLatestFromActivity(page);
    await closeOverlay(page);

    await logoutBulk(page);
  });

  // ── Test 6: timeline filter and pagination ────────────────────────────────

  test('adminEn verifies timeline — ADVERTISEMENT and USER type filters, CREATED and UPDATED action filters, multi-actor filter with chip removal, pagination', async () => {
    test.setTimeout(3 * 60 * 1000);
    await loginBulk(page, TEST_USERS.adminEn);

    await openTimelineTab(page);

    const total = await getTotalCount(page);
    expect(total).toBeGreaterThan(SEED_COUNT);
    await screenshot(page, 'timeline-total-count');

    await openTimelineFilter(page);
    await assertActorPickerVisible(page, expect, true);

    // Filter by ADVERTISEMENT entity type — all rows must be advertisement
    await fillEntityType(page, 'ADVERTISEMENT');
    await applyFilter(page, TIMELINE_BLOCK);
    await page.locator('.activity-feed .activity-feed-row').first().waitFor({ timeout: 8000 });
    await assertAllRowsHaveType(page, expect, 'advertisement', 'timeline-filter-entity-advertisement');

    // Filter by USER entity type — all rows must be user
    await clearFilter(page, TIMELINE_BLOCK);
    await fillEntityType(page, 'USER');
    await applyFilter(page, TIMELINE_BLOCK);
    await page.locator('.activity-feed .activity-feed-row').first().waitFor({ timeout: 8000 });
    await assertAllRowsHaveType(page, expect, 'user', 'timeline-filter-entity-user');

    // Filter by CREATED action — all rows must be created
    await clearFilter(page, TIMELINE_BLOCK);
    await fillActionType(page, 'CREATED');
    await applyFilter(page, TIMELINE_BLOCK);
    await page.locator('.activity-feed .activity-feed-row').first().waitFor({ timeout: 8000 });
    await assertAllRowsHaveAction(page, expect, 'created', 'timeline-filter-action-created');

    // Filter by UPDATED action — all rows must be updated
    await clearFilter(page, TIMELINE_BLOCK);
    await fillActionType(page, 'UPDATED');
    await applyFilter(page, TIMELINE_BLOCK);
    await page.locator('.activity-feed .activity-feed-row').first().waitFor({ timeout: 8000 });
    await assertAllRowsHaveAction(page, expect, 'updated', 'timeline-filter-action-updated');

    // Filter by actor — result count must be less than unfiltered total. Target the last
    // name-sorted "Seed User" (not adminEn, which sorts near the top and would never exercise
    // the picker grid's second data-provider page — see improvement-056).
    await clearFilter(page, TIMELINE_BLOCK);
    await fillActorPicker(page, 'Seed User 60');
    await applyFilter(page, TIMELINE_BLOCK);
    await page.locator('.activity-feed .activity-feed-row').first().waitFor({ timeout: 8000 });
    const actorFilteredCount = await getTotalCount(page);
    expect(actorFilteredCount).toBeGreaterThan(0);
    expect(actorFilteredCount).toBeLessThan(total);
    expect(await actorChipCount(page)).toBe(1);
    await screenshot(page, 'timeline-filter-actor-adminen');

    // Picking a second actor adds to the selection (not replace) — two chips, result count
    // grows to cover "any of the selected actors" via = ANY(), not just the first pick. Uses
    // maxEn's 100-char boundary name (not another short "Seed User N") so the chip's own
    // max-width/ellipsis truncation gets exercised by a real maximum-length actor name.
    await fillActorPicker(page, MAX_ACTOR_NAME, { useSearch: true });
    expect(await actorChipCount(page)).toBe(2);
    await applyFilter(page, TIMELINE_BLOCK);
    await page.locator('.activity-feed .activity-feed-row').first().waitFor({ timeout: 8000 });
    const twoActorsCount = await getTotalCount(page);
    expect(twoActorsCount).toBeGreaterThanOrEqual(actorFilteredCount);
    expect(twoActorsCount).toBeLessThan(total);
    await screenshot(page, 'timeline-filter-actor-two-selected');

    // Removing one chip shrinks the selection back to a single actor and the result count
    // back down to the single-actor value.
    await removeActorChip(page, MAX_ACTOR_NAME);
    expect(await actorChipCount(page)).toBe(1);
    await applyFilter(page, TIMELINE_BLOCK);
    await page.locator('.activity-feed .activity-feed-row').first().waitFor({ timeout: 8000 });
    expect(await getTotalCount(page)).toBe(actorFilteredCount);
    await screenshot(page, 'timeline-filter-actor-chip-removed');

    // Clear and verify pagination navigates correctly
    await clearFilter(page, TIMELINE_BLOCK);
    await page.locator('.activity-feed .activity-feed-row').first().waitFor({ timeout: 8000 });
    await expect(page.locator('.pagination-count:visible')).toContainText('1\u201320 of', { timeout: 8000 });
    await goToNextPage(page);
    await page.locator('.activity-feed .activity-feed-row').first().waitFor({ timeout: 8000 });
    await expect(page.locator('.pagination-count:visible')).toContainText('21\u201340 of', { timeout: 8000 });
    await screenshot(page, 'timeline-pagination-page2');

    await logoutBulk(page);
  });
});
