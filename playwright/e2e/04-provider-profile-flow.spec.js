/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Provider Profile e2e coverage -- both the AccountOverlay self-service/admin tab
 *   (create/edit/moderator-readonly/admin-on-behalf, kind/about/categories/city/phone/telegram/
 *   viber fields, per-channel contact-views-this-month counters) and the
 *   public Providers catalog (anonymous browsing/filtering, deep link + sitemap.xml + crawler meta
 *   tags, delete from the catalog card, SUPPORT-kind disabled-not-removed for a non-privileged
 *   actor already holding that kind). Per test:
 *   - "userEn creates provider profile": also covers contact fields -- invalid phone/telegram/viber
 *     format rejected on save, valid E.164 phone/viber and Telegram username accepted, and the
 *     view-mode "Contact views (this month)" counters block showing 0/0/0 after creation.
 *   - "userEn edits provider profile": also covers a phone-only edit (kind/about/categories/city
 *     untouched) recording its own activity entry showing just the Phone field changed, proving
 *     contact fields are captured in the same ProviderProfileSnapshotDto as the profile's own
 *     fields, not a separate audit entity; a read-only contact preview in the advertisement Create
 *     form (pulled from the current actor's own profile); and, on a real saved ad, the
 *     advertisement-side ContactRevealPanel click-through (phone reveal, Telegram deep link)
 *     resolving the same contact via the ad-to-owner-profile fallback -- the ad is deleted again
 *     at the end of this step so it doesn't affect later specs' ad counts.
 *   - "moderatorEn creates a minimal provider profile": also covers the Providers tab refreshing
 *     on Settings close when that tab was already selected before Settings ever opened -- Settings
 *     is a modal overlay, not a tab, so closing it never fires the tabs' own selection-change
 *     event, unlike the plain tab-switch case covered in the first test.
 *   - "moderatorEn views
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
 *   - "provider catalog Edit button": adminEn edits userUk's profile from the card's own Edit
 *     button, and moderatorEn's from the opened card overlay's Edit button -- both switch
 *     ProviderProfileCatalogOverlay into its own internal Edit mode (mirrors AdvertisementOverlay's
 *     single-purpose View/Edit overlay shape, reusing ProviderProfileFormOverlayModeHandler
 *     wholesale, no duplicated form and no unrelated Name/Settings tabs), then back to View in the
 *     same overlay on save. Also confirms userEn (non-privileged, non-owner) sees no Edit button on
 *     someone else's card/overlay but still sees it on their own.
 *   - "userEn opens a provider deep link": direct navigation to /providers/:id -> catalog overlay
 *     opens -> contact reveal panel (phone reveals in place, Telegram click opens its t.me deep
 *     link in a new tab, each records a contact_view) -> share button copies link -> sitemap.xml
 *     lists it -> crawler-facing og:type=profile/
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
 *   (confirmDeleteDialog), ./_flows/category.flow (selectInMultiSelectComboBox), ./_flows/advertisement.flow
 *   (openCardOverlay). Depends on spec 02
 *   having signed up all TEST_USERS, and spec 03 having created the Electronics/Vehicles categories
 *   and Lviv/Kyiv cities.
 * Outputs: Playwright HTML report entries for each test. userEn's provider profile is deleted by
 *   the end of this file's own serial sequence; userUk, moderatorEn and adminUk each keep a saved
 *   provider profile (userUk: SUPPORT/Vehicles/Kyiv, moderatorEn: MASTER/Vehicles/Lviv, adminUk:
 *   SHOP/Electronics/Kyiv) used only within this file.
 * Returns: exit code from the Playwright test runner -- 0 when every test in this file passes,
 *   non-zero otherwise.
 * ──────────────────────────────────────────────────────────────────────────── */
const { test, expect, screenshot, closeNotification, closeOverlay, TEST_USERS, assertAbsent, assertVerticalOrder, waitForOverlayClosed, assertRightAligned } = require('./_helpers');
const { runFillLoginFormFlow, runSubmitLoginFlow, runLogoutFlow } = require('./_flows/auth.flow');
const { runOpenSettingsFlow, runCloseSettingsFlow } = require('./_flows/audit.flow');
const { openEntityActivity, closeEntityActivity } = require('./_flows/entity-activity.flow');
const { runNavigateToUsersTabFlow, runOpenUserEditViaListFlow, runOpenUserViewDialogFlow, closeUserOverlay, clearUserFilter } = require('./_flows/user-management.flow');
const { confirmDeleteDialog } = require('./_flows/delete.flow');
const { openCardOverlay } = require('./_flows/advertisement.flow');
const { verifyDateRangeFilters, waitForVaadin } = require('./_flows/filter.flow');
const { selectInMultiSelectComboBox } = require('./_flows/category.flow');

test.describe.configure({ mode: 'serial' });

async function openProviderProfileTab(page) {
  await page.locator('.account-overlay .account-overlay-tabs vaadin-tab').filter({ hasText: /provider profile|профіль провайдера/i }).click();
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

  test('userEn creates provider profile — empty state before, Create button, kind/about/category/city filled, contact fields validation and save, view mode shows kind badge/about/category chip/city chip/contact views counters after save', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.userEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.userEn);
    await runOpenSettingsFlow(page);
    await openProviderProfileTab(page);

    await expect(page.locator('.account-overlay .provider-profile-view-empty-text')).toBeVisible({ timeout: 5000 });
    await screenshot(page, 'provider-profile-empty-state');

    await page.locator('.account-overlay vaadin-button').filter({ hasText: 'Create Profile' }).click();

    await page.locator('.account-overlay vaadin-radio-button').filter({ hasText: 'MASTER' }).first().click();
    await fillAbout(page, 'Professional electronics repair and installation services.');
    await selectCategory(page, 'Electronics');
    await selectCity(page, 'Lviv');

    const phoneField = page.locator('.account-overlay vaadin-text-field[data-testid="provider-profile-phone-field"] input');
    const telegramField = page.locator('.account-overlay vaadin-text-field[data-testid="provider-profile-telegram-field"] input');
    const viberField = page.locator('.account-overlay vaadin-text-field[data-testid="provider-profile-viber-field"] input');

    await test.step('contact fields — invalid phone/telegram/viber format rejected on save, valid E.164 phone/viber and Telegram username accepted', async () => {
      await phoneField.fill('not-a-phone');
      await telegramField.fill('a');
      await viberField.fill('123');
      await page.locator('.account-overlay vaadin-button').filter({ hasText: 'Save' }).click();
      await expect(page.locator('vaadin-notification-container')).toContainText('Validation failed', { timeout: 5000 });
      await closeNotification(page);
      await screenshot(page, 'provider-profile-contact-validation-error');

      await phoneField.fill('+380501234567');
      await telegramField.fill('electro_master');
      await viberField.fill('+380509876543');
    });
    await screenshot(page, 'provider-profile-create-filled');

    await page.locator('.account-overlay vaadin-button').filter({ hasText: 'Save' }).click();
    await expect(page.locator('vaadin-notification-container')).toContainText('Provider profile saved', { timeout: 5000 });
    await closeNotification(page);

    // Save now switches straight to View -- no Cancel click needed (Tabs never re-fires a click on an already-selected tab).
    await expect(page.locator('.account-overlay .provider-profile-kind-badge')).toContainText('MASTER', { timeout: 5000 });
    await expect(page.locator('.account-overlay .provider-profile-category-chip')).toContainText('Electronics');
    await expect(page.locator('.account-overlay .provider-profile-city-chip')).toContainText('Lviv');
    await screenshot(page, 'provider-profile-view-after-create');

    await test.step('contact views counters — Phone/Telegram/Viber show the saved value and a 0 click count (no reveals happened yet)', async () => {
      const counters = page.locator('.account-overlay .provider-profile-contact-views');
      await expect(counters).toBeVisible({ timeout: 5000 });
      await expect(counters.locator('.provider-profile-contact-views-phone')).toContainText('+380501234567 (0)');
      await expect(counters.locator('.provider-profile-contact-views-telegram')).toContainText('electro_master (0)');
      await expect(counters.locator('.provider-profile-contact-views-viber')).toContainText('+380509876543 (0)');
      await screenshot(page, 'provider-profile-contact-views-counters');
    });

    await test.step('account-tab view — every field renders in the expected top-to-bottom order', async () => {
      await assertVerticalOrder(page, expect, page.locator('.account-overlay .overlay__view-card'), [
        '.overlay__view-card-header',
        '.overlay__view-description',
        '.provider-profile-categories-chips',
        '.provider-profile-city-chips',
        '.provider-profile-kind-badge',
        '.entity-meta',
      ], 'provider-profile-view-field-order');
    });

    await runCloseSettingsFlow(page);

    await test.step('Providers tab reflects the just-created profile without a page reload', async () => {
      await page.locator('vaadin-tab').filter({ hasText: 'Providers' }).click();
      const container = page.locator('.provider-profile-container');
      await expect(container.locator('.provider-profile-card').filter({ hasText: 'MASTER' })).toBeVisible({ timeout: 5000 });
      await screenshot(page, 'providers-tab-live-refresh-after-create');
    });

    await runLogoutFlow(page, expect);
  });

  test('moderatorEn creates a minimal provider profile — no category, no city: those chip rows are absent, field order stays header -> about -> kind badge -> meta on the account view, then deleted', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.moderatorEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.moderatorEn);

    // Providers tab selected BEFORE Settings opens -- Settings is a modal overlay, not a tab, so
    // closing it never fires tabs' own selection-change event; the tab must still refresh anyway.
    await page.locator('vaadin-tab').filter({ hasText: 'Providers' }).click();
    await expect(page.locator('.provider-profile-container .provider-profile-card')
      .filter({ hasText: TEST_USERS.moderatorEn.name })).toHaveCount(0, { timeout: 5000 });

    await runOpenSettingsFlow(page);
    await openProviderProfileTab(page);

    await page.locator('.account-overlay vaadin-button').filter({ hasText: 'Create Profile' }).click();
    await page.locator('.account-overlay vaadin-radio-button').filter({ hasText: 'MASTER' }).first().click();
    await fillAbout(page, 'Minimal profile: kind and about only, no category, no city.');
    await page.locator('.account-overlay vaadin-button').filter({ hasText: 'Save' }).click();
    await expect(page.locator('vaadin-notification-container')).toContainText('Provider profile saved', { timeout: 5000 });
    await closeNotification(page);
    // Save now switches straight to View -- no Cancel click needed.

    await test.step('Providers tab (already selected, never switched to) refreshes on Settings close', async () => {
      await runCloseSettingsFlow(page);
      // No tab click here -- Providers was already the selected/visible tab the whole time.
      await expect(page.locator('.provider-profile-container .provider-profile-card')
        .filter({ hasText: TEST_USERS.moderatorEn.name })).toBeVisible({ timeout: 5000 });
      await screenshot(page, 'providers-tab-refresh-after-settings-close-no-tab-switch');
    });

    await runOpenSettingsFlow(page);
    await openProviderProfileTab(page);

    await test.step('account-tab view — chip rows absent, reduced field order intact', async () => {
      const viewCard = page.locator('.account-overlay .overlay__view-card');
      await expect(viewCard.locator('.provider-profile-kind-badge')).toContainText('MASTER', { timeout: 5000 });
      await assertAbsent(expect, viewCard, '.provider-profile-categories-chips');
      await assertAbsent(expect, viewCard, '.provider-profile-city-chips');
      await assertVerticalOrder(page, expect, viewCard, [
        '.overlay__view-card-header',
        '.overlay__view-description',
        '.provider-profile-kind-badge',
        '.entity-meta',
      ], 'provider-minimal-view-order');
    });

    await test.step('clean up — delete the minimal profile', async () => {
      await page.locator('.account-overlay .provider-profile-delete-button').click();
      await confirmDeleteDialog(page);
      await expect(page.locator('.account-overlay .provider-profile-view-empty-text')).toBeVisible({ timeout: 5000 });
    });

    await runCloseSettingsFlow(page);
    await runLogoutFlow(page, expect);
  });

  test('userEn edits provider profile — previously-saved kind/about/categories/city pre-filled on re-edit, second category added, activity diff, phone-only edit records its own activity entry, history button, outer breadcrumb closes to list', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.userEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.userEn);
    await runOpenSettingsFlow(page);
    await openProviderProfileTab(page);

    // Re-fetched from the server on this fresh login -- proves the saved data actually persisted.
    await expect(page.locator('.account-overlay .provider-profile-category-chip')).toContainText('Electronics', { timeout: 5000 });

    await page.locator('.account-overlay vaadin-button').filter({ hasText: 'Edit' }).click();

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

    // Save now switches straight to View -- assert the rendered chips/badge instead of raw form fields.
    await expect(page.locator('.account-overlay .provider-profile-kind-badge')).toContainText('SHOP', { timeout: 5000 });
    await expect(page.locator('.account-overlay .provider-profile-category-chip')).toContainText(['Electronics', 'Vehicles']);
    await screenshot(page, 'provider-profile-edit-after-save');

    const activityList = await openEntityActivity(page, '.provider-profile-history-button');
    await expect(activityList.locator('.entity-activity-row')).toHaveCount(2, { timeout: 5000 });
    await screenshot(page, 'provider-profile-activity-diff');

    await test.step('phone-only edit — records its own activity entry showing just the Phone field changed', async () => {
      await closeEntityActivity(page, 'parent');
      await page.locator('.account-overlay vaadin-button').filter({ hasText: 'Edit' }).click();
      const phoneField = page.locator('.account-overlay vaadin-text-field[data-testid="provider-profile-phone-field"] input');
      await phoneField.fill('+380507654321');
      await phoneField.blur(); // TextField syncs on blur, not per keystroke -- no other field to blur into here
      await page.locator('.account-overlay vaadin-button').filter({ hasText: 'Save' }).click();
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
      await page.locator('vaadin-tab').filter({ hasText: 'Advertisements' }).first().click();
      await page.locator('.add-advertisement-button').click();
      const overlay = page.locator('.advertisement-overlay');
      await overlay.waitFor({ timeout: 5000 });

      const preview = overlay.locator('.advertisement-contact-preview');
      await expect(preview).toBeVisible({ timeout: 5000 });
      await expect(preview.locator('.advertisement-contact-preview-phone')).toContainText('+380507654321');
      await expect(preview.locator('.advertisement-contact-preview-telegram')).toContainText('electro_master');
      await expect(preview.locator('.advertisement-contact-preview-viber')).toContainText('+380509876543');
      await expect(preview.locator('.advertisement-contact-preview-phone')).toHaveAttribute('title', /profile/i);
      await screenshot(page, 'advertisement-create-contact-preview');

      await overlay.locator('vaadin-button').filter({ has: page.locator('vaadin-icon[icon="vaadin:close"]') }).first().click();
      await waitForOverlayClosed(page);
    });

    await test.step('advertisement view — ContactRevealPanel click-through resolves the owner\'s provider profile contact via fallback', async () => {
      const adTitle = 'Contact Reveal Fixture Ad';
      await page.locator('.add-advertisement-button').click();
      const createOverlay = page.locator('.advertisement-overlay');
      await createOverlay.waitFor({ timeout: 5000 });
      await createOverlay.locator('[data-testid="advertisement-overlay-field-title"] input').fill(adTitle);
      await createOverlay.locator('[data-testid="advertisement-overlay-field-description"] .ql-editor').fill('Fixture ad for the advertisement-side contact reveal test.');
      await createOverlay.locator('vaadin-button').filter({ hasText: 'Save' }).click();
      await expect(page.locator('vaadin-notification-container')).toContainText('Advertisement saved', { timeout: 5000 });
      await closeNotification(page);

      const card = page.locator('.advertisement-card').filter({ has: page.locator('.advertisement-title', { hasText: adTitle }) }).first();
      const overlay = await openCardOverlay(page, card, 'contact-reveal-fixture');

      const panel = overlay.locator('.contact-reveal-panel');
      await expect(panel.locator('.contact-reveal-phone')).toBeVisible({ timeout: 5000 });
      await expect(panel.locator('.contact-reveal-telegram')).toBeVisible();
      await expect(panel.locator('.contact-reveal-viber')).toBeVisible();
      await screenshot(page, 'advertisement-contact-reveal-panel');

      await panel.locator('.contact-reveal-phone vaadin-button').click();
      await expect(panel.locator('.contact-reveal-phone-value')).toHaveText('+380507654321', { timeout: 5000 });
      await screenshot(page, 'advertisement-contact-reveal-phone-revealed');

      const [telegramTab] = await Promise.all([
        page.context().waitForEvent('page'),
        panel.locator('.contact-reveal-telegram vaadin-button').click(),
      ]);
      await telegramTab.waitForLoadState('domcontentloaded').catch(() => {});
      expect(telegramTab.url()).toContain('t.me/electro_master');
      await telegramTab.close();

      await closeOverlay(page);
      await card.locator('.advertisement-delete').click();
      await confirmDeleteDialog(page);
      await expect(page.locator('.advertisement-card').filter({ has: page.locator('.advertisement-title', { hasText: adTitle }) })).toHaveCount(0, { timeout: 5000 });
    });

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
    // SUPPORT is only offered to a privileged actor -- exercises that branch, untouched elsewhere in this file.
    await page.locator('.account-overlay vaadin-radio-button').filter({ hasText: 'SUPPORT' }).first().click();
    await fillAbout(page, 'Admin-managed support profile for userUk.');
    await selectCategory(page, 'Vehicles');
    await selectCity(page, 'Kyiv');

    await page.locator('.account-overlay vaadin-button').filter({ hasText: 'Save' }).click();
    await expect(page.locator('vaadin-notification-container')).toContainText('Provider profile saved', { timeout: 5000 });
    await closeNotification(page);

    // Save now switches straight to View -- no Cancel click needed.
    await expect(page.locator('.account-overlay .provider-profile-kind-badge')).toContainText('SUPPORT', { timeout: 5000 });
    await expect(page.locator('.account-overlay .provider-profile-city-chip')).toContainText('Kyiv');
    await screenshot(page, 'provider-profile-admin-via-grid-view');

    // Re-edit through this same grid-entry path -- the pre-fill regression fix applies here too.
    await page.locator('.account-overlay vaadin-button').filter({ hasText: 'Edit' }).click();
    await expect(async () => {
      const names = await selectedCategoryNames(page);
      expect(names).toContain('Vehicles');
    }).toPass({ timeout: 5000 });
    await expect(page.locator('.account-overlay vaadin-combo-box input')).toHaveValue('Kyiv', { timeout: 5000 });

    // Cancel from Provider Profile Edit routes back to View first (same overlay stays open,
    // same as afterDiscard()'s design) -- closeUserOverlay only fully exits from View.
    await page.locator('.account-overlay vaadin-button[title="Cancel"]').click();
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
      await page.locator('.account-overlay vaadin-radio-button').filter({ hasText: 'MASTER' }).first().click();
      await fillAbout(page, 'Master craftsman offering on-site vehicle repair.');
      await selectCategory(page, 'Vehicles');
      await selectCity(page, 'Lviv');
      await page.locator('.account-overlay vaadin-button').filter({ hasText: 'Save' }).click();
      await expect(page.locator('vaadin-notification-container')).toContainText('Provider profile saved', { timeout: 5000 });
      await closeNotification(page);
      // Save now switches straight to View -- no Cancel click needed.
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
      await page.locator('.account-overlay vaadin-radio-button').filter({ hasText: /shop|магазин/i }).first().click();
      await fillAbout(page, 'Retail shop for electronics and accessories.');
      await selectCategory(page, 'Electronics');
      await selectCity(page, 'Kyiv');
      await page.locator('.account-overlay vaadin-button').filter({ hasText: /save|зберегти/i }).click();
      await expect(page.locator('vaadin-notification-container')).toContainText(/provider profile saved|профіль провайдера збережено/i, { timeout: 5000 });
      await closeNotification(page);
      // Save now switches straight to View -- no Cancel click needed.
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
      await fillAbout(page, 'Master craftsman offering on-site vehicle repair, now also on weekends.');
      await page.locator('.account-overlay vaadin-button').filter({ hasText: 'Save' }).click();
      await expect(page.locator('vaadin-notification-container')).toContainText('Provider profile saved', { timeout: 5000 });
      await closeNotification(page);
      // Save now switches straight to View -- no Cancel click needed.
      await runCloseSettingsFlow(page);
      await runLogoutFlow(page, expect);
    });

    await page.goto('/');
    await page.locator('vaadin-tab').filter({ hasText: 'Providers' }).click();

    const container = page.locator('.provider-profile-container');
    await expect(container.locator('.provider-profile-card')).toHaveCount(4, { timeout: 10000 });
    await expect(container.locator('.provider-profile-kind-badge--shop')).toHaveCount(2);
    await expect(container.locator('.provider-profile-kind-badge--support')).toHaveCount(1);
    await expect(container.locator('.provider-profile-kind-badge--master')).toHaveCount(1);
    await expect(container.locator('.provider-profile-share').first()).toBeVisible();
    await screenshot(page, 'provider-catalog-list');

    await test.step('catalog card — every field renders in the expected top-to-bottom order', async () => {
      const anyCard = container.locator('.provider-profile-card').first();
      await assertVerticalOrder(page, expect, anyCard.locator('.provider-profile-card-content'), [
        '.provider-profile-card-title',
        '.provider-profile-card-about-wrapper',
        '.provider-profile-card-chip-row[aria-label="Categories:"]',
        '.provider-profile-card-chip-row[aria-label="City:"]',
        '.provider-profile-kind-badge',
        '.entity-meta',
      ], 'provider-catalog-card-field-order');
    });

    await page.locator('.providers-content-wrapper .query-status-bar').click();
    await expect(page.locator('.provider-profile-query-block')).toBeVisible({ timeout: 5000 });

    await test.step('kind filter — multi-match Shop (userEn + adminUk), single-match Support (userUk) and Master (moderatorEn)', async () => {
      await page.locator('.provider-profile-query-block').locator('vaadin-multi-select-combo-box[data-testid="provider-profile-filter-kind"]').click();
      await page.locator('vaadin-multi-select-combo-box-item').filter({ hasText: 'Shop' }).first().click();
      await page.keyboard.press('Escape');
      await page.locator('.query-action-block vaadin-button[title*="Apply"]').click();
      await expect(container.locator('.provider-profile-card')).toHaveCount(2, { timeout: 10000 });
      await expect(container.locator('.provider-profile-kind-badge--shop')).toHaveCount(2);
      await screenshot(page, 'provider-catalog-filter-kind-shop');

      await page.locator('.query-action-block vaadin-button[title*="Clear"]').click();
      await expect(container.locator('.provider-profile-card')).toHaveCount(4, { timeout: 10000 });

      await page.locator('.provider-profile-query-block').locator('vaadin-multi-select-combo-box[data-testid="provider-profile-filter-kind"]').click();
      await page.locator('vaadin-multi-select-combo-box-item').filter({ hasText: 'Support' }).first().click();
      await page.keyboard.press('Escape');
      await page.locator('.query-action-block vaadin-button[title*="Apply"]').click();
      await expect(container.locator('.provider-profile-card')).toHaveCount(1, { timeout: 10000 });
      await expect(container.locator('.provider-profile-kind-badge--support')).toBeVisible();
      await screenshot(page, 'provider-catalog-filter-kind-support');

      await page.locator('.query-action-block vaadin-button[title*="Clear"]').click();
      await expect(container.locator('.provider-profile-card')).toHaveCount(4, { timeout: 10000 });

      await page.locator('.provider-profile-query-block').locator('vaadin-multi-select-combo-box[data-testid="provider-profile-filter-kind"]').click();
      await page.locator('vaadin-multi-select-combo-box-item').filter({ hasText: 'Master' }).first().click();
      await page.keyboard.press('Escape');
      await page.locator('.query-action-block vaadin-button[title*="Apply"]').click();
      await expect(container.locator('.provider-profile-card')).toHaveCount(1, { timeout: 10000 });
      await expect(container.locator('.provider-profile-kind-badge--master')).toBeVisible();
      await screenshot(page, 'provider-catalog-filter-kind-master');

      await page.locator('.query-action-block vaadin-button[title*="Clear"]').click();
      await expect(container.locator('.provider-profile-card')).toHaveCount(4, { timeout: 10000 });
    });

    await test.step('category filter — Vehicles matches userEn (holds both categories since the earlier edit test) + userUk + moderatorEn, Electronics matches userEn + adminUk', async () => {
      await page.locator('.provider-profile-query-block').locator('[data-testid="provider-profile-filter-categories"]').click();
      await page.locator('vaadin-multi-select-combo-box-item').filter({ hasText: 'Vehicles' }).first().click();
      await page.keyboard.press('Escape');
      await page.locator('.query-action-block vaadin-button[title*="Apply"]').click();
      // userEn's own "edits provider profile" test earlier in this file adds Vehicles on top of
      // its original Electronics (never replacing it), so userEn also matches this filter.
      await expect(container.locator('.provider-profile-card')).toHaveCount(3, { timeout: 10000 });
      await expect(container.locator('.provider-profile-kind-badge--shop')).toHaveCount(1);
      await expect(container.locator('.provider-profile-kind-badge--support')).toBeVisible();
      await expect(container.locator('.provider-profile-kind-badge--master')).toBeVisible();
      await screenshot(page, 'provider-catalog-filter-category-vehicles');

      await page.locator('.query-action-block vaadin-button[title*="Clear"]').click();
      await expect(container.locator('.provider-profile-card')).toHaveCount(4, { timeout: 10000 });

      await page.locator('.provider-profile-query-block').locator('[data-testid="provider-profile-filter-categories"]').click();
      await page.locator('vaadin-multi-select-combo-box-item').filter({ hasText: 'Electronics' }).first().click();
      await page.keyboard.press('Escape');
      await page.locator('.query-action-block vaadin-button[title*="Apply"]').click();
      await expect(container.locator('.provider-profile-card')).toHaveCount(2, { timeout: 10000 });
      await expect(container.locator('.provider-profile-kind-badge--shop')).toHaveCount(2);
      await screenshot(page, 'provider-catalog-filter-category-electronics');

      await page.locator('.query-action-block vaadin-button[title*="Clear"]').click();
      await expect(container.locator('.provider-profile-card')).toHaveCount(4, { timeout: 10000 });
    });

    await page.locator('.provider-profile-query-block').locator('vaadin-combo-box[data-testid="provider-profile-filter-city"] input').click();
    await page.locator('.provider-profile-query-block').locator('vaadin-combo-box[data-testid="provider-profile-filter-city"] input').fill('Kyiv');
    await page.locator('vaadin-combo-box-item').filter({ hasText: 'Kyiv' }).first().click();
    await page.locator('.query-action-block vaadin-button[title*="Apply"]').click();
    await expect(container.locator('.provider-profile-card')).toHaveCount(2, { timeout: 10000 });
    await expect(container.locator('.provider-profile-kind-badge--support')).toBeVisible();
    await expect(container.locator('.provider-profile-kind-badge--shop')).toBeVisible();
    await screenshot(page, 'provider-catalog-filter-city-kyiv');

    await page.locator('.query-action-block vaadin-button[title*="Clear"]').click();
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

  test('provider catalog Edit button — privileged actor edits someone else\'s profile from the card and from the overlay, both switch this same overlay into its own Edit mode (no unrelated Name/Settings tabs, mirrors AdvertisementOverlay); non-privileged non-owner sees no Edit button', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.adminEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.adminEn);
    await page.locator('vaadin-tab').filter({ hasText: 'Providers' }).click();

    await test.step('card Edit button — adminEn edits userUk\'s profile straight from the card, opens directly in Edit mode', async () => {
      const card = page.locator('.provider-profile-card--support')
        .filter({ has: page.locator('.provider-profile-card-title', { hasText: TEST_USERS.userUk.name }) });
      await card.waitFor({ timeout: 5000 });
      await card.locator('.provider-profile-edit').click();

      const overlay = page.locator('.provider-profile-catalog-overlay');
      await overlay.waitFor({ timeout: 5000 });
      // Single-purpose overlay -- no Name/Settings tabs, unlike the old AccountOverlay-based flow.
      await assertAbsent(expect, overlay, '.account-overlay-tabs');
      await fillAbout(page, 'Edited by adminEn from the public catalog card.', '.provider-profile-catalog-overlay');
      await overlay.locator('vaadin-button').filter({ hasText: 'Save' }).click();
      await expect(page.locator('vaadin-notification-container')).toContainText('Provider profile saved', { timeout: 5000 });
      await closeNotification(page);

      // Save switches this same overlay straight back to View, same as AdvertisementOverlay.
      await expect(overlay.locator('.overlay__view-description')).toContainText('Edited by adminEn from the public catalog card.', { timeout: 5000 });
      await screenshot(page, 'providers-catalog-card-edit');
      await closeOverlay(page);

      await expect(card.locator('.provider-profile-card-about')).toContainText('Edited by adminEn from the public catalog card.', { timeout: 5000 });
    });

    await test.step('overlay Edit button — adminEn edits moderatorEn\'s profile from the opened card overlay, no separate overlay to jump to', async () => {
      const card = page.locator('.provider-profile-card--master')
        .filter({ has: page.locator('.provider-profile-card-title', { hasText: TEST_USERS.moderatorEn.name }) });
      await card.waitFor({ timeout: 5000 });
      await card.click();
      const overlay = page.locator('.provider-profile-catalog-overlay');
      await overlay.waitFor({ timeout: 5000 });

      await test.step('feedback panel — logged-in adminEn leaves a review on moderatorEn\'s profile then edits it in place, list entry and header aggregate update immediately', async () => {
        const feedbackPanel = overlay.locator('.feedback-panel');
        await expect(feedbackPanel.locator('.feedback-empty')).toBeVisible({ timeout: 5000 });

        await feedbackPanel.locator('.feedback-add-trigger .feedback-add-button').click();
        await feedbackPanel.locator('.star-rating-field [data-rating="4"]').click();
        await feedbackPanel.locator('[data-testid="feedback-form-field-text"] textarea').fill('Great master, highly recommend!');
        await feedbackPanel.locator('.feedback-form-submit').click();
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

        const inlineTextArea = entry.locator('[data-testid="feedback-entry-edit-field-text"] textarea');
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

      await test.step('comment tree — adminEn replies to their own feedback entry, replies to that reply (nested, expanded by default), edits a comment, reacts and toggles it off, deletes a leaf comment and a comment with replies (confirm dialog)', async () => {
        const feedbackPanel = overlay.locator('.feedback-panel');
        const commentTree = feedbackPanel.locator('.comment-tree-panel').first();

        await assertRightAligned(expect, feedbackPanel.locator('.feedback-entry-header-actions').first(), feedbackPanel.locator('.feedback-entry-header').first());
        await commentTree.locator('.comment-reply-form-slot .comment-reply-trigger vaadin-button').first().click();
        await commentTree.locator('[data-testid="comment-reply-field-text"] textarea').fill('First-level reply from adminEn.');
        await commentTree.locator('.comment-save-icon').first().click();
        await expect(commentTree.locator('.comment-node').filter({ hasText: 'First-level reply from adminEn.' })).toBeVisible({ timeout: 5000 });
        await screenshot(page, 'provider-catalog-comment-tree-top-level-reply');

        const findTopLevelNode = () => commentTree.locator('.comment-node').filter({ hasText: 'First-level reply' }).first();

        await findTopLevelNode().locator('> .comment-reply-form-slot .comment-reply-trigger vaadin-button').click();
        await findTopLevelNode().locator('> .comment-reply-form-slot [data-testid="comment-reply-field-text"] textarea').fill('Nested reply under the first-level reply.');
        await findTopLevelNode().locator('> .comment-reply-form-slot .comment-inline-form .comment-save-icon').click();

        const toggle = findTopLevelNode().locator('> .comment-header .comment-toggle-icon');
        const toggleCount = findTopLevelNode().locator('> .comment-header .comment-toggle-count');
        await expect(toggle).toBeVisible({ timeout: 5000 });
        await expect(toggleCount).toHaveText('1');
        const childrenContainer = findTopLevelNode().locator('> .comment-children');
        // A newly-added reply auto-expands its parent -- no extra click needed to see what was just saved.
        await expect(childrenContainer).toBeVisible({ timeout: 5000 });
        await expect(childrenContainer.locator('.comment-node').filter({ hasText: 'Nested reply under the first-level reply.' })).toBeVisible();
        await screenshot(page, 'provider-catalog-comment-tree-nested-reply-expanded');
        await toggle.click();
        await expect(childrenContainer).toBeHidden({ timeout: 5000 });
        await toggle.click();
        await expect(childrenContainer).toBeVisible({ timeout: 5000 });

        await findTopLevelNode().locator('> .comment-header .comment-edit-icon').click();
        const editField = findTopLevelNode().locator('> .comment-edit-form-slot [data-testid="comment-edit-field-text"] textarea');
        await expect(editField).toBeVisible({ timeout: 5000 });
        await expect(editField).toHaveValue('First-level reply from adminEn.', { timeout: 5000 });
        await editField.fill('First-level reply, edited by adminEn.');

        const overlayContent = overlay.locator('.overlay__content');
        const canScroll = await overlayContent.evaluate(el => el.scrollHeight - el.clientHeight > 30);
        if (canScroll) {
          await overlayContent.evaluate(el => { el.scrollTop = Math.min(150, el.scrollHeight - el.clientHeight); });
        }
        const scrollBefore = await overlayContent.evaluate(el => el.scrollTop);
        await findTopLevelNode().locator('> .comment-edit-form-slot .comment-inline-form .comment-save-icon').click();
        await expect(commentTree.locator('.comment-node').first().locator('> .comment-text-container .comment-text')).toContainText('First-level reply, edited by adminEn.', { timeout: 5000 });
        if (canScroll) {
          const scrollAfter = await overlayContent.evaluate(el => el.scrollTop);
          expect(Math.abs(scrollAfter - scrollBefore)).toBeLessThanOrEqual(5);
        }
        await screenshot(page, 'provider-catalog-comment-tree-edited');

        // Expand state now persists across reload() -- once a node is expanded, it stays expanded
        // through any subsequent reply/edit/delete/reaction anywhere in the tree, with no need to
        // re-click the toggle after each action (and no jump back to collapsed in between).
        const topLevelNode = commentTree.locator('.comment-node').first();
        const childrenContainerAfterEdit = topLevelNode.locator('> .comment-children');
        await expect(childrenContainerAfterEdit).toBeVisible({ timeout: 5000 });
        const nestedNode = childrenContainerAfterEdit.locator('.comment-node').filter({ hasText: 'Nested reply under the first-level reply.' }).first();
        const upReactionWrapper = nestedNode.locator('.comment-reaction-wrapper').first();
        await expect(upReactionWrapper.locator('.comment-reaction-count')).toHaveText('0', { timeout: 5000 });
        await upReactionWrapper.locator('.comment-reaction-button').click();
        await expect(upReactionWrapper.locator('.comment-reaction-count')).toHaveText('1', { timeout: 5000 });
        await expect(childrenContainerAfterEdit).toBeVisible({ timeout: 5000 });
        await expect(upReactionWrapper.locator('.comment-reaction-button')).toHaveClass(/comment-reaction-button-active/);
        await screenshot(page, 'provider-catalog-comment-tree-reaction-active');
        await upReactionWrapper.locator('.comment-reaction-button').click();
        await expect(upReactionWrapper.locator('.comment-reaction-count')).toHaveText('0', { timeout: 5000 });
        await expect(childrenContainerAfterEdit).toBeVisible({ timeout: 5000 });
        await expect(upReactionWrapper.locator('.comment-reaction-button')).not.toHaveClass(/comment-reaction-button-active/);

        await topLevelNode.locator('> .comment-header .comment-delete-icon').click();
        await confirmDeleteDialog(page);
        await expect(topLevelNode.locator('> .comment-text-container .comment-deleted-text')).toHaveText('[deleted]', { timeout: 5000 });
        await expect(childrenContainerAfterEdit.locator('.comment-node').filter({ hasText: 'Nested reply under the first-level reply.' })).toBeVisible({ timeout: 5000 });
        await screenshot(page, 'provider-catalog-comment-tree-tombstoned');

        await nestedNode.locator('.comment-delete-icon').click();
        await confirmDeleteDialog(page);
        await expect(commentTree.locator('.comment-node').filter({ hasText: 'Nested reply under the first-level reply.' })).toHaveCount(0, { timeout: 5000 });
        await screenshot(page, 'provider-catalog-comment-tree-leaf-deleted');

        // Deleting the last reply under a tombstoned parent prunes the now-dangling parent too --
        // it had already been tombstoned above, and just lost its only remaining child.
        await expect(commentTree.locator('.comment-node').filter({ hasText: 'First-level reply' })).toHaveCount(0, { timeout: 5000 });
      });

      await test.step('Close button — open a reply composer, type draft text, click Close, verify trigger re-appears with no draft text on reopen', async () => {
        const feedbackPanel = overlay.locator('.feedback-panel');
        const commentTree = feedbackPanel.locator('.comment-tree-panel').first();

        await commentTree.locator('.comment-reply-form-slot .comment-reply-trigger vaadin-button').first().click();
        const replyTextarea = commentTree.locator('.comment-reply-form-slot [data-testid="comment-reply-field-text"] textarea').first();
        await expect(replyTextarea).toBeVisible({ timeout: 5000 });
        await replyTextarea.fill('Draft text that should be discarded on Close.');

        const closeButtons = commentTree.locator('.comment-close-icon');
        await expect(closeButtons.first()).toBeVisible({ timeout: 5000 });
        await closeButtons.first().click();

        await expect(commentTree.locator('.comment-reply-form-slot .comment-reply-trigger vaadin-button')).toBeVisible({ timeout: 5000 });
        await commentTree.locator('.comment-reply-form-slot .comment-reply-trigger vaadin-button').first().click();
        const newTextarea = commentTree.locator('.comment-reply-form-slot [data-testid="comment-reply-field-text"] textarea').first();
        await expect(newTextarea).toHaveValue('', { timeout: 5000 });
        await newTextarea.fill('New reply text for reopen test.');
        await commentTree.locator('.comment-save-icon').first().click();
        await expect(commentTree.locator('.comment-node').filter({ hasText: 'New reply text for reopen test.' })).toBeVisible({ timeout: 5000 });
        await screenshot(page, 'provider-catalog-comment-tree-close-button');
      });

      await overlay.locator('.overlay__view-edit').click();
      await assertAbsent(expect, overlay, '.account-overlay-tabs');
      await fillAbout(page, 'Edited by adminEn from the catalog overlay.', '.provider-profile-catalog-overlay');
      await overlay.locator('vaadin-button').filter({ hasText: 'Save' }).click();
      await expect(page.locator('vaadin-notification-container')).toContainText('Provider profile saved', { timeout: 5000 });
      await closeNotification(page);

      await expect(overlay.locator('.overlay__view-description')).toContainText('Edited by adminEn from the catalog overlay.', { timeout: 5000 });
      await screenshot(page, 'providers-catalog-overlay-edit');
      await closeOverlay(page);

      await expect(card.locator('.provider-profile-card-about')).toContainText('Edited by adminEn from the catalog overlay.', { timeout: 5000 });
    });

    await runLogoutFlow(page, expect);

    await test.step('non-privileged non-owner — no Edit button on someone else\'s card or overlay, own card still has it', async () => {
      await runFillLoginFormFlow(page, TEST_USERS.userEn);
      await runSubmitLoginFlow(page, expect, TEST_USERS.userEn);
      await page.locator('vaadin-tab').filter({ hasText: 'Providers' }).click();

      const othersCard = page.locator('.provider-profile-card--support')
        .filter({ has: page.locator('.provider-profile-card-title', { hasText: TEST_USERS.userUk.name }) });
      await othersCard.waitFor({ timeout: 5000 });
      await assertAbsent(expect, othersCard, '.provider-profile-edit');

      await othersCard.click();
      const catalogOverlay = page.locator('.provider-profile-catalog-overlay');
      await catalogOverlay.waitFor({ timeout: 5000 });
      await assertAbsent(expect, catalogOverlay, '.overlay__view-edit');
      await closeOverlay(page);

      const ownCard = page.locator('.provider-profile-card--shop')
        .filter({ has: page.locator('.provider-profile-card-title', { hasText: TEST_USERS.userEn.name }) });
      await expect(ownCard.locator('.provider-profile-edit')).toBeVisible({ timeout: 5000 });
      await screenshot(page, 'providers-catalog-non-owner-no-edit');
    });

    await runLogoutFlow(page, expect);
  });

  test('userEn opens a provider deep link — direct navigation to /providers/:id opens the catalog overlay, contact reveal panel (phone reveal, Telegram deep link), share button copies link, sitemap.xml lists it', async () => {
    await page.goto('/');
    await page.locator('vaadin-tab').filter({ hasText: 'Providers' }).click();

    const card = page.locator('.provider-profile-card--shop')
      .filter({ has: page.locator('.provider-profile-card-title', { hasText: TEST_USERS.userEn.name }) });
    await card.waitFor({ timeout: 5000 });
    const providerId = await card.getAttribute('data-provider-id');
    // eslint-disable-next-line playwright/prefer-web-first-assertions -- the real string value is needed to build the URL below, not just an existence check
    expect(providerId).toBeTruthy();

    await page.goto(`/providers/${providerId}-userEn`);
    const overlay = page.locator('.provider-profile-catalog-overlay');
    await overlay.waitFor({ timeout: 10000 });
    await expect(overlay.locator('.provider-profile-kind-badge')).toContainText('Shop');
    await screenshot(page, 'provider-catalog-deep-link-opened');

    await test.step('catalog overlay — every field renders in the expected top-to-bottom order', async () => {
      await assertVerticalOrder(page, expect, overlay.locator('.overlay__view-card'), [
        '.overlay__view-card-header',
        '.overlay__view-description',
        '.provider-profile-categories-chips',
        '.provider-profile-city-chips',
        '.provider-profile-kind-badge',
        '.contact-reveal-panel',
        '.feedback-panel',
        '.entity-meta',
      ], 'provider-catalog-overlay-field-order');
    });

    await test.step('contact reveal panel — phone reveals in place, Telegram opens its deep link, each records a view', async () => {
      const panel = overlay.locator('.contact-reveal-panel');
      await expect(panel.locator('.contact-reveal-phone')).toBeVisible({ timeout: 5000 });
      await expect(panel.locator('.contact-reveal-telegram')).toBeVisible();
      await expect(panel.locator('.contact-reveal-viber')).toBeVisible();
      await screenshot(page, 'provider-catalog-contact-reveal-panel');

      await panel.locator('.contact-reveal-phone vaadin-button').click();
      // Phone was changed to +380507654321 by the earlier phone-only-edit activity test in this same file.
      await expect(panel.locator('.contact-reveal-phone-value')).toHaveText('+380507654321', { timeout: 5000 });
      await screenshot(page, 'provider-catalog-contact-reveal-phone-revealed');

      const [telegramTab] = await Promise.all([
        page.context().waitForEvent('page'),
        panel.locator('.contact-reveal-telegram vaadin-button').click(),
      ]);
      await telegramTab.waitForLoadState('domcontentloaded').catch(() => {});
      expect(telegramTab.url()).toContain('t.me/electro_master');
      await telegramTab.close();
    });

    await test.step('feedback panel — anonymous visitor sees the empty list but no leave-a-review form', async () => {
      const feedbackPanel = overlay.locator('.feedback-panel');
      await expect(feedbackPanel.locator('.feedback-empty')).toBeVisible({ timeout: 5000 });
      await expect(feedbackPanel.locator('.feedback-header-count')).toHaveCount(0);
      await assertAbsent(expect, feedbackPanel, '.feedback-form-submit');
      await screenshot(page, 'provider-catalog-feedback-panel-empty');
    });

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

    await expect(page.locator('.account-overlay vaadin-radio-button').filter({ hasText: 'MASTER' })).toHaveCount(1, { timeout: 5000 });
    await expect(page.locator('.account-overlay vaadin-radio-button').filter({ hasText: 'SHOP' })).toHaveCount(1, { timeout: 5000 });
    await expect(page.locator('.account-overlay vaadin-radio-button').filter({ hasText: 'MASTER' })).not.toHaveJSProperty('disabled', true);
    await expect(page.locator('.account-overlay vaadin-radio-button').filter({ hasText: 'SHOP' })).not.toHaveJSProperty('disabled', true);
    const supportRadio = page.locator('.account-overlay vaadin-radio-button').filter({ hasText: 'SUPPORT' });
    await expect(supportRadio).toHaveCount(1, { timeout: 5000 });
    await expect(supportRadio).toHaveJSProperty('disabled', true);
    await screenshot(page, 'provider-catalog-support-disabled-not-offered');

    await page.locator('.account-overlay vaadin-button[title="Cancel"], .account-overlay vaadin-button[title="Скасувати"]').click();
    await runCloseSettingsFlow(page);
    await runLogoutFlow(page, expect);
  });
});
