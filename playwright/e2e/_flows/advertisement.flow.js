const fs = require('fs');
const { test, screenshot, downloadPng, closeNotification } = require('../_helpers');
const { clickLightboxThumb, getVideoSrc, waitForVideoWrapperVisible } = require('./attachment.flow');

const YT_URL = 'https://www.youtube.com/watch?v=dQw4w9WgXcQ';

const avatar = seed =>
  `https://api.dicebear.com/9.x/adventurer/png?seed=${seed}&size=256&backgroundColor=b6e3f4`;

// Minimal valid WebM container — passes MIME type check and uploads without validation errors
const MINIMAL_WEBM = Buffer.from([
  0x1A, 0x45, 0xDF, 0xA3, 0x9F,
  0x42, 0x86, 0x81, 0x01,
  0x42, 0xF7, 0x81, 0x01,
  0x42, 0xF2, 0x81, 0x04,
  0x42, 0xF3, 0x81, 0x08,
  0x42, 0x82, 0x84, 0x77, 0x65, 0x62, 0x6D,
  0x42, 0x87, 0x81, 0x04,
  0x42, 0x85, 0x81, 0x02,
  0x18, 0x53, 0x80, 0x67, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
  0x1F, 0x43, 0xB6, 0x75, 0x81, 0x00,
]);

// ── helpers ───────────────────────────────────────────────────────────────────

function cardByTitle(page, title) {
  return page.locator('.advertisement-card')
    .filter({ has: page.locator('.advertisement-title', { hasText: new RegExp(`^${title}$`) }) });
}

async function openCardOverlay(page, card, screenshotPrefix) {
  await card.waitFor({ timeout: 5000 });
  await card.first().click();
  const overlay = page.locator('.advertisement-overlay');
  await overlay.waitFor({ timeout: 5000 });
  await screenshot(page, `${screenshotPrefix}-view-opened`);
  return overlay;
}

async function switchToEditMode(page, overlay, screenshotPrefix) {
  await overlay.locator('vaadin-button').filter({ hasText: /edit|редагувати/i }).first().click();
  await overlay.locator('[data-testid="advertisement-overlay-field-title"] input').waitFor({ timeout: 5000 });
  if (screenshotPrefix) await screenshot(page, `${screenshotPrefix}-edit-opened`);
}

async function saveAndWaitForIdle(page, expect, overlay, screenshotPrefix) {
  await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
  await expect(
    overlay.locator('vaadin-button').filter({ hasText: /скинути зміни|discard changes/i }).first()
  ).toBeDisabled({ timeout: 8000 });
  await closeNotification(page);
  await screenshot(page, `${screenshotPrefix}-saved`);
}

async function openActivityTab(overlay) {
  await overlay.locator('.adv-form-tabs vaadin-tab').filter({ hasText: /activ|активн/i }).click();
  const activityList = overlay.locator('.entity-activity-list');
  await activityList.waitFor({ timeout: 5000 });
  return activityList;
}

async function closeEditAndVerifyView(page, expect, overlay, expectedTitle, expectedDescription, screenshotName) {
  const closeBtn = page.locator('.advertisement-overlay vaadin-button')
    .filter({ has: page.locator('vaadin-icon[icon="vaadin:close"]') })
    .first();
  await closeBtn.click();
  await overlay.locator('.overlay__view-title').waitFor({ timeout: 5000 });
  await expect(overlay.locator('.overlay__view-title')).toContainText(expectedTitle);
  await expect(overlay.locator('.overlay__view-description')).toContainText(expectedDescription);
  await screenshot(page, screenshotName);
}

async function closeOverlayToList(page, overlay) {
  await overlay.locator('.overlay__breadcrumb-back').click();
  await page.locator('.base-overlay.overlay--visible').waitFor({ state: 'hidden', timeout: 5000 });
}

async function verifyCardInList(page, expect, card, description, mediaCount, screenshotName) {
  await expect(card).toBeVisible({ timeout: 5000 });
  await expect(card.locator('.advertisement-description')).toContainText(description.substring(0, 30));
  if (mediaCount === 0) {
    await expect(card.locator('.advertisement-thumbnail-badge')).toHaveCount(0);
    await expect(card.locator('.advertisement-thumbnail-wrapper')).toHaveCount(0);
  } else if (mediaCount != null) {
    await expect(card.locator('.advertisement-thumbnail-badge')).toContainText(String(mediaCount));
    await expect(card.locator('img').first()).toHaveAttribute('src', /.+/);
  }
  await screenshot(page, screenshotName);
}

async function deleteAllGalleryItems(expect, overlay) {
  const count = await overlay.locator('.attachment-gallery__item').count();
  for (let i = 0; i < count; i++) {
    await overlay.locator('.attachment-gallery__item .attachment-gallery__delete-btn').first().click();
    await expect(overlay.locator('.attachment-gallery__item')).toHaveCount(count - i - 1, { timeout: 5000 });
  }
}

async function assertSingleCurrentBadge(page, expect, overlay) {
  const activityList = await openActivityTab(overlay);
  const badgeCount = await activityList.locator('.entity-activity-current-badge').count();
  expect(badgeCount).toBe(1);
}

async function assertLatestActivityVersion(page, overlay, expect, version, screenshotName) {
  const activityList = await openActivityTab(overlay);
  await expect(activityList.locator('.entity-activity-row').nth(0).locator('.entity-activity-version'))
    .toContainText(`v${version}`);
  await screenshot(page, screenshotName);
}

async function openLightboxAndNavigate(page, card, screenshotPrefix) {
  await card.locator('.advertisement-thumbnail-wrapper').click();
  await page.locator('.card-lightbox__content').waitFor({ timeout: 5000 });
  await screenshot(page, `${screenshotPrefix}-lightbox-first`);
  await page.locator('.card-lightbox__nav').nth(1).click();
  await screenshot(page, `${screenshotPrefix}-lightbox-next`);
  // Verify WebM video item (thumb index 2 — YouTube=0, image=1, WebM=2)
  await clickLightboxThumb(page, 2);
  await waitForVideoWrapperVisible(page);
  const videoSrc = await getVideoSrc(page);
  if (!videoSrc) throw new Error('Card lightbox: no video src for WebM item');
  await screenshot(page, `${screenshotPrefix}-lightbox-video`);
  await page.locator('.card-lightbox__close').click({ force: true });
  await page.locator('.card-lightbox__content').waitFor({ state: 'hidden', timeout: 5000 });
}

// ── flows ─────────────────────────────────────────────────────────────────────

async function runCreateAdvertisementFlow(page, expect, { title, description, screenshotPrefix }) {
  const imagePath = `/tmp/${screenshotPrefix}-image.png`;
  await downloadPng(avatar(screenshotPrefix), imagePath);

  await page.locator('.add-advertisement-button').click();
  const overlay = page.locator('.advertisement-overlay');
  await overlay.waitFor({ timeout: 5000 });

  await overlay.locator('[data-testid="advertisement-overlay-field-title"] input').fill(title);
  await overlay.locator('[data-testid="advertisement-overlay-field-description"] textarea').fill(description);
  await screenshot(page, `${screenshotPrefix}-form-filled`);

  await overlay.locator('.attachment-gallery__video-input input').fill(YT_URL);
  await overlay.locator('.attachment-gallery__video-input vaadin-button').click();
  await overlay.locator('.attachment-gallery__item').first().waitFor({ timeout: 10000 });
  await screenshot(page, `${screenshotPrefix}-youtube-added`);

  await overlay.locator('vaadin-upload input[type="file"]').setInputFiles(imagePath);
  await overlay.locator('.attachment-gallery__item').nth(1).waitFor({ timeout: 10000 });
  await screenshot(page, `${screenshotPrefix}-image-added`);

  await overlay.locator('vaadin-upload input[type="file"]').setInputFiles({
    name: 'test-video.webm',
    mimeType: 'video/webm',
    buffer: MINIMAL_WEBM,
  });
  await overlay.locator('.attachment-gallery__item').nth(2).waitFor({ timeout: 15000 });
  await screenshot(page, `${screenshotPrefix}-video-added`);

  await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
  await page.locator('.base-overlay.overlay--visible').waitFor({ state: 'hidden', timeout: 10000 });
  await screenshot(page, `${screenshotPrefix}-saved`);

  const card = cardByTitle(page, title);
  await verifyCardInList(page, expect, card, description, 3, `${screenshotPrefix}-card`);
  await openLightboxAndNavigate(page, card, screenshotPrefix);

  await test.step('single history entry has no restore button', async () => {
    const freshCard = cardByTitle(page, title);
    const overlay = await openCardOverlay(page, freshCard, `${screenshotPrefix}-single-history`);
    await switchToEditMode(page, overlay, null);
    const activityList = await openActivityTab(overlay);
    await expect(activityList.locator('.entity-activity-row')).toHaveCount(1, { timeout: 5000 });
    await expect(activityList.locator('.entity-activity-restore-btn')).toHaveCount(0);
    await closeOverlayToList(page, overlay);
  });
}

async function runEditAdvertisementFlow(page, expect, { originalTitle, originalDescription, newTitle, newDescription, startingVersion = 1, checkCurrentBadge = false, screenshotPrefix }) {
  const editVersion       = startingVersion + 1;
  const textEditVersion   = startingVersion + 2;
  const rowsAfterEdit     = editVersion;
  const rowsAfterTextEdit = textEditVersion;

  const overlay = await openCardOverlay(page, cardByTitle(page, originalTitle), screenshotPrefix);
  await switchToEditMode(page, overlay, screenshotPrefix);

  await test.step('discard restores state', async () => {
    const count = await overlay.locator('.attachment-gallery__item').count();
    await deleteAllGalleryItems(expect, overlay);
    await screenshot(page, `${screenshotPrefix}-media-deleted`);

    await overlay.locator('vaadin-button').filter({ hasText: /скинути зміни|discard changes/i }).first().click();
    await closeNotification(page);
    await expect(overlay.locator('[data-testid="advertisement-overlay-field-title"] input')).toHaveValue(originalTitle, { timeout: 5000 });
    await expect(overlay.locator('[data-testid="advertisement-overlay-field-description"] textarea')).toHaveValue(originalDescription, { timeout: 5000 });
    await expect(overlay.locator('.attachment-gallery__item')).toHaveCount(count, { timeout: 8000 });
    await screenshot(page, `${screenshotPrefix}-discarded`);

    // Adding media to existing gallery then discarding must also restore original count
    await overlay.locator('.attachment-gallery__video-input input').fill(YT_URL);
    await overlay.locator('.attachment-gallery__video-input vaadin-button').click();
    await expect(overlay.locator('.attachment-gallery__item')).toHaveCount(count + 1, { timeout: 10000 });
    await screenshot(page, `${screenshotPrefix}-media-added-to-existing`);

    const discardBtn = overlay.locator('vaadin-button').filter({ hasText: /скинути зміни|discard changes/i }).first();
    await expect(discardBtn).toBeEnabled({ timeout: 5000 });
    await discardBtn.click();
    await closeNotification(page);
    await expect(overlay.locator('.attachment-gallery__item')).toHaveCount(count, { timeout: 8000 });
    await screenshot(page, `${screenshotPrefix}-discarded-add`);
  });

  await test.step(`edit and save v${editVersion} — delete all media, update title and description`, async () => {
    await deleteAllGalleryItems(expect, overlay);

    const titleInput = overlay.locator('[data-testid="advertisement-overlay-field-title"] input');
    await titleInput.clear();
    await titleInput.fill(newTitle);

    const descInput = overlay.locator('[data-testid="advertisement-overlay-field-description"] textarea');
    await descInput.clear();
    await descInput.fill(newDescription);
    await screenshot(page, `${screenshotPrefix}-form-updated`);

    await saveAndWaitForIdle(page, expect, overlay, screenshotPrefix);

    const activityList = await openActivityTab(overlay);
    await expect(activityList.locator('.entity-activity-row')).toHaveCount(rowsAfterEdit, { timeout: 5000 });

    const row0 = activityList.locator('.entity-activity-row').nth(0);
    await expect(row0.locator('.entity-activity-action')).toContainText(/updated|оновлено/i);
    await expect(row0.locator('.entity-activity-version')).toContainText(`v${editVersion}`);
    const changes0 = row0.locator('.entity-activity-changes');
    await expect(changes0).toContainText(originalTitle);
    await expect(changes0).toContainText(newTitle);
    await expect(changes0).toContainText(originalDescription.substring(0, 20));
    await expect(changes0).toContainText(newDescription);
    await expect(changes0).toContainText('YouTube-dQw4w9WgXcQ');
    await expect(changes0).toContainText('→');

    const lastRow = activityList.locator('.entity-activity-row').nth(rowsAfterEdit - 1);
    await expect(lastRow.locator('.entity-activity-action')).toContainText(/created|створено/i);
    await expect(lastRow.locator('.entity-activity-version')).toContainText('v1');
    await expect(lastRow.locator('.entity-activity-changes')).toContainText(originalTitle);
    await screenshot(page, `${screenshotPrefix}-activity`);
  });

  const textOnlyDescription = newDescription + ' (v3)';

  await test.step(`text-only edit v${textEditVersion} — media field shows — after deletion`, async () => {
    await overlay.locator('.adv-form-tabs vaadin-tab').first().click();
    await overlay.locator('[data-testid="advertisement-overlay-field-description"] textarea').waitFor({ timeout: 3000 });

    const descInput = overlay.locator('[data-testid="advertisement-overlay-field-description"] textarea');
    await descInput.clear();
    await descInput.fill(textOnlyDescription);
    await saveAndWaitForIdle(page, expect, overlay, `${screenshotPrefix}-v3`);

    const activityList = await openActivityTab(overlay);
    await expect(activityList.locator('.entity-activity-row')).toHaveCount(rowsAfterTextEdit, { timeout: 5000 });

    const textEditRow = activityList.locator('.entity-activity-row').nth(0);
    await expect(textEditRow.locator('.entity-activity-version')).toContainText(`v${textEditVersion}`);
    const rowTexts = await textEditRow.locator('.entity-activity-changes-item').allTextContents();
    const mediaLine = rowTexts.find(t => /медіа|media/i.test(t));
    expect(mediaLine).toBeTruthy();
    expect(mediaLine).toContain('—');
    await screenshot(page, `${screenshotPrefix}-v3-activity`);
  });

  if (checkCurrentBadge) {
    await test.step('activity shows exactly one current-state badge', async () => {
      await assertSingleCurrentBadge(page, expect, overlay);
    });
  }

  await test.step('view reflects saved state', async () => {
    await closeEditAndVerifyView(page, expect, overlay, newTitle, textOnlyDescription, `${screenshotPrefix}-view-updated`);
  });

  await test.step('card reflects saved state', async () => {
    await closeOverlayToList(page, overlay);
    const updatedCard = cardByTitle(page, newTitle);
    await verifyCardInList(page, expect, updatedCard, textOnlyDescription, 0, `${screenshotPrefix}-list-updated`);
  });
}

async function runRestoreAdvertisementFlow(page, expect, { currentTitle, restoredTitle, restoredDescription, screenshotPrefix }) {
  const overlay = await openCardOverlay(page, cardByTitle(page, currentTitle), screenshotPrefix);

  await switchToEditMode(page, overlay, null);

  // Activity tab — 3 rows before restore (v3 text-only, v2 media+text, v1 created)
  const activityList = await openActivityTab(overlay);
  await expect(activityList.locator('.entity-activity-row')).toHaveCount(3, { timeout: 5000 });

  const v1Row = activityList.locator('.entity-activity-row').nth(2);
  await expect(v1Row.locator('.entity-activity-version')).toContainText('v1');
  await screenshot(page, `${screenshotPrefix}-before-restore`);
  await v1Row.locator('.entity-activity-restore-btn').click();
  await closeNotification(page);

  // After restore: auto-switches to "Basic information" tab, form populated with v1 values + 3 media items
  const titleInput = overlay.locator('[data-testid="advertisement-overlay-field-title"] input');
  await expect(titleInput).toHaveValue(restoredTitle, { timeout: 8000 });
  const descInput = overlay.locator('[data-testid="advertisement-overlay-field-description"] textarea');
  await expect(descInput).toHaveValue(restoredDescription, { timeout: 5000 });
  await expect(overlay.locator('.attachment-gallery__item')).toHaveCount(3, { timeout: 10000 });
  await screenshot(page, `${screenshotPrefix}-restored-in-form`);

  await saveAndWaitForIdle(page, expect, overlay, screenshotPrefix);

  // Activity tab — 4 rows: v4 restored, v3 text-only, v2 media+text, v1 created
  const activityListAfter = await openActivityTab(overlay);
  await expect(activityListAfter.locator('.entity-activity-row')).toHaveCount(4, { timeout: 5000 });

  const row0 = activityListAfter.locator('.entity-activity-row').nth(0);
  await expect(row0.locator('.entity-activity-action')).toContainText(/updated|оновлено/i);
  await expect(row0.locator('.entity-activity-version')).toContainText('v4');
  const changes0 = row0.locator('.entity-activity-changes');
  await expect(changes0).toContainText(currentTitle);
  await expect(changes0).toContainText(restoredTitle);
  await expect(changes0).toContainText('YouTube-dQw4w9WgXcQ');
  await expect(changes0).toContainText('→');
  await screenshot(page, `${screenshotPrefix}-activity-after-restore`);

  await closeEditAndVerifyView(page, expect, overlay, restoredTitle, restoredDescription.substring(0, 30), `${screenshotPrefix}-view-restored`);

  await closeOverlayToList(page, overlay);

  const restoredCard = cardByTitle(page, restoredTitle);
  await verifyCardInList(page, expect, restoredCard, restoredDescription, 3, `${screenshotPrefix}-list-restored`);
  await openLightboxAndNavigate(page, restoredCard, screenshotPrefix);
}

async function runCrossUserMediaReplaceFlow(page, expect, { adTitle, startingVersion, screenshotPrefix }) {
  const addVersion     = startingVersion + 1;
  const replaceVersion = startingVersion + 2;

  const img1 = `/tmp/${screenshotPrefix}-media-a.png`;
  const img2 = `/tmp/${screenshotPrefix}-media-b.png`;
  await downloadPng(avatar(`${screenshotPrefix}-a`), img1);
  await downloadPng(avatar(`${screenshotPrefix}-b`), img2);

  const overlay = await openCardOverlay(page, cardByTitle(page, adTitle), screenshotPrefix);
  await switchToEditMode(page, overlay, null);

  await test.step(`cross-user adds image — v${addVersion}`, async () => {
    await overlay.locator('vaadin-upload input[type="file"]').setInputFiles(img1);
    await overlay.locator('.attachment-gallery__item').first().waitFor({ timeout: 10000 });
    await screenshot(page, `${screenshotPrefix}-image-added`);
    await saveAndWaitForIdle(page, expect, overlay, `${screenshotPrefix}-add`);
    await assertLatestActivityVersion(page, overlay, expect, addVersion, `${screenshotPrefix}-add-activity`);
  });

  await test.step(`cross-user replaces image — v${replaceVersion}`, async () => {
    // Switch back to Basic Information tab to access the gallery
    await overlay.locator('.adv-form-tabs vaadin-tab').first().click();
    await overlay.locator('[data-testid="advertisement-overlay-field-title"] input').waitFor({ timeout: 3000 });

    await overlay.locator('.attachment-gallery__item .attachment-gallery__delete-btn').first().click();
    await expect(overlay.locator('.attachment-gallery__item')).toHaveCount(0, { timeout: 5000 });
    await overlay.locator('vaadin-upload input[type="file"]').setInputFiles(img2);
    await overlay.locator('.attachment-gallery__item').first().waitFor({ timeout: 10000 });
    await screenshot(page, `${screenshotPrefix}-image-replaced`);
    await saveAndWaitForIdle(page, expect, overlay, `${screenshotPrefix}-replace`);
    await assertLatestActivityVersion(page, overlay, expect, replaceVersion, `${screenshotPrefix}-replace-activity`);
  });

  await test.step('exactly one current-state badge after cross-user media add and replace', async () => {
    await assertSingleCurrentBadge(page, expect, overlay);
  });

  await closeOverlayToList(page, overlay);
  [img1, img2].forEach(p => { try { fs.unlinkSync(p); } catch (_) {} });
}

module.exports = { MINIMAL_WEBM, runCreateAdvertisementFlow, runEditAdvertisementFlow, runRestoreAdvertisementFlow, runCrossUserMediaReplaceFlow };
