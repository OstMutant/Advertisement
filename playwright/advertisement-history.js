const { chromium } = require('playwright');
const { check, screenshot, login } = require('./_common');

const UX = process.argv.includes('--ux');

(async () => {
    const browser = await chromium.launch();
    const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });

    await login(page);

    // Create an advertisement
    await page.waitForSelector('vaadin-button', { timeout: 5000 });
    await page.locator('vaadin-button').filter({ hasText: /new|add|create|нове|додати/i }).first().click();
    await page.waitForTimeout(1500);

    const overlay = page.locator('.advertisement-overlay');
    await overlay.locator('vaadin-text-field input').fill('History Test Ad');
    await overlay.locator('vaadin-text-area textarea').fill('Original description v1');
    await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save|submit/i }).click();
    await page.waitForTimeout(2000);
    await screenshot(page, 'history-01-ad-created', UX);

    // Find and open the created ad
    await page.locator('text=History Test Ad').first().click();
    await page.waitForTimeout(1000);
    await screenshot(page, 'history-02-ad-opened', UX);

    // Click History tab
    const historyTab = overlay.locator('vaadin-tab').filter({ hasText: /history|istoriya|Історія|History/i });
    await historyTab.click();
    await page.waitForTimeout(800);
    await screenshot(page, 'history-03-history-tab', UX);

    await check('History list visible', async () => {
        const visible = await page.locator('.adv-history-list').isVisible();
        if (!visible) throw new Error('adv-history-list not visible');
    });

    const rowCountInit = await page.locator('.adv-history-row').count();
    await check('History has entries after create', async () => {
        if (rowCountInit === 0) throw new Error('No history rows');
        console.log('      rows:', rowCountInit);
    });

    // Switch to view tab and edit the ad
    const viewTab = overlay.locator('vaadin-tab').filter({ hasText: /view|перегляд/i });
    await viewTab.click();
    await page.waitForTimeout(400);

    await overlay.locator('vaadin-button').filter({ hasText: /edit|редагувати/i }).first().click();
    await page.waitForTimeout(800);

    await overlay.locator('vaadin-text-area textarea').fill('Updated description v2');
    await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save|submit/i }).click();
    await page.waitForTimeout(2000);
    await screenshot(page, 'history-04-after-edit', UX);

    // Overlay is still open in view mode — click History tab directly
    await overlay.locator('vaadin-tab').filter({ hasText: /history|Історія|History/i }).click();
    await page.waitForTimeout(800);
    await screenshot(page, 'history-05-history-with-diff', UX);

    const rowCountAfter = await page.locator('.adv-history-row').count();
    await check('History has 2+ entries after edit', async () => {
        if (rowCountAfter < 2) throw new Error(`Expected >=2 rows, got ${rowCountAfter}`);
        console.log('      rows after edit:', rowCountAfter);
    });

    await check('Changes summary shown', async () => {
        const count = await page.locator('.adv-history-changes').count();
        console.log('      changes summary elements:', count);
        if (count === 0) throw new Error('No changes summary found');
        const text = await page.locator('.adv-history-changes').first().textContent();
        console.log('      changes text:', text);
    });

    // Restore from oldest snapshot (last row = v1)
    const restoreBtns = page.locator('.adv-history-restore-btn');
    const btnCount = await restoreBtns.count();
    await check('Restore buttons present', async () => {
        if (btnCount === 0) throw new Error('No restore buttons');
        console.log('      restore buttons:', btnCount);
    });

    await restoreBtns.last().click();
    await page.waitForTimeout(800);
    await screenshot(page, 'history-06-restore-confirm-dialog', UX);

    // Confirm the restore dialog (Vaadin dialog renders in portal — check overlay)
    await check('Restore confirm dialog shown', async () => {
        const overlay = page.locator('vaadin-dialog-overlay');
        const visible = await overlay.isVisible();
        if (!visible) throw new Error('Confirm dialog overlay not visible');
        const label = await page.locator('vaadin-dialog[opened]').getAttribute('aria-label');
        console.log('      dialog title:', label);
    });

    // Vaadin dialog footer buttons are in light DOM of vaadin-dialog
    const dialogBtns = await page.evaluate(() => {
        const dialog = document.querySelector('vaadin-dialog[opened]');
        if (!dialog) return [];
        const btns = [...dialog.querySelectorAll('vaadin-button')];
        return btns.map(b => b.textContent?.trim());
    });
    console.log('      dialog buttons:', dialogBtns);

    await page.evaluate(() => {
        const dialog = document.querySelector('vaadin-dialog[opened]');
        if (!dialog) return;
        const btns = [...dialog.querySelectorAll('vaadin-button')];
        const btn = btns.find(b => /Оновити|Update/.test(b.textContent?.trim()));
        if (btn) btn.click();
    });
    await page.waitForTimeout(200);
    await page.waitForTimeout(1500);
    await screenshot(page, 'history-07-after-restore', UX);

    await check('View shown after restore', async () => {
        const visible = await page.locator('.overlay__view-title').isVisible();
        if (!visible) throw new Error('view title not visible after restore');
        const title = await page.locator('.overlay__view-title').textContent();
        console.log('      title after restore:', title);
    });

    // Check that history tab now has UPDATED entry (not RESTORED)
    await overlay.locator('vaadin-tab').filter({ hasText: /history|Історія|History/i }).click();
    await page.waitForTimeout(800);
    await screenshot(page, 'history-08-history-after-restore', UX);

    await check('No RESTORED badge (all entries are UPDATED)', async () => {
        const restored = await page.locator('.adv-history-action--restored').count();
        if (restored > 0) throw new Error('RESTORED badge found but should not exist');
        const updated = await page.locator('.adv-history-action--updated').count();
        console.log('      updated badges:', updated);
        if (updated === 0) throw new Error('No UPDATED badge found after restore');
    });

    console.log('advertisement-history: all checks passed');
    await browser.close();
})();
