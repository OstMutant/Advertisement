const fs = require('fs');
const { test, expect, screenshot, waitForOverlayClosed, closeOverlay, TEST_USERS, YT_URL, avatar, downloadPng } = require('./_helpers');

async function waitForOverlay(page, timeout = 10000) {
  await page.locator('.base-overlay.overlay--visible').waitFor({ timeout });
}
const { runFillLoginFormFlow, runSubmitLoginFlow, runLogoutFlow } = require('./_flows/auth.flow');
const { MINIMAL_WEBM, RICH_TAGS, runCreateAdvertisementFlow, runEditAdvertisementFlow, runRestoreAdvertisementFlow, runCrossUserMediaReplaceFlow, cardByTitle, openCardOverlay, switchToEditMode, openActivityTab, saveAndWaitForIdle, closeOverlayToList } = require('./_flows/advertisement.flow');
const { runCreateSimpleAdvertisementFlow } = require('./_flows/delete.flow');
const { openTimelineTab, openTimelineFilter, closeTimelineFilter, fillEntityType, assertFeedHasRow, assertTimelineHasRows } = require('./_flows/timeline.flow');
const { waitForLightboxOpen, waitForLightboxClosed, getIframeSrc, clickLightboxThumb, getVideoSrc, isVideoWrapperVisible, waitForVideoWrapperVisible, waitForMainImageVisible } = require('./_flows/attachment.flow');

test.describe.configure({ mode: 'serial' });

const CREATE = {
  enAd: { user: TEST_USERS.userEn, title: 'EN Advertisement', description: 'Advertisement created by English user with all media types: YouTube, image and video.' },
  ukAd: { user: TEST_USERS.userUk, title: 'UK Оголошення',   description: 'Оголошення створене українським користувачем з усіма типами медіа: YouTube, зображення та відео.' },
};

const UPDATE = {
  enAd: { title: 'EN Advertisement Updated', description: 'Updated description for the EN advertisement.' },
  ukAd: { title: 'UK Оголошення Оновлено',  description: 'Оновлений опис для UK оголошення.'             },
};

const CROSS_UPDATE = {
  enAd: { title: 'EN Advertisement Moderated', description: 'Description updated by moderator.'      },
  ukAd: { title: 'UK Оголошення Адмін',        description: 'Опис оновлений адміністратором.'        },
};

test.describe('Advertisement flow', () => {
  let page;

  test.beforeAll(async ({ browser }) => {
    page = await browser.newPage();
    await page.goto('/');
  });

  test.afterAll(async () => {
    await page.close();
  });

  test('userEn creates advertisement — create discard clears form, YouTube, image and video, lightbox plays video, two category rows, categories text and view chips', async () => {
    await runFillLoginFormFlow(page, CREATE.enAd.user);
    await runSubmitLoginFlow(page, expect, CREATE.enAd.user);
    await runCreateAdvertisementFlow(page, expect, { title: CREATE.enAd.title, description: CREATE.enAd.description, screenshotPrefix: 'adv-useren-create', categories: ['Electronics', 'Vehicles'] });

    await test.step('attachment lightbox — play icon visible, video src valid', async () => {
      await page.locator('.advertisement-card')
        .filter({ has: page.locator('.advertisement-title', { hasText: CREATE.enAd.title }) })
        .first().click();
      await waitForOverlay(page);
      // WebM video is 3rd gallery item (YouTube=0, image=1, WebM=2)
      await page.locator('.attachment-gallery__item').nth(2).waitFor({ timeout: 5000 });
      await page.locator('.attachment-gallery__item').nth(2).click();
      await page.locator('.attachment-lightbox').waitFor({ timeout: 5000 });
      const videoEl = page.locator('.attachment-lightbox video');
      await expect(videoEl).toBeVisible();
      const src = await videoEl.getAttribute('src');
      expect(src).toBeTruthy();
      await screenshot(page, 'adv-useren-create-attachment-lightbox');
      await page.evaluate(() => document.querySelector('.attachment-lightbox .card-lightbox__close')?.click());
      await page.locator('.attachment-lightbox').waitFor({ state: 'detached', timeout: 5000 }).catch(() => {});
      await closeOverlay(page);
    });

    await runLogoutFlow(page, expect);
  });

  test('userUk creates advertisement — YouTube, image and video, single activity row', async () => {
    await runFillLoginFormFlow(page, CREATE.ukAd.user);
    await runSubmitLoginFlow(page, expect, CREATE.ukAd.user);
    await runCreateAdvertisementFlow(page, expect, { title: CREATE.ukAd.title, description: CREATE.ukAd.description, screenshotPrefix: 'adv-useruk-create' });
    await runLogoutFlow(page, expect);
  });

  test('userEn edits advertisement — discard, two saves with activity diff, all rich formats in view and card, format-only edit, admin timeline check', async () => {
    await runFillLoginFormFlow(page, CREATE.enAd.user);
    await runSubmitLoginFlow(page, expect, CREATE.enAd.user);
    await runEditAdvertisementFlow(page, expect, {
      originalTitle: CREATE.enAd.title, originalDescription: CREATE.enAd.description,
      newTitle: UPDATE.enAd.title,      newDescription: UPDATE.enAd.description,
      startingVersion: 3,
      richText: true,
      screenshotPrefix: 'adv-useren-edit',
    });
    await runLogoutFlow(page, expect);

    await test.step('admin verifies timeline shows advertisement updates with rich text formatting and tooltips', async () => {
      await runFillLoginFormFlow(page, TEST_USERS.adminEn);
      await runSubmitLoginFlow(page, expect, TEST_USERS.adminEn);
      await openTimelineTab(page);
      await openTimelineFilter(page);
      await fillEntityType(page, 'ADVERTISEMENT');
      await closeTimelineFilter(page);
      await assertTimelineHasRows(page, expect, { action: 'updated', entityType: 'advertisement', minCount: 2, titleText: UPDATE.enAd.title, screenshotName: 'adv-useren-edit-timeline-admin' });

      // Collect all change-item HTML across all rows for this ad to verify every rich format tag
      const tlRows = page.locator('.activity-feed .activity-feed-row');
      const tlRowCount = await tlRows.count();
      let tlAllHtml = '';
      let tlRichRowIdx = -1;
      let tlRichItemIdx = -1;
      for (let i = 0; i < tlRowCount; i++) {
        const rowTitle = await tlRows.nth(i).locator('.activity-feed-name').textContent().catch(() => '');
        if (!rowTitle.includes(UPDATE.enAd.title)) continue;
        const items = tlRows.nth(i).locator('.activity-feed-changes-item');
        const n = await items.count();
        for (let j = 0; j < n; j++) {
          const html = await items.nth(j).innerHTML();
          tlAllHtml += html;
          if (tlRichRowIdx < 0 && RICH_TAGS.some(t => html.includes(t))) { tlRichRowIdx = i; tlRichItemIdx = j; }
        }
      }
      for (const tag of RICH_TAGS) {
        expect(tlAllHtml, `timeline should contain ${tag} for rich text description`).toContain(tag);
      }
      await screenshot(page, 'adv-useren-edit-timeline-rich-html');

      expect(tlRichRowIdx, 'timeline should have a row with rich html items').toBeGreaterThanOrEqual(0);
      await screenshot(page, 'adv-useren-edit-timeline-rich-items');

      await runLogoutFlow(page, expect);
    });
  });

  test('userUk edits advertisement — discard, two saves with activity diff, admin timeline check', async () => {
    await runFillLoginFormFlow(page, CREATE.ukAd.user);
    await runSubmitLoginFlow(page, expect, CREATE.ukAd.user);
    await runEditAdvertisementFlow(page, expect, {
      originalTitle: CREATE.ukAd.title, originalDescription: CREATE.ukAd.description,
      newTitle: UPDATE.ukAd.title,      newDescription: UPDATE.ukAd.description,
      screenshotPrefix: 'adv-useruk-edit',
    });
    await runLogoutFlow(page, expect);

    await test.step('admin verifies timeline shows advertisement updates', async () => {
      await runFillLoginFormFlow(page, TEST_USERS.adminEn);
      await runSubmitLoginFlow(page, expect, TEST_USERS.adminEn);
      await openTimelineTab(page);
      await openTimelineFilter(page);
      await fillEntityType(page, 'ADVERTISEMENT');
      await closeTimelineFilter(page);
      await assertTimelineHasRows(page, expect, { action: 'updated', entityType: 'advertisement', minCount: 4, titleText: UPDATE.ukAd.title, screenshotName: 'adv-useruk-edit-timeline-admin' });
      await runLogoutFlow(page, expect);
    });
  });

  test('userEn restores advertisement — activity diff shows restored media and text, view and card updated', async () => {
    await runFillLoginFormFlow(page, CREATE.enAd.user);
    await runSubmitLoginFlow(page, expect, CREATE.enAd.user);
    await runRestoreAdvertisementFlow(page, expect, {
      currentTitle: UPDATE.enAd.title,
      restoredTitle: CREATE.enAd.title, restoredDescription: CREATE.enAd.description,
      rowsBeforeRestore: 6,
      screenshotPrefix: 'adv-useren-restore',
    });
    await runLogoutFlow(page, expect);
  });

  test('userUk restores advertisement — activity diff shows restored media and text, view and card updated', async () => {
    await runFillLoginFormFlow(page, CREATE.ukAd.user);
    await runSubmitLoginFlow(page, expect, CREATE.ukAd.user);
    await runRestoreAdvertisementFlow(page, expect, {
      currentTitle: UPDATE.ukAd.title,
      restoredTitle: CREATE.ukAd.title, restoredDescription: CREATE.ukAd.description,
      screenshotPrefix: 'adv-useruk-restore',
    });
    await runLogoutFlow(page, expect);
  });

  test('moderatorEn edits EN advertisement — discard, two saves with activity diff, add and replace media, timeline check', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.moderatorEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.moderatorEn);
    await runEditAdvertisementFlow(page, expect, {
      originalTitle:       CREATE.enAd.title,
      originalDescription: CREATE.enAd.description,
      newTitle:            CROSS_UPDATE.enAd.title,
      newDescription:      CROSS_UPDATE.enAd.description,
      startingVersion:     7,
      checkCurrentBadge:   true,
      richText:            true,
      screenshotPrefix:    'adv-moderatoren-edit',
    });
    await runCrossUserMediaReplaceFlow(page, expect, {
      adTitle:          CROSS_UPDATE.enAd.title,
      startingVersion:  10,
      screenshotPrefix: 'adv-moderatoren-media',
    });
    await openTimelineTab(page);
    await openTimelineFilter(page);
    await fillEntityType(page, 'ADVERTISEMENT');
    await closeTimelineFilter(page);
    await assertTimelineHasRows(page, expect, { action: 'updated', entityType: 'advertisement', minCount: 4, titleText: CROSS_UPDATE.enAd.title, actorText: TEST_USERS.moderatorEn.name, screenshotName: 'timeline-moderatoren-edit-ad' });
    await runLogoutFlow(page, expect);
  });

  test('adminEn edits UK advertisement — discard, two saves with activity diff, category added and removed with diff, add and replace media, timeline check', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.adminEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.adminEn);
    await runEditAdvertisementFlow(page, expect, {
      originalTitle:       CREATE.ukAd.title,
      originalDescription: CREATE.ukAd.description,
      newTitle:            CROSS_UPDATE.ukAd.title,
      newDescription:      CROSS_UPDATE.ukAd.description,
      startingVersion:     4,
      checkCurrentBadge:   true,
      richText:            true,
      categoryToAdd:       'Vehicles',
      categoryToRemove:    'Vehicles',
      screenshotPrefix:    'adv-adminen-edit-uk',
    });
    await runCrossUserMediaReplaceFlow(page, expect, {
      adTitle:          CROSS_UPDATE.ukAd.title,
      startingVersion:  11,
      screenshotPrefix: 'adv-adminen-media-uk',
    });
    await openTimelineTab(page);
    await openTimelineFilter(page);
    await fillEntityType(page, 'ADVERTISEMENT');
    await closeTimelineFilter(page);
    await assertTimelineHasRows(page, expect, { action: 'updated', entityType: 'advertisement', minCount: 4, titleText: CROSS_UPDATE.ukAd.title, actorText: TEST_USERS.adminEn.name, screenshotName: 'timeline-adminen-edit-ad' });
    await runLogoutFlow(page, expect);
  });

  test('userEn verifies lightbox — YouTube to image blanks iframe, WebM to image stops video', async () => {
    const imgPath = '/tmp/lightbox-avatar.png';
    await downloadPng(avatar('lightbox'), imgPath);

    await runFillLoginFormFlow(page, TEST_USERS.userEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.userEn);

    // Create ad with YouTube + image + WebM
    await page.locator('.add-advertisement-button').click();
    await waitForOverlay(page);
    const ov = page.locator('.advertisement-overlay');
    await ov.locator('[data-testid="advertisement-overlay-field-title"] input').fill('Lightbox Test Ad');
    await ov.locator('[data-testid="advertisement-overlay-field-description"] .ql-editor').fill('YouTube lightbox test');
    await ov.locator('.attachment-gallery__video-input input').fill(YT_URL);
    await ov.locator('.attachment-gallery__video-input vaadin-button').click();
    await ov.locator('.attachment-gallery__item').first().waitFor({ timeout: 10000 });
    await ov.locator('vaadin-upload input[type="file"]').setInputFiles(imgPath);
    await ov.locator('.attachment-gallery__item').nth(1).waitFor({ timeout: 10000 });
    await ov.locator('vaadin-upload input[type="file"]').setInputFiles({ name: 'lightbox-test.webm', mimeType: 'video/webm', buffer: MINIMAL_WEBM });
    await ov.locator('.attachment-gallery__item').nth(2).waitFor({ timeout: 15000 });
    await ov.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await waitForOverlayClosed(page);
    if (fs.existsSync(imgPath)) fs.unlinkSync(imgPath);

    await page.locator('.advertisement-card')
      .filter({ has: page.locator('.advertisement-title', { hasText: 'Lightbox Test Ad' }) })
      .first().locator('.advertisement-thumbnail-wrapper').click();
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

    await runLogoutFlow(page, expect);
  });

  test('adminEn verifies long description — activity diff shows all fields, description truncated on card, no mid-word breaks', async () => {
    const LONG_DESC = 'This advertisement description is intentionally very long to trigger the show more toggle. ' +
      'It must span more than three visible lines in the card list view even on a very wide viewport. ' +
      'To guarantee overflow we add extra sentences here: the show more button should appear whenever ' +
      'the rendered text height exceeds the three-line cap, not when the raw HTML byte count crosses an arbitrary threshold. ' +
      'This fifth sentence pushes the content further past the limit. ' +
      'A sixth sentence adds even more text to ensure the description overflows at any typical viewport width. ' +
      'The seventh and final sentence makes this text long enough to reliably exceed the three-line maximum on any screen.';
    const SHORT_DESC = 'Short initial description for expand test.';
    const TITLE = 'Expand Toggle Test Ad';

    await runFillLoginFormFlow(page, TEST_USERS.adminEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.adminEn);

    await runCreateSimpleAdvertisementFlow(page, { title: TITLE, description: SHORT_DESC, screenshotPrefix: 'trunc-create' });

    const overlay = await openCardOverlay(page, cardByTitle(page, TITLE), 'trunc');
    await switchToEditMode(page, overlay, 'trunc');
    await overlay.locator('[data-testid="advertisement-overlay-field-description"] .ql-editor').fill(LONG_DESC);
    await saveAndWaitForIdle(page, expect, overlay, 'trunc-save');

    await test.step('activity diff shows all fields including description change', async () => {
      const activityList = await openActivityTab(overlay);
      const latestRow = activityList.locator('.entity-activity-row').nth(0);
      const changes = latestRow.locator('.entity-activity-changes');
      await expect(changes).toContainText(TITLE);
      await expect(changes).toContainText(SHORT_DESC.substring(0, 20));
      await screenshot(page, 'trunc-activity-all-fields');
    });

    await test.step('activity diff item show more/less — expand and collapse long description value', async () => {
      const activityList = overlay.locator('.entity-activity-list');
      const changes = activityList.locator('.entity-activity-row').nth(0).locator('.entity-activity-changes');
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

    await test.step('card title and description — no mid-word breaks via Range API', async () => {
      const card = cardByTitle(page, TITLE);
      await expect(card).toBeVisible({ timeout: 5000 });

      const brokenWords = await card.evaluate(cardEl => {
        function checkWordBreaks(el) {
          const walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT);
          const broken = [];
          let node;
          while ((node = walker.nextNode())) {
            const text = node.textContent;
            let offset = 0;
            for (const word of text.split(/\s+/)) {
              // Skip very short words and hyphenated compounds — CSS legitimately breaks at hyphens
              if (word.length < 3 || word.includes('-')) { offset += word.length + 1; continue; }
              const idx = text.indexOf(word, offset);
              if (idx < 0) continue;
              const range = document.createRange();
              range.setStart(node, idx);
              range.setEnd(node, idx + word.length);
              const rects = Array.from(range.getClientRects());
              const tops = [...new Set(rects.map(r => Math.round(r.top)))];
              if (tops.length > 1) broken.push(word);
              offset = idx + word.length;
            }
          }
          return broken;
        }
        const title = cardEl.querySelector('.advertisement-title');
        const desc  = cardEl.querySelector('.advertisement-description');
        return [
          ...checkWordBreaks(title || cardEl),
          ...checkWordBreaks(desc  || cardEl),
        ];
      });

      expect(brokenWords, `these words are split mid-character: ${brokenWords.join(', ')}`).toHaveLength(0);
    });

    await runLogoutFlow(page, expect);
  });
});
