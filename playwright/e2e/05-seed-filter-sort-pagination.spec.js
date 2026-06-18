const { test, expect, screenshot } = require('./_test-helpers');
const { TEST_USERS } = require('./_helpers');
const { signUpBulk, loginBulk, logoutBulk, createAdvertisementBulk } = require('./_flows/seed.flow');
const {
  openQueryPanel, clearFilter, applyFilter,
  clickSort, resetDefaultSorts,
  fillText, fillNumber, fillRole, setDateRange,
  getTotalCount,
  goToNextPage, goToPrevPage, goToFirstPage, goToLastPage,
} = require('./_flows/filter.flow');

test.describe.configure({ mode: 'serial' });

const ADV_BLOCK  = '.advertisement-query-block';
const USER_BLOCK = '.user-query-block';
const ADV_ITEM   = '.advertisement-card';
const USER_ITEM  = '.user-grid-name';

const SEED_COUNT = 50;
const CATEGORIES = ['Electronics', 'Clothing', 'Furniture', 'Books', 'Sports'];

const seedUser = i => ({
  name: `Seed User ${String(i).padStart(2, '0')}`,
  email: `seed.user${String(i).padStart(2, '0')}@example.com`,
  password: 'password',
});

const seedAd = i => ({
  title: `Seed Advertisement ${String(i).padStart(2, '0')}`,
  description: `Description for seed advertisement ${i}. Category: ${CATEGORIES[(i - 1) % CATEGORIES.length]}.`,
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
    await page.getByRole('button', { name: /^yes$|^так$/i }).click();
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

  test(`seed ${SEED_COUNT} users via signup`, async () => {
    test.setTimeout(5 * 60 * 1000);
    for (let i = 1; i <= SEED_COUNT; i++) {
      await signUpBulk(page, seedUser(i));
    }
  });

  // ── Test 2: seed advertisements ───────────────────────────────────────────

  test(`seed ${SEED_COUNT} advertisements as adminEn`, async () => {
    test.setTimeout(5 * 60 * 1000);
    await loginBulk(page, TEST_USERS.adminEn);
    for (let i = 1; i <= SEED_COUNT; i++) {
      await createAdvertisementBulk(page, seedAd(i));
    }
    await logoutBulk(page);
  });

  // ── Test 3: advertisement filters, sort, pagination ───────────────────────

  test('advertisement filters, sort, and pagination', async () => {
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

    // ── title partial match → 50 results ─────────────────────────────────────
    await fillText(page, ADV_BLOCK, 'Title', 'Seed');
    await applyFilter(page, ADV_BLOCK);
    await expect(page.locator('.pagination-count:visible')).toContainText(`of ${SEED_COUNT}`, { timeout: 8000 });
    await screenshot(page, 'adv-filter-title-partial');
    await clearFilter(page, ADV_BLOCK);

    // ── created at date range filter (created today) ──────────────────────────
    const today = new Date().toISOString().slice(0, 10);
    await setDateRange(page, ADV_BLOCK, 0, today, today);
    await applyFilter(page, ADV_BLOCK);
    expect(await getTotalCount(page)).toBeGreaterThanOrEqual(SEED_COUNT);
    await screenshot(page, 'adv-filter-created-range');
    await clearFilter(page, ADV_BLOCK);

    // ── updated at date range filter (updated today) ──────────────────────────
    await setDateRange(page, ADV_BLOCK, 2, today, today);
    await applyFilter(page, ADV_BLOCK);
    expect(await getTotalCount(page)).toBeGreaterThanOrEqual(SEED_COUNT);
    await screenshot(page, 'adv-filter-updated-range');
    await clearFilter(page, ADV_BLOCK);

    // ── sort title ASC / DESC ─────────────────────────────────────────────────
    // Default sort is "Updated At DESC, Created At DESC"; clear resets to that default.
    // Reset both to NEUTRAL so Title is the only active sort.
    await resetDefaultSorts(page, ADV_BLOCK);
    await fillText(page, ADV_BLOCK, 'Title', 'Seed');
    await applyFilter(page, ADV_BLOCK);

    await clickSort(page, ADV_BLOCK, 'Title', ADV_ITEM); // NEUTRAL → ASC
    await expect(page.locator(`${ADV_ITEM} .advertisement-title`).first())
      .toContainText('Seed Advertisement 01', { timeout: 8000 });
    await screenshot(page, 'adv-sort-title-asc');

    await clickSort(page, ADV_BLOCK, 'Title', ADV_ITEM); // ASC → DESC
    await expect(page.locator(`${ADV_ITEM} .advertisement-title`).first())
      .toContainText('Seed Advertisement 50', { timeout: 8000 });
    await screenshot(page, 'adv-sort-title-desc');

    // ── sort created at DESC / ASC ────────────────────────────────────────────
    // After clearFilter: defaults restored (Updated At DESC, Created At DESC).
    // Remove Updated At by clicking once → only Created At DESC remains.
    await clearFilter(page, ADV_BLOCK);
    await clickSort(page, ADV_BLOCK, 'Updated At', ADV_ITEM); // DESC → NEUTRAL
    await fillText(page, ADV_BLOCK, 'Title', 'Seed');
    await applyFilter(page, ADV_BLOCK);
    // Sort: Created At DESC → last created = Seed Advertisement 50
    await expect(page.locator(`${ADV_ITEM} .advertisement-title`).first())
      .toContainText('Seed Advertisement 50', { timeout: 8000 });
    await screenshot(page, 'adv-sort-created-desc');

    await clickSort(page, ADV_BLOCK, 'Created At', ADV_ITEM); // DESC → ASC
    await expect(page.locator(`${ADV_ITEM} .advertisement-title`).first())
      .toContainText('Seed Advertisement 01', { timeout: 8000 });
    await screenshot(page, 'adv-sort-created-asc');

    // ── sort updated at DESC / ASC ────────────────────────────────────────────
    // After clearFilter: defaults restored. Remove Created At → only Updated At DESC remains.
    await clearFilter(page, ADV_BLOCK);
    await clickSort(page, ADV_BLOCK, 'Created At', ADV_ITEM); // DESC → NEUTRAL
    await fillText(page, ADV_BLOCK, 'Title', 'Seed');
    await applyFilter(page, ADV_BLOCK);
    // Sort: Updated At DESC → last updated = Seed Advertisement 50
    await expect(page.locator(`${ADV_ITEM} .advertisement-title`).first())
      .toContainText('Seed Advertisement 50', { timeout: 8000 });
    await screenshot(page, 'adv-sort-updated-desc');

    await clickSort(page, ADV_BLOCK, 'Updated At', ADV_ITEM); // DESC → ASC
    await expect(page.locator(`${ADV_ITEM} .advertisement-title`).first())
      .toContainText('Seed Advertisement 01', { timeout: 8000 });
    await screenshot(page, 'adv-sort-updated-asc');

    // ── pagination ────────────────────────────────────────────────────────────
    await clearFilter(page, ADV_BLOCK);
    await resetDefaultSorts(page, ADV_BLOCK);
    await fillText(page, ADV_BLOCK, 'Title', 'Seed');
    await applyFilter(page, ADV_BLOCK);

    await expect(page.locator('.pagination-count:visible'))
      .toContainText(`1\u201320 of ${SEED_COUNT}`, { timeout: 8000 });
    await screenshot(page, 'adv-pagination-page1');

    await goToNextPage(page);
    await expect(page.locator('.pagination-count:visible'))
      .toContainText(`21\u201340 of ${SEED_COUNT}`, { timeout: 5000 });
    await screenshot(page, 'adv-pagination-page2');

    await goToNextPage(page);
    await expect(page.locator('.pagination-count:visible'))
      .toContainText(`41\u201350 of ${SEED_COUNT}`, { timeout: 5000 });
    await screenshot(page, 'adv-pagination-page3');

    await goToFirstPage(page);
    await expect(page.locator('.pagination-count:visible'))
      .toContainText(`1\u201320 of ${SEED_COUNT}`, { timeout: 5000 });

    await goToLastPage(page);
    await expect(page.locator('.pagination-count:visible'))
      .toContainText(`41\u201350 of ${SEED_COUNT}`, { timeout: 5000 });
    await screenshot(page, 'adv-pagination-last');

    await goToPrevPage(page);
    await expect(page.locator('.pagination-count:visible'))
      .toContainText(`21\u201340 of ${SEED_COUNT}`, { timeout: 5000 });
    await screenshot(page, 'adv-pagination-prev');

    await clearFilter(page, ADV_BLOCK);
    await logoutBulk(page);
  });

  // ── Test 4: user filters, sort, pagination ────────────────────────────────

  test('user filters, sort, and pagination', async () => {
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

    // ── email partial match → 50 results ─────────────────────────────────────
    await fillText(page, USER_BLOCK, 'Email', 'seed.user');
    await applyFilter(page, USER_BLOCK);
    await expect(page.locator('.pagination-count:visible')).toContainText(`of ${SEED_COUNT}`, { timeout: 8000 });
    await screenshot(page, 'user-filter-email-partial');
    await clearFilter(page, USER_BLOCK);

    // ── name partial match → 50 results ──────────────────────────────────────
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

    // ── ID range filter ───────────────────────────────────────────────────────
    await fillNumber(page, USER_BLOCK, 'ID', 1, 10);
    await applyFilter(page, USER_BLOCK);
    const idCount = await getTotalCount(page);
    expect(idCount).toBeGreaterThanOrEqual(1);
    expect(idCount).toBeLessThanOrEqual(10);
    await screenshot(page, 'user-filter-id-range');
    await clearFilter(page, USER_BLOCK);

    // ── created at date range filter (users created today) ────────────────────
    const today = new Date().toISOString().slice(0, 10);
    await setDateRange(page, USER_BLOCK, 0, today, today);
    await applyFilter(page, USER_BLOCK);
    expect(await getTotalCount(page)).toBeGreaterThanOrEqual(SEED_COUNT);
    await screenshot(page, 'user-filter-created-range');
    await clearFilter(page, USER_BLOCK);

    // ── updated at date range filter (users updated today) ────────────────────
    await setDateRange(page, USER_BLOCK, 2, today, today);
    await applyFilter(page, USER_BLOCK);
    expect(await getTotalCount(page)).toBeGreaterThanOrEqual(SEED_COUNT);
    await screenshot(page, 'user-filter-updated-range');
    await clearFilter(page, USER_BLOCK);

    // ── sort name ASC / DESC (filtered to seed users for predictable order) ───
    await resetDefaultSorts(page, USER_BLOCK);
    await fillText(page, USER_BLOCK, 'Name', 'Seed');
    await applyFilter(page, USER_BLOCK);

    await clickSort(page, USER_BLOCK, 'Name', USER_ITEM); // NEUTRAL → ASC
    await expect(page.locator(USER_ITEM).first()).toContainText('Seed User 01', { timeout: 8000 });
    await screenshot(page, 'user-sort-name-asc');

    await clickSort(page, USER_BLOCK, 'Name', USER_ITEM); // ASC → DESC
    await expect(page.locator(USER_ITEM).first()).toContainText('Seed User 50', { timeout: 8000 });
    await screenshot(page, 'user-sort-name-desc');

    // ── sort email ASC / DESC ─────────────────────────────────────────────────
    await clearFilter(page, USER_BLOCK);
    await resetDefaultSorts(page, USER_BLOCK);
    await fillText(page, USER_BLOCK, 'Email', 'seed.user');
    await applyFilter(page, USER_BLOCK);

    await clickSort(page, USER_BLOCK, 'Email', USER_ITEM); // NEUTRAL → ASC
    await expect(page.locator('.user-grid-email').first())
      .toContainText('seed.user01@example.com', { timeout: 8000 });
    await screenshot(page, 'user-sort-email-asc');

    await clickSort(page, USER_BLOCK, 'Email', USER_ITEM); // ASC → DESC
    await expect(page.locator('.user-grid-email').first())
      .toContainText('seed.user50@example.com', { timeout: 8000 });
    await screenshot(page, 'user-sort-email-desc');

    // ── sort ID ASC / DESC ────────────────────────────────────────────────────
    await clearFilter(page, USER_BLOCK);
    await resetDefaultSorts(page, USER_BLOCK);
    await fillText(page, USER_BLOCK, 'Name', 'Seed');
    await applyFilter(page, USER_BLOCK);

    await clickSort(page, USER_BLOCK, 'ID', USER_ITEM); // NEUTRAL → ASC
    await expect(page.locator(USER_ITEM).first()).toContainText('Seed User 01', { timeout: 8000 });
    await screenshot(page, 'user-sort-id-asc');

    await clickSort(page, USER_BLOCK, 'ID', USER_ITEM); // ASC → DESC
    await expect(page.locator(USER_ITEM).first()).toContainText('Seed User 50', { timeout: 8000 });
    await screenshot(page, 'user-sort-id-desc');

    // ── sort role ASC / DESC ──────────────────────────────────────────────────
    // ADMIN < USER alphabetically: ASC → adminEn (ADMIN) first; DESC → seed users (USER) first.
    await clearFilter(page, USER_BLOCK);
    await resetDefaultSorts(page, USER_BLOCK);

    await clickSort(page, USER_BLOCK, 'Role', USER_ITEM); // NEUTRAL → ASC
    await expect(page.locator('.user-role-badge').first()).toContainText('ADMIN', { timeout: 8000 });
    await screenshot(page, 'user-sort-role-asc');

    await clickSort(page, USER_BLOCK, 'Role', USER_ITEM); // ASC → DESC
    await expect(page.locator('.user-role-badge').first()).toContainText('USER', { timeout: 8000 });
    await screenshot(page, 'user-sort-role-desc');

    // ── sort created at DESC / ASC ────────────────────────────────────────────
    await clearFilter(page, USER_BLOCK);
    await clickSort(page, USER_BLOCK, 'Updated At', USER_ITEM); // DESC → NEUTRAL
    await fillText(page, USER_BLOCK, 'Name', 'Seed');
    await applyFilter(page, USER_BLOCK);
    // Sort: Created At DESC → last registered seed user appears first
    await expect(page.locator(USER_ITEM).first()).toContainText('Seed User 50', { timeout: 8000 });
    await screenshot(page, 'user-sort-created-desc');

    await clickSort(page, USER_BLOCK, 'Created At', USER_ITEM); // DESC → ASC
    await expect(page.locator(USER_ITEM).first()).toContainText('Seed User 01', { timeout: 8000 });
    await screenshot(page, 'user-sort-created-asc');

    // ── sort updated at DESC / ASC ────────────────────────────────────────────
    await clearFilter(page, USER_BLOCK);
    await clickSort(page, USER_BLOCK, 'Created At', USER_ITEM); // DESC → NEUTRAL
    await fillText(page, USER_BLOCK, 'Name', 'Seed');
    await applyFilter(page, USER_BLOCK);
    // Sort: Updated At DESC → last registered seed user appears first (updatedAt = createdAt for seed users)
    await expect(page.locator(USER_ITEM).first()).toContainText('Seed User 50', { timeout: 8000 });
    await screenshot(page, 'user-sort-updated-desc');

    await clickSort(page, USER_BLOCK, 'Updated At', USER_ITEM); // DESC → ASC
    await expect(page.locator(USER_ITEM).first()).toContainText('Seed User 01', { timeout: 8000 });
    await screenshot(page, 'user-sort-updated-asc');

    // ── pagination ────────────────────────────────────────────────────────────
    await clearFilter(page, USER_BLOCK);
    await resetDefaultSorts(page, USER_BLOCK);
    await fillText(page, USER_BLOCK, 'Email', 'seed.user');
    await applyFilter(page, USER_BLOCK);

    await expect(page.locator('.pagination-count:visible'))
      .toContainText(`1\u201320 of ${SEED_COUNT}`, { timeout: 8000 });
    await screenshot(page, 'user-pagination-page1');

    await goToNextPage(page);
    await expect(page.locator('.pagination-count:visible'))
      .toContainText(`21\u201340 of ${SEED_COUNT}`, { timeout: 5000 });
    await screenshot(page, 'user-pagination-page2');

    await goToNextPage(page);
    await expect(page.locator('.pagination-count:visible'))
      .toContainText(`41\u201350 of ${SEED_COUNT}`, { timeout: 5000 });
    await screenshot(page, 'user-pagination-page3');

    await goToFirstPage(page);
    await expect(page.locator('.pagination-count:visible'))
      .toContainText(`1\u201320 of ${SEED_COUNT}`, { timeout: 5000 });

    await goToLastPage(page);
    await expect(page.locator('.pagination-count:visible'))
      .toContainText(`41\u201350 of ${SEED_COUNT}`, { timeout: 5000 });
    await screenshot(page, 'user-pagination-last');

    await goToPrevPage(page);
    await expect(page.locator('.pagination-count:visible'))
      .toContainText(`21\u201340 of ${SEED_COUNT}`, { timeout: 5000 });
    await screenshot(page, 'user-pagination-prev');

    await clearFilter(page, USER_BLOCK);
    await logoutBulk(page);
  });
});
