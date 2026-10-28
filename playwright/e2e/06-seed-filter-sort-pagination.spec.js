/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Query-bar behavior at scale for the advertisement and user grids, settings page
 *   sizes, and the Timeline filters. Every test seeds its own uniquely labelled data through the
 *   REST API first and scopes every count to that data (a run-scoped title, e-mail or name
 *   fragment, its own categories and cities, or the actor filter on its own users), so the totals
 *   stay exact while other workers write to the same database. Entirely skipped unless PW_FULL is
 *   set.
 *   Per test:
 *   - "administrator filters own 60 seeded advertisements -- ...": seed 5 categories, 3 cities and
 *     60 ads -> title exact (1 card) and partial (60) -> category (12) -> city (20) -> listing type
 *     within own titles (20) -> date ranges within own titles (60, then 0 at the boundaries) ->
 *     title, created and updated sorts -> pagination of 60.
 *   - "administrator filters own 60 seeded users -- ...": seed 60 users and one administrator ->
 *     e-mail exact (1), partial (60), missing (0) -> name (61) -> role USER (60) and ADMIN (1)
 *     within own names -> ID range of own first ten ids (10) -> fractional ID flagged invalid ->
 *     date ranges within own e-mails (60) -> name, e-mail, ID, role, created and updated sorts ->
 *     pagination of 60.
 *   - "new administrator changes page sizes -- ...": seed 21 ads and 21 users so every grid page is
 *     full -> change all three sizes (5/6/7) -> worker user's own grid still shows 20 -> activity
 *     (all three fields, breadcrumb chain) -> ads grid (1-5) -> users grid (1-7) -> providers field
 *     round-trips (6) -> restore -> activity (>=2 rows) -> ads and users grids (1-20) -> providers
 *     field (20) -> an unsaved edit survives a trip into history and back.
 *   - "administrator verifies timeline of own actors -- ...": seed 55 picker users, an actor with
 *     20 ads and one ad update, and a second actor with a 100-character name -> actor filter found
 *     by scrolling the narrowed picker grid (23 rows) -> ADVERTISEMENT (21) and USER (1) type
 *     filters -> CREATED (22) and UPDATED (1) action filters -> second actor chip (25) -> chip
 *     removal (23) -> pagination of 23.
 * Usage: run via the Playwright test runner -- `bash /app/playwright/run.sh 06-seed-filter-sort-
 *   pagination --full --ux` (runs alone or with any other spec), or as part of the full e2e suite
 *   with the --full flag (`bash /app/playwright/run.sh e2e --full --ux`); omitted from a plain
 *   `e2e --ux` run.
 * Uses: @playwright/test.
 * Env: PW_FULL -- when unset/falsy, this entire spec file's describe block is skipped. PW_SCREENSHOTS
 *   -- read directly by this file's own screenshotThenClose helper to gate optional screenshots.
 * Input: ./_fixtures (test, expect, unique, adminKey, createUser, userAccount, adminAccount, pageAs),
 *   ./_helpers (screenshot, closeOverlay, closeNotification), ./_flows/seed.flow,
 *   ./_flows/filter.flow, ./_flows/settings.flow, ./_flows/timeline.flow.
 * Outputs: Playwright HTML report entries (one per test), PNG screenshots attached to the report
 *   when PW_SCREENSHOTS is set. Creates only its own uniquely named data.
 * Returns: exit code from the Playwright test runner -- 0 when every test in this file passes,
 *   non-zero otherwise.
 * ──────────────────────────────────────────────────────────────────────────── */
const { test, expect } = require('./_fixtures');
const { screenshot, closeOverlay, closeNotification } = require('./_helpers');

async function openSettings(page) {
  await page.getByRole('button', { name: /^(settings|налаштування)$/i }).click();
  await page.locator('.base-overlay.overlay--visible').waitFor({ timeout: 10000 });
}

async function switchToTab(page, tabName, itemSelector) {
  await page.getByRole('tab', { name: tabName }).first().click();
  await page.locator(itemSelector).first().waitFor({ timeout: 8000 });
}

async function screenshotThenClose(page, name) {
  if (process.env.PW_SCREENSHOTS) {
    const buffer = await page.screenshot({ fullPage: false });
    await test.info().attach(name, { body: buffer, contentType: 'image/png' });
  }
  await closeNotification(page);
}
const { issueApiKeyViaApi, postViaApi, seedUsersViaApi, seedTaxonsViaApi, seedAdvertisementsViaApi } = require('./_flows/seed.flow');
const {
  openQueryPanel, clearFilter, applyFilter,
  resetDefaultSorts,
  fillText, fillNumber, fillRole, fillCategory, fillCity, fillAdKind,
  getRow,
  verifyPagination, verifyDateRangeFilters, verifySortColumn, verifySortColumnChanges,
} = require('./_flows/filter.flow');
const { changePageSizes, openHistory, closeHistory, restoreLatestFromActivity, getPageSizes, pageSizeField } = require('./_flows/settings.flow');
const { openTimelineTab, openTimelineFilter, assertActorPickerVisible, assertAllRowsHaveType, assertAllRowsHaveAction, fillEntityType, fillActionType, fillActorPicker, removeActorChip, actorChipCount, TIMELINE_BLOCK } = require('./_flows/timeline.flow');
const { goToNextPage } = require('./_flows/filter.flow');

const ADV_BLOCK  = '.advertisement-query-block';
const USER_BLOCK = '.user-query-block';
const ADV_ITEM   = '.advertisement-card';
const USER_ITEM  = '.user-grid-name';

const SEED_COUNT = 60;
// One row more than the default page size, so every grid page in the page-size test is full.
const PAGE_FILL = 21;
// More picker users than the picker grid's first data-provider page of 50 rows.
const PICKER_FILLER = 55;
const TIMELINE_ADS = 20;
// Registration writes a USER and a USER_SETTINGS creation entry with the new user as actor.
const REGISTRATION_ROWS = 2;
const ACTOR_ROWS = REGISTRATION_ROWS + TIMELINE_ADS + 1;
const AD_KINDS = ['Offer', 'Request', 'Product'];
const AD_KIND_ENUM = ['OFFER', 'REQUEST', 'PRODUCT'];
const CATEGORIES = [
  { en: 'Clothing',  descriptionEn: 'Clothes, fashion and apparel.',    uk: 'Одяг',    descriptionUk: 'Одяг, мода та аксесуари.' },
  { en: 'Books',     descriptionEn: 'Books, magazines and literature.',  uk: 'Книги',   descriptionUk: 'Книги, журнали та література.' },
  { en: 'Furniture', descriptionEn: 'Home and office furniture.',        uk: 'Меблі',   descriptionUk: 'Домашні та офісні меблі.' },
  { en: 'Sports',    descriptionEn: 'Sports equipment and accessories.', uk: 'Спорт',   descriptionUk: 'Спортивне обладнання та аксесуари.' },
  { en: 'Toys',      descriptionEn: 'Toys, games and hobbies.',          uk: 'Іграшки', descriptionUk: 'Іграшки, ігри та хобі.' },
];
const CITIES = [
  { en: 'Kharkiv', descriptionEn: 'Major city in eastern Ukraine.',  uk: 'Харків', descriptionUk: 'Велике місто на сході України.' },
  { en: 'Odesa',   descriptionEn: 'Port city on the Black Sea.',     uk: 'Одеса',  descriptionUk: 'Портове місто на Чорному морі.' },
  { en: 'Dnipro',  descriptionEn: 'Major city on the Dnipro river.', uk: 'Дніпро', descriptionUk: 'Велике місто на річці Дніпро.' },
];
const MAX_NAME_BASE = 'MaxBoundaryUserNameForValidationTest'.repeat(3);

const pad = i => String(i).padStart(2, '0');

// Run-scoped label; the trailing word keeps one label from being a substring of a later one.
function seedLabel(unique) {
  const label = `${unique('Seed')} Set`;
  return { label, slug: label.toLowerCase().replace(/ /g, '.') };
}

async function expectTotal(page, total) {
  await expect(page.locator('.pagination-count:visible')).toHaveText(new RegExp(`of ${total}\\b`), { timeout: 8000 });
}

async function registerUsers(request, users) {
  const ids = [];
  for (const user of users) ids.push((await postViaApi(request, null, '/api/users', user)).id);
  return ids;
}

async function createPlainAds(request, apiKey, titlePrefix, count) {
  const ids = [];
  for (let i = 1; i <= count; i++) {
    const body = { title: `${titlePrefix} ${pad(i)}`, description: `Description for ${titlePrefix} ${i}.`, adKind: 'OFFER' };
    ids.push((await postViaApi(request, apiKey, '/api/advertisements', body)).id);
  }
  return ids;
}

const seedUser = (label, slug, word, i) => ({
  name: `${label} ${word} ${pad(i)}`,
  email: `${slug}.${word.toLowerCase()}${pad(i)}@example.com`,
  password: 'password',
});

test.describe('Seed data and query validation', () => {
  test.skip(!process.env.PW_FULL, 'Skipped by default — run with --full to generate seed data');

  // ── advertisement filters, sort, pagination ───────────────────────────────

  test('administrator filters own 60 seeded advertisements — title, date, category, city and listing type filters, column sort, pagination', async ({ request, adminKey, adminAccount, pageAs, unique }) => {
    test.setTimeout(5 * 60 * 1000);
    const { label } = seedLabel(unique);
    const titlePrefix = `${label} Advertisement`;
    const toTaxon = t => ({ nameEn: `${label} ${t.en}`, descriptionEn: t.descriptionEn, nameUk: `${label} ${t.uk}`, descriptionUk: t.descriptionUk });
    const categoryIds = await seedTaxonsViaApi(request, adminKey, 'CATEGORY', CATEGORIES.map(toTaxon));
    const cityIds = await seedTaxonsViaApi(request, adminKey, 'CITY', CITIES.map(toTaxon));
    const ads = Array.from({ length: SEED_COUNT }, (_, idx) => ({
      title: `${titlePrefix} ${pad(idx + 1)}`,
      description: `Description for seed advertisement ${idx + 1}.`,
      adKind: AD_KIND_ENUM[idx % AD_KIND_ENUM.length],
      categoryId: categoryIds[idx % categoryIds.length],
      cityTaxonId: cityIds[idx % cityIds.length],
    }));
    await seedAdvertisementsViaApi(request, adminKey, ads);

    const page = await pageAs(adminAccount);
    await page.getByRole('tab', { name: /Advertisements|Оголошення/i }).click();
    await page.locator(ADV_ITEM).first().waitFor({ timeout: 8000 });

    await openQueryPanel(page, ADV_BLOCK);
    await screenshot(page, 'adv-filter-panel-open');

    // ── title exact match ─────────────────────────────────────────────────────
    await fillText(page, ADV_BLOCK, 'Title', `${titlePrefix} 01`);
    await applyFilter(page, ADV_BLOCK);
    await expect(page.locator(ADV_ITEM)).toHaveCount(1, { timeout: 8000 });
    await screenshot(page, 'adv-filter-title-exact');
    await clearFilter(page, ADV_BLOCK);

    // ── title partial match → own 60 results ─────────────────────────────────
    await fillText(page, ADV_BLOCK, 'Title', titlePrefix);
    await applyFilter(page, ADV_BLOCK);
    await expectTotal(page, SEED_COUNT);
    await screenshot(page, 'adv-filter-title-partial');
    await clearFilter(page, ADV_BLOCK);

    // ── own category → SEED_COUNT / 5 results ────────────────────────────────
    await fillCategory(page, ADV_BLOCK, `${label} ${CATEGORIES[0].en}`);
    await applyFilter(page, ADV_BLOCK);
    await expectTotal(page, SEED_COUNT / CATEGORIES.length);
    await screenshot(page, 'adv-filter-category');
    await clearFilter(page, ADV_BLOCK);

    // ── own city → SEED_COUNT / 3 results ────────────────────────────────────
    await fillCity(page, ADV_BLOCK, `${label} ${CITIES[0].en}`);
    await applyFilter(page, ADV_BLOCK);
    await expectTotal(page, SEED_COUNT / CITIES.length);
    await screenshot(page, 'adv-filter-city');
    await clearFilter(page, ADV_BLOCK);

    // ── listing type within own titles → SEED_COUNT / 3 results ─────────────
    await fillText(page, ADV_BLOCK, 'Title', titlePrefix);
    await fillAdKind(page, ADV_BLOCK, AD_KINDS[0]);
    await applyFilter(page, ADV_BLOCK);
    await expectTotal(page, SEED_COUNT / AD_KINDS.length);
    await screenshot(page, 'adv-filter-ad-kind');
    await clearFilter(page, ADV_BLOCK);

    // ── date range filters within own titles (today + boundary cases) ────────
    await verifyDateRangeFilters(page, ADV_BLOCK, 'adv', SEED_COUNT, { scopeFilter: { field: 'Title', value: titlePrefix } });

    // ── sorts ─────────────────────────────────────────────────────────────────
    await verifySortColumn(page, {
      block: ADV_BLOCK, sortCol: 'Title', itemSelector: ADV_ITEM,
      assertSelector: `${ADV_ITEM} .advertisement-title`,
      setup: { reset: 'all', filter: { field: 'Title', value: titlePrefix } },
      firstAsc: `${titlePrefix} 01`, firstDesc: `${titlePrefix} ${SEED_COUNT}`, prefix: 'adv',
    });
    // Host clock isn't guaranteed monotonic between near-simultaneous inserts, so these check the sort control works, not which specific ad ends up first.
    await verifySortColumnChanges(page, {
      block: ADV_BLOCK, sortCol: 'Created At', itemSelector: ADV_ITEM,
      assertSelector: `${ADV_ITEM} .advertisement-title`,
      setup: { reset: 'Updated At', filter: { field: 'Title', value: titlePrefix } },
      prefix: 'adv',
    });
    await verifySortColumnChanges(page, {
      block: ADV_BLOCK, sortCol: 'Updated At', itemSelector: ADV_ITEM,
      assertSelector: `${ADV_ITEM} .advertisement-title`,
      setup: { reset: 'Created At', filter: { field: 'Title', value: titlePrefix } },
      prefix: 'adv',
    });

    // ── pagination ────────────────────────────────────────────────────────────
    await clearFilter(page, ADV_BLOCK);
    await resetDefaultSorts(page, ADV_BLOCK);
    await fillText(page, ADV_BLOCK, 'Title', titlePrefix);
    await applyFilter(page, ADV_BLOCK);
    await verifyPagination(page, 'adv', SEED_COUNT);
  });

  // ── user filters, sort, pagination ────────────────────────────────────────

  test('administrator filters own 60 seeded users — email, name, role and date filters, ID range, invalid fractional ID input, column sort, pagination', async ({ request, adminAccount, createUser, pageAs, unique }) => {
    test.setTimeout(5 * 60 * 1000);
    const { label, slug } = seedLabel(unique);
    const emailPrefix = `${slug}.user`;
    // Sequential registration keeps created_at and id in seed order for the sort checks.
    const ids = await registerUsers(request, Array.from({ length: SEED_COUNT }, (_, i) => seedUser(label, slug, 'User', i + 1)));
    await createUser({ role: 'ADMIN', name: `${label} Admin` });

    const page = await pageAs(adminAccount);
    await page.getByRole('tab', { name: /Users|Користувачі/i }).click();
    await page.locator(USER_ITEM).first().waitFor({ timeout: 8000 });

    await openQueryPanel(page, USER_BLOCK);
    await screenshot(page, 'user-filter-panel-open');

    // ── email exact match ─────────────────────────────────────────────────────
    await fillText(page, USER_BLOCK, 'Email', `${emailPrefix}01@example.com`);
    await applyFilter(page, USER_BLOCK);
    await expect(page.locator('.pagination-count:visible')).toContainText('1–1 of 1', { timeout: 8000 });
    await screenshot(page, 'user-filter-email-exact');
    await clearFilter(page, USER_BLOCK);

    // ── email partial match → own 60 results ─────────────────────────────────
    await fillText(page, USER_BLOCK, 'Email', emailPrefix);
    await applyFilter(page, USER_BLOCK);
    await expectTotal(page, SEED_COUNT);
    await screenshot(page, 'user-filter-email-partial');
    await clearFilter(page, USER_BLOCK);

    // ── email nonexistent → 0 results ────────────────────────────────────────
    await fillText(page, USER_BLOCK, 'Email', `${slug}.nobody@example.com`);
    await applyFilter(page, USER_BLOCK);
    await expectTotal(page, 0);
    await screenshot(page, 'user-filter-email-no-results');
    await clearFilter(page, USER_BLOCK);

    // ── name partial match → own 60 users plus own administrator ─────────────
    await fillText(page, USER_BLOCK, 'Name', label);
    await applyFilter(page, USER_BLOCK);
    await expectTotal(page, SEED_COUNT + 1);
    await screenshot(page, 'user-filter-name-partial');
    await clearFilter(page, USER_BLOCK);

    // ── role filter (USER) within own names ──────────────────────────────────
    await fillText(page, USER_BLOCK, 'Name', label);
    await fillRole(page, USER_BLOCK, 'USER');
    await applyFilter(page, USER_BLOCK);
    await expectTotal(page, SEED_COUNT);
    await screenshot(page, 'user-filter-role-user');
    await clearFilter(page, USER_BLOCK);

    // ── role filter (ADMIN) within own names ─────────────────────────────────
    await fillText(page, USER_BLOCK, 'Name', label);
    await fillRole(page, USER_BLOCK, 'ADMIN');
    await applyFilter(page, USER_BLOCK);
    await expectTotal(page, 1);
    await screenshot(page, 'user-filter-role-admin');
    await clearFilter(page, USER_BLOCK);

    // ── ID range of own first ten users ──────────────────────────────────────
    await fillText(page, USER_BLOCK, 'Email', emailPrefix);
    await fillNumber(page, USER_BLOCK, 'ID', ids[0], ids[9]);
    await applyFilter(page, USER_BLOCK);
    await expectTotal(page, 10);
    await screenshot(page, 'user-filter-id-range');
    await clearFilter(page, USER_BLOCK);

    // ── ID filter fractional input — flagged invalid, not silently truncated ──
    const idMinInput = getRow(page, USER_BLOCK, 'ID').locator('.query-number input').first();
    const idMinField = getRow(page, USER_BLOCK, 'ID').locator('.query-number').first();
    await idMinInput.fill('1.5');
    await expect(idMinField).toHaveAttribute('invalid', '', { timeout: 5000 });
    await screenshot(page, 'user-filter-id-fractional-invalid');
    await idMinInput.fill('1');
    await expect(idMinField).not.toHaveAttribute('invalid', '', { timeout: 5000 });
    await clearFilter(page, USER_BLOCK);

    // ── date range filters within own e-mails (today + boundary cases) ───────
    await verifyDateRangeFilters(page, USER_BLOCK, 'user', SEED_COUNT, { scopeFilter: { field: 'Email', value: emailPrefix } });

    // ── sorts ─────────────────────────────────────────────────────────────────
    const ownUsers = { field: 'Email', value: emailPrefix };
    await verifySortColumn(page, {
      block: USER_BLOCK, sortCol: 'Name', itemSelector: USER_ITEM,
      assertSelector: USER_ITEM,
      setup: { reset: 'all', filter: ownUsers },
      firstAsc: `${label} User 01`, firstDesc: `${label} User ${SEED_COUNT}`, prefix: 'user',
    });
    await verifySortColumn(page, {
      block: USER_BLOCK, sortCol: 'Email', itemSelector: USER_ITEM,
      assertSelector: '.user-grid-email',
      setup: { reset: 'clearAll', filter: ownUsers },
      firstAsc: `${emailPrefix}01@example.com`, firstDesc: `${emailPrefix}${SEED_COUNT}@example.com`, prefix: 'user',
    });
    await verifySortColumn(page, {
      block: USER_BLOCK, sortCol: 'ID', itemSelector: USER_ITEM,
      assertSelector: USER_ITEM,
      setup: { reset: 'clearAll', filter: ownUsers },
      firstAsc: `${label} User 01`, firstDesc: `${label} User ${SEED_COUNT}`, prefix: 'user',
    });
    // ADMIN < USER alphabetically: ASC → own administrator first; DESC → own seeded users first.
    await verifySortColumn(page, {
      block: USER_BLOCK, sortCol: 'Role', itemSelector: USER_ITEM,
      assertSelector: '.user-role-badge',
      setup: { reset: 'clearAll', filter: { field: 'Name', value: label } },
      firstAsc: 'ADMIN', firstDesc: 'USER', prefix: 'user',
    });
    // Host clock isn't guaranteed monotonic between near-simultaneous inserts, so this checks the sort control works, not which specific user ends up first.
    await verifySortColumnChanges(page, {
      block: USER_BLOCK, sortCol: 'Created At', itemSelector: USER_ITEM,
      assertSelector: USER_ITEM,
      setup: { reset: 'Updated At', filter: ownUsers },
      prefix: 'user',
    });
    await verifySortColumnChanges(page, {
      block: USER_BLOCK, sortCol: 'Updated At', itemSelector: USER_ITEM,
      assertSelector: USER_ITEM,
      setup: { reset: 'Created At', filter: ownUsers },
      prefix: 'user',
    });

    // ── pagination ────────────────────────────────────────────────────────────
    await clearFilter(page, USER_BLOCK);
    await resetDefaultSorts(page, USER_BLOCK);
    await fillText(page, USER_BLOCK, 'Email', emailPrefix);
    await applyFilter(page, USER_BLOCK);
    await verifyPagination(page, 'user', SEED_COUNT);
  });

  // ── settings page sizes, activity verification, restore ───────────────────

  test('new administrator changes page sizes — activity diff, ads/users grids reflect sizes, providers field persists, restore defaults, no cross-session bleed', async ({ request, adminKey, createUser, userAccount, pageAs, unique }) => {
    test.setTimeout(3 * 60 * 1000);
    const { label, slug } = seedLabel(unique);
    await createPlainAds(request, adminKey, `${label} Page Ad`, PAGE_FILL);
    await seedUsersViaApi(request, Array.from({ length: PAGE_FILL }, (_, i) => seedUser(label, slug, 'Page', i + 1)));
    const owner = await createUser({ role: 'ADMIN' });
    const page = await pageAs(owner);

    // ── verify page size defaults are 20 before any change ───────────────────
    const { adsPageSize: adsDefault, providerProfilesPageSize: providersDefault, usersPageSize: usersDefault } = await getPageSizes(page);
    expect(adsDefault).toBe(20);
    expect(providersDefault).toBe(20);
    expect(usersDefault).toBe(20);

    // ── change all three page sizes, verify change appears in activity ────────
    await changePageSizes(page, 5, 6, 7);
    await screenshotThenClose(page, 'settings-changed');

    // ── cross-session bleed check: the worker user's own session must stay unaffected ──
    const userPage = await pageAs(userAccount);
    await userPage.getByRole('tab', { name: /Advertisements|Оголошення/i }).click();
    await userPage.locator(ADV_ITEM).first().waitFor({ timeout: 8000 });
    await expect(userPage.locator('.pagination-count:visible'))
      .toContainText('1–20 of', { timeout: 5000 });
    await screenshot(userPage, 'settings-bleed-check-user-unaffected');

    // ── history is a nested overlay now, not a tab: breadcrumb chain Home / Settings / Activity ─
    await openHistory(page);
    await expect(page.locator('.entity-activity-breadcrumb-step')).toBeVisible();
    await expect(page.locator('.entity-activity-breadcrumb-parent')).toBeVisible();
    await expect(page.locator('.entity-activity-close-button')).toBeVisible();
    // exactly one separator between each of the 3 segments -- catches a doubled-up "›" regression
    await expect(page.locator('.entity-activity-overlay .overlay__breadcrumb-sep')).toHaveCount(2);
    await expect(page.locator('.entity-activity-overlay .overlay__breadcrumb')).not.toContainText('››');
    await expect(page.locator('.entity-activity-overlay .entity-activity-list .entity-activity-row').first())
      .toBeVisible({ timeout: 5000 });
    const firstActivityRow = page.locator('.entity-activity-overlay .entity-activity-list .entity-activity-row').first();
    await expect(firstActivityRow.locator('.entity-activity-changes-item').filter({ hasText: /Ads per page|Оголошень/i }).first()).toBeVisible();
    await expect(firstActivityRow.locator('.entity-activity-changes-item').filter({ hasText: /Users per page|Користувач/i }).first()).toBeVisible();
    await expect(firstActivityRow.locator('.entity-activity-changes-item').filter({ hasText: /Provider profiles per page|Постачальників/i }).first()).toBeVisible();
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
      .toContainText('1–5 of', { timeout: 5000 });
    await screenshot(page, 'settings-ads-page-size-5');

    await switchToTab(page, 'Users', USER_ITEM);
    await expect(page.locator('.pagination-count:visible'))
      .toContainText('1–7 of', { timeout: 5000 });
    await screenshot(page, 'settings-users-page-size-7');

    // Provider Profiles has too few real rows in this suite's seeded data (well under
    // PageSizeLimits.MIN_PAGE_SIZE) for a page-boundary/truncation check to mean anything here --
    // that real-data-volume proof already lives in ProviderProfilePaginationScenarioTest
    // (Level 3, real Postgres). This asserts the field itself round-trips through save -> reload.
    const { providerProfilesPageSize: providersChanged } = await getPageSizes(page);
    expect(providersChanged).toBe(6);

    // ── restore defaults via activity, verify restore entry recorded ──────────
    await openSettings(page);
    // single-link breadcrumb case (Settings itself) -- same regression risk, one separator only
    await expect(page.locator('.account-overlay .overlay__breadcrumb-sep')).toHaveCount(1);
    await expect(page.locator('.account-overlay .overlay__breadcrumb')).not.toContainText('››');
    await openHistory(page);
    await restoreLatestFromActivity(page);
    await expect(page.locator('.entity-activity-overlay.overlay--visible')).toBeHidden();
    await expect(page.locator('.settings-overlay-content')).toBeVisible();
    await screenshotThenClose(page, 'settings-restored');

    await openHistory(page);
    const activityCount = await page.locator('.entity-activity-overlay .entity-activity-list .entity-activity-row').count();
    expect(activityCount).toBeGreaterThanOrEqual(2);
    await screenshot(page, 'settings-activity-after-restore');
    // closing via the breadcrumb back-link uncovers Settings again (other path than the X above)
    await closeHistory(page);
    await closeOverlay(page);

    // ── verify default page sizes (20) are restored in both views ─────────────
    await switchToTab(page, 'Advertisements', ADV_ITEM);
    await expect(page.locator('.pagination-count:visible'))
      .toContainText('1–20 of', { timeout: 5000 });
    await screenshot(page, 'settings-ads-restored-20');

    await switchToTab(page, 'Users', USER_ITEM);
    await expect(page.locator('.pagination-count:visible'))
      .toContainText('1–20 of', { timeout: 5000 });
    await screenshot(page, 'settings-users-restored-20');

    // ── verify the field itself round-trips through save -> restore -> reload ─
    const { providerProfilesPageSize: providersRestored } = await getPageSizes(page);
    expect(providersRestored).toBe(20);

    // ── verify an unsaved field edit survives a trip into history and back ────
    // History now covers the whole Settings form (a separate overlay, not a tab), so an
    // in-progress edit must still be there, untouched, once history is closed again.
    await openSettings(page);
    const tabSizeInput = pageSizeField(page, 'ads');
    await tabSizeInput.click({ clickCount: 3 });
    await tabSizeInput.fill('15');
    await openHistory(page);
    await closeHistory(page);
    await expect(page.locator('.overlay__form-fields-card').first()).toBeVisible({ timeout: 5000 });
    await expect(tabSizeInput).toHaveValue('15');
    await page.locator('.base-overlay.overlay--visible').getByRole('button', { name: /зберегти|save/i }).click();
    await page.locator('vaadin-notification-card').first().waitFor({ timeout: 8000 });
    await closeNotification(page);
    await screenshot(page, 'settings-history-trip-preserves-unsaved-edit');
  });

  // ── timeline filter and pagination ────────────────────────────────────────

  test('administrator verifies timeline of own actors — ADVERTISEMENT and USER type filters, CREATED and UPDATED action filters, multi-actor filter with chip removal, pagination', async ({ request, adminAccount, createUser, pageAs, unique }) => {
    test.setTimeout(3 * 60 * 1000);
    const { label, slug } = seedLabel(unique);
    const pickerPrefix = `${label} Picker`;
    await seedUsersViaApi(request, Array.from({ length: PICKER_FILLER }, (_, i) => seedUser(label, slug, 'Picker', i + 1)));
    // Sorts after every filler name, so the scroll path reaches the picker grid's second page.
    const actor = await createUser({ name: `${pickerPrefix} zz Actor` });
    // A real 100-character name exercises the actor chip's own truncation.
    const maxActor = await createUser({ name: `${unique('Max')} ${MAX_NAME_BASE}`.substring(0, 100) });

    const actorKey = await issueApiKeyViaApi(request, actor);
    const [firstAdId] = await createPlainAds(request, actorKey, `${label} Timeline Ad`, TIMELINE_ADS);
    const current = await request.get(`/api/advertisements/${firstAdId}`);
    const update = await request.put(`/api/advertisements/${firstAdId}`, {
      headers: { Authorization: `Bearer ${actorKey}`, 'If-Match': current.headers()['etag'] },
      data: { title: `${label} Timeline Ad 01 updated`, description: 'Updated for the timeline check.', adKind: 'OFFER' },
    });
    expect(update.ok()).toBe(true);

    const page = await pageAs(adminAccount);
    await openTimelineTab(page);
    await openTimelineFilter(page);
    await assertActorPickerVisible(page, expect, true);

    // Own actor only — every entry this actor authored
    await fillActorPicker(page, actor.name, { narrowTo: pickerPrefix });
    await applyFilter(page, TIMELINE_BLOCK);
    await expectTotal(page, ACTOR_ROWS);
    expect(await actorChipCount(page)).toBe(1);
    await screenshot(page, 'timeline-filter-actor-own');

    // Own actor + ADVERTISEMENT entity type — all rows must be advertisement
    await clearFilter(page, TIMELINE_BLOCK);
    await fillActorPicker(page, actor.name, { useSearch: true });
    await fillEntityType(page, 'ADVERTISEMENT');
    await applyFilter(page, TIMELINE_BLOCK);
    await expectTotal(page, TIMELINE_ADS + 1);
    await assertAllRowsHaveType(page, expect, 'advertisement', 'timeline-filter-entity-advertisement');

    // Own actor + USER entity type — only the actor's own registration
    await clearFilter(page, TIMELINE_BLOCK);
    await fillActorPicker(page, actor.name, { useSearch: true });
    await fillEntityType(page, 'USER');
    await applyFilter(page, TIMELINE_BLOCK);
    await expectTotal(page, 1);
    await assertAllRowsHaveType(page, expect, 'user', 'timeline-filter-entity-user');

    // Own actor + CREATED action — registration entries and every created ad
    await clearFilter(page, TIMELINE_BLOCK);
    await fillActorPicker(page, actor.name, { useSearch: true });
    await fillActionType(page, 'CREATED');
    await applyFilter(page, TIMELINE_BLOCK);
    await expectTotal(page, REGISTRATION_ROWS + TIMELINE_ADS);
    await assertAllRowsHaveAction(page, expect, 'created', 'timeline-filter-action-created');

    // Own actor + UPDATED action — the one ad update
    await clearFilter(page, TIMELINE_BLOCK);
    await fillActorPicker(page, actor.name, { useSearch: true });
    await fillActionType(page, 'UPDATED');
    await applyFilter(page, TIMELINE_BLOCK);
    await expectTotal(page, 1);
    await assertAllRowsHaveAction(page, expect, 'updated', 'timeline-filter-action-updated');

    // A second actor adds to the selection (not replace) — two chips, rows of either actor
    await clearFilter(page, TIMELINE_BLOCK);
    await fillActorPicker(page, actor.name, { useSearch: true });
    await fillActorPicker(page, maxActor.name, { useSearch: true });
    expect(await actorChipCount(page)).toBe(2);
    await applyFilter(page, TIMELINE_BLOCK);
    await expectTotal(page, ACTOR_ROWS + REGISTRATION_ROWS);
    await screenshot(page, 'timeline-filter-actor-two-selected');

    // Removing one chip shrinks the selection and the total back to the single actor
    await removeActorChip(page, maxActor.name);
    expect(await actorChipCount(page)).toBe(1);
    await applyFilter(page, TIMELINE_BLOCK);
    await expectTotal(page, ACTOR_ROWS);
    await screenshot(page, 'timeline-filter-actor-chip-removed');

    // Pagination within the single actor's own entries
    await expect(page.locator('.pagination-count:visible')).toContainText(`1–20 of ${ACTOR_ROWS}`, { timeout: 8000 });
    await goToNextPage(page);
    await expect(page.locator('.pagination-count:visible')).toContainText(`21–${ACTOR_ROWS} of ${ACTOR_ROWS}`, { timeout: 8000 });
    await screenshot(page, 'timeline-pagination-page2');
  });
});
