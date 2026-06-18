const fs = require('fs');
const { test, expect, screenshot, waitForOverlay, waitForOverlayClosed, TEST_USERS, YT_URL, avatar, downloadPng } = require('./_helpers');
const { runFillLoginFormFlow, runSubmitLoginFlow, runLogoutFlow } = require('./_flows/auth.flow');
const { runCreateAdvertisementFlow, runEditAdvertisementFlow, runRestoreAdvertisementFlow } = require('./_flows/advertisement.flow');

test.describe.configure({ mode: 'serial' });

const CREATE = {
  enAd: { user: TEST_USERS.userEn, title: 'EN Advertisement', description: 'Advertisement created by English user with all media types: YouTube, image and video.' },
  ukAd: { user: TEST_USERS.userUk, title: 'UK Оголошення',   description: 'Оголошення створене українським користувачем з усіма типами медіа: YouTube, зображення та відео.' },
};

const UPDATE = {
  enAd: { title: 'EN Advertisement Updated', description: 'Updated description for the EN advertisement.' },
  ukAd: { title: 'UK Оголошення Оновлено',  description: 'Оновлений опис для UK оголошення.'             },
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

  test('userEn verifies YouTube lightbox — iframe src switches to about:blank on image select', async () => {
    const imgPath = '/tmp/lightbox-avatar.png';
    await downloadPng(avatar('lightbox'), imgPath);

    await runFillLoginFormFlow(page, TEST_USERS.userEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.userEn);

    // Create ad with YouTube video + image
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
    await ov.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await waitForOverlayClosed(page);
    if (fs.existsSync(imgPath)) fs.unlinkSync(imgPath);

    // Open lightbox via card thumbnail click
    await page.locator('.advertisement-card')
      .filter({ has: page.locator('.advertisement-title', { hasText: 'Lightbox Test Ad' }) })
      .first().locator('.advertisement-thumbnail-wrapper').click();
    await page.waitForFunction(() => {
      function s(root) {
        return !!root.querySelector('.card-lightbox__close') ||
          [...root.querySelectorAll('*')].some(el => el.shadowRoot && s(el.shadowRoot));
      }
      return s(document);
    }, { timeout: 10000 });
    await screenshot(page, 'lightbox-youtube');

    // Strip must have at least 2 thumbnails (video + image)
    const thumbCount = await page.evaluate(() => {
      function findAll(root, sel) {
        const els = [...root.querySelectorAll(sel)];
        for (const c of root.querySelectorAll('*'))
          if (c.shadowRoot) els.push(...findAll(c.shadowRoot, sel));
        return els;
      }
      return findAll(document, '.card-lightbox__strip .card-lightbox__thumb').length;
    });
    expect(thumbCount).toBeGreaterThanOrEqual(2);

    // Click the non-active (image) thumbnail
    await page.evaluate(() => {
      function findAll(root, sel) {
        const els = [...root.querySelectorAll(sel)];
        for (const c of root.querySelectorAll('*'))
          if (c.shadowRoot) els.push(...findAll(c.shadowRoot, sel));
        return els;
      }
      const thumbs = findAll(document, '.card-lightbox__strip .card-lightbox__thumb');
      const imageThumb = thumbs.find(t => !t.classList.contains('card-lightbox__thumb--active'));
      if (imageThumb) imageThumb.click();
    });

    // Main image visible, iframe src cleared to about:blank
    await page.waitForFunction(() => {
      function s(root) {
        const img = root.querySelector('.card-lightbox__main-image');
        if (img && !img.hasAttribute('hidden')) return true;
        return [...root.querySelectorAll('*')].some(el => el.shadowRoot && s(el.shadowRoot));
      }
      return s(document);
    }, { timeout: 5000 });
    const iframeSrc = await page.evaluate(() => {
      function s(root) {
        const f = root.querySelector('.card-lightbox__iframe');
        if (f) return f.src;
        for (const c of root.querySelectorAll('*'))
          if (c.shadowRoot) { const r = s(c.shadowRoot); if (r) return r; }
        return null;
      }
      return s(document);
    });
    expect(iframeSrc).toBe('about:blank');
    await screenshot(page, 'lightbox-image');

    await page.keyboard.press('Escape');
    await page.locator('vaadin-dialog.card-lightbox[opened]').waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {});

    await runLogoutFlow(page, expect);
  });
});
