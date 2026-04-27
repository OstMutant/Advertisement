const { chromium } = require('playwright');
const { check, screenshot, login, waitForOverlay, waitForOverlayClosed } = require('./_common');

// Settings activity: page size change creates activity entry, restore button reverts it
const UX = process.argv.includes('--ux');

async function getAdsPageSizeValue(page) {
    return page.evaluate(() => {
        const field = document.querySelector('.settings-overlay-content vaadin-integer-field');
        return field ? Number(field.value) : null;
    });
}

(async () => {
    const browser = await chromium.launch();
    const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });

    await login(page);

    // ── Open settings and record original page size ──────────────────────
    await page.locator('.header-settings-button').click();
    await waitForOverlay(page);
    await screenshot(page, 'setact-01-settings-open', UX);

    const overlay = page.locator('.base-overlay.overlay--visible');

    const originalSize = await getAdsPageSizeValue(page);
    const newSize = originalSize === 10 ? 15 : 10;
    console.log('      original adsPageSize:', originalSize, '→ newSize:', newSize);

    const saveBtn = () => overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i });
    const sizeInput = () => overlay.locator('vaadin-integer-field').first().locator('input');

    // ── Save newSize → then originalSize so activity has both snapshots ──
    // This ensures btn[0]=originalSize, btn[1]=newSize in activity (newest-first)
    await check('Change ads page size', async () => {
        await sizeInput().fill(String(newSize));
    });
    await saveBtn().click();
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1100); // ensure different created_at timestamp

    await sizeInput().fill(String(originalSize));
    await saveBtn().click();
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(200);
    await screenshot(page, 'setact-02-saved', UX);

    await check('Activity tab present in settings', async () => {
        const tabs = await page.locator('.base-overlay.overlay--visible vaadin-tab').allTextContents();
        console.log('      settings tabs:', tabs);
        if (!tabs.some(t => /activ|активн/i.test(t))) throw new Error('Activity tab not found');
    });

    await page.locator('.base-overlay.overlay--visible vaadin-tab')
        .filter({ hasText: /activ|активн/i }).click();
    await page.waitForSelector('.user-activity-list', { timeout: 5000 });
    await screenshot(page, 'setact-03-activity-tab', UX);

    await check('Settings change appears in activity', async () => {
        const rows = page.locator('.user-activity-row');
        const count = await rows.count();
        console.log('      activity rows:', count);
        if (count === 0) throw new Error('No activity rows');
        const body = await page.locator('.user-activity-list').textContent();
        console.log('      activity body:', body.replace(/\s+/g, ' ').trim().slice(0, 400));
        if (!body.includes('сторінці') && !body.includes('page')) throw new Error('Settings change summary not found in activity');
    });

    await check('Page size diff shown in activity', async () => {
        const changes = page.locator('.user-activity-changes');
        const count = await changes.count();
        console.log('      changes elements:', count);
        if (count === 0) throw new Error('No changes summary found');
        const allText = await page.locator('.user-activity-list').textContent();
        const hasDiff = allText.includes('→');
        console.log('      has → diff:', hasDiff);
        if (!hasDiff) throw new Error('No diff arrow → found in activity');
    });

    await check('Restore settings button present', async () => {
        const restoreBtns = page.locator('.user-activity-list .adv-history-restore-btn');
        const count = await restoreBtns.count();
        console.log('      restore buttons in activity:', count);
        if (count === 0) throw new Error('No restore button for settings');
        const btnText = await restoreBtns.first().textContent();
        console.log('      restore btn text:', btnText);
    });
    await screenshot(page, 'setact-04-restore-btn', UX);

    // ── Click restore — btn[0] = newest snapshot = "originalSize→newSize" stores oldSettings=originalSize ─
    // With new semantics: snapshot stores oldSettings, so btn[0]="newSize→originalSize" restores to newSize
    // Saves order: first saved newSize, then saved originalSize (current state)
    // Activity newest-first: btn[0]="newSize→originalSize"(oldSettings=newSize) → restores to newSize ✓
    const restoreBtns = page.locator('.user-activity-list .adv-history-restore-btn');
    const btnCount = await restoreBtns.count();
    const restoreBtnIndex = 0;
    console.log('      clicking restore btn index:', restoreBtnIndex, 'of', btnCount);
    await restoreBtns.nth(restoreBtnIndex).click();
    // Restore stays in overlay and updates the fields — wait for server round-trip
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(200);
    await screenshot(page, 'setact-05-after-restore', UX);

    // After restore: auto-switched to Settings tab
    await page.waitForSelector('.settings-overlay-content vaadin-integer-field', { timeout: 5000 });

    await check('Settings overlay on settings tab after restore', async () => {
        const visible = await page.locator('.base-overlay.overlay--visible').isVisible().catch(() => false);
        if (!visible) throw new Error('Overlay should stay open after restore');
        // Settings panel must be visible (tab switched)
        const settingsPanelVisible = await page.locator('.overlay__form-fields-card').isVisible();
        console.log('      settings panel visible:', settingsPanelVisible);
        if (!settingsPanelVisible) throw new Error('Settings tab should be active after restore');
    });

    await check('Page size changed after restore', async () => {
        const currentSize = await getAdsPageSizeValue(page);
        console.log('      size after restore:', currentSize, '(expected:', newSize, ')');
        if (currentSize !== newSize) throw new Error(`Expected ${newSize} after restore, got ${currentSize}`);
        console.log('      restore worked: size is now', currentSize);
    });
    await screenshot(page, 'setact-06-size-verified', UX);

    // close
    await page.locator('.base-overlay.overlay--visible vaadin-button')
        .filter({ hasText: /додому|home|закрити|close/i }).last().click();

    console.log('settings-activity: all checks passed');
    await browser.close();
})();
