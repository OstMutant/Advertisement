const { test, expect, loginAs,
        waitForOverlay, waitForOverlayClosed,
        openAdDetail, openHistory, openSettings, openActivityTab, confirmDialog } = require('./_test-helpers');
const fs   = require('fs');
const zlib = require('zlib');

const AD_TITLE = 'Photo Activity Smoke';

function makePng(path, r, g, b) {
  const w = 64, h = 64;
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
  fs.writeFileSync(path, Buffer.concat([sig, mkchunk('IHDR', ihdr), mkchunk('IDAT', idat), mkchunk('IEND', Buffer.alloc(0))]));
}

async function openEditMode(page) {
  await page.locator('.base-overlay.overlay--visible vaadin-button')
    .filter({ hasText: /edit|редагувати/i }).first().click();
  await page.locator('.base-overlay.overlay--visible vaadin-text-field input').first().waitFor();
}

async function closeAdOverlay(page) {
  await page.locator('.adv-overlay-tabs vaadin-tab').filter({ hasText: /view|перегляд/i }).click();
  await page.locator('.overlay__view-title').waitFor({ timeout: 3000 });
  await page.locator('.advertisement-overlay .overlay__breadcrumb-back').click();
  await expect(page.locator('.advertisement-overlay.overlay--visible')).toBeHidden({ timeout: 8000 });
}

function checkPhotoInText(text, label) {
  const count = (text.match(/фото|photo/gi) || []).length;
  if (count === 0) throw new Error(`No photo change in ${label}: ` + text.slice(0, 200));
}

test.describe('Photo activity', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, 'user3@example.com');
  });

  test('create/edit/restore photos shows photo diffs in history and activity', async ({ page }) => {
    const paths = ['/tmp/phact-1.png', '/tmp/phact-2.png', '/tmp/phact-3.png'];
    makePng(paths[0], 100, 150, 200);
    makePng(paths[1], 200, 100, 150);
    makePng(paths[2], 150, 200, 100);

    await page.locator('.add-advertisement-button').click();
    await waitForOverlay(page);
    const overlay = page.locator('.advertisement-overlay');
    await overlay.locator('vaadin-text-field input').fill(AD_TITLE);
    await overlay.locator('vaadin-text-area textarea').fill('Photo activity test description');
    await overlay.locator('vaadin-upload input[type="file"]').setInputFiles(paths[0]);
    await page.locator('.attachment-gallery__item').waitFor({ timeout: 10000 });
    await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await expect(page.locator('.advertisement-overlay.overlay--visible')).toBeHidden({ timeout: 8000 });

    await openAdDetail(page, AD_TITLE);
    await openHistory(page);

    await test.step('History shows photo change after create', async () => {
      checkPhotoInText(await page.locator('.adv-history-list').textContent(), 'history-after-create');
    });

    await closeAdOverlay(page);

    await openSettings(page);
    await openActivityTab(page);

    await test.step('Settings activity shows photo change after create', async () => {
      checkPhotoInText(
        await page.locator('.base-overlay.overlay--visible .user-activity-list').textContent(),
        'settings-activity-after-create');
    });

    await page.keyboard.press('Escape');
    await waitForOverlayClosed(page);

    await page.locator('vaadin-tab').filter({ hasText: /advertisement|оголошен/i }).first().click();
    await page.locator('.advertisement-container').waitFor({ timeout: 5000 });
    await openAdDetail(page, AD_TITLE);
    await openEditMode(page);
    await page.locator('.base-overlay.overlay--visible vaadin-upload input[type="file"]').setInputFiles(paths[1]);
    await page.locator('.attachment-gallery__item').nth(1).waitFor({ timeout: 10000 });
    await page.locator('.base-overlay.overlay--visible vaadin-button')
      .filter({ hasText: /зберегти|save/i }).click();
    await page.locator('.overlay__view-title').waitFor({ timeout: 5000 });

    await openHistory(page);

    await test.step('History shows photo diff after adding second photo', async () => {
      checkPhotoInText(await page.locator('.adv-history-list').textContent(), 'history-after-edit');
      if (!(await page.locator('.adv-history-list').textContent()).includes('→'))
        throw new Error('No diff arrow → in history');
    });

    await test.step('Restore button on older entry', async () => {
      if (await page.locator('.adv-history-restore-btn').count() === 0)
        throw new Error('No restore button');
    });

    await closeAdOverlay(page);

    await page.locator('vaadin-tab').filter({ hasText: /advertisement|оголошен/i }).first().click();
    await page.locator('.advertisement-container').waitFor({ timeout: 5000 });
    await openAdDetail(page, AD_TITLE);
    await openHistory(page);
    await page.locator('.adv-history-list .adv-history-restore-btn').last().click();
    await confirmDialog(page);
    await page.locator('.overlay__view-title').waitFor({ timeout: 5000 });

    await test.step('History grows after restore', async () => {
      await openHistory(page);
      const rows = await page.locator('.adv-history-row').count();
      if (rows < 3) throw new Error(`Expected >=3 rows after restore, got ${rows}`);
      checkPhotoInText(await page.locator('.adv-history-list').textContent(), 'history-after-restore');
    });

    paths.forEach(p => { if (fs.existsSync(p)) fs.unlinkSync(p); });
  });
});
