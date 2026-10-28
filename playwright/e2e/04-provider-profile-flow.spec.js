/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Provider Profile e2e coverage in independent tests -- the AccountOverlay
 *   self-service/admin tab (create/edit/moderator-readonly/admin-on-behalf, kind/about/categories/
 *   city/phone/telegram/viber fields, per-channel contact-views-this-month counters), the public
 *   Providers catalog (browsing/filtering/sorting, card Edit button, deep link + sitemap.xml +
 *   crawler meta tags, delete from the catalog card), feedback replies, and the moderation queue.
 *   Every test creates its own users, categories, cities and profiles with names carrying the run
 *   id, and looks only at that data. Per test:
 *   - "user creates provider profile": empty state, contact field validation, saved view with kind
 *     badge/about/chips/contact counters, field order, live refresh of the Providers tab.
 *   - "user creates a minimal provider profile": no category and no city, the Providers tab
 *     refreshing on Settings close when it was already selected, then the profile is deleted.
 *   - "user edits provider profile": pre-filled re-edit, second category, activity diff, phone-only
 *     edit, outer breadcrumb, advertisement create contact preview, advertisement contact reveal,
 *     then the same contacts as the owner (view and edit), another user (catalog) and an
 *     administrator (Users grid, view and edit), each in its own session.
 *   - "moderator views a user's account": read-only Name, Settings and Provider Profile tabs; no API keys tab.
 *   - "administrator creates and edits a user's SUPPORT provider profile via the Users grid, then
 *     that user edits it": SUPPORT stays visible but disabled for the non-privileged owner.
 *   - "anonymous visitor browses the public Providers catalog": four own profiles (SHOP, SUPPORT,
 *     MASTER, SHOP), own-category scope, kind/category/city filters, Updated/Created sort cycles,
 *     date-range filters.
 *   - "administrator edits other users' provider profiles from the catalog": card Edit button,
 *     overlay Edit button, the administrator's own review and its edit, no reply trigger for a non-owner.
 *   - "provider owner writes, edits and deletes the single reply to a review".
 *   - "non-privileged non-owner sees no Edit button": someone else's card and overlay, own card.
 *   - "user opens a provider deep link": overlay, field order, contact reveal, anonymous feedback
 *     panel, share button, sitemap.xml, crawler meta tags, card click and browser Back.
 *   - "user deletes their own provider profile from the public catalog".
 *   - "moderator reports and moderates a review and a reply": report popup, merged queue, profile
 *     link, approve restores both.
 *   - "moderator works a 7-item moderation queue": filters, pagination, confirm violation and the
 *     status filter.
 * Usage: run via the Playwright test runner -- `bash /app/playwright/run.sh 04-provider-profile-
 *   flow --ux` (runs alone or with any other spec), or as part of the full e2e suite
 *   (`bash /app/playwright/run.sh e2e --ux`).
 * Uses: @playwright/test (via ./_fixtures and ./_helpers).
 * Env: None.
 * Input: ./_fixtures (test, expect, pageAs, adminAccount, moderatorAccount, adminKey, createUser,
 *   unique), ./_helpers (screenshot, closeNotification, closeOverlay, assertAbsent,
 *   assertVerticalOrder, waitForOverlayClosed, assertRightAligned), ./_flows/audit.flow,
 *   ./_flows/entity-activity.flow, ./_flows/user-management.flow, ./_flows/delete.flow,
 *   ./_flows/advertisement.flow, ./_flows/advertisement-filter.flow, ./_flows/filter.flow,
 *   ./_flows/settings.flow, ./_flows/category.flow, ./_flows/seed.flow.
 * Outputs: Playwright HTML report entries (one per test). Creates only uniquely named users,
 *   categories, cities, provider profiles, an advertisement, reviews, replies and flags.
 * Returns: exit code from the Playwright test runner -- 0 when every test in this file passes,
 *   non-zero otherwise.
 * ──────────────────────────────────────────────────────────────────────────── */
const { test, expect } = require('./_fixtures');
const { screenshot, closeNotification, closeOverlay, assertAbsent, assertVerticalOrder, waitForOverlayClosed, assertRightAligned } = require('./_helpers');
const { runOpenSettingsFlow, runCloseSettingsFlow } = require('./_flows/audit.flow');
const { openEntityActivity, closeEntityActivity } = require('./_flows/entity-activity.flow');
const { runNavigateToUsersTabFlow, runOpenUserEditViaListFlow, runOpenUserViewDialogFlow, closeUserOverlay } = require('./_flows/user-management.flow');
const { confirmDeleteDialog } = require('./_flows/delete.flow');
const { openCardOverlay } = require('./_flows/advertisement.flow');
const { runOpenFilterPanelFlow, runFillTitleFilterFlow, runApplyFilterFlow } = require('./_flows/advertisement-filter.flow');
const { verifyDateRangeFilters, openQueryPanel, applyFilter, clearFilter, fillText, fillRole, goToNextPage, goToPrevPage, goToFirstPage, goToLastPage } = require('./_flows/filter.flow');
const { changePageSizes, getPageSizes } = require('./_flows/settings.flow');
const { selectInMultiSelectComboBox } = require('./_flows/category.flow');
const { issueApiKeyViaApi, postViaApi, createProviderProfileViaApi, seedTaxonsViaApi } = require('./_flows/seed.flow');

const CATALOG_BLOCK = '.provider-profile-query-block';
const MOD_BLOCK = '.moderation-query-block';

const bearer = key => ({ Authorization: `Bearer ${key}` });
const escapeRegExp = text => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
// Matches a unique name without matching a longer name that merely starts with it ("x-5" vs "x-50").
const named = name => new RegExp(`(?<![\\w-])${escapeRegExp(name)}(?![\\w-])`);

// ── REST preconditions ───────────────────────────────────────────────────────

async function createTaxon(request, adminKey, unique, type, label) {
  const name = unique(label);
  const [id] = await seedTaxonsViaApi(request, adminKey, type, [{ nameEn: name, descriptionEn: label, nameUk: `${name} uk`, descriptionUk: label }]);
  return { id, name };
}

const createCategory = (request, adminKey, unique, label) => createTaxon(request, adminKey, unique, 'CATEGORY', label);
const createCity = (request, adminKey, unique, label) => createTaxon(request, adminKey, unique, 'CITY', label);

function profileBody({ kind, about, categories = [], city, phone, telegram, viber }) {
  return { kind, about, categoryIds: categories.map(c => c.id), cityTaxonId: city?.id, phone, telegram, viber };
}

// Registers an owner (createUser options as given) and creates its single profile through the REST API.
async function createOwnerWithProfile(request, createUser, body, userOptions) {
  const owner = await createUser(userOptions);
  const key = await issueApiKeyViaApi(request, owner);
  const profile = await createProviderProfileViaApi(request, key, body);
  return { owner, key, body, profileId: profile.id };
}

async function reviewViaApi(request, reviewer, profileId, rating, text) {
  const key = await issueApiKeyViaApi(request, reviewer);
  const feedback = await postViaApi(request, key, `/api/provider-profiles/${profileId}/feedback`, { rating, text });
  return { key, feedbackId: feedback.id };
}

async function replyViaApi(request, ownerKey, feedbackId, text) {
  return postViaApi(request, ownerKey, `/api/feedback/${feedbackId}/reply`, { text });
}

async function flagViaApi(request, key, path, reason) {
  const response = await request.post(path, { headers: bearer(key), data: { reason } });
  if (!response.ok()) throw new Error(`POST ${path} failed: ${response.status()} ${await response.text()}`);
}

// ── UI helpers ───────────────────────────────────────────────────────────────

// Routes the Telegram link host to an empty page, so the new tab opens at once and the test only checks its address.
async function stubTelegramLink(page) {
  await page.context().route('https://t.me/**', route =>
    route.fulfill({ status: 200, contentType: 'text/html', body: '<!doctype html><html><body></body></html>' })
  );
}

async function openProviderProfileTab(page) {
  await page.locator('.account-overlay').getByRole('tab', { name: /provider profile|профіль провайдера/i }).click();
}

async function fillAbout(page, text, scope = '.account-overlay') {
  await page.locator(`${scope} .overlay__description-rich-editor .ql-editor`).fill(text);
}

async function selectCategory(page, name) {
  const box = page.locator('.account-overlay vaadin-multi-select-combo-box');
  await selectInMultiSelectComboBox(page, box, name);
}

async function selectedCategoryNames(page) {
  return page.locator('.account-overlay vaadin-multi-select-combo-box')
    .evaluate(el => (el.selectedItems || []).map(i => i.label));
}

const cityCombo = page => page.locator('.account-overlay').getByRole('combobox', { name: /city|місто/i });

const emptyProfileText = page => page.locator('.account-overlay').getByText(/haven't created a provider profile yet|ще не створили профіль/i);

async function selectCity(page, name) {
  const box = cityCombo(page);
  await box.click();
  await box.fill(name);
  await page.getByRole('option', { name: named(name) }).first().click();
}

async function openProvidersTab(page) {
  await page.getByRole('tab', { name: 'Providers' }).click();
}

async function openCatalogQuery(page) {
  if (await page.locator(CATALOG_BLOCK).isVisible()) return;
  await page.locator('.providers-content-wrapper .query-status-bar').click();
  await expect(page.locator(CATALOG_BLOCK)).toBeVisible({ timeout: 5000 });
}

// Types the text into a catalog multi-select filter and picks the one matching item.
async function pickCatalogMultiSelect(page, testId, text) {
  const combo = page.locator(CATALOG_BLOCK).getByTestId(testId);
  await combo.getByRole('combobox').click();
  await page.keyboard.type(text);
  const item = page.getByRole('option', { name: named(text) }).first();
  await item.waitFor({ state: 'visible', timeout: 5000 });
  await item.click();
  await page.keyboard.press('Escape');
}

async function pickCatalogCity(page, name) {
  const input = page.locator(CATALOG_BLOCK).getByTestId('provider-profile-filter-city').getByRole('combobox');
  await input.click();
  await input.fill(name);
  await page.getByRole('option', { name: named(name) }).first().click();
}

// Clears the catalog filter, applies the given categories/kind/city and waits for the result.
async function applyCatalogFilter(page, { categories, kind, city }) {
  await openCatalogQuery(page);
  await clearFilter(page, CATALOG_BLOCK);
  for (const category of categories) {
    await pickCatalogMultiSelect(page, 'provider-profile-filter-categories', category.name);
  }
  if (kind) await pickCatalogMultiSelect(page, 'provider-profile-filter-kind', kind);
  if (city) await pickCatalogCity(page, city.name);
  await applyFilter(page, CATALOG_BLOCK);
}

const catalogCard = (page, owner) => page.locator('.provider-profile-card')
  .filter({ has: page.locator('.provider-profile-card-title', { hasText: named(owner.name) }) });

async function reportDialogSubmit(page, reasonText) {
  await page.locator('vaadin-dialog-overlay').waitFor({ timeout: 5000 });
  const reasonTextarea = page.locator('vaadin-dialog[opened]').getByTestId('report-reason-field-text').locator('textarea');
  await reasonTextarea.fill(reasonText);
  // A native JS .click() (below) doesn't blur the textarea the way a real mouse click would --
  // blur explicitly first so the typed value syncs to the server before the handler reads it.
  await reasonTextarea.press('Tab');
  await page.evaluate(() => {
    const dialog = document.querySelector('vaadin-dialog[opened]');
    [...dialog.querySelectorAll('vaadin-button')].find(b => /^Report$|^Поскаржитися$/u.test(b.textContent?.trim()))?.click();
  });
  await page.locator('vaadin-dialog-overlay').waitFor({ state: 'hidden', timeout: 5000 });
}

// Opens the Moderation tab's query panel if needed and shows only the queue items whose reason starts with the given text.
async function scopeModerationQueue(page, reasonText, fillExtra) {
  await page.locator('.moderation-content-wrapper .query-status-bar').waitFor({ state: 'visible', timeout: 10000 });
  if (!(await page.locator(MOD_BLOCK).isVisible())) await openQueryPanel(page, MOD_BLOCK);
  await clearFilter(page, MOD_BLOCK);
  await fillText(page, MOD_BLOCK, 'Reason', reasonText);
  if (fillExtra) await fillExtra();
  await applyFilter(page, MOD_BLOCK);
}

const deepLink = profileId => `/providers/${profileId}-profile`;

test.describe('Provider Profile flow', () => {

  test('user creates provider profile — empty state before, Create button, kind/about/category/city filled, headed Contacts card, contact fields validation and save, view mode shows kind badge/about/category chip/city chip/contact views counters after save, field order, Providers tab live refresh', async ({ pageAs, createUser, adminKey, request, unique }) => {
    test.setTimeout(180_000);
    const user = await createUser();
    const category = await createCategory(request, adminKey, unique, 'Electronics');
    const city = await createCity(request, adminKey, unique, 'Lviv');
    const page = await pageAs(user);

    await runOpenSettingsFlow(page);
    await openProviderProfileTab(page);

    await expect(emptyProfileText(page)).toBeVisible({ timeout: 5000 });
    await screenshot(page, 'provider-profile-empty-state');

    await page.locator('.account-overlay').getByRole('button', { name: 'Create Profile' }).click();

    await page.locator('.account-overlay').getByRole('radio', { name: 'MASTER' }).first().click();
    await fillAbout(page, 'Professional electronics repair and installation services.');
    await selectCategory(page, category.name);
    await selectCity(page, city.name);

    const phoneField = page.getByTestId('provider-profile-phone-field').locator('input');
    const telegramField = page.getByTestId('provider-profile-telegram-field').locator('input');
    const viberField = page.getByTestId('provider-profile-viber-field').locator('input');

    await test.step('contact fields — own headed Contacts card below the main card, invalid phone/telegram/viber format rejected on save, valid E.164 phone/viber and Telegram username accepted', async () => {
      const formCards = page.locator('.account-overlay .overlay__form-fields-card');
      await expect(formCards).toHaveCount(2);
      const contactsCard = formCards.filter({ has: page.getByTestId('provider-profile-phone-field') });
      await expect(contactsCard.locator('.overlay__form-card-header')).toHaveText(/contacts/i);
      await expect(contactsCard.getByTestId('provider-profile-telegram-field')).toBeVisible();
      await expect(contactsCard.getByTestId('provider-profile-viber-field')).toBeVisible();

      await phoneField.fill('not-a-phone');
      await telegramField.fill('a');
      await viberField.fill('123');
      await page.locator('.account-overlay').getByRole('button', { name: 'Save', exact: true }).click();
      await expect(page.locator('vaadin-notification-container')).toContainText('Validation failed', { timeout: 5000 });
      await closeNotification(page);
      await screenshot(page, 'provider-profile-contact-validation-error');

      await phoneField.fill('+380501234567');
      await telegramField.fill('electro_master');
      await viberField.fill('+380509876543');
    });
    await screenshot(page, 'provider-profile-create-filled');

    await page.locator('.account-overlay').getByRole('button', { name: 'Save', exact: true }).click();
    await expect(page.locator('vaadin-notification-container')).toContainText('Provider profile saved', { timeout: 5000 });
    await closeNotification(page);

    // Save now switches straight to View -- no Cancel click needed (Tabs never re-fires a click on an already-selected tab).
    await expect(page.locator('.account-overlay .provider-profile-kind-badge')).toContainText('MASTER', { timeout: 5000 });
    await expect(page.locator('.account-overlay .provider-profile-category-chip')).toContainText(category.name);
    await expect(page.locator('.account-overlay .provider-profile-city-chip')).toContainText(city.name);
    await screenshot(page, 'provider-profile-view-after-create');

    await test.step('contact views counters — Phone/Telegram/Viber show the saved value and a 0 click count (no reveals happened yet) and the caption naming the profile page', async () => {
      const counters = page.locator('.account-overlay .provider-profile-contact-views');
      await expect(counters).toBeVisible({ timeout: 5000 });
      await expect(counters.locator('.provider-profile-contact-views-label')).toContainText('profile page');
      await expect(counters.locator('.provider-profile-contact-views-phone')).toContainText('+380501234567 (0)');
      await expect(counters.locator('.provider-profile-contact-views-telegram')).toContainText('electro_master (0)');
      await expect(counters.locator('.provider-profile-contact-views-viber')).toContainText('+380509876543 (0)');
      await screenshot(page, 'provider-profile-contact-views-counters');
    });

    await test.step('account-tab view — every field renders in the expected top-to-bottom order', async () => {
      await assertVerticalOrder(page, expect, [
        '.overlay__view-card-header',
        '.overlay__view-description',
        '.provider-profile-categories-chips',
        '.provider-profile-city-chips',
        '.provider-profile-kind-badge',
        '.entity-meta',
      ].map(s => page.locator('.account-overlay .overlay__view-card').locator(s)), 'provider-profile-view-field-order');
    });

    await runCloseSettingsFlow(page);

    await test.step('Providers tab reflects the just-created profile without a page reload', async () => {
      await openProvidersTab(page);
      await expect(catalogCard(page, user).filter({ hasText: 'MASTER' })).toBeVisible({ timeout: 5000 });
      await screenshot(page, 'providers-tab-live-refresh-after-create');
    });
  });

  test('user creates a minimal provider profile — no category, no city: those chip rows are absent, field order stays header -> about -> kind badge -> meta on the account view, Providers tab already selected refreshes on Settings close, then deleted', async ({ pageAs, createUser }) => {
    const user = await createUser();
    const page = await pageAs(user);

    // Providers tab selected BEFORE Settings opens -- Settings is a modal overlay, not a tab, so
    // closing it never fires tabs' own selection-change event; the tab must still refresh anyway.
    await openProvidersTab(page);
    await expect(catalogCard(page, user)).toHaveCount(0, { timeout: 5000 });

    await runOpenSettingsFlow(page);
    await openProviderProfileTab(page);

    await page.locator('.account-overlay').getByRole('button', { name: 'Create Profile' }).click();
    await page.locator('.account-overlay').getByRole('radio', { name: 'MASTER' }).first().click();
    await fillAbout(page, 'Minimal profile: kind and about only, no category, no city.');
    await page.locator('.account-overlay').getByRole('button', { name: 'Save', exact: true }).click();
    await expect(page.locator('vaadin-notification-container')).toContainText('Provider profile saved', { timeout: 5000 });
    await closeNotification(page);

    await test.step('Providers tab (already selected, never switched to) refreshes on Settings close', async () => {
      await runCloseSettingsFlow(page);
      await expect(catalogCard(page, user)).toBeVisible({ timeout: 5000 });
      await screenshot(page, 'providers-tab-refresh-after-settings-close-no-tab-switch');
    });

    await runOpenSettingsFlow(page);
    await openProviderProfileTab(page);

    await test.step('account-tab view — chip rows absent, reduced field order intact', async () => {
      const viewCard = page.locator('.account-overlay .overlay__view-card');
      await expect(viewCard.locator('.provider-profile-kind-badge')).toContainText('MASTER', { timeout: 5000 });
      await assertAbsent(expect, viewCard.locator('.provider-profile-categories-chips'));
      await assertAbsent(expect, viewCard.locator('.provider-profile-city-chips'));
      await assertVerticalOrder(page, expect, [
        '.overlay__view-card-header',
        '.overlay__view-description',
        '.provider-profile-kind-badge',
        '.entity-meta',
      ].map(s => viewCard.locator(s)), 'provider-minimal-view-order');
    });

    await test.step('clean up — delete the minimal profile', async () => {
      await page.locator('.account-overlay').getByRole('button', { name: 'Delete', exact: true }).click();
      await confirmDeleteDialog(page);
      await expect(emptyProfileText(page)).toBeVisible({ timeout: 5000 });
    });
  });

  test('user edits provider profile — previously-saved kind/about/categories/city pre-filled on re-edit, second category added, activity diff, phone-only edit records its own activity entry, history button, outer breadcrumb closes to list, advertisement create contact preview, advertisement contact reveal through the owner profile fallback, contacts seen by the owner, another user and an administrator in view and edit', async ({ pageAs, adminAccount, createUser, adminKey, request, unique }) => {
    test.setTimeout(180_000);
    const electronics = await createCategory(request, adminKey, unique, 'Electronics');
    const vehicles = await createCategory(request, adminKey, unique, 'Vehicles');
    const city = await createCity(request, adminKey, unique, 'Lviv');
    const { owner, key } = await createOwnerWithProfile(request, createUser, profileBody({
      kind: 'MASTER',
      about: 'Professional electronics repair and installation services.',
      categories: [electronics],
      city,
      phone: '+380501234567',
      telegram: 'electro_master',
      viber: '+380509876543',
    }));
    const adTitle = unique('Contact Reveal Fixture Ad');
    await postViaApi(request, key, '/api/advertisements', {
      title: adTitle,
      description: 'Fixture ad for the advertisement-side contact reveal test.',
      adKind: 'OFFER',
      categoryIds: [electronics.id],
      cityTaxonId: city.id,
    });
    const page = await pageAs(owner);

    await runOpenSettingsFlow(page);
    await openProviderProfileTab(page);

    // Re-fetched from the server on this fresh login -- proves the saved data actually persisted.
    await expect(page.locator('.account-overlay .provider-profile-category-chip')).toContainText(electronics.name, { timeout: 5000 });

    await page.locator('.account-overlay').getByRole('button', { name: 'Edit', exact: true }).click();

    // The regression check: the combo box must show the previously-saved category, not empty.
    await expect(async () => {
      const names = await selectedCategoryNames(page);
      expect(names).toContain(electronics.name);
    }).toPass({ timeout: 5000 });
    await expect(page.locator('.account-overlay .overlay__description-rich-editor .ql-editor')).toContainText('Professional electronics', { timeout: 5000 });
    await expect(cityCombo(page)).toHaveValue(city.name, { timeout: 5000 });
    await screenshot(page, 'provider-profile-edit-prefilled');

    await page.locator('.account-overlay').getByRole('radio', { name: 'SHOP' }).first().click();
    await selectCategory(page, vehicles.name);

    await page.locator('.account-overlay').getByRole('button', { name: 'Save', exact: true }).click();
    await expect(page.locator('vaadin-notification-container')).toContainText('Provider profile saved', { timeout: 5000 });
    await closeNotification(page);

    // Save now switches straight to View -- assert the rendered chips/badge instead of raw form fields.
    await expect(page.locator('.account-overlay .provider-profile-kind-badge')).toContainText('SHOP', { timeout: 5000 });
    await expect(page.locator('.account-overlay .provider-profile-category-chip')).toContainText([electronics.name, vehicles.name]);
    await screenshot(page, 'provider-profile-edit-after-save');

    const activityList = await openEntityActivity(page, '.provider-profile-history-button');
    await expect(activityList.locator('.entity-activity-row')).toHaveCount(2, { timeout: 5000 });
    await screenshot(page, 'provider-profile-activity-diff');

    await test.step('phone-only edit — records its own activity entry showing just the Phone field changed', async () => {
      await closeEntityActivity(page, 'parent');
      await page.locator('.account-overlay').getByRole('button', { name: 'Edit', exact: true }).click();
      const phoneField = page.getByTestId('provider-profile-phone-field').locator('input');
      await phoneField.fill('+380507654321');
      await phoneField.blur(); // TextField syncs on blur, not per keystroke -- no other field to blur into here
      await page.locator('.account-overlay').getByRole('button', { name: 'Save', exact: true }).click();
      await expect(page.locator('vaadin-notification-container')).toContainText('Provider profile saved', { timeout: 5000 });
      await closeNotification(page);

      const phoneActivityList = await openEntityActivity(page, '.provider-profile-history-button');
      await expect(phoneActivityList.locator('.entity-activity-row')).toHaveCount(3, { timeout: 5000 });
      // The row shows every field's current value; only actually-changed fields get the "old → new" arrow.
      const latestChanges = phoneActivityList.locator('.entity-activity-row').nth(0).locator('.entity-activity-changes');
      await expect(latestChanges).toContainText('Phone: +380501234567 → +380507654321');
      await expect(latestChanges).not.toContainText(/Category:[^•]*→/);
      await screenshot(page, 'provider-profile-activity-diff-phone-only');
    });

    await closeEntityActivity(page, 'outer');
    await expect(page.locator('.base-overlay.overlay--visible')).toHaveCount(0, { timeout: 5000 });
    await screenshot(page, 'provider-profile-outer-breadcrumb-closed');

    await test.step('advertisement create form — read-only contact preview pulled from own provider profile', async () => {
      await page.getByRole('tab', { name: 'Advertisements' }).first().click();
      await page.getByRole('button', { name: /add advertisement|додати оголошення/i }).click();
      const overlay = page.locator('.advertisement-overlay');
      await overlay.waitFor({ timeout: 5000 });

      const preview = overlay.locator('.advertisement-contact-preview');
      await expect(preview).toBeVisible({ timeout: 5000 });
      const contactsCard = overlay.locator('.overlay__form-fields-card').filter({ has: page.locator('.advertisement-contact-preview') });
      await expect(contactsCard.locator('.overlay__form-card-header')).toHaveText(/contacts/i);
      await expect(preview.locator('.advertisement-contact-preview-phone')).toContainText('+380507654321');
      await expect(preview.locator('.advertisement-contact-preview-telegram')).toContainText('electro_master');
      await expect(preview.locator('.advertisement-contact-preview-viber')).toContainText('+380509876543');
      await expect(preview.locator('.advertisement-contact-preview-phone')).toHaveAttribute('title', /profile/i);
      await screenshot(page, 'advertisement-create-contact-preview');

      await overlay.locator('vaadin-button').filter({ has: page.locator('vaadin-icon[icon="vaadin:close"]') }).first().click();
      await waitForOverlayClosed(page);
    });

    await test.step('advertisement view — ContactRevealPanel click-through resolves the owner\'s provider profile contact via fallback', async () => {
      await runOpenFilterPanelFlow(page, expect);
      await runFillTitleFilterFlow(page, adTitle);
      await runApplyFilterFlow(page, expect);

      const card = page.locator('.advertisement-card').filter({ has: page.locator('.advertisement-title', { hasText: named(adTitle) }) }).first();
      const overlay = await openCardOverlay(page, card, 'contact-reveal-fixture');

      const panel = overlay.locator('.contact-reveal-panel');
      await expect(panel.locator('.overlay__view-card-header')).toHaveText(/contacts/i);
      await expect(panel.locator('.contact-reveal-phone')).toBeVisible({ timeout: 5000 });
      await expect(panel.locator('.contact-reveal-telegram')).toBeVisible();
      await expect(panel.locator('.contact-reveal-viber')).toBeVisible();
      await screenshot(page, 'advertisement-contact-reveal-panel');

      await panel.locator('.contact-reveal-phone vaadin-button').click();
      await expect(panel.locator('.contact-reveal-phone-value')).toHaveText('+380507654321', { timeout: 5000 });
      await screenshot(page, 'advertisement-contact-reveal-phone-revealed');

      await stubTelegramLink(page);
      const [telegramTab] = await Promise.all([
        page.context().waitForEvent('page'),
        panel.locator('.contact-reveal-telegram vaadin-button').click(),
      ]);
      await telegramTab.waitForURL('**/t.me/electro_master', { waitUntil: 'commit', timeout: 10000 });
      expect(telegramTab.url()).toContain('t.me/electro_master');
      await telegramTab.close();
    });

    const contactsHeader = /contacts|контакти/i;

    await test.step('owner reopens own account — Provider Profile view shows a Contacts header above the saved values with view counters, Edit shows the same values in a headed Contacts card', async () => {
      await closeOverlay(page);
      await runOpenSettingsFlow(page);
      await openProviderProfileTab(page);

      const counters = page.locator('.account-overlay .provider-profile-contact-views');
      await expect(counters.locator('.overlay__view-card-header')).toHaveText(contactsHeader, { timeout: 5000 });
      await expect(counters.locator('.provider-profile-contact-views-phone')).toContainText(/\+380507654321 \(\d+\)/);
      await expect(counters.locator('.provider-profile-contact-views-telegram')).toContainText(/electro_master \(\d+\)/);
      await expect(counters.locator('.provider-profile-contact-views-viber')).toContainText(/\+380509876543 \(\d+\)/);
      await screenshot(page, 'provider-profile-contacts-owner-view');

      await page.locator('.account-overlay').getByRole('button', { name: 'Edit', exact: true }).click();
      const contactsCard = page.locator('.account-overlay .overlay__form-fields-card')
        .filter({ has: page.getByTestId('provider-profile-phone-field') });
      await expect(contactsCard.locator('.overlay__form-card-header')).toHaveText(contactsHeader, { timeout: 5000 });
      await expect(page.getByTestId('provider-profile-phone-field').locator('input')).toHaveValue('+380507654321');
      await expect(page.getByTestId('provider-profile-telegram-field').locator('input')).toHaveValue('electro_master');
      await expect(page.getByTestId('provider-profile-viber-field').locator('input')).toHaveValue('+380509876543');
      await screenshot(page, 'provider-profile-contacts-owner-edit');

      await page.locator('.account-overlay').getByRole('button', { name: 'Cancel', exact: true }).click();
      await runCloseSettingsFlow(page);
    });

    await test.step('another signed-in user — catalog card overlay shows a Contacts header with reveal buttons only, no stored values and no Edit button', async () => {
      const visitor = await createUser();
      const visitorPage = await pageAs(visitor);
      await openProvidersTab(visitorPage);
      await applyCatalogFilter(visitorPage, { categories: [electronics] });

      await catalogCard(visitorPage, owner).click();
      const overlay = visitorPage.locator('.provider-profile-catalog-overlay');
      await overlay.waitFor({ timeout: 5000 });
      const panel = overlay.locator('.contact-reveal-panel');
      await expect(panel.locator('.overlay__view-card-header')).toHaveText(contactsHeader, { timeout: 5000 });
      await expect(panel.locator('.contact-reveal-phone vaadin-button')).toBeVisible();
      await expect(panel.locator('.contact-reveal-telegram')).toBeVisible();
      await expect(panel.locator('.contact-reveal-viber')).toBeVisible();
      await expect(overlay).not.toContainText('+380507654321');
      await assertAbsent(expect, overlay.locator('.overlay__view-edit'));
      await screenshot(visitorPage, 'provider-profile-contacts-visitor-view');

      await panel.locator('.contact-reveal-phone vaadin-button').click();
      await expect(panel.locator('.contact-reveal-phone-value')).toHaveText('+380507654321', { timeout: 5000 });
      await screenshot(visitorPage, 'provider-profile-contacts-visitor-phone-revealed');
      await closeOverlay(visitorPage);
    });

    await test.step('administrator opens the owner account from the Users grid — Provider Profile view shows the Contacts header with values and counters, Edit shows the same values', async () => {
      const adminPage = await pageAs(adminAccount);
      await runNavigateToUsersTabFlow(adminPage, expect);
      await runOpenUserViewDialogFlow(adminPage, owner.email);
      await openProviderProfileTab(adminPage);

      const counters = adminPage.locator('.account-overlay .provider-profile-contact-views');
      await expect(counters.locator('.overlay__view-card-header')).toHaveText(contactsHeader, { timeout: 5000 });
      await expect(counters.locator('.provider-profile-contact-views-phone')).toContainText(/\+380507654321 \(\d+\)/);
      await screenshot(adminPage, 'provider-profile-contacts-admin-view');

      await adminPage.locator('.account-overlay').getByRole('button', { name: 'Edit', exact: true }).click();
      await expect(adminPage.getByTestId('provider-profile-phone-field').locator('input')).toHaveValue('+380507654321', { timeout: 5000 });
      await expect(adminPage.getByTestId('provider-profile-telegram-field').locator('input')).toHaveValue('electro_master');
      await screenshot(adminPage, 'provider-profile-contacts-admin-edit');

      await adminPage.locator('.account-overlay').getByRole('button', { name: 'Cancel', exact: true }).click();
      await closeUserOverlay(adminPage);
    });
  });

  test('moderator views a user\'s account — read-only on Name, Settings and Provider Profile tabs, both saved categories visible, no Edit/Create/Save button anywhere, no API keys tab on another user\'s account', async ({ pageAs, moderatorAccount, createUser, adminKey, request, unique }) => {
    const electronics = await createCategory(request, adminKey, unique, 'Electronics');
    const vehicles = await createCategory(request, adminKey, unique, 'Vehicles');
    const city = await createCity(request, adminKey, unique, 'Lviv');
    const { owner } = await createOwnerWithProfile(request, createUser, profileBody({
      kind: 'SHOP',
      about: 'Professional electronics repair and installation services.',
      categories: [electronics, vehicles],
      city,
    }));
    const page = await pageAs(moderatorAccount);

    await runNavigateToUsersTabFlow(page, expect);
    await runOpenUserViewDialogFlow(page, owner.email);

    // Name tab (default) — no Edit button.
    await expect(page.locator('.account-overlay').getByRole('button', { name: 'Edit' })).toHaveCount(0, { timeout: 5000 });
    await screenshot(page, 'provider-profile-moderator-name-readonly');

    // Settings tab — no Save/Discard, page-size field read-only.
    await page.locator('.account-overlay').getByRole('tab', { name: 'Settings' }).click();
    await expect(page.locator('.account-overlay').getByRole('button', { name: 'Save' })).toHaveCount(0, { timeout: 5000 });
    await expect(page.locator('.account-overlay').getByRole('button', { name: 'Discard changes' })).toHaveCount(0, { timeout: 5000 });
    await expect(page.locator('.account-overlay vaadin-integer-field').first()).toHaveJSProperty('readonly', true, { timeout: 5000 });
    await screenshot(page, 'provider-profile-moderator-settings-readonly');
    await expect(page.getByRole('tab', { name: 'API keys' })).toHaveCount(0);

    // Provider Profile tab — data visible (both categories), no Edit button.
    await openProviderProfileTab(page);
    const moderatorChips = page.locator('.account-overlay .provider-profile-category-chip');
    await expect(moderatorChips).toHaveCount(2, { timeout: 5000 });
    await expect(moderatorChips).toContainText([electronics.name, vehicles.name]);
    await expect(page.locator('.account-overlay').getByRole('button', { name: 'Edit' })).toHaveCount(0, { timeout: 5000 });
    await screenshot(page, 'provider-profile-moderator-providerprofile-readonly');

    await closeUserOverlay(page);
  });

  test('administrator creates and edits a user\'s SUPPORT provider profile via the Users grid, then that user edits it — not self-service, same create/edit round trip through the grid entry path, SUPPORT stays visible but disabled for the non-privileged owner, MASTER/SHOP remain enabled', async ({ pageAs, adminAccount, createUser, adminKey, request, unique }) => {
    test.setTimeout(180_000);
    const target = await createUser();
    const vehicles = await createCategory(request, adminKey, unique, 'Vehicles');
    const city = await createCity(request, adminKey, unique, 'Kyiv');
    const adminPage = await pageAs(adminAccount);

    await runNavigateToUsersTabFlow(adminPage, expect);
    await runOpenUserEditViaListFlow(adminPage, target.email);
    await openProviderProfileTab(adminPage);

    await expect(emptyProfileText(adminPage)).toBeVisible({ timeout: 5000 });

    await adminPage.locator('.account-overlay').getByRole('button', { name: 'Create Profile' }).click();
    // SUPPORT is only offered to a privileged actor -- exercises that branch, untouched elsewhere in this file.
    await adminPage.locator('.account-overlay').getByRole('radio', { name: 'SUPPORT' }).first().click();
    await fillAbout(adminPage, 'Admin-managed support profile for the user.');
    await selectCategory(adminPage, vehicles.name);
    await selectCity(adminPage, city.name);

    await adminPage.locator('.account-overlay').getByRole('button', { name: 'Save', exact: true }).click();
    await expect(adminPage.locator('vaadin-notification-container')).toContainText('Provider profile saved', { timeout: 5000 });
    await closeNotification(adminPage);

    // Save now switches straight to View -- no Cancel click needed.
    await expect(adminPage.locator('.account-overlay .provider-profile-kind-badge')).toContainText('SUPPORT', { timeout: 5000 });
    await expect(adminPage.locator('.account-overlay .provider-profile-city-chip')).toContainText(city.name);
    await screenshot(adminPage, 'provider-profile-admin-via-grid-view');

    // Re-edit through this same grid-entry path -- the pre-fill regression fix applies here too.
    await adminPage.locator('.account-overlay').getByRole('button', { name: 'Edit', exact: true }).click();
    await expect(async () => {
      const names = await selectedCategoryNames(adminPage);
      expect(names).toContain(vehicles.name);
    }).toPass({ timeout: 5000 });
    await expect(cityCombo(adminPage)).toHaveValue(city.name, { timeout: 5000 });

    // Cancel from Provider Profile Edit routes back to View first (same overlay stays open,
    // same as afterDiscard()'s design) -- closeUserOverlay only fully exits from View.
    await adminPage.locator('.account-overlay').getByRole('button', { name: 'Cancel', exact: true }).click();
    await closeUserOverlay(adminPage);

    await test.step('non-privileged owner edits the SUPPORT profile — SUPPORT stays visible but disabled, MASTER/SHOP remain enabled', async () => {
      const page = await pageAs(target);
      await runOpenSettingsFlow(page);
      await openProviderProfileTab(page);

      // Edit the profile to confirm SUPPORT stays selectable-as-is (disabled, not removed) so the
      // Binder can still represent the actor's real current value.
      await page.locator('.account-overlay').getByRole('button', { name: 'Edit', exact: true }).click();

      await expect(page.locator('.account-overlay vaadin-radio-button').filter({ hasText: 'MASTER' })).toHaveCount(1, { timeout: 5000 });
      await expect(page.locator('.account-overlay vaadin-radio-button').filter({ hasText: 'SHOP' })).toHaveCount(1, { timeout: 5000 });
      await expect(page.locator('.account-overlay vaadin-radio-button').filter({ hasText: 'MASTER' })).not.toHaveJSProperty('disabled', true);
      await expect(page.locator('.account-overlay vaadin-radio-button').filter({ hasText: 'SHOP' })).not.toHaveJSProperty('disabled', true);
      const supportRadio = page.locator('.account-overlay vaadin-radio-button').filter({ hasText: 'SUPPORT' });
      await expect(supportRadio).toHaveCount(1, { timeout: 5000 });
      await expect(supportRadio).toHaveJSProperty('disabled', true);
      await screenshot(page, 'provider-catalog-support-disabled-not-offered');

      await page.locator('.account-overlay').getByRole('button', { name: 'Cancel', exact: true }).click();
    });
  });

  test('anonymous visitor browses the public Providers catalog — four own profiles (SHOP, SUPPORT, MASTER, SHOP) scoped by own categories, filters by kind/category/city, sorts by updated and created date, created/updated date-range filters', async ({ page, adminKey, createUser, request, unique }) => {
    test.setTimeout(180_000);
    const vehicles = await createCategory(request, adminKey, unique, 'Vehicles');
    const electronics = await createCategory(request, adminKey, unique, 'Electronics');
    const lviv = await createCity(request, adminKey, unique, 'Lviv');
    const kyiv = await createCity(request, adminKey, unique, 'Kyiv');
    const marker = unique('Catalog');

    // Created in this order: shop one, support, master, shop two; then master is re-edited so its updated_at is the latest.
    const shopOne = await createOwnerWithProfile(request, createUser, profileBody({
      kind: 'SHOP', about: 'Retail shop for electronics and vehicle accessories.', categories: [electronics, vehicles], city: lviv,
    }), { name: `${marker} shop one` });
    const support = await createOwnerWithProfile(request, createUser, profileBody({
      kind: 'SUPPORT', about: 'Support profile for vehicles.', categories: [vehicles], city: kyiv,
    }), { name: `${marker} support`, role: 'MODERATOR' });
    const master = await createOwnerWithProfile(request, createUser, profileBody({
      kind: 'MASTER', about: 'Master craftsman offering on-site vehicle repair.', categories: [vehicles], city: lviv,
    }), { name: `${marker} master` });
    const shopTwo = await createOwnerWithProfile(request, createUser, profileBody({
      kind: 'SHOP', about: 'Retail shop for electronics and accessories.', categories: [electronics], city: kyiv,
    }), { name: `${marker} shop two` });

    const current = await request.get(`/api/provider-profiles/${master.profileId}`);
    const reEdit = await request.put(`/api/provider-profiles/${master.profileId}`, {
      headers: { ...bearer(master.key), 'If-Match': current.headers()['etag'] },
      data: { ...master.body, about: 'Master craftsman offering on-site vehicle repair, now also on weekends.' },
    });
    expect(reEdit.status()).toBe(200);

    await page.goto('/');
    await openProvidersTab(page);
    await openCatalogQuery(page);
    const bothCategories = [vehicles, electronics];
    await applyCatalogFilter(page, { categories: bothCategories });

    const container = page.locator('.provider-profile-container');
    const cards = container.locator('.provider-profile-card');
    await expect(cards).toHaveCount(4, { timeout: 10000 });
    await expect(container.locator('.provider-profile-kind-badge--shop')).toHaveCount(2);
    await expect(container.locator('.provider-profile-kind-badge--support')).toHaveCount(1);
    await expect(container.locator('.provider-profile-kind-badge--master')).toHaveCount(1);
    await expect(container.locator('.provider-profile-share').first()).toBeVisible();
    await screenshot(page, 'provider-catalog-list');

    await test.step('catalog card — every field renders in the expected top-to-bottom order', async () => {
      const cardContent = cards.first().locator('.provider-profile-card-content');
      await assertVerticalOrder(page, expect, [
        cardContent.locator('.provider-profile-card-title'),
        cardContent.locator('.provider-profile-card-about-wrapper'),
        cardContent.getByRole('list', { name: 'Categories:' }),
        cardContent.getByRole('list', { name: 'City:' }),
        cardContent.locator('.provider-profile-kind-badge'),
        cardContent.locator('.entity-meta'),
      ], 'provider-catalog-card-field-order');
    });

    await test.step('kind filter — multi-match Shop (two shops), single-match Support and Master', async () => {
      await applyCatalogFilter(page, { categories: bothCategories, kind: 'Shop' });
      await expect(cards).toHaveCount(2, { timeout: 10000 });
      await expect(container.locator('.provider-profile-kind-badge--shop')).toHaveCount(2);
      await screenshot(page, 'provider-catalog-filter-kind-shop');

      await applyCatalogFilter(page, { categories: bothCategories, kind: 'Support' });
      await expect(cards).toHaveCount(1, { timeout: 10000 });
      await expect(container.locator('.provider-profile-kind-badge--support')).toBeVisible();
      await screenshot(page, 'provider-catalog-filter-kind-support');

      await applyCatalogFilter(page, { categories: bothCategories, kind: 'Master' });
      await expect(cards).toHaveCount(1, { timeout: 10000 });
      await expect(container.locator('.provider-profile-kind-badge--master')).toBeVisible();
      await screenshot(page, 'provider-catalog-filter-kind-master');
    });

    await test.step('category filter — Vehicles matches shop one (holds both categories) + support + master, Electronics matches shop one + shop two', async () => {
      await applyCatalogFilter(page, { categories: [vehicles] });
      await expect(cards).toHaveCount(3, { timeout: 10000 });
      await expect(container.locator('.provider-profile-kind-badge--shop')).toHaveCount(1);
      await expect(container.locator('.provider-profile-kind-badge--support')).toBeVisible();
      await expect(container.locator('.provider-profile-kind-badge--master')).toBeVisible();
      await screenshot(page, 'provider-catalog-filter-category-vehicles');

      await applyCatalogFilter(page, { categories: [electronics] });
      await expect(cards).toHaveCount(2, { timeout: 10000 });
      await expect(container.locator('.provider-profile-kind-badge--shop')).toHaveCount(2);
      await screenshot(page, 'provider-catalog-filter-category-electronics');
    });

    await test.step('city filter — Kyiv matches support + shop two', async () => {
      await applyCatalogFilter(page, { categories: bothCategories, city: kyiv });
      await expect(cards).toHaveCount(2, { timeout: 10000 });
      await expect(container.locator('.provider-profile-kind-badge--support')).toBeVisible();
      await expect(container.locator('.provider-profile-kind-badge--shop')).toBeVisible();
      await screenshot(page, 'provider-catalog-filter-city-kyiv');
    });

    await applyCatalogFilter(page, { categories: bothCategories });
    await expect(cards).toHaveCount(4, { timeout: 10000 });

    const cardOrder = () => cards.evaluateAll(els => els.map(el => el.getAttribute('data-provider-id')));
    const shopOneId = String(shopOne.profileId);
    const supportId = String(support.profileId);
    const masterId = String(master.profileId);
    const shopTwoId = String(shopTwo.profileId);

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

    await test.step('sort by updated date — Updated row\'s own sort icon cycles DESC -> NEUTRAL -> ASC -> DESC, real order of the own four cards asserted at each state', async () => {
      // Default sort is updatedAt DESC, createdAt DESC -- master (re-edited last, so its
      // updatedAt is the latest of all four) is deterministically first without any click needed.
      await expect(updatedIcon).toHaveAttribute('aria-label', 'Descending');
      expect(await cardOrder()).toEqual([masterId, shopTwoId, supportId, shopOneId]);
      await screenshot(page, 'provider-catalog-sort-updated-desc');

      // Neutralize createdAt (the silent tie-breaker) first -- updatedAt is appended after
      // createdAt in the underlying multi-field sort, so createdAt would otherwise stay the
      // dominant sort key and mask updatedAt's own direction changes below.
      await createdIcon.click();
      await expect(createdIcon).toHaveAttribute('aria-label', 'No sorting');
      await applyFilter(page, CATALOG_BLOCK);

      // DESC -> NEUTRAL: no sort criterion left at all -- no ORDER BY clause is issued, so
      // Postgres returns rows in whatever physical order it finds them. That order is not a
      // documented contract (an UPDATE can move a row's physical position), so only the count and
      // icon state are asserted here, not a specific card order.
      await updatedIcon.click();
      await expect(updatedIcon).toHaveAttribute('aria-label', 'No sorting');
      await applyFilter(page, CATALOG_BLOCK);
      await expect(cards).toHaveCount(4, { timeout: 10000 });

      // NEUTRAL -> ASC: order becomes fully determined by real updated_at timestamps (a real
      // ORDER BY, not a coincidental physical-storage order) -- the real proof this row's own sort
      // drives the result.
      await updatedIcon.click();
      await expect(updatedIcon).toHaveAttribute('aria-label', 'Ascending');
      await applyFilter(page, CATALOG_BLOCK);
      await expect(cards).toHaveCount(4, { timeout: 10000 });
      expect(await cardOrder()).toEqual([shopOneId, supportId, shopTwoId, masterId]);
      await screenshot(page, 'provider-catalog-sort-updated-asc');

      // ASC -> DESC, back to the default state.
      await updatedIcon.click();
      await expect(updatedIcon).toHaveAttribute('aria-label', 'Descending');
      await applyFilter(page, CATALOG_BLOCK);
      await expect(cards).toHaveCount(4, { timeout: 10000 });
      expect(await cardOrder()).toEqual([masterId, shopTwoId, supportId, shopOneId]);
    });

    await test.step('sort by created date — Created row\'s own sort icon cycles independently of the Updated row, real order of the own four cards asserted at each state', async () => {
      // Neutralize Updated first (currently DESC, the default) so Created's own direction is
      // what actually drives the visible order below, not masked by Updated staying dominant.
      await updatedIcon.click();
      await expect(updatedIcon).toHaveAttribute('aria-label', 'No sorting');
      await applyFilter(page, CATALOG_BLOCK);

      // Created row is currently NEUTRAL (cleared in the previous step). Clicking it does not
      // disturb Updated's own icon state, proving the two rows are independently tracked.
      await expect(createdIcon).toHaveAttribute('aria-label', 'No sorting');
      await createdIcon.click();
      await expect(createdIcon).toHaveAttribute('aria-label', 'Ascending');
      await expect(updatedIcon).toHaveAttribute('aria-label', 'No sorting');
      await applyFilter(page, CATALOG_BLOCK);
      await expect(cards).toHaveCount(4, { timeout: 10000 });
      // Creation order: shop one, support, master, shop two -- createdAt ASC reproduces it exactly,
      // unaffected by master's later re-edit since createdAt never changes after creation.
      expect(await cardOrder()).toEqual([shopOneId, supportId, masterId, shopTwoId]);
      await screenshot(page, 'provider-catalog-sort-created-asc');

      await createdIcon.click();
      await expect(createdIcon).toHaveAttribute('aria-label', 'Descending');
      await applyFilter(page, CATALOG_BLOCK);
      await expect(cards).toHaveCount(4, { timeout: 10000 });
      expect(await cardOrder()).toEqual([shopTwoId, masterId, supportId, shopOneId]);
      await screenshot(page, 'provider-catalog-sort-created-desc');

      await createdIcon.click();
      await expect(createdIcon).toHaveAttribute('aria-label', 'No sorting');
      await applyFilter(page, CATALOG_BLOCK);
    });

    await test.step('created/updated date-range filters — reuses the shared verifyDateRangeFilters helper (today holds at least the own four)', async () => {
      await verifyDateRangeFilters(page, CATALOG_BLOCK, 'provider', 4);
    });
  });

  test('administrator edits other users\' provider profiles from the catalog — card Edit button opens this same overlay in Edit mode (no unrelated Name/Settings tabs), overlay Edit button does the same, the administrator leaves a review and edits it in place, a non-owner sees no reply trigger', async ({ pageAs, adminAccount, createUser, adminKey, request, unique }) => {
    test.setTimeout(180_000);
    const category = await createCategory(request, adminKey, unique, 'Vehicles');
    const city = await createCity(request, adminKey, unique, 'Kyiv');
    const support = await createOwnerWithProfile(request, createUser, profileBody({
      kind: 'SUPPORT', about: 'Support profile for vehicles.', categories: [category], city,
    }), { role: 'MODERATOR' });
    const master = await createOwnerWithProfile(request, createUser, profileBody({
      kind: 'MASTER', about: 'Master craftsman offering on-site vehicle repair.', categories: [category], city,
    }));
    const page = await pageAs(adminAccount);
    await openProvidersTab(page);
    await applyCatalogFilter(page, { categories: [category] });

    await test.step('card Edit button — administrator edits the SUPPORT profile straight from the card, opens directly in Edit mode', async () => {
      const card = page.locator('.provider-profile-card--support').filter({ has: page.locator('.provider-profile-card-title', { hasText: named(support.owner.name) }) });
      await card.waitFor({ timeout: 5000 });
      await card.getByRole('button', { name: 'Edit', exact: true }).click();

      const overlay = page.locator('.provider-profile-catalog-overlay');
      await overlay.waitFor({ timeout: 5000 });
      // Single-purpose overlay -- no Name/Settings tabs, unlike the old AccountOverlay-based flow.
      await assertAbsent(expect, overlay.locator('.account-overlay-tabs'));
      await fillAbout(page, 'Edited by adminEn from the public catalog card.', '.provider-profile-catalog-overlay');
      await overlay.getByRole('button', { name: 'Save', exact: true }).click();
      await expect(page.locator('vaadin-notification-container')).toContainText('Provider profile saved', { timeout: 5000 });
      await closeNotification(page);

      // Save switches this same overlay straight back to View, same as AdvertisementOverlay.
      await expect(overlay.locator('.overlay__view-description')).toContainText('Edited by adminEn from the public catalog card.', { timeout: 5000 });
      await screenshot(page, 'providers-catalog-card-edit');
      await closeOverlay(page);

      await expect(card.locator('.provider-profile-card-about')).toContainText('Edited by adminEn from the public catalog card.', { timeout: 5000 });
    });

    await test.step('overlay Edit button — administrator edits the MASTER profile from the opened card overlay, no separate overlay to jump to', async () => {
      const card = page.locator('.provider-profile-card--master').filter({ has: page.locator('.provider-profile-card-title', { hasText: named(master.owner.name) }) });
      await card.waitFor({ timeout: 5000 });
      await card.click();
      const overlay = page.locator('.provider-profile-catalog-overlay');
      await overlay.waitFor({ timeout: 5000 });

      await test.step('feedback panel — logged-in administrator leaves a review on the profile then edits it in place, list entry and header aggregate update immediately', async () => {
        const feedbackPanel = overlay.locator('.feedback-panel');
        await expect(feedbackPanel.locator('.feedback-empty')).toBeVisible({ timeout: 5000 });

        await feedbackPanel.getByRole('button', { name: 'Add feedback' }).click();
        await feedbackPanel.locator('.star-rating-field [data-rating="4"]').click();
        await feedbackPanel.getByTestId('feedback-form-field-text').locator('textarea').fill('Great master, highly recommend!');
        await expect(feedbackPanel.getByTestId('feedback-form-field-text').locator('textarea')).toHaveAttribute('maxlength', '2000');
        await feedbackPanel.getByRole('button', { name: 'Leave feedback' }).click();
        await page.locator('vaadin-notification-card').filter({ hasText: /thanks for your feedback/i }).first().waitFor({ timeout: 5000 });
        await closeNotification(page);

        const entry = feedbackPanel.locator('.feedback-entry').first();
        await expect(entry.locator('.feedback-entry-rating')).toHaveText('★★★★☆', { timeout: 5000 });
        await expect(entry.locator('.feedback-entry-text')).toContainText('Great master, highly recommend!');
        await expect(feedbackPanel.locator('.feedback-header-count')).toContainText('(1 feedback entries)');
        await expect(feedbackPanel.locator('.feedback-header-avg')).toContainText('4.0');
        await screenshot(page, 'provider-catalog-feedback-panel-submitted');

        const editButton = entry.locator('.feedback-entry-edit-icon');
        await expect(editButton).toBeVisible({ timeout: 5000 });
        await editButton.click();

        const inlineTextArea = entry.getByTestId('feedback-entry-edit-field-text').locator('textarea');
        await expect(inlineTextArea).toHaveValue('Great master, highly recommend!', { timeout: 5000 });

        await entry.locator('.feedback-entry-edit-rating [data-rating="5"]').click();
        await inlineTextArea.fill('Updated: even better than I thought!');
        await entry.locator('.feedback-entry-save-icon').click();
        await page.locator('vaadin-notification-card').filter({ hasText: /thanks for your feedback/i }).first().waitFor({ timeout: 5000 });
        await closeNotification(page);

        await expect(entry.locator('.feedback-entry-rating')).toHaveText('★★★★★', { timeout: 5000 });
        await expect(entry.locator('.feedback-entry-text')).toContainText('Updated: even better than I thought!');
        await expect(feedbackPanel.locator('.feedback-header-count')).toContainText('(1 feedback entries)');
        await expect(feedbackPanel.locator('.feedback-header-avg')).toContainText('5.0');
        await screenshot(page, 'provider-catalog-feedback-panel-edited');
      });

      await test.step('reply panel — non-owner (administrator) sees no reply trigger on the profile owner\'s feedback entry', async () => {
        const feedbackPanel = overlay.locator('.feedback-panel');
        await assertRightAligned(expect, feedbackPanel.locator('.entity-actions-column').first(), feedbackPanel.locator('.feedback-entry-row').first(), 20);

        const replyPanel = feedbackPanel.locator('.feedback-reply-panel').first();
        await assertAbsent(expect, replyPanel.locator('.feedback-reply-trigger'));
        await assertAbsent(expect, replyPanel.locator('.feedback-reply-composer'));
        await screenshot(page, 'provider-catalog-reply-panel-non-owner-no-trigger');
      });

      await overlay.locator('.overlay__view-edit').click();
      await assertAbsent(expect, overlay.locator('.account-overlay-tabs'));
      await fillAbout(page, 'Edited by adminEn from the catalog overlay.', '.provider-profile-catalog-overlay');
      await overlay.getByRole('button', { name: 'Save', exact: true }).click();
      await expect(page.locator('vaadin-notification-container')).toContainText('Provider profile saved', { timeout: 5000 });
      await closeNotification(page);

      await expect(overlay.locator('.overlay__view-description')).toContainText('Edited by adminEn from the catalog overlay.', { timeout: 5000 });
      await screenshot(page, 'providers-catalog-overlay-edit');
      await closeOverlay(page);

      await expect(card.locator('.provider-profile-card-about')).toContainText('Edited by adminEn from the catalog overlay.', { timeout: 5000 });
    });
  });

  test('provider owner writes, edits and deletes the single reply to a review — a second attempt shows no trigger, the composer is gone once a reply exists, deleting it brings the trigger back', async ({ pageAs, createUser, adminKey, request, unique }) => {
    const category = await createCategory(request, adminKey, unique, 'Vehicles');
    const city = await createCity(request, adminKey, unique, 'Lviv');
    const master = await createOwnerWithProfile(request, createUser, profileBody({
      kind: 'MASTER', about: 'Master craftsman offering on-site vehicle repair.', categories: [category], city,
    }));
    const reviewer = await createUser();
    await reviewViaApi(request, reviewer, master.profileId, 5, 'Updated: even better than I thought!');
    const page = await pageAs(master.owner);

    await page.goto(deepLink(master.profileId));
    const ownOverlay = page.locator('.provider-profile-catalog-overlay');
    await ownOverlay.waitFor({ timeout: 10000 });

    const feedbackPanel = ownOverlay.locator('.feedback-panel');
    const replyPanel = feedbackPanel.locator('.feedback-reply-panel').first();

    await replyPanel.getByRole('button', { name: 'Reply', exact: true }).click();
    const replyField = replyPanel.getByTestId('feedback-reply-field-text').locator('textarea');
    await replyField.fill('Thanks for the kind words!');
    await expect(replyField).toHaveAttribute('maxlength', '2000');
    await replyPanel.locator('.feedback-reply-save-icon').click();
    await expect(replyPanel.locator('.feedback-reply-text')).toContainText('Thanks for the kind words!', { timeout: 5000 });
    await screenshot(page, 'provider-catalog-reply-panel-owner-replied');

    // Once a reply exists, the composer/trigger are both gone -- the only way to leave another one.
    await assertAbsent(expect, replyPanel.locator('.feedback-reply-trigger'));
    await assertAbsent(expect, replyPanel.locator('.feedback-reply-composer'));

    await replyPanel.locator('.feedback-reply-edit-icon').click();
    const editField = replyPanel.getByTestId('feedback-reply-field-text').locator('textarea');
    await expect(editField).toHaveValue('Thanks for the kind words!', { timeout: 5000 });
    await screenshot(page, 'provider-catalog-reply-panel-owner-editing');
    await editField.fill('Thanks for the kind words, edited!');
    await replyPanel.locator('.feedback-reply-save-icon').click();
    await expect(replyPanel.locator('.feedback-reply-text')).toContainText('Thanks for the kind words, edited!', { timeout: 5000 });
    await screenshot(page, 'provider-catalog-reply-panel-owner-edited');

    await replyPanel.locator('.feedback-reply-delete-icon').click();
    await confirmDeleteDialog(page);
    await expect(replyPanel.locator('.feedback-reply-node')).toHaveCount(0, { timeout: 5000 });
    await expect(replyPanel.locator('.feedback-reply-trigger')).toBeVisible({ timeout: 5000 });
    await screenshot(page, 'provider-catalog-reply-panel-owner-deleted');
  });

  test('non-privileged non-owner sees no Edit button — none on someone else\'s card or overlay, own card still has it', async ({ pageAs, createUser, adminKey, request, unique }) => {
    const category = await createCategory(request, adminKey, unique, 'Vehicles');
    const city = await createCity(request, adminKey, unique, 'Kyiv');
    const support = await createOwnerWithProfile(request, createUser, profileBody({
      kind: 'SUPPORT', about: 'Support profile for vehicles.', categories: [category], city,
    }), { role: 'MODERATOR' });
    const viewer = await createOwnerWithProfile(request, createUser, profileBody({
      kind: 'SHOP', about: 'Retail shop for electronics and accessories.', categories: [category], city,
    }));
    const page = await pageAs(viewer.owner);
    await openProvidersTab(page);
    await applyCatalogFilter(page, { categories: [category] });

    const othersCard = page.locator('.provider-profile-card--support').filter({ has: page.locator('.provider-profile-card-title', { hasText: named(support.owner.name) }) });
    await othersCard.waitFor({ timeout: 5000 });
    await assertAbsent(expect, othersCard.getByRole('button', { name: 'Edit', exact: true }));

    await othersCard.click();
    const catalogOverlay = page.locator('.provider-profile-catalog-overlay');
    await catalogOverlay.waitFor({ timeout: 5000 });
    await assertAbsent(expect, catalogOverlay.locator('.overlay__view-edit'));
    await closeOverlay(page);

    const ownCard = page.locator('.provider-profile-card--shop').filter({ has: page.locator('.provider-profile-card-title', { hasText: named(viewer.owner.name) }) });
    await expect(ownCard.getByRole('button', { name: 'Edit', exact: true })).toBeVisible({ timeout: 5000 });
    await screenshot(page, 'providers-catalog-non-owner-no-edit');
  });

  test('user opens a provider deep link — direct navigation to /providers/:id opens the catalog overlay, contact reveal panel (phone reveal, Telegram deep link), anonymous visitor sees the empty feedback panel without a form, share button copies link, sitemap.xml lists it, crawler-facing meta tags, card click updates the URL and browser Back closes the overlay', async ({ page: anonymousPage, pageAs, createUser, adminKey, request, unique }) => {
    test.setTimeout(180_000);
    const category = await createCategory(request, adminKey, unique, 'Electronics');
    const city = await createCity(request, adminKey, unique, 'Lviv');
    const shop = await createOwnerWithProfile(request, createUser, profileBody({
      kind: 'SHOP',
      about: 'Retail shop for electronics and accessories.',
      categories: [category],
      city,
      phone: '+380507654321',
      telegram: 'electro_master',
      viber: '+380509876543',
    }));
    const providerId = String(shop.profileId);
    const viewer = await createUser();
    // Signed in, because the contact reveal limiter is keyed per viewer when signed in and shared across anonymous visitors.
    const page = await pageAs(viewer);

    await page.goto(deepLink(providerId));
    const overlay = page.locator('.provider-profile-catalog-overlay');
    await overlay.waitFor({ timeout: 10000 });
    await expect(overlay.locator('.provider-profile-kind-badge')).toContainText('Shop');
    await screenshot(page, 'provider-catalog-deep-link-opened');

    await test.step('catalog overlay — every field renders in the expected top-to-bottom order', async () => {
      await assertVerticalOrder(page, expect, [
        '.overlay__view-card-header',
        '.overlay__view-description',
        '.provider-profile-categories-chips',
        '.provider-profile-city-chips',
        '.provider-profile-kind-badge',
        '.contact-reveal-panel',
        '.feedback-panel',
        '.entity-meta',
      ].map(s => overlay.locator('.overlay__view-card').locator(s)), 'provider-catalog-overlay-field-order');
    });

    await test.step('contact reveal panel — phone reveals in place, Telegram opens its deep link, each records a view', async () => {
      const panel = overlay.locator('.contact-reveal-panel');
      await expect(panel.locator('.overlay__view-card-header')).toHaveText(/contacts/i);
      await expect(panel.locator('.contact-reveal-phone')).toBeVisible({ timeout: 5000 });
      await expect(panel.locator('.contact-reveal-telegram')).toBeVisible();
      await expect(panel.locator('.contact-reveal-viber')).toBeVisible();
      await screenshot(page, 'provider-catalog-contact-reveal-panel');

      await panel.locator('.contact-reveal-phone vaadin-button').click();
      await expect(panel.locator('.contact-reveal-phone-value')).toHaveText('+380507654321', { timeout: 5000 });
      await screenshot(page, 'provider-catalog-contact-reveal-phone-revealed');

      await stubTelegramLink(page);
      const [telegramTab] = await Promise.all([
        page.context().waitForEvent('page'),
        panel.locator('.contact-reveal-telegram vaadin-button').click(),
      ]);
      await telegramTab.waitForURL('**/t.me/electro_master', { waitUntil: 'commit', timeout: 10000 });
      expect(telegramTab.url()).toContain('t.me/electro_master');
      await telegramTab.close();
    });

    await test.step('feedback panel — anonymous visitor sees the empty list but no leave-a-review form', async () => {
      await anonymousPage.goto(deepLink(providerId));
      const anonymousOverlay = anonymousPage.locator('.provider-profile-catalog-overlay');
      await anonymousOverlay.waitFor({ timeout: 10000 });
      const feedbackPanel = anonymousOverlay.locator('.feedback-panel');
      await expect(feedbackPanel.locator('.feedback-empty')).toBeVisible({ timeout: 5000 });
      await expect(feedbackPanel.locator('.feedback-header-count')).toHaveCount(0);
      await assertAbsent(expect, feedbackPanel.locator('.feedback-form-submit'));
      await screenshot(anonymousPage, 'provider-catalog-feedback-panel-empty');
    });

    await test.step('share button — copies link to clipboard, shows confirmation notification', async () => {
      await page.evaluate(() => {
        navigator.clipboard.writeText = () => Promise.resolve();
      });
      await overlay.getByRole('button', { name: 'Share', exact: true }).click();
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
      await openProvidersTab(page);
      await applyCatalogFilter(page, { categories: [category] });
      await catalogCard(page, shop.owner).click();
      await overlay.waitFor({ timeout: 10000 });
      await expect(page).toHaveURL(new RegExp(`/providers/${providerId}$`));
      await page.goBack();
      await overlay.waitFor({ state: 'hidden', timeout: 10000 });
      await expect(page).toHaveURL(/\/$/);
    });
  });

  test('user deletes their own provider profile from the public catalog — confirm dialog, only the own card removed from the scoped catalog, empty AccountOverlay state', async ({ pageAs, createUser, adminKey, request, unique }) => {
    const category = await createCategory(request, adminKey, unique, 'Electronics');
    const city = await createCity(request, adminKey, unique, 'Kyiv');
    const own = await createOwnerWithProfile(request, createUser, profileBody({
      kind: 'SHOP', about: 'Retail shop for electronics and accessories.', categories: [category], city,
    }));
    await createOwnerWithProfile(request, createUser, profileBody({
      kind: 'SHOP', about: 'Another retail shop for electronics.', categories: [category], city,
    }));
    const page = await pageAs(own.owner);
    await openProvidersTab(page);
    await applyCatalogFilter(page, { categories: [category] });

    await expect(page.locator('.provider-profile-card')).toHaveCount(2, { timeout: 10000 });
    const card = page.locator('.provider-profile-card--shop').filter({ has: page.locator('.provider-profile-card-title', { hasText: named(own.owner.name) }) });
    await card.waitFor({ timeout: 5000 });
    await card.hover();
    await card.getByRole('button', { name: 'Delete', exact: true }).click();
    await confirmDeleteDialog(page);

    await expect(page.locator('vaadin-notification-container')).toContainText('deleted', { timeout: 5000 });
    await closeNotification(page);
    await expect(card).toHaveCount(0, { timeout: 5000 });
    // The other shop's card remains -- only the own card is gone, so the scoped catalog drops from
    // 2 to 1 and exactly one SHOP card survives.
    await applyCatalogFilter(page, { categories: [category] });
    await expect(page.locator('.provider-profile-card')).toHaveCount(1, { timeout: 5000 });
    await expect(page.locator('.provider-profile-card--shop')).toHaveCount(1, { timeout: 5000 });
    await screenshot(page, 'provider-catalog-deleted');

    await runOpenSettingsFlow(page);
    await openProviderProfileTab(page);
    await expect(emptyProfileText(page)).toBeVisible({ timeout: 5000 });
    await runCloseSettingsFlow(page);
  });

  test('moderator reports and moderates a review and a reply on a provider\'s profile — report-with-reason popup, merged moderation queue lists both with a working profile link, approve restores both', async ({ pageAs, moderatorAccount, createUser, adminKey, request, unique }) => {
    test.setTimeout(180_000);
    const category = await createCategory(request, adminKey, unique, 'Vehicles');
    const city = await createCity(request, adminKey, unique, 'Kyiv');
    const master = await createOwnerWithProfile(request, createUser, profileBody({
      kind: 'MASTER', about: 'Master craftsman offering on-site vehicle repair.', categories: [category], city,
    }));
    const reviewer = await createUser();
    const { feedbackId } = await reviewViaApi(request, reviewer, master.profileId, 3, 'Reportable feedback text.');
    await replyViaApi(request, master.key, feedbackId, 'Reportable reply text.');
    const tag = unique('Moderation');
    const page = await pageAs(moderatorAccount);

    await page.goto(deepLink(master.profileId));
    const overlay = page.locator('.provider-profile-catalog-overlay');
    await overlay.waitFor({ timeout: 10000 });
    const feedbackPanel = overlay.locator('.feedback-panel');
    const replyPanel = feedbackPanel.locator('.feedback-reply-panel').first();

    await replyPanel.locator('.feedback-reply-report-icon').click();
    await reportDialogSubmit(page, `${tag}: rude reply`);
    await expect(replyPanel.locator('.feedback-reply-censored-text')).toHaveText('[censored]', { timeout: 5000 });
    await expect(replyPanel.locator('.feedback-reply-report-icon')).toBeDisabled({ timeout: 5000 });
    await screenshot(page, 'moderation-reply-reported-and-hidden');

    const reportableEntry = feedbackPanel.locator('.feedback-entry').filter({ hasText: 'Reportable feedback text.' });
    await reportableEntry.locator('.feedback-report-icon').click();
    await reportDialogSubmit(page, `${tag}: fake review`);
    await page.locator('vaadin-notification-card').first().waitFor({ timeout: 5000 });
    await closeNotification(page);
    await expect(feedbackPanel.locator('.feedback-entry-censored-text')).toHaveText('[censored]', { timeout: 5000 });
    await expect(feedbackPanel.locator('.feedback-report-icon')).toBeDisabled({ timeout: 5000 });
    await screenshot(page, 'moderation-both-reported-and-hidden');

    await closeOverlay(page);

    await page.getByRole('tab', { name: /moderation/i }).click();
    await scopeModerationQueue(page, `${tag}:`);
    const list = page.locator('.moderation-list');
    const feedbackCard = list.locator('.moderation-card').filter({ hasText: 'Reportable feedback text.' });
    const replyCard = list.locator('.moderation-card').filter({ hasText: 'Reportable reply text.' });
    await expect(list.locator('.moderation-card')).toHaveCount(2, { timeout: 5000 });
    await expect(feedbackCard).toBeVisible({ timeout: 5000 });
    await expect(replyCard).toBeVisible({ timeout: 5000 });
    await expect(list.locator('.moderation-card').filter({ hasText: 'fake review' })).toBeVisible({ timeout: 5000 });
    await expect(list.locator('.moderation-card').filter({ hasText: 'rude reply' })).toBeVisible({ timeout: 5000 });
    await screenshot(page, 'moderation-queue-shows-both-merged');

    // Clicking a card's profile link opens the overlay in place (switches to Providers tab); closing it returns to Moderation.
    await feedbackCard.locator('.moderation-profile-link').first().click();
    await overlay.waitFor({ timeout: 5000 });
    await closeOverlay(page);
    await expect(page.locator('.moderation-view-layout')).toBeVisible({ timeout: 5000 });
    await screenshot(page, 'moderation-profile-link-opens-and-returns');
    await scopeModerationQueue(page, `${tag}:`);

    await feedbackCard.getByRole('button', { name: 'Approve', exact: true }).click();
    await page.locator('vaadin-notification-card').first().waitFor({ timeout: 5000 });
    await closeNotification(page);

    await replyCard.getByRole('button', { name: 'Approve', exact: true }).click();
    await page.locator('vaadin-notification-card').first().waitFor({ timeout: 5000 });
    await closeNotification(page);

    await page.reload();
    await page.getByRole('tab', { name: /moderation/i }).click();
    await scopeModerationQueue(page, `${tag}:`);

    await expect(page.locator('.moderation-list .moderation-card')).toHaveCount(0, { timeout: 5000 });
    await screenshot(page, 'moderation-both-approved');

    await page.goto(deepLink(master.profileId));
    await overlay.waitFor({ timeout: 10000 });
    await expect(feedbackPanel.locator('.feedback-entry').filter({ hasText: 'Reportable feedback text.' })).toBeVisible({ timeout: 5000 });
    await expect(replyPanel.locator('.feedback-reply-text')).toContainText('Reportable reply text.', { timeout: 5000 });
  });

  test('moderator works a 7-item moderation queue on a provider\'s profile — 4 reviewers\' feedback plus the owner\'s 4 replies, one reporter flags 7 of the 8 items, scrollable card list renders consistently, pagination count reflects the real total, type/author/reason/status filters, real pagination, confirming a violation hides its own action button and the status filter surfaces it again', async ({ pageAs, createUser, adminKey, request, unique }) => {
    test.setTimeout(180_000);
    const category = await createCategory(request, adminKey, unique, 'Vehicles');
    const city = await createCity(request, adminKey, unique, 'Kyiv');
    const master = await createOwnerWithProfile(request, createUser, profileBody({
      kind: 'MASTER', about: 'Master craftsman offering on-site vehicle repair.', categories: [category], city,
    }));
    const tag = unique('Queue');
    const feedbackTexts = [1, 2, 3, 4].map(n => `Scroll test feedback ${n}.`);

    // 4 distinct reviewers each leave one feedback entry, the owner replies to all 4.
    const feedbackIds = [];
    for (let i = 0; i < feedbackTexts.length; i++) {
      const reviewer = await createUser();
      const { feedbackId } = await reviewViaApi(request, reviewer, master.profileId, (i % 5) + 1, feedbackTexts[i]);
      await replyViaApi(request, master.key, feedbackId, `Scroll test reply ${i + 1}.`);
      feedbackIds.push(feedbackId);
    }

    // The reporter flags 3 of the 4 feedback entries (not the third) plus all 4 replies -- 7 flagged items.
    const reporter = await createUser();
    const reporterKey = await issueApiKeyViaApi(request, reporter);
    for (const i of [0, 1, 3]) {
      await flagViaApi(request, reporterKey, `/api/feedback/${feedbackIds[i]}/flags`, `${tag}: flagged feedback ${i + 1}`);
    }
    for (let i = 0; i < feedbackIds.length; i++) {
      await flagViaApi(request, reporterKey, `/api/feedback/${feedbackIds[i]}/reply/flags`, `${tag}: flagged reply ${i + 1}`);
    }
    const otherReviewerTexts = feedbackTexts.filter(t => t !== 'Scroll test feedback 3.');

    // A moderator of its own, because this test changes the account's page sizes.
    const moderator = await createUser({ role: 'MODERATOR' });
    const page = await pageAs(moderator);

    await page.goto(deepLink(master.profileId));
    const ownerOverlay = page.locator('.provider-profile-catalog-overlay');
    await ownerOverlay.waitFor({ timeout: 10000 });
    await expect(ownerOverlay.locator('.feedback-panel .feedback-entry')).toHaveCount(4, { timeout: 5000 });
    await ownerOverlay.locator('.overlay__content').evaluate(el => el.scrollTo(0, el.scrollHeight));
    await screenshot(page, 'provider-catalog-feedback-panel-scrolled-many-items');
    await closeOverlay(page);

    // ── the Moderation queue, scoped to this test's own items: 7 visible, scrollable list, pagination count reflects the real total ──
    await page.getByRole('tab', { name: /moderation/i }).click();
    const list = page.locator('.moderation-list');
    await scopeModerationQueue(page, `${tag}:`);
    await expect(list.locator('.moderation-card')).toHaveCount(7, { timeout: 8000 });
    for (const text of otherReviewerTexts) {
      await expect(list.locator('.moderation-card').filter({ hasText: text })).toBeVisible({ timeout: 5000 });
    }
    for (let i = 1; i <= feedbackTexts.length; i++) {
      await expect(list.locator('.moderation-card').filter({ hasText: `Scroll test reply ${i}.` })).toBeVisible({ timeout: 5000 });
    }
    await expect(page.locator('.moderation-view-layout .pagination-count')).toContainText('7', { timeout: 5000 });

    // ── filter controls: type, author, reason text -- each combined with the own-items reason scope ──
    await scopeModerationQueue(page, `${tag}:`, () => fillRole(page, MOD_BLOCK, 'COMMENT', 'moderation-filter-type'));
    await expect(list.locator('.moderation-card')).toHaveCount(4, { timeout: 8000 });
    await screenshot(page, 'moderation-filter-type-comment');

    await scopeModerationQueue(page, `${tag}:`, () => fillText(page, MOD_BLOCK, 'Author', master.owner.name));
    await expect(list.locator('.moderation-card')).toHaveCount(4, { timeout: 8000 });
    await screenshot(page, 'moderation-filter-author');

    await scopeModerationQueue(page, `${tag}: flagged reply`);
    await expect(list.locator('.moderation-card')).toHaveCount(4, { timeout: 8000 });
    await screenshot(page, 'moderation-filter-reason');

    await scopeModerationQueue(page, `${tag}:`);
    await expect(list.locator('.moderation-card')).toHaveCount(7, { timeout: 8000 });

    // ── pagination: real multi-page navigation using the minimum allowed page size (5 -- below
    // PageSizeLimits.MIN_PAGE_SIZE is rejected, so 5+2 is the smallest real split for 7 items) ──
    const { adsPageSize, providerProfilesPageSize, usersPageSize, moderationPageSize: moderationDefault } = await getPageSizes(page);
    await changePageSizes(page, adsPageSize, providerProfilesPageSize, usersPageSize, 5);
    // changePageSizes only clicks Save -- it deliberately doesn't close Settings, so close it explicitly here.
    await page.locator('.base-overlay.overlay--visible .overlay__breadcrumb-back').click();
    await page.locator('.base-overlay.overlay--visible').waitFor({ state: 'hidden', timeout: 5000 });
    await scopeModerationQueue(page, `${tag}:`);

    await expect(list.locator('.moderation-card')).toHaveCount(5, { timeout: 8000 });
    await expect(page.locator('.moderation-view-layout .pagination-count')).toContainText('1–5 of 7', { timeout: 5000 });
    await screenshot(page, 'moderation-pagination-page1');

    await goToNextPage(page);
    await expect(list.locator('.moderation-card')).toHaveCount(2, { timeout: 8000 });
    await expect(page.locator('.moderation-view-layout .pagination-count')).toContainText('6–7 of 7', { timeout: 5000 });
    await screenshot(page, 'moderation-pagination-page2');

    await goToPrevPage(page);
    await expect(list.locator('.moderation-card')).toHaveCount(5, { timeout: 8000 });

    // "First"/"Last" are only enabled from a non-boundary page -- go forward once more before
    // testing each, so neither click ever targets an already-disabled button.
    await goToNextPage(page);
    await expect(list.locator('.moderation-card')).toHaveCount(2, { timeout: 8000 });

    await goToFirstPage(page);
    await expect(list.locator('.moderation-card')).toHaveCount(5, { timeout: 8000 });

    await goToLastPage(page);
    await expect(list.locator('.moderation-card')).toHaveCount(2, { timeout: 8000 });
    await screenshot(page, 'moderation-pagination-last-page');

    await goToFirstPage(page);
    await expect(list.locator('.moderation-card')).toHaveCount(5, { timeout: 8000 });

    await changePageSizes(page, adsPageSize, providerProfilesPageSize, usersPageSize, moderationDefault);
    await page.locator('.base-overlay.overlay--visible .overlay__breadcrumb-back').click();
    await page.locator('.base-overlay.overlay--visible').waitFor({ state: 'hidden', timeout: 5000 });
    await scopeModerationQueue(page, `${tag}:`);
    // Restoring the default page size means all 7 items fit on a single page again.
    await expect(list.locator('.moderation-card')).toHaveCount(7, { timeout: 8000 });

    await list.evaluate(el => el.scrollIntoView({ block: 'end' }));
    await screenshot(page, 'moderation-queue-scrolled-many-items');

    // ── status transition: confirming a violation hides its own Confirm-violation button, drops it
    // out of the default (status-unfiltered) view, and the Status filter brings it back ───────────
    const statusTarget = list.locator('.moderation-card').filter({ hasText: 'Scroll test feedback 1.' });
    await expect(statusTarget.getByRole('button', { name: 'Confirm violation', exact: true })).toBeVisible({ timeout: 5000 });
    await statusTarget.getByRole('button', { name: 'Confirm violation', exact: true }).click();
    await page.locator('vaadin-notification-card').first().waitFor({ timeout: 5000 });
    await closeNotification(page);

    await expect(list.locator('.moderation-card')).toHaveCount(6, { timeout: 8000 });
    await screenshot(page, 'moderation-confirmed-item-hidden-by-default');

    await scopeModerationQueue(page, `${tag}:`, () => fillRole(page, MOD_BLOCK, 'CONFIRMED_HIDDEN', 'moderation-filter-status'));
    await expect(list.locator('.moderation-card')).toHaveCount(1, { timeout: 8000 });
    const confirmedCard = list.locator('.moderation-card').filter({ hasText: 'Scroll test feedback 1.' });
    await expect(confirmedCard).toContainText('CONFIRMED_HIDDEN');
    await expect(confirmedCard.getByRole('button', { name: 'Confirm violation', exact: true })).toHaveCount(0);
    await expect(confirmedCard.getByRole('button', { name: 'Approve', exact: true })).toBeVisible();
    await screenshot(page, 'moderation-confirmed-item-status-filter');

    await confirmedCard.getByRole('button', { name: 'Approve', exact: true }).click();
    await page.locator('vaadin-notification-card').first().waitFor({ timeout: 5000 });
    await closeNotification(page);
    await scopeModerationQueue(page, `${tag}:`);
    await expect(list.locator('.moderation-card')).toHaveCount(6, { timeout: 8000 });

    // ── cleanup: approve every own card so the shared queue holds none of this test's items ─────
    let remaining = await list.getByRole('button', { name: 'Approve', exact: true }).count();
    while (remaining > 0) {
      await list.getByRole('button', { name: 'Approve', exact: true }).first().click();
      await page.locator('vaadin-notification-card').first().waitFor({ timeout: 5000 });
      await closeNotification(page);
      remaining = await list.getByRole('button', { name: 'Approve', exact: true }).count();
    }
    await expect(list.locator('.moderation-card')).toHaveCount(0, { timeout: 5000 });
  });
});
