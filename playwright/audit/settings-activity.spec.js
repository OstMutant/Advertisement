const { test, expect, loginAs,
        waitForOverlay, waitForOverlayClosed, openSettings, openActivityTab, confirmDialog, createAd, screenshot } = require('./_test-helpers');
const fs   = require('fs');
const zlib = require('zlib');

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

test.describe('Settings activity', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, 'user3@example.com');
  });

  test('page size change creates activity entry, restore reverts it', async ({ page }) => {
    await openSettings(page);

    const overlay = page.locator('.base-overlay.overlay--visible');
    const getAdsPageSizeValue = async (p) => {
      const val = await p.locator('.settings-overlay-content vaadin-integer-field').first().locator('input').inputValue();
      return parseInt(val, 10);
    };

    const originalSize = await getAdsPageSizeValue(page);
    const newSize = originalSize === 10 ? 15 : 10;

    const sizeInput = () => page.locator('.settings-overlay-content vaadin-integer-field').first().locator('input');
    const saveBtn = () => overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i });

    await sizeInput().click({ clickCount: 3 });
    await sizeInput().fill(String(newSize));
    await saveBtn().click();
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1100);

    await sizeInput().fill(String(originalSize));
    await saveBtn().click();
    await page.waitForLoadState('networkidle');

    await test.step('Activity tab present in settings', async () => {
      const tabs = await overlay.locator('vaadin-tab').allTextContents();
      if (!tabs.some(t => /activ|активн/i.test(t))) throw new Error('Activity tab not found');
    });

    await openActivityTab(page);

    await test.step('Settings change appears in activity', async () => {
      if (await page.locator('.activity-feed-row').count() === 0) throw new Error('No activity rows');
      const body = await page.locator('.activity-feed-list').first().textContent();
      if (!body.includes('сторінці') && !body.includes('page'))
        throw new Error('Settings change summary not found in activity');
    });

    await test.step('Page size diff shown in activity', async () => {
      if (await page.locator('.activity-feed-changes').count() === 0) throw new Error('No changes summary found');
      if (!(await page.locator('.activity-feed-list').first().textContent()).includes('→'))
        throw new Error('No diff arrow → found in activity');
    });

    await test.step('Restore settings button present', async () => {
      if (await page.locator('.activity-feed-list .entity-history-restore-btn').count() === 0)
        throw new Error('No restore button for settings');
    });

    await test.step('Restore button is left-aligned in activity row', async () => {
      const row = page.locator('.activity-feed-row').filter({ has: page.locator('.entity-history-restore-btn') }).first();
      const btn = row.locator('.entity-history-restore-btn');
      const rowBox = await row.boundingBox();
      const btnBox = await btn.boundingBox();
      if (btnBox.x - rowBox.x > 48)
        throw new Error(`Restore button not left-aligned: offset=${btnBox.x - rowBox.x}px`);
    });
    await screenshot(page, 'settings-activity-01-activity-list');

    await page.locator('.activity-feed-list .entity-history-restore-btn').nth(0).click();
    await confirmDialog(page, 'Оновити|Update');
    await page.waitForLoadState('networkidle');

    await page.locator('.settings-overlay-content vaadin-integer-field').first().waitFor({ timeout: 5000 });

    await test.step('Settings overlay stays open after restore', async () => {
      await expect(page.locator('.base-overlay.overlay--visible')).toBeVisible();
      await expect(page.locator('.overlay__form-fields-card')).toBeVisible();
    });

    await test.step('Page size changed after restore', async () => {
      const currentSize = await getAdsPageSizeValue(page);
      if (currentSize !== newSize) throw new Error(`Expected ${newSize} after restore, got ${currentSize}`);
    });
    await screenshot(page, 'settings-activity-02-after-restore');
  });

  test('saving settings while on activity tab navigates back to settings tab', async ({ page }) => {
    await createAd(page, { title: `Tab Switch Test ${Date.now()}`, description: 'tab switch test' });

    await openSettings(page);
    await openActivityTab(page);

    await test.step('Activity panel visible before save', async () => {
      await expect(page.locator('.base-overlay.overlay--visible .activity-feed-list').first()).toBeVisible();
    });

    await page.locator('.base-overlay.overlay--visible vaadin-button')
      .filter({ hasText: /зберегти|save/i }).click();
    await page.waitForLoadState('networkidle');

    await test.step('Settings panel visible after save (not activity)', async () => {
      await expect(page.locator('.settings-overlay .overlay__form-fields-card')).toBeVisible();
      await expect(page.locator('.settings-overlay .activity-feed-list')).toBeHidden();
    });
    await screenshot(page, 'settings-activity-04-settings-tab-after-save');
  });

  test('create ad with title+image shows as single activity row', async ({ page }) => {
    const imgPath = '/tmp/settings-act-test.png';
    makePng(imgPath, 80, 120, 200);
    const adTitle = `Settings Activity Merge Test ${Date.now()}`;

    await createAd(page, { title: adTitle, description: 'merge test', imagePath: imgPath });

    await openSettings(page);
    await openActivityTab(page);

    await test.step('Ad with image shows as one row in settings activity, not two', async () => {
      const adRowCount = await page.locator('.base-overlay.overlay--visible .activity-feed-row')
        .filter({ hasText: adTitle }).count();
      if (adRowCount !== 1) throw new Error(`Expected 1 activity row for ad, got ${adRowCount} — text and image changes must be merged`);
    });

    await test.step('Single row contains both field change and image change', async () => {
      const rows = page.locator('.base-overlay.overlay--visible .activity-feed-row');
      const firstAdRow = rows.filter({ hasText: adTitle }).first();
      const rowText = await firstAdRow.textContent();
      if (!rowText.includes('медіа') && !rowText.includes('media'))
        throw new Error('Image change not found in combined activity row');
      if (!rowText.includes(adTitle))
        throw new Error('Title change not found in combined activity row');
    });
    await screenshot(page, 'settings-activity-03-merge-check');

    if (fs.existsSync(imgPath)) fs.unlinkSync(imgPath);
  });
});
