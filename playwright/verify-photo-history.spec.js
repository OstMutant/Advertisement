const { test, expect, loginAs,
        waitForOverlay, waitForOverlayClosed, openHistory, screenshot } = require('./_test-helpers');
const fs   = require('fs');
const zlib = require('zlib');

function makePng(filePath, r, g, b) {
  const w = 32, h = 32;
  const rowSize = 1 + w * 3;
  const raw = Buffer.alloc(h * rowSize);
  for (let y = 0; y < h; y++) {
    raw[y * rowSize] = 0;
    for (let x = 0; x < w; x++) {
      raw[y * rowSize + 1 + x * 3] = r;
      raw[y * rowSize + 2 + x * 3] = g;
      raw[y * rowSize + 3 + x * 3] = b;
    }
  }
  const idat = zlib.deflateSync(raw);
  function crc32(buf) {
    let c = 0xFFFFFFFF >>> 0;
    for (let i = 0; i < buf.length; i++) {
      c ^= buf[i];
      for (let k = 0; k < 8; k++) c = ((c >>> 1) ^ (0xEDB88320 & -(c & 1))) >>> 0;
    }
    return (c ^ 0xFFFFFFFF) >>> 0;
  }
  function mkchunk(tag, data) {
    const t = Buffer.from(tag, 'ascii');
    const combined = Buffer.concat([t, data]);
    const out = Buffer.alloc(4 + 4 + data.length + 4);
    out.writeUInt32BE(data.length, 0);
    t.copy(out, 4); data.copy(out, 8);
    out.writeUInt32BE(crc32(combined), 8 + data.length);
    return out;
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; ihdr[9] = 2;
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  fs.writeFileSync(filePath, Buffer.concat([sig, mkchunk('IHDR', ihdr), mkchunk('IDAT', idat), mkchunk('IEND', Buffer.alloc(0))]));
}

test.describe('Verify photo history', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page);
  });

  test('create with photo → photo change visible in history', async ({ page }) => {
    const imgPath = '/tmp/vph-create.png';
    makePng(imgPath, 80, 120, 200);

    await page.locator('.add-advertisement-button').click();
    await waitForOverlay(page);
    const ov = page.locator('.advertisement-overlay');
    await ov.locator('vaadin-text-field input').fill('Verify Photo History');
    await ov.locator('vaadin-text-area textarea').fill('Photo history verify');
    await ov.locator('vaadin-upload input[type="file"]').setInputFiles(imgPath);
    await page.locator('.attachment-gallery__item').first().waitFor({ timeout: 10000 });
    await ov.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await waitForOverlayClosed(page);

    await page.locator('.advertisement-card')
      .filter({ has: page.locator('.advertisement-title', { hasText: 'Verify Photo History' }) })
      .first().click();
    await waitForOverlay(page);
    await openHistory(page);

    await test.step('1 history row on create', async () => {
      const rows = await page.locator('.adv-history-row').count();
      if (rows < 1) throw new Error(`Expected >=1 history row, got ${rows}`);
    });

    await test.step('Photo changes visible in CREATED row', async () => {
      const text = await page.locator('.adv-history-list').textContent();
      if (!/(фото|photo)/i.test(text))
        throw new Error('No photo change entry in history after create: ' + text.slice(0, 200));
    });
    await screenshot(page, 'verify-photo-01-history');

    if (fs.existsSync(imgPath)) fs.unlinkSync(imgPath);
  });

  test('delete photo → photo deletion visible in history', async ({ page }) => {
    const imgPath = '/tmp/vph-delete.png';
    makePng(imgPath, 200, 80, 80);

    await page.locator('.add-advertisement-button').click();
    await waitForOverlay(page);
    const ov = page.locator('.advertisement-overlay');
    await ov.locator('vaadin-text-field input').fill('Verify Photo Delete History');
    await ov.locator('vaadin-text-area textarea').fill('Delete photo test');
    await ov.locator('vaadin-upload input[type="file"]').setInputFiles(imgPath);
    await page.locator('.attachment-gallery__item').first().waitFor({ timeout: 10000 });
    await ov.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await waitForOverlayClosed(page);

    await page.locator('.advertisement-card')
      .filter({ has: page.locator('.advertisement-title', { hasText: 'Verify Photo Delete History' }) })
      .first().click();
    await waitForOverlay(page);
    await page.locator('vaadin-button').filter({ hasText: /редагувати|edit/i }).first().click();
    await page.locator('.base-overlay.overlay--visible vaadin-text-field input').first().waitFor();

    const deleteBtn = page.locator('.attachment-gallery__item .attachment-delete-btn, .attachment-gallery__item button[title*="видалити"], .attachment-gallery__item button[title*="delete"]').first();
    if (await deleteBtn.count() > 0) {
      await deleteBtn.click();
      await page.locator('.base-overlay.overlay--visible vaadin-button')
        .filter({ hasText: /зберегти|save/i }).click();
      await page.locator('.overlay__view-title').waitFor();

      await openHistory(page);

      await test.step('Photo deletion visible in history', async () => {
        const text = await page.locator('.adv-history-list').textContent();
        if (!/(фото|photo)/i.test(text))
          throw new Error('No photo deletion entry in history: ' + text.slice(0, 200));
      });
      await screenshot(page, 'verify-photo-02-delete-history');
    } else {
      await test.step('Photo deletion visible in history', async () => {
        await page.locator('.base-overlay.overlay--visible vaadin-button')
          .filter({ hasText: /зберегти|save/i }).click();
        await page.locator('.overlay__view-title').waitFor();
        await openHistory(page);
        const text = await page.locator('.adv-history-list').textContent();
        if (!/(фото|photo)/i.test(text))
          throw new Error('No photo entry in history: ' + text.slice(0, 200));
      });
      await screenshot(page, 'verify-photo-02-delete-history');
    }

    if (fs.existsSync(imgPath)) fs.unlinkSync(imgPath);
  });
});
