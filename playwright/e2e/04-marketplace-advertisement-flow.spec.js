const fs = require('fs');
const { test, expect, screenshot, waitForOverlayClosed, closeOverlay, TEST_USERS, YT_URL, avatar, downloadPng } = require('./_helpers');

async function waitForOverlay(page, timeout = 10000) {
  await page.locator('.base-overlay.overlay--visible').waitFor({ timeout });
}
const { runFillLoginFormFlow, runSubmitLoginFlow, runLogoutFlow } = require('./_flows/auth.flow');
const { MINIMAL_WEBM, runCreateAdvertisementFlow, runEditAdvertisementFlow, runRestoreAdvertisementFlow, runCrossUserMediaReplaceFlow } = require('./_flows/advertisement.flow');
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

  test('userEn creates advertisement with YouTube, image and video', async () => {
    await runFillLoginFormFlow(page, CREATE.enAd.user);
    await runSubmitLoginFlow(page, expect, CREATE.enAd.user);
    await runCreateAdvertisementFlow(page, expect, { title: CREATE.enAd.title, description: CREATE.enAd.description, screenshotPrefix: 'adv-useren-create' });

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

  test('userUk creates advertisement with YouTube, image and video', async () => {
    await runFillLoginFormFlow(page, CREATE.ukAd.user);
    await runSubmitLoginFlow(page, expect, CREATE.ukAd.user);
    await runCreateAdvertisementFlow(page, expect, { title: CREATE.ukAd.title, description: CREATE.ukAd.description, screenshotPrefix: 'adv-useruk-create' });
    await runLogoutFlow(page, expect);
  });

  test('userEn edits advertisement — removes all media, updates title and description', async () => {
    await runFillLoginFormFlow(page, CREATE.enAd.user);
    await runSubmitLoginFlow(page, expect, CREATE.enAd.user);
    await runEditAdvertisementFlow(page, expect, {
      originalTitle: CREATE.enAd.title, originalDescription: CREATE.enAd.description,
      newTitle: UPDATE.enAd.title,      newDescription: UPDATE.enAd.description,
      screenshotPrefix: 'adv-useren-edit',
    });
    await runLogoutFlow(page, expect);

    await test.step('admin verifies timeline shows advertisement updates', async () => {
      await runFillLoginFormFlow(page, TEST_USERS.adminEn);
      await runSubmitLoginFlow(page, expect, TEST_USERS.adminEn);
      await openTimelineTab(page);
      await openTimelineFilter(page);
      await fillEntityType(page, 'ADVERTISEMENT');
      await closeTimelineFilter(page);
      await assertTimelineHasRows(page, expect, { action: 'updated', entityType: 'advertisement', minCount: 2, screenshotName: 'adv-useren-edit-timeline-admin' });
      await runLogoutFlow(page, expect);
    });
  });

  test('userUk edits advertisement — removes all media, updates title and description', async () => {
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
      await assertTimelineHasRows(page, expect, { action: 'updated', entityType: 'advertisement', minCount: 4, screenshotName: 'adv-useruk-edit-timeline-admin' });
      await runLogoutFlow(page, expect);
    });
  });

  test('userEn restores advertisement to original version', async () => {
    await runFillLoginFormFlow(page, CREATE.enAd.user);
    await runSubmitLoginFlow(page, expect, CREATE.enAd.user);
    await runRestoreAdvertisementFlow(page, expect, {
      currentTitle: UPDATE.enAd.title,
      restoredTitle: CREATE.enAd.title, restoredDescription: CREATE.enAd.description,
      screenshotPrefix: 'adv-useren-restore',
    });
    await runLogoutFlow(page, expect);
  });

  test('userUk restores advertisement to original version', async () => {
    await runFillLoginFormFlow(page, CREATE.ukAd.user);
    await runSubmitLoginFlow(page, expect, CREATE.ukAd.user);
    await runRestoreAdvertisementFlow(page, expect, {
      currentTitle: UPDATE.ukAd.title,
      restoredTitle: CREATE.ukAd.title, restoredDescription: CREATE.ukAd.description,
      screenshotPrefix: 'adv-useruk-restore',
    });
    await runLogoutFlow(page, expect);
  });

  test('moderatorEn edits EN advertisement — cross-user edit with badge check', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.moderatorEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.moderatorEn);
    await runEditAdvertisementFlow(page, expect, {
      originalTitle:       CREATE.enAd.title,
      originalDescription: CREATE.enAd.description,
      newTitle:            CROSS_UPDATE.enAd.title,
      newDescription:      CROSS_UPDATE.enAd.description,
      startingVersion:     4,
      checkCurrentBadge:   true,
      screenshotPrefix:    'adv-moderatoren-edit',
    });
    await runCrossUserMediaReplaceFlow(page, expect, {
      adTitle:          CROSS_UPDATE.enAd.title,
      startingVersion:  6,
      screenshotPrefix: 'adv-moderatoren-media',
    });
    await openTimelineTab(page);
    await openTimelineFilter(page);
    await fillEntityType(page, 'ADVERTISEMENT');
    await closeTimelineFilter(page);
    await assertTimelineHasRows(page, expect, { action: 'updated', entityType: 'advertisement', minCount: 4, screenshotName: 'timeline-moderatoren-edit-ad' });
    await runLogoutFlow(page, expect);
  });

  test('adminEn edits UK advertisement — cross-user edit with badge check', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.adminEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.adminEn);
    await runEditAdvertisementFlow(page, expect, {
      originalTitle:       CREATE.ukAd.title,
      originalDescription: CREATE.ukAd.description,
      newTitle:            CROSS_UPDATE.ukAd.title,
      newDescription:      CROSS_UPDATE.ukAd.description,
      startingVersion:     4,
      checkCurrentBadge:   true,
      screenshotPrefix:    'adv-adminen-edit-uk',
    });
    await runCrossUserMediaReplaceFlow(page, expect, {
      adTitle:          CROSS_UPDATE.ukAd.title,
      startingVersion:  6,
      screenshotPrefix: 'adv-adminen-media-uk',
    });
    await openTimelineTab(page);
    await openTimelineFilter(page);
    await fillEntityType(page, 'ADVERTISEMENT');
    await closeTimelineFilter(page);
    await assertTimelineHasRows(page, expect, { action: 'updated', entityType: 'advertisement', minCount: 4, screenshotName: 'timeline-adminen-edit-ad' });
    await runLogoutFlow(page, expect);
  });

  test('userEn verifies lightbox — YouTube→image blanks iframe; WebM→image stops video', async () => {
    const imgPath = '/tmp/lightbox-avatar.png';
    await downloadPng(avatar('lightbox'), imgPath);

    await runFillLoginFormFlow(page, TEST_USERS.userEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.userEn);

    // Create ad with YouTube + image + WebM
    await page.locator('.add-advertisement-button').click();
    await waitForOverlay(page);
    const ov = page.locator('.advertisement-overlay');
    await ov.locator('[data-testid="advertisement-overlay-field-title"] input').fill('Lightbox Test Ad');
    await ov.locator('[data-testid="advertisement-overlay-field-description"] textarea').fill('YouTube lightbox test');
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
});
