const { test, expect, loginAs, waitForOverlay, waitForOverlayClosed, downloadPng, screenshot } = require('./_test-helpers');
const fs = require('fs');

const avatar = seed =>
  `https://api.dicebear.com/9.x/adventurer/png?seed=${seed}&size=256&backgroundColor=b6e3f4`;

const YT_URL = 'https://www.youtube.com/watch?v=dQw4w9WgXcQ';

// Search for element piercing all shadow DOMs
async function findInShadow(page, selector) {
  return page.evaluate((sel) => {
    function search(root) {
      const el = root.querySelector(sel);
      if (el) return el;
      for (const child of root.querySelectorAll('*')) {
        if (child.shadowRoot) {
          const found = search(child.shadowRoot);
          if (found) return found;
        }
      }
      return null;
    }
    return search(document);
  }, selector);
}

async function getIframeSrc(page) {
  return page.evaluate((sel) => {
    function search(root) {
      const el = root.querySelector(sel);
      if (el) return el;
      for (const child of root.querySelectorAll('*')) {
        if (child.shadowRoot) {
          const found = search(child.shadowRoot);
          if (found) return found;
        }
      }
      return null;
    }
    const iframe = search(document);
    return iframe ? iframe.src : null;
  }, '.card-lightbox__iframe');
}

async function waitForLightbox(page) {
  await page.waitForFunction(() => {
    function search(root) {
      if (root.querySelector('.card-lightbox__close')) return true;
      for (const child of root.querySelectorAll('*')) {
        if (child.shadowRoot && search(child.shadowRoot)) return true;
      }
      return false;
    }
    return search(document);
  }, { timeout: 10000 });
}

async function clickLightboxThumb(page, index) {
  await page.evaluate((idx) => {
    function findAll(root, sel) {
      const els = [...root.querySelectorAll(sel)];
      for (const child of root.querySelectorAll('*')) {
        if (child.shadowRoot) els.push(...findAll(child.shadowRoot, sel));
      }
      return els;
    }
    const thumbs = findAll(document, '.card-lightbox__strip .card-lightbox__thumb');
    if (thumbs[idx]) thumbs[idx].click();
  }, index);
}

test.describe('CardPhotoLightbox video/image switching', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page);
  });

  test('switching from YouTube to image stops iframe; switching back restores embed URL', async ({ page }) => {
    const imgPath = '/tmp/lightbox-switch-test.png';
    await downloadPng(avatar('lightbox-switch'), imgPath);

    await test.step('Create ad with YouTube video + image', async () => {
      await page.locator('.add-advertisement-button').click();
      await waitForOverlay(page);
      const ov = page.locator('.advertisement-overlay');
      await ov.locator('vaadin-text-field input').first().fill('Lightbox Switch Test');
      await ov.locator('vaadin-text-area textarea').fill('Testing lightbox video/image switching');

      await ov.locator('.attachment-gallery__video-input input').fill(YT_URL);
      await ov.locator('.attachment-gallery__video-input vaadin-button').click();
      await ov.locator('.attachment-gallery__item').first().waitFor({ timeout: 10000 });

      await ov.locator('vaadin-upload input[type="file"]').setInputFiles(imgPath);
      await ov.locator('.attachment-gallery__item').nth(1).waitFor({ timeout: 10000 });

      await ov.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
      await waitForOverlayClosed(page);
    });

    await test.step('Click ad thumbnail on card to open CardPhotoLightbox', async () => {
      const card = page.locator('.advertisement-card')
        .filter({ has: page.locator('.advertisement-title', { hasText: 'Lightbox Switch Test' }) })
        .first();
      await card.locator('.advertisement-thumbnail-wrapper').click();
    });

    await test.step('Lightbox opens with YouTube iframe active', async () => {
      await waitForLightbox(page);
      await screenshot(page, 'lightbox-01-youtube-open');
      const src = await getIframeSrc(page);
      expect(src).toMatch(/youtube\.com\/embed/);
    });

    await test.step('Click image thumbnail — iframe must be blanked', async () => {
      // YouTube is thumb[0], image is thumb[1]
      await clickLightboxThumb(page, 1);
      await page.waitForFunction(() => {
        function search(root) {
          const img = root.querySelector('.card-lightbox__main-image');
          if (img && !img.hasAttribute('hidden')) return true;
          for (const child of root.querySelectorAll('*')) {
            if (child.shadowRoot && search(child.shadowRoot)) return true;
          }
          return false;
        }
        return search(document);
      }, { timeout: 5000 });

      await screenshot(page, 'lightbox-02-switched-to-image');
      const src = await getIframeSrc(page);
      expect(src).toBe('about:blank');
    });

    await test.step('Click YouTube thumbnail again — iframe must restore embed URL', async () => {
      await clickLightboxThumb(page, 0);
      await page.waitForFunction(() => {
        function search(root) {
          const iframe = root.querySelector('.card-lightbox__iframe');
          if (iframe && !iframe.hasAttribute('hidden')) return true;
          for (const child of root.querySelectorAll('*')) {
            if (child.shadowRoot && search(child.shadowRoot)) return true;
          }
          return false;
        }
        return search(document);
      }, { timeout: 5000 });
      await screenshot(page, 'lightbox-03-youtube-restored');
      const src = await getIframeSrc(page);
      expect(src).toMatch(/youtube\.com\/embed/);
    });

    if (fs.existsSync(imgPath)) fs.unlinkSync(imgPath);
  });
});
