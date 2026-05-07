const { test, expect, loginAs, waitForOverlay, waitForOverlayClosed,
        openHistory, screenshot } = require('./_test-helpers');
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
    for (const byte of buf) {
      c ^= byte;
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
  fs.writeFileSync(filePath,
    Buffer.concat([sig, mkchunk('IHDR', ihdr), mkchunk('IDAT', idat), mkchunk('IEND', Buffer.alloc(0))]));
}

test.describe('Photo line always shown in history', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, 'user1@example.com');
  });

  test('photo field shows — in text-only edit after photo deletion', async ({ page }) => {
    const imgPath = '/tmp/photo-always-shown.png';
    makePng(imgPath, 100, 150, 200);
    const TITLE = `Photo Always Shown ${Date.now()}`;

    // v1: create ad with photo
    await page.locator('.add-advertisement-button').click();
    await waitForOverlay(page);
    const ov = page.locator('.advertisement-overlay');
    await ov.locator('vaadin-text-field input').fill(TITLE);
    await ov.locator('vaadin-text-area textarea').fill('Initial description');
    await ov.locator('vaadin-upload input[type="file"]').setInputFiles(imgPath);
    await page.locator('.attachment-gallery__item').first().waitFor({ timeout: 10000 });
    await ov.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await waitForOverlayClosed(page);

    // v2: delete photo
    await page.locator('.advertisement-card')
      .filter({ has: page.locator('.advertisement-title', { hasText: TITLE }) })
      .first().click();
    await waitForOverlay(page);
    await ov.locator('vaadin-button').filter({ hasText: /edit|редагувати/i }).first().click();
    await page.locator('.base-overlay.overlay--visible vaadin-text-field input').first().waitFor();

    const deleteBtn = page.locator('.attachment-gallery__item .attachment-gallery__delete-btn').first();
    await deleteBtn.click();
    await page.locator('.attachment-gallery__empty').waitFor({ timeout: 3000 });

    await ov.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await page.locator('.overlay__view-title').waitFor();

    // v3: text-only edit
    await ov.locator('vaadin-button').filter({ hasText: /edit|редагувати/i }).first().click();
    await page.locator('.base-overlay.overlay--visible vaadin-text-field input').first().waitFor();
    const titleInput = page.locator('.base-overlay.overlay--visible vaadin-text-field input').first();
    await titleInput.click({ clickCount: 3 });
    await titleInput.fill(TITLE + ' edited');
    await ov.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await page.locator('.overlay__view-title').waitFor();

    // verify history
    await openHistory(page);

    const rows = page.locator('.adv-history-row');
    await test.step('3 history rows present', async () => {
      expect(await rows.count()).toBeGreaterThanOrEqual(3);
    });

    const latestRow = rows.first();

    await test.step('Latest row (text-only edit) still has photo line', async () => {
      const changes = latestRow.locator('.adv-history-changes-item');
      const texts = await changes.allTextContents();
      const photoLine = texts.find(t => /^[•\s]*(фото|photos)\s*:/i.test(t));
      if (!photoLine) {
        throw new Error(
          'Photo line missing in text-only edit row. Changes found: ' + JSON.stringify(texts)
        );
      }
    });

    await test.step('Photo line contains — (photos were deleted in v2)', async () => {
      const changes = latestRow.locator('.adv-history-changes-item');
      const texts = await changes.allTextContents();
      const photoLine = texts.find(t => /^[•\s]*(фото|photos)\s*:/i.test(t));
      if (!photoLine.includes('—')) {
        throw new Error('Expected — in photo line, got: ' + photoLine);
      }
    });
    await screenshot(page, 'photo-always-shown-01-v3-has-photo-line');

    if (fs.existsSync(imgPath)) fs.unlinkSync(imgPath);
  });
});
