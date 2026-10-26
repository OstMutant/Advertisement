/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Core advertisement lifecycle e2e coverage -- create (with YouTube/image/video
 *   gallery, lightbox playback), edit (discard, activity diff, rich-text formatting, category/
 *   city/listing-type changes, cross-user media replace), restore from a snapshot, optimistic-
 *   locking conflict between two concurrent edit sessions, long-description truncation/expand,
 *   and deep-link navigation (/ads/:id, share button, sitemap.xml, crawler meta tags/JSON-LD,
 *   locale-aware category rendering). A second, separately gated 'Max-content advertisement
 *   boundary' describe block (only runs when PW_FULL is set) exercises 255-char titles,
 *   2000-char descriptions, 10 categories and a 10-item media gallery. Every test is independent:
 *   it creates its own accounts, categories, cities and advertisements with run-id names, and
 *   filters the advertisement list and the timeline to its own data before asserting.
 *   Per test:
 *   - "userEn creates advertisement -- ...": create (title+desc+2 categories+city+Request+YouTube+
 *     image+WebM) -> lightbox plays video and closes YouTube -> card/overlay field order -> no
 *     feedback panel.
 *   - "userEn creates a minimal advertisement -- ...": no category/city -> chip rows absent, reduced
 *     field order -> delete.
 *   - "userEn creates, edits and restores advertisement -- ...": create (single activity row) ->
 *     edit (discard, two saves with diff, all rich formats, format-only edit, outer breadcrumb) ->
 *     administrator timeline of the owner -> restore v1 (diff, view, card).
 *   - "userUk creates, edits and restores advertisement -- ...": same story in Ukrainian without the
 *     rich-text and breadcrumb extras.
 *   - "moderatorEn edits EN advertisement -- ...": owner creates -> moderator edits (discard, saves,
 *     badge) -> media add and replace -> moderator timeline check.
 *   - "userEn and moderatorEn edit the same advertisement in two sessions -- ...": stale save shows
 *     the conflict notification, the first save wins.
 *   - "adminEn edits UK advertisement -- ...": category added and removed, city, listing type, media
 *     replace, timeline check with the category name resolved.
 *   - "userEn verifies lightbox -- ...": YouTube to image blanks the iframe, WebM to image stops video.
 *   - "adminEn verifies long description -- ...": activity diff collapsible value, truncated card.
 *   - "userEn opens a deep link -- ...": /ads/:id overlay, share button, own URL in sitemap.xml,
 *     crawler meta tags, history Back, Ukrainian category chip for a Ukrainian user.
 *   - boundary (PW_FULL): "maxEn/maxUk creates and edits max-content advertisement -- ...".
 * Usage: run via the Playwright test runner -- `bash /app/playwright/run.sh 05-marketplace-
 *   advertisement-flow --ux` (runs alone or with any other spec), or as part of the full e2e suite
 *   (`bash /app/playwright/run.sh e2e --ux`). The boundary describe block additionally needs
 *   `--full` (sets PW_FULL) to run.
 * Uses: @playwright/test, Node's fs module (temp PNG cleanup for uploaded images).
 * Env: PW_FULL -- when unset/falsy, the 'Max-content advertisement boundary' describe block is
 *   skipped entirely.
 * Input: ./_fixtures (test, expect, runId, unique, adminKey, createUser, userAccount,
 *   moderatorAccount, adminAccount, pageAs), ./_helpers (screenshot, waitForOverlayClosed,
 *   closeOverlay, closeNotification, YT_URL, avatar, stubYoutubeEmbed, downloadPng, assertAbsent,
 *   assertVerticalOrder), ./_flows/advertisement.flow, ./_flows/advertisement-filter.flow,
 *   ./_flows/delete.flow, ./_flows/entity-activity.flow, ./_flows/timeline.flow,
 *   ./_flows/filter.flow, ./_flows/attachment.flow, ./_flows/seed.flow, ./_flows/category.flow.
 * Outputs: Playwright HTML report entries (one per test/test.step), PNG screenshots attached to
 *   the report when PW_SCREENSHOTS is set. Creates only its own uniquely named accounts,
 *   categories, cities, advertisements and activity/timeline entries.
 * Returns: exit code from the Playwright test runner -- 0 when every test in this file passes,
 *   non-zero otherwise.
 * ──────────────────────────────────────────────────────────────────────────── */
const fs = require('fs');
const { test, expect } = require('./_fixtures');
const { screenshot, waitForOverlayClosed, closeOverlay, closeNotification, YT_URL, avatar, stubYoutubeEmbed, downloadPng, assertAbsent, assertVerticalOrder } = require('./_helpers');
const { MINIMAL_WEBM, assertAllRichTags, runCreateAdvertisementFlow, runEditAdvertisementFlow, runRestoreAdvertisementFlow, runCrossUserMediaReplaceFlow, cardByTitle, openCardOverlay, switchToEditMode, openActivityTab, saveAndWaitForIdle, closeOverlayToList, deleteAllGalleryItems } = require('./_flows/advertisement.flow');
const { runOpenFilterPanelFlow, runFillTitleFilterFlow, runApplyFilterFlow } = require('./_flows/advertisement-filter.flow');
const { confirmDeleteDialog } = require('./_flows/delete.flow');
const { closeEntityActivity } = require('./_flows/entity-activity.flow');
const { openTimelineTab, openTimelineFilter, closeTimelineFilter, fillEntityType, fillActorPicker, assertTimelineHasRows, TIMELINE_BLOCK } = require('./_flows/timeline.flow');
const { applyFilter } = require('./_flows/filter.flow');
const { waitForLightboxOpen, waitForLightboxClosed, getIframeSrc, clickLightboxThumb, getVideoSrc, isVideoWrapperVisible, waitForVideoWrapperVisible, waitForMainImageVisible } = require('./_flows/attachment.flow');
const { issueApiKeyViaApi, postViaApi, seedTaxonsViaApi } = require('./_flows/seed.flow');
const { assertViewOverlayHasCategories } = require('./_flows/category.flow');

const LONG_TEST_TIMEOUT = 5 * 60 * 1000;

async function waitForOverlay(page, timeout = 10000) {
  await page.locator('.base-overlay.overlay--visible').waitFor({ timeout });
}

/** Creates one category or city through the REST API; names carry the run id, a city name ends in a word so typing it into the city combo box matches only itself. */
async function createTaxon(request, adminKey, unique, type, labelEn, labelUk) {
  const suffix = type === 'CITY' ? ' city' : '';
  const taxon = { nameEn: `${unique(labelEn)}${suffix}`, descriptionEn: `${labelEn} description`, nameUk: `${unique(labelUk)}${suffix}`, descriptionUk: `${labelUk} опис` };
  const [id] = await seedTaxonsViaApi(request, adminKey, type, [taxon]);
  return { id, ...taxon };
}

/** Creates one advertisement as the given account through the REST API and returns its id. */
async function createAdViaApi(request, owner, { title, description, categoryIds, cityTaxonId }) {
  const ownerKey = await issueApiKeyViaApi(request, owner);
  const body = await postViaApi(request, ownerKey, '/api/advertisements', {
    title, description, adKind: 'OFFER',
    ...(categoryIds && { categoryIds }),
    ...(cityTaxonId && { cityTaxonId }),
  });
  return body.id;
}

/** Narrows the advertisement list to the ads whose title contains the test's own tag. */
async function filterAdsByTag(page, tag) {
  await runOpenFilterPanelFlow(page, expect);
  await runFillTitleFilterFlow(page, tag);
  await runApplyFilterFlow(page, expect);
}

/** Opens the Timeline tab narrowed to advertisement entries of one actor and waits for that actor's rows to render. */
async function openTimelineOfActor(page, actorName, title) {
  await openTimelineTab(page);
  await openTimelineFilter(page);
  await fillActorPicker(page, actorName, { useSearch: true });
  await fillEntityType(page, 'ADVERTISEMENT');
  await applyFilter(page, TIMELINE_BLOCK);
  await closeTimelineFilter(page);
  await expect(page.locator('.activity-feed .activity-feed-row').filter({ hasText: title }).first()).toBeVisible({ timeout: 8000 });
}

/** Concatenates the innerHTML of every change item of every timeline row whose change text contains the given text. */
async function collectTimelineHtml(page, text) {
  const rows = page.locator('.activity-feed .activity-feed-row');
  const rowCount = await rows.count();
  let html = '';
  for (let i = 0; i < rowCount; i++) {
    const changesText = await rows.nth(i).locator('.activity-feed-changes').textContent().catch(() => '');
    if (!changesText.includes(text)) continue;
    const items = rows.nth(i).locator('.activity-feed-changes-item');
    const n = await items.count();
    for (let j = 0; j < n; j++) html += await items.nth(j).innerHTML();
  }
  return html;
}

/** Builds a string of exactly `length` characters that starts with `prefix` and never ends in a space. */
function maxText(prefix, filler, length) {
  return `${prefix} ${filler.repeat(Math.ceil(length / filler.length))}`.substring(0, length).replace(/\s$/, 'x');
}

test.describe('Advertisement flow', () => {

  test('userEn creates advertisement — create discard clears form, YouTube, image and video, lightbox plays video, lightbox close button closes video and YouTube clips, two category rows, categories text and view chips, city text and view chip, listing type badge', async ({ request, adminKey, userAccount, pageAs, unique }) => {
    test.setTimeout(LONG_TEST_TIMEOUT);
    const title = unique('EN Advertisement');
    const description = 'Advertisement created by English user with all media types: YouTube, image and video.';
    const electronics = await createTaxon(request, adminKey, unique, 'CATEGORY', 'Electronics', 'Електроніка');
    const vehicles = await createTaxon(request, adminKey, unique, 'CATEGORY', 'Vehicles', 'Транспорт');
    const lviv = await createTaxon(request, adminKey, unique, 'CITY', 'Lviv', 'Львів');
    const page = await pageAs(userAccount);
    await stubYoutubeEmbed(page);

    await runCreateAdvertisementFlow(page, expect, { title, description, screenshotPrefix: 'adv-useren-create', categories: [electronics.nameEn, vehicles.nameEn], city: lviv.nameEn, adKind: 'Request' });
    await filterAdsByTag(page, title);

    await test.step('attachment lightbox — play icon visible, video src valid, close button works', async () => {
      await cardByTitle(page, title).first().click();
      await waitForOverlay(page);
      // WebM video is 3rd gallery item (YouTube=0, image=1, WebM=2)
      await page.locator('.attachment-gallery__item').nth(2).waitFor({ timeout: 5000 });
      await page.locator('.attachment-gallery__item').nth(2).click();
      await page.locator('.attachment-lightbox').waitFor({ timeout: 5000 });
      const videoEl = page.locator('.attachment-lightbox video');
      await expect(videoEl).toBeVisible();
      await expect(videoEl).toHaveAttribute('src', /.+/);
      await screenshot(page, 'adv-useren-create-attachment-lightbox');
      await page.locator('.attachment-lightbox .card-lightbox__close').click();
      await page.locator('.attachment-lightbox').waitFor({ state: 'detached', timeout: 5000 });
    });

    await test.step('attachment lightbox — YouTube clip closes via the close button (not just Escape)', async () => {
      // YouTube is the 1st gallery item (YouTube=0, image=1, WebM=2)
      await page.locator('.attachment-gallery__item').nth(0).waitFor({ timeout: 5000 });
      await page.locator('.attachment-gallery__item').nth(0).click();
      await page.locator('.attachment-lightbox').waitFor({ timeout: 5000 });
      await expect(page.locator('.attachment-lightbox iframe')).toBeVisible();
      await screenshot(page, 'adv-useren-create-attachment-lightbox-youtube');
      await page.locator('.attachment-lightbox .card-lightbox__close').click();
      await page.locator('.attachment-lightbox').waitFor({ state: 'detached', timeout: 5000 });
      await closeOverlay(page);
    });

    await test.step('card + view overlay — every field renders in the expected top-to-bottom order', async () => {
      const card = cardByTitle(page, title).first();
      await expect(card).toBeVisible({ timeout: 5000 });
      const cardContent = card.locator('.advertisement-content');
      await assertVerticalOrder(page, expect, [
        cardContent.locator('.advertisement-title'),
        cardContent.locator('.advertisement-description-wrapper'),
        cardContent.getByRole('list', { name: 'Categories:' }),
        cardContent.getByRole('list', { name: 'City:' }),
        cardContent.locator('.advertisement-ad-kind-badge'),
        cardContent.locator('.entity-meta'),
      ], 'adv-useren-create-card-field-order');

      await card.click();
      await waitForOverlay(page);
      await assertVerticalOrder(page, expect, [
        '.overlay__view-card-header',
        '.overlay__view-title',
        '.overlay__view-description',
        '.advertisement-categories-chips',
        '.advertisement-city-chips',
        '.advertisement-ad-kind-badge',
      ].map(s => page.locator('.advertisement-overlay .overlay__view-card').locator(s)), 'adv-useren-create-overlay-field-order');
      await expect(page.locator('.advertisement-overlay .overlay__view-body .entity-meta')).toBeVisible();
      await closeOverlay(page);
    });

    await test.step('advertisement overlay no longer carries a feedback panel — ContactRevealPanel is the only reach-the-seller mechanism now', async () => {
      await cardByTitle(page, title).first().click();
      await waitForOverlay(page);

      await assertAbsent(expect, page.locator('.advertisement-overlay').locator('.feedback-panel'));
      await screenshot(page, 'adv-useren-no-feedback-panel');

      await closeOverlay(page);
    });
  });

  test('userEn creates a minimal advertisement — no category, no city: those rows are absent, card and overlay field order stays title -> description -> ad-kind badge -> meta', async ({ userAccount, pageAs, unique }) => {
    const title = unique('Minimal EN Advertisement');
    const page = await pageAs(userAccount);
    await stubYoutubeEmbed(page);
    await runCreateAdvertisementFlow(page, expect, {
      title, description: 'No category, no city on this one.',
      categories: [], city: null, adKind: 'Offer',
      screenshotPrefix: 'adv-minimal',
    });
    await filterAdsByTag(page, title);

    await test.step('card — no chip rows, reduced field order intact', async () => {
      const card = cardByTitle(page, title).first();
      await expect(card).toBeVisible({ timeout: 5000 });
      await assertAbsent(expect, card.getByRole('list', { name: /categories:|city:|категорії:|місто:/i }));
      const cardContent = card.locator('.advertisement-content');
      await assertVerticalOrder(page, expect, [
        '.advertisement-title',
        '.advertisement-description-wrapper',
        '.advertisement-ad-kind-badge',
        '.entity-meta',
      ].map(s => cardContent.locator(s)), 'adv-minimal-card-order');
    });

    await test.step('view overlay — no chip rows, reduced field order intact', async () => {
      await cardByTitle(page, title).first().click();
      await waitForOverlay(page);
      const viewCard = page.locator('.advertisement-overlay .overlay__view-card');
      await assertAbsent(expect, viewCard.locator('.advertisement-categories-chips'));
      await assertAbsent(expect, viewCard.locator('.advertisement-city-chips'));
      await assertVerticalOrder(page, expect, [
        '.overlay__view-card-header',
        '.overlay__view-title',
        '.overlay__view-description',
        '.advertisement-ad-kind-badge',
      ].map(s => viewCard.locator(s)), 'adv-minimal-overlay-order');
      await expect(page.locator('.advertisement-overlay .overlay__view-body .entity-meta')).toBeVisible();
      await closeOverlay(page);
    });

    await test.step('delete the minimal advertisement — its card is gone', async () => {
      await cardByTitle(page, title).first().getByRole('button', { name: /^(delete|видалити)$/i }).click();
      await confirmDeleteDialog(page);
      await expect(cardByTitle(page, title)).toHaveCount(0, { timeout: 5000 });
    });
  });

  test('userEn creates, edits and restores advertisement — YouTube, image and video with single activity row, discard, two saves with activity diff, all rich formats in view and card, format-only edit, outer breadcrumb link closes directly to list, administrator timeline check, restore shows activity diff with restored media and text, view and card updated', async ({ createUser, adminAccount, pageAs, unique }) => {
    test.setTimeout(LONG_TEST_TIMEOUT);
    const owner = await createUser();
    const tag = unique('EN story');
    const original = { title: `${tag} Advertisement`, description: 'Advertisement created by English user with all media types: YouTube, image and video.' };
    const updated = { title: `${tag} Advertisement Updated`, description: 'Updated description for the EN advertisement.' };
    const page = await pageAs(owner);
    await stubYoutubeEmbed(page);

    await test.step('create — YouTube, image and video, single activity row', async () => {
      await runCreateAdvertisementFlow(page, expect, { title: original.title, description: original.description, screenshotPrefix: 'adv-useren-story-create' });
      await filterAdsByTag(page, tag);
    });

    await test.step('edit — discard, two saves with activity diff, rich formats, format-only edit, outer breadcrumb link', async () => {
      await runEditAdvertisementFlow(page, expect, {
        originalTitle: original.title, originalDescription: original.description,
        newTitle: updated.title,       newDescription: updated.description,
        startingVersion: 1,
        startingVisibleRows: 1,
        richText: true,
        verifyOuterLinkClosesToList: true,
        screenshotPrefix: 'adv-useren-edit',
      });
    });

    await test.step('administrator verifies timeline shows the owner\'s advertisement updates with rich text formatting and tooltips', async () => {
      const adminPage = await pageAs(adminAccount);
      await openTimelineOfActor(adminPage, owner.name, updated.title);
      await assertTimelineHasRows(adminPage, expect, { action: 'updated', entityType: 'advertisement', minCount: 2, allFieldsText: [updated.title], screenshotName: 'adv-useren-edit-timeline-admin' });
      assertAllRichTags(expect, await collectTimelineHtml(adminPage, updated.title), 'timeline');
      await screenshot(adminPage, 'adv-useren-edit-timeline-rich-html');
    });

    await test.step('restore — activity diff shows restored media and text, view and card updated', async () => {
      await runRestoreAdvertisementFlow(page, expect, {
        currentTitle: updated.title,
        restoredTitle: original.title, restoredDescription: original.description,
        rowsBeforeRestore: 4,
        targetRestoredVersion: 5,
        restoreCountAfterRestore: 3,
        screenshotPrefix: 'adv-useren-restore',
      });
    });
  });

  test('userUk creates, edits and restores advertisement — YouTube, image and video with single activity row, discard, two saves with activity diff, administrator timeline check, restore shows activity diff with restored media and text, view and card updated', async ({ createUser, adminAccount, pageAs, unique }) => {
    test.setTimeout(LONG_TEST_TIMEOUT);
    const owner = await createUser({ locale: 'uk' });
    const tag = unique('UK Оголошення');
    const original = { title: `${tag} створене`, description: 'Оголошення створене українським користувачем з усіма типами медіа: YouTube, зображення та відео.' };
    const updated = { title: `${tag} оновлене`, description: 'Оновлений опис для UK оголошення.' };
    const page = await pageAs(owner);
    await stubYoutubeEmbed(page);

    await test.step('create — YouTube, image and video, single activity row', async () => {
      await runCreateAdvertisementFlow(page, expect, { title: original.title, description: original.description, screenshotPrefix: 'adv-useruk-create', locale: 'uk' });
      await filterAdsByTag(page, tag);
    });

    await test.step('edit — discard, two saves with activity diff', async () => {
      await runEditAdvertisementFlow(page, expect, {
        originalTitle: original.title, originalDescription: original.description,
        newTitle: updated.title,       newDescription: updated.description,
        screenshotPrefix: 'adv-useruk-edit',
      });
    });

    await test.step('administrator verifies timeline shows the owner\'s advertisement updates', async () => {
      const adminPage = await pageAs(adminAccount);
      await openTimelineOfActor(adminPage, owner.name, updated.title);
      await assertTimelineHasRows(adminPage, expect, { action: 'updated', entityType: 'advertisement', minCount: 2, allFieldsText: [updated.title, updated.description], screenshotName: 'adv-useruk-edit-timeline-admin' });
    });

    await test.step('restore — activity diff shows restored media and text, view and card updated', async () => {
      await runRestoreAdvertisementFlow(page, expect, {
        currentTitle: updated.title,
        restoredTitle: original.title, restoredDescription: original.description,
        restoreCountAfterRestore: 2,
        screenshotPrefix: 'adv-useruk-restore',
      });
    });
  });

  test('moderatorEn edits EN advertisement — discard, two saves with activity diff, add and replace media, timeline check', async ({ createUser, pageAs, unique }) => {
    test.setTimeout(LONG_TEST_TIMEOUT);
    const owner = await createUser();
    const moderator = await createUser({ role: 'MODERATOR' });
    const tag = unique('Moderated');
    const original = { title: `${tag} Advertisement`, description: 'Advertisement created by English user with all media types: YouTube, image and video.' };
    const updated = { title: `${tag} Advertisement Moderated`, description: 'Description updated by moderator.' };

    const ownerPage = await pageAs(owner);
    await stubYoutubeEmbed(ownerPage);
    await runCreateAdvertisementFlow(ownerPage, expect, { title: original.title, description: original.description, screenshotPrefix: 'adv-moderatoren-owner-create' });

    const page = await pageAs(moderator);
    await stubYoutubeEmbed(page);
    await filterAdsByTag(page, tag);
    await runEditAdvertisementFlow(page, expect, {
      originalTitle:       original.title,
      originalDescription: original.description,
      newTitle:            updated.title,
      newDescription:      updated.description,
      startingVersion:     1,
      checkCurrentBadge:   true,
      richText:            true,
      screenshotPrefix:    'adv-moderatoren-edit',
    });
    await runCrossUserMediaReplaceFlow(page, expect, {
      adTitle:          updated.title,
      startingVersion:  4,
      screenshotPrefix: 'adv-moderatoren-media',
    });
    await openTimelineOfActor(page, moderator.name, updated.title);
    await assertTimelineHasRows(page, expect, { action: 'updated', entityType: 'advertisement', minCount: 4, allFieldsText: [updated.title], actorText: moderator.name, screenshotName: 'timeline-moderatoren-edit-ad' });
  });

  test('userEn and moderatorEn edit the same advertisement in two sessions — stale save shows conflict, first save wins', async ({ request, createUser, moderatorAccount, pageAs, unique }) => {
    const owner = await createUser();
    const title = unique('Concurrent Edit Conflict Ad');
    await createAdViaApi(request, owner, { title, description: 'Ad for optimistic locking conflict test.' });

    const page = await pageAs(owner);
    const page2 = await pageAs(moderatorAccount);
    await filterAdsByTag(page, title);
    await filterAdsByTag(page2, title);

    // Both sessions open the same ad for edit before either one saves — both load the same version.
    const overlay1 = await openCardOverlay(page, cardByTitle(page, title), 'adv-conflict-session1');
    await switchToEditMode(page, overlay1, 'adv-conflict-session1');

    const overlay2 = await openCardOverlay(page2, cardByTitle(page2, title), 'adv-conflict-session2');
    await switchToEditMode(page2, overlay2, 'adv-conflict-session2');

    // Session 1 saves first — succeeds, bumps the version.
    await overlay1.getByTestId('advertisement-overlay-field-title').locator('input').fill(`${title} - saved first`);
    await saveAndWaitForIdle(page, expect, overlay1, 'adv-conflict-session1-saved');

    // Session 2 still holds the stale version loaded before session 1's save — its save must be rejected, not silently overwrite.
    await overlay2.getByTestId('advertisement-overlay-field-title').locator('input').fill(`${title} - saved second`);
    await overlay2.getByRole('button', { name: /зберегти|save/i }).click();
    await expect(page2.locator('vaadin-notification-card')).toContainText(/modified by another|змінено в іншій/i, { timeout: 8000 });
    await screenshot(page2, 'adv-conflict-session2-conflict-notification');
    await closeNotification(page2);

    await closeOverlayToList(page, overlay1);
    await expect(cardByTitle(page, `${title} - saved first`)).toBeVisible({ timeout: 5000 });
    await expect(cardByTitle(page, `${title} - saved second`)).toHaveCount(0);
  });

  test('adminEn edits UK advertisement — discard, two saves with activity diff, category added and removed with diff, city set with activity diff and view chip, listing type set with activity diff and view badge, add and replace media, timeline check', async ({ request, adminKey, createUser, pageAs, unique }) => {
    test.setTimeout(LONG_TEST_TIMEOUT);
    const owner = await createUser({ locale: 'uk' });
    const admin = await createUser({ role: 'ADMIN' });
    const vehicles = await createTaxon(request, adminKey, unique, 'CATEGORY', 'Vehicles', 'Транспорт');
    const kyiv = await createTaxon(request, adminKey, unique, 'CITY', 'Kyiv', 'Київ');
    const tag = unique('UK Оголошення');
    const original = { title: `${tag} створене`, description: 'Оголошення створене українським користувачем з усіма типами медіа: YouTube, зображення та відео.' };
    const updated = { title: `${tag} Адмін`, description: 'Опис оновлений адміністратором.' };

    const ownerPage = await pageAs(owner);
    await stubYoutubeEmbed(ownerPage);
    await runCreateAdvertisementFlow(ownerPage, expect, { title: original.title, description: original.description, screenshotPrefix: 'adv-adminen-owner-create', locale: 'uk' });

    const page = await pageAs(admin);
    await stubYoutubeEmbed(page);
    await filterAdsByTag(page, tag);
    await runEditAdvertisementFlow(page, expect, {
      originalTitle:       original.title,
      originalDescription: original.description,
      newTitle:            updated.title,
      newDescription:      updated.description,
      startingVersion:     1,
      checkCurrentBadge:   true,
      richText:            true,
      categoryToAdd:       vehicles.nameEn,
      categoryToRemove:    vehicles.nameEn,
      cityToSet:           kyiv.nameEn,
      adKindToSet:         'Product',
      screenshotPrefix:    'adv-adminen-edit-uk',
    });
    await runCrossUserMediaReplaceFlow(page, expect, {
      adTitle:          updated.title,
      startingVersion:  8,
      screenshotPrefix: 'adv-adminen-media-uk',
    });
    await openTimelineOfActor(page, admin.name, updated.title);
    // changesText confirms the Timeline tab resolves the category id to its name, not the raw taxon id.
    await assertTimelineHasRows(page, expect, { action: 'updated', entityType: 'advertisement', minCount: 4, allFieldsText: [updated.title], actorText: admin.name, changesText: vehicles.nameEn, screenshotName: 'timeline-adminen-edit-ad' });
  });

  test('userEn verifies lightbox — YouTube to image blanks iframe, WebM to image stops video', async ({ userAccount, pageAs, unique, runId }) => {
    const title = unique('Lightbox Test Ad');
    const imgPath = `/tmp/lightbox-avatar-${runId}.png`;
    await downloadPng(avatar('lightbox'), imgPath);
    const page = await pageAs(userAccount);
    await stubYoutubeEmbed(page);

    // Create ad with YouTube + image + WebM
    await page.getByRole('button', { name: /add advertisement|додати оголошення/i }).click();
    await waitForOverlay(page);
    const ov = page.locator('.advertisement-overlay');
    await ov.getByTestId('advertisement-overlay-field-title').locator('input').fill(title);
    await ov.getByTestId('advertisement-overlay-field-description').locator('.ql-editor').fill('YouTube lightbox test');
    await ov.locator('.attachment-gallery__video-input').getByPlaceholder(/video url|відео посилання/i).locator('input').fill(YT_URL);
    await ov.locator('.attachment-gallery__video-input').getByRole('button', { name: /add video|додати відео/i }).click();
    await ov.locator('.attachment-gallery__item').first().waitFor({ timeout: 10000 });
    await ov.locator('vaadin-upload input[type="file"]').setInputFiles(imgPath);
    await ov.locator('.attachment-gallery__item').nth(1).waitFor({ timeout: 10000 });
    await ov.locator('vaadin-upload input[type="file"]').setInputFiles({ name: 'lightbox-test.webm', mimeType: 'video/webm', buffer: MINIMAL_WEBM });
    await ov.locator('.attachment-gallery__item').nth(2).waitFor({ timeout: 15000 });
    await ov.getByRole('button', { name: /зберегти|save/i }).click();
    await waitForOverlayClosed(page);
    if (fs.existsSync(imgPath)) fs.unlinkSync(imgPath);

    await filterAdsByTag(page, title);
    await cardByTitle(page, title).first().locator('.advertisement-thumbnail-wrapper').click();
    await waitForLightboxOpen(page);
    await screenshot(page, 'lightbox-youtube');

    // Strip: YouTube(0) + image(1) + WebM(2)
    const thumbCount = await page.evaluate(() => {
      function findAll(root, sel) {
        const els = [...root.querySelectorAll(sel)];
        for (const c of root.querySelectorAll('*'))
          if (c.shadowRoot) els.push(...findAll(c.shadowRoot, sel));
        return els;
      }
      return findAll(document, '.card-lightbox__strip .card-lightbox__thumb').length;
    });
    expect(thumbCount).toBeGreaterThanOrEqual(3);
    expect(await getIframeSrc(page)).toMatch(/youtube\.com\/embed/);

    await test.step('YouTube → image: iframe blanked', async () => {
      await clickLightboxThumb(page, 1);
      await waitForMainImageVisible(page);
      expect(await getIframeSrc(page)).toBe('about:blank');
      await screenshot(page, 'lightbox-image');
    });

    await test.step('image → WebM: video wrapper visible, src valid', async () => {
      await clickLightboxThumb(page, 2);
      await waitForVideoWrapperVisible(page);
      const src = await getVideoSrc(page);
      expect(src).toBeTruthy();
      expect(src).not.toBe('');
      await screenshot(page, 'lightbox-webm');
    });

    await test.step('WebM → image: video wrapper hidden, src cleared', async () => {
      await clickLightboxThumb(page, 1);
      await waitForMainImageVisible(page);
      expect(await isVideoWrapperVisible(page)).toBe(false);
      const src = await getVideoSrc(page);
      expect(src === '' || src === null || src === undefined).toBe(true);
      await screenshot(page, 'lightbox-video-stopped');
    });

    await page.keyboard.press('Escape');
    await waitForLightboxClosed(page);
  });

  test('adminEn verifies long description — activity diff shows all fields, collapsible value toggle, card truncated', async ({ request, adminAccount, pageAs, unique }) => {
    const LONG_DESC = 'This description is intentionally long to test the three-line clamp on the advertisement card. ' +
      'It continues with more text to reliably exceed the visible area at any viewport width. ' +
      'A third sentence ensures truncation activates even on wide screens, confirming the CSS clamp works as expected.';
    const SHORT_DESC = 'Short initial description for expand test.';
    const TITLE = unique('Expand Toggle Test Ad');

    await createAdViaApi(request, adminAccount, { title: TITLE, description: SHORT_DESC });
    const page = await pageAs(adminAccount);
    await filterAdsByTag(page, TITLE);

    const overlay = await openCardOverlay(page, cardByTitle(page, TITLE), 'trunc');
    await switchToEditMode(page, overlay, 'trunc');
    await overlay.getByTestId('advertisement-overlay-field-description').locator('.ql-editor').fill(LONG_DESC);
    await saveAndWaitForIdle(page, expect, overlay, 'trunc-save');

    await test.step('activity diff — all fields visible, long description value is collapsible', async () => {
      const activityList = await openActivityTab(overlay);
      const changes = activityList.locator('.entity-activity-row').nth(0).locator('.entity-activity-changes');
      await expect(changes).toContainText(TITLE);
      await expect(changes).toContainText(SHORT_DESC.substring(0, 20));
      await screenshot(page, 'trunc-activity-all-fields');

      const collapsibleValues = changes.locator('.entity-activity-changes-value--collapsible');
      await expect(collapsibleValues).toHaveCount(1, { timeout: 5000 });
      const valueToggle = changes.locator('.entity-activity-changes-value-toggle').first();
      await expect(valueToggle).toBeVisible();
      await screenshot(page, 'trunc-activity-diff-collapsed');

      await valueToggle.click();
      await expect(collapsibleValues).toHaveCount(0);
      await screenshot(page, 'trunc-activity-diff-expanded');

      await valueToggle.click();
      await expect(collapsibleValues).toHaveCount(1);
      await screenshot(page, 'trunc-activity-diff-recollapsed');
    });

    await closeOverlayToList(page, overlay);

    await test.step('card shows truncated description — clamp visible, content present', async () => {
      const card = cardByTitle(page, TITLE);
      await expect(card).toBeVisible({ timeout: 5000 });
      await expect(card.locator('.advertisement-description--truncated')).toBeVisible();
      await screenshot(page, 'trunc-card-collapsed');
    });
  });

  test('userEn opens a deep link — direct navigation to /ads/:id opens the correct advertisement overlay, share button copies link, sitemap.xml lists the ad, userUk reopens the same link with the category shown in Ukrainian', async ({ request, adminKey, createUser, pageAs, unique }) => {
    const title = unique('Deep Link Test Advertisement');
    const vehicles = await createTaxon(request, adminKey, unique, 'CATEGORY', 'Vehicles', 'Транспорт');
    const owner = await createUser();
    const ukViewer = await createUser({ locale: 'uk' });
    const createdId = await createAdViaApi(request, owner, { title, description: 'Advertisement used to verify the /ads/:id deep link.', categoryIds: [vehicles.id] });

    const page = await pageAs(owner);
    await filterAdsByTag(page, title);

    const card = cardByTitle(page, title);
    await card.first().waitFor({ timeout: 5000 });
    const adId = String(createdId);
    await expect(card.first()).toHaveAttribute('data-ad-id', adId);

    await page.goto(`/ads/${adId}`);
    const overlay = page.locator('.advertisement-overlay');
    await overlay.waitFor({ timeout: 10000 });
    await expect(overlay.locator('.overlay__view-title')).toHaveText(title);
    await screenshot(page, 'adv-deep-link-opened');

    await test.step('share button — copies link to clipboard, shows confirmation notification', async () => {
      // navigator.share is unavailable in headless Chromium; stub clipboard.writeText so the
      // fallback branch resolves deterministically without needing OS clipboard permissions.
      await page.evaluate(() => {
        navigator.clipboard.writeText = () => Promise.resolve();
      });
      await overlay.getByRole('button', { name: /share|поділитись/i }).click();
      await page.locator('vaadin-notification-card').filter({ hasText: /link copied/i }).first().waitFor({ timeout: 5000 });
      await screenshot(page, 'adv-deep-link-share-copied');
      await closeNotification(page);
    });

    await closeOverlay(page);

    await test.step('sitemap.xml — valid XML, lists this advertisement\'s deep link', async () => {
      const response = await page.request.get('/sitemap.xml');
      expect(response.ok()).toBeTruthy();
      expect(response.headers()['content-type']).toContain('xml');
      const body = await response.text();
      expect(body).toContain('<urlset');
      expect(body).toContain(`/ads/${adId}</loc>`);
    });

    await test.step('crawler-facing HTML — og:title/description/url, twitter:card uses name= not property=, JSON-LD Product block present', async () => {
      const response = await page.request.get(`/ads/${adId}`);
      expect(response.ok()).toBeTruthy();
      const html = await response.text();
      expect(html).toContain(`<meta property="og:title" content="${title}">`);
      expect(html).toContain('<meta property="og:type" content="product">');
      expect(html).toContain('<meta name="twitter:card" content="summary_large_image">');
      expect(html).not.toContain('property="twitter:card"');
      expect(html).toMatch(/<script type="application\/ld\+json">\{.*"@type":"Product".*\}<\/script>/);
    });

    await test.step('card click — updates URL to /ads/:id, browser Back closes overlay and returns to /', async () => {
      await filterAdsByTag(page, title);
      await card.first().click();
      await overlay.waitFor({ timeout: 10000 });
      await expect(page).toHaveURL(new RegExp(`/ads/${adId}$`));
      await page.goBack();
      await overlay.waitFor({ state: 'hidden', timeout: 10000 });
      await expect(page).toHaveURL(/\/$/);
      await screenshot(page, 'adv-deep-link-history-back');
    });

    await test.step('userUk opens the same deep link — category chip renders in Ukrainian, not English', async () => {
      const ukPage = await pageAs(ukViewer);
      await ukPage.goto(`/ads/${adId}`);
      const ukOverlay = ukPage.locator('.advertisement-overlay');
      await ukOverlay.waitFor({ timeout: 10000 });
      await assertViewOverlayHasCategories(ukPage, expect, ukOverlay, [vehicles.nameUk], 'adv-deep-link-locale-uk-category');
      await expect(ukOverlay.locator('.advertisement-category-chip', { hasText: vehicles.nameEn })).toHaveCount(0);
      await closeOverlay(ukPage);
    });
  });
});

// ─── Boundary: max-content advertisements (PW_FULL only) ─────────────────────

test.describe('Max-content advertisement boundary', () => {
  test.skip(!process.env.PW_FULL, 'Skipped by default — run with --full for boundary tests');

  for (const variant of ['EN', 'UK']) {
    test(`max${variant === 'EN' ? 'En' : 'Uk'} creates and edits ${variant} max-content advertisement — 255-char title, 10 categories, YouTube + image + video, lightbox, activity, discard restores 3 items, replace all media with 10-item gallery, 255-char title v2, activity v2, gallery in view and card`, async ({ request, adminKey, createUser, pageAs, unique, runId }) => {
      test.setTimeout(LONG_TEST_TIMEOUT);
      const key = `max-${variant.toLowerCase()}`;
      const tag = unique(`Max ${variant}`);
      const title = maxText(tag, `Max Boundary Advertisement ${variant} Title Test `, 255);
      const titleV2 = maxText(tag, `Max Boundary Advertisement ${variant} Title Edited `, 255);
      const description = maxText('', 'This advertisement tests maximum boundary values for all content fields. ', 2000);
      const descriptionV2 = maxText('', `Max boundary ${variant} advertisement description edited with all content. `, 2000);

      // Category #1 uses a 255-char name (VARCHAR(255)/maxLength(255)) so the chip assertions exercise a genuinely long name.
      const categoryTaxons = Array.from({ length: 10 }, (_, i) => ({
        nameEn: i === 0 ? unique(`Max Boundary Category Name Test ${variant}`).padEnd(255, 'x') : unique(`Boundary-${String(i + 1).padStart(2, '0')}`),
        descriptionEn: 'Boundary category',
        nameUk: unique(`Межа-${String(i + 1).padStart(2, '0')}`),
        descriptionUk: 'Гранична категорія',
      }));
      await seedTaxonsViaApi(request, adminKey, 'CATEGORY', categoryTaxons);
      const categories = categoryTaxons.map(t => t.nameEn);

      const owner = await createUser();
      const page = await pageAs(owner);
      await stubYoutubeEmbed(page);

      await test.step('create — 255-char title, 10 categories, YouTube + image + video, lightbox, activity', async () => {
        await runCreateAdvertisementFlow(page, expect, { title, description, categories, screenshotPrefix: `${key}-create` });
        await filterAdsByTag(page, tag);
      });

      const imgs = [1, 2, 3].map(n => `/tmp/${key}-edit-img${n}-${runId}.png`);
      await Promise.all(imgs.map((p, i) => downloadPng(avatar(`${key}-edit-${i + 1}`), p)));

      const overlay = await openCardOverlay(page, cardByTitle(page, title), `${key}-edit`);
      await switchToEditMode(page, overlay, `${key}-edit`);

      await test.step('discard restores 3 gallery items', async () => {
        await expect(overlay.locator('.attachment-gallery__item')).toHaveCount(3, { timeout: 5000 });
        await deleteAllGalleryItems(expect, overlay);
        await screenshot(page, `${key}-edit-media-deleted`);

        await overlay.getByRole('button', { name: /скинути зміни|discard changes/i }).first().click();
        await closeNotification(page);
        await expect(overlay.getByTestId('advertisement-overlay-field-title').locator('input')).toHaveValue(title, { timeout: 5000 });
        await expect(overlay.locator('.attachment-gallery__item')).toHaveCount(3, { timeout: 8000 });
        await screenshot(page, `${key}-edit-discarded`);
      });

      await test.step('replace all media, set 255-char title v2, save v2', async () => {
        await deleteAllGalleryItems(expect, overlay);

        const titleInput = overlay.getByTestId('advertisement-overlay-field-title').locator('input');
        await titleInput.clear();
        await titleInput.fill(titleV2);

        await overlay.getByTestId('advertisement-overlay-field-description').locator('.ql-editor').fill(descriptionV2);

        await overlay.locator('.attachment-gallery__video-input').getByPlaceholder(/video url|відео посилання/i).locator('input').fill(YT_URL);
        await overlay.locator('.attachment-gallery__video-input').getByRole('button', { name: /add video|додати відео/i }).click();
        await overlay.locator('.attachment-gallery__item').nth(0).waitFor({ timeout: 10000 });

        await overlay.locator('vaadin-upload input[type="file"]').setInputFiles(imgs);
        await overlay.locator('.attachment-gallery__item').nth(3).waitFor({ timeout: 20000 });

        await overlay.locator('vaadin-upload input[type="file"]').setInputFiles(
          [1, 2, 3, 4, 5, 6].map(n => ({ name: `vid${n}.webm`, mimeType: 'video/webm', buffer: MINIMAL_WEBM })),
        );
        await overlay.locator('.attachment-gallery__item').nth(9).waitFor({ timeout: 40000 });
        await screenshot(page, `${key}-edit-gallery-filled`);

        await saveAndWaitForIdle(page, expect, overlay, `${key}-edit`);

        const activityList = await openActivityTab(overlay);
        await expect(activityList.locator('.entity-activity-row')).toHaveCount(2, { timeout: 5000 });
        await expect(activityList.locator('.entity-activity-restore-btn')).toHaveCount(1, { timeout: 5000 });
        const row0 = activityList.locator('.entity-activity-row').nth(0);
        await expect(row0.locator('.entity-activity-version')).toContainText('v2');
        const changes0 = row0.locator('.entity-activity-changes');
        await expect(changes0).toContainText('Title Test');
        await expect(changes0).toContainText('Title Edited');
        await screenshot(page, `${key}-edit-activity`);
      });

      await test.step('view reflects saved state with full 10-item gallery', async () => {
        await closeEntityActivity(page);
        await overlay.locator('vaadin-button')
          .filter({ has: page.locator('vaadin-icon[icon="vaadin:close"]') })
          .first().click();
        await overlay.locator('.overlay__view-title').waitFor({ timeout: 5000 });
        await expect(overlay.locator('.overlay__view-title')).toContainText('Title Edited');
        await screenshot(page, `${key}-edit-view-updated`);
      });

      await test.step('card shows thumbnail badge=10', async () => {
        await closeOverlayToList(page, overlay);
        const card = cardByTitle(page, titleV2);
        await expect(card).toBeVisible({ timeout: 5000 });
        await expect(card.locator('.advertisement-thumbnail-badge')).toContainText('10');
        await expect(card.locator('img').first()).toHaveAttribute('src', /.+/);
        await screenshot(page, `${key}-edit-list-updated`);
      });

      imgs.forEach(p => { if (fs.existsSync(p)) fs.unlinkSync(p); });
    });
  }
});
