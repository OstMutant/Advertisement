const { test, expect, loginAs, waitForOverlay, waitForOverlayClosed, screenshot } = require('./_test-helpers');
const fs   = require('fs');
const zlib = require('zlib');

function createMinimalMp4(dest) {
  const ftyp = Buffer.from([
    0x00,0x00,0x00,0x14, 0x66,0x74,0x79,0x70,
    0x6D,0x70,0x34,0x32, 0x00,0x00,0x00,0x00,
    0x6D,0x70,0x34,0x32
  ]);
  const mdat = Buffer.from([0x00,0x00,0x00,0x08, 0x6D,0x64,0x61,0x74]);
  fs.writeFileSync(dest, Buffer.concat([ftyp, mdat]));
}

function createMinimalPng(dest) {
  const w = 64, h = 64;
  const rowSize = 1 + w * 3;
  const raw = Buffer.alloc(h * rowSize);
  for (let y = 0; y < h; y++) {
    raw[y * rowSize] = 0;
    for (let x = 0; x < w; x++) { raw[y * rowSize + 1 + x * 3] = 100; raw[y * rowSize + 2 + x * 3] = 150; raw[y * rowSize + 3 + x * 3] = 200; }
  }
  const idat = zlib.deflateSync(raw);
  function crc32(buf) { let c = 0xFFFFFFFF >>> 0; for (let i = 0; i < buf.length; i++) { c ^= buf[i]; for (let k = 0; k < 8; k++) c = ((c >>> 1) ^ (0xEDB88320 & -(c & 1))) >>> 0; } return (c ^ 0xFFFFFFFF) >>> 0; }
  function mkchunk(tag, data) { const t = Buffer.from(tag, 'ascii'); const combined = Buffer.concat([t, data]); const out = Buffer.alloc(4 + 4 + data.length + 4); out.writeUInt32BE(data.length, 0); t.copy(out, 4); data.copy(out, 8); out.writeUInt32BE(crc32(combined), 8 + data.length); return out; }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 2;
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  fs.writeFileSync(dest, Buffer.concat([sig, mkchunk('IHDR', ihdr), mkchunk('IDAT', idat), mkchunk('IEND', Buffer.alloc(0))]));
}

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

test.describe('CardMediaLightbox video/image switching', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page);
  });

  test('switching from YouTube to image stops iframe; switching back restores embed URL', async ({ page }) => {
    const imgPath = '/tmp/lightbox-switch-test.png';
    createMinimalPng(imgPath);
    const adTitle = `Lightbox Switch Test ${Date.now()}`;

    await test.step('Create ad with YouTube video + image', async () => {
      await page.locator('.add-advertisement-button').click();
      await waitForOverlay(page);
      const ov = page.locator('.advertisement-overlay');
      await ov.locator('[data-testid="advertisement-overlay-field-title"] input').fill(adTitle);
      await ov.locator('[data-testid="advertisement-overlay-field-description"] textarea').fill('Testing lightbox video/image switching');

      await ov.locator('.attachment-gallery__video-input input').fill(YT_URL);
      await ov.locator('.attachment-gallery__video-input vaadin-button').click();
      await ov.locator('.attachment-gallery__item').first().waitFor({ timeout: 10000 });

      await ov.locator('vaadin-upload input[type="file"]').setInputFiles(imgPath);
      await ov.locator('.attachment-gallery__item').nth(1).waitFor({ timeout: 10000 });

      await ov.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
      await waitForOverlayClosed(page);
    });

    await test.step('Click ad thumbnail on card to open CardMediaLightbox', async () => {
      const card = page.locator('.advertisement-card')
        .filter({ has: page.locator('.advertisement-title', { hasText: adTitle }) })
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

  test('switching from uploaded video to image pauses video and clears src', async ({ page }) => {
    const videoPath = '/tmp/lightbox-videopause.mp4';
    const imgPath   = '/tmp/lightbox-videopause.png';
    createMinimalMp4(videoPath);
    createMinimalPng(imgPath);
    const adTitle = `Video Pause Lightbox Test ${Date.now()}`;

    await test.step('Create ad with MP4 video + image', async () => {
      await page.locator('.add-advertisement-button').click();
      await waitForOverlay(page);
      const ov = page.locator('.advertisement-overlay');
      await ov.locator('[data-testid="advertisement-overlay-field-title"] input').fill(adTitle);
      await ov.locator('[data-testid="advertisement-overlay-field-description"] textarea').fill('Video pause test');
      await ov.locator('vaadin-upload input[type="file"]').setInputFiles([videoPath, imgPath]);
      await ov.locator('.attachment-gallery__item').nth(1).waitFor({ timeout: 10000 });
      await ov.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
      await waitForOverlayClosed(page);
    });

    await test.step('Open card lightbox — video is first item', async () => {
      await page.locator('.advertisement-card')
        .filter({ has: page.locator('.advertisement-title', { hasText: adTitle }) })
        .first().locator('.advertisement-thumbnail-wrapper').click();
      await waitForLightbox(page);
      // Check visible via hidden attribute — getBoundingClientRect() returns 0 while
      // Vaadin Dialog is still completing its opening animation.
      await page.waitForFunction(() => {
        function search(root) {
          const w = root.querySelector('.card-lightbox__main-video-wrapper');
          if (w) return !w.hasAttribute('hidden');
          for (const child of root.querySelectorAll('*')) {
            if (child.shadowRoot && search(child.shadowRoot)) return true;
          }
          return false;
        }
        return search(document);
      }, { timeout: 10000 });
      await screenshot(page, 'lightbox-video-01-video-open');
    });

    await test.step('Switch to image — video wrapper hidden and src cleared', async () => {
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

      const videoWrapperVisible = await page.evaluate(() => {
        function search(root) {
          const w = root.querySelector('.card-lightbox__main-video-wrapper');
          if (w) return !w.hasAttribute('hidden');
          for (const child of root.querySelectorAll('*')) {
            if (child.shadowRoot) {
              const found = search(child.shadowRoot);
              if (found !== undefined) return found;
            }
          }
          return false;
        }
        return search(document);
      });
      if (videoWrapperVisible) throw new Error('Video wrapper still visible after switching to image');

      const videoSrc = await page.evaluate(() => {
        function search(root) {
          const v = root.querySelector('.card-lightbox__main-video');
          if (v) return v.getAttribute('src');
          for (const child of root.querySelectorAll('*')) {
            if (child.shadowRoot) {
              const found = search(child.shadowRoot);
              if (found !== undefined) return found;
            }
          }
          return null;
        }
        return search(document);
      });
      if (videoSrc !== '' && videoSrc !== null)
        throw new Error(`Video src not cleared after image switch: ${videoSrc}`);

      await screenshot(page, 'lightbox-video-02-image-video-stopped');
    });

    [videoPath, imgPath].forEach(p => { try { fs.unlinkSync(p); } catch (_) {} });
  });
});
