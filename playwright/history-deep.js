const { chromium } = require('playwright');
const { check, screenshot, login } = require('./_common');

// Deep history test: multiple versions, action badges, restore button visibility
const UX = process.argv.includes('--ux');

async function openHistoryTab(page, overlay) {
    await overlay.locator('vaadin-tab').filter({ hasText: /history|Історія/i }).click();
    await page.waitForTimeout(800);
}

(async () => {
    const browser = await chromium.launch();
    const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });

    await login(page);

    const overlay = page.locator('.advertisement-overlay');

    // ── Create advertisement ────────────────────────────────────────────
    await page.locator('vaadin-button').filter({ hasText: /додати|add/i }).first().click();
    await page.waitForTimeout(1200);
    await overlay.locator('vaadin-text-field input').fill('Deep History Ad');
    await overlay.locator('vaadin-text-area textarea').fill('Version 1 content');
    await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await page.waitForTimeout(1500);

    // Open and go to history — should have 1 row (CREATED)
    await page.locator('text=Deep History Ad').first().click();
    await page.waitForTimeout(800);
    await openHistoryTab(page, overlay);
    await screenshot(page, 'histdeep-01-after-create', UX);

    await check('CREATED badge present after create', async () => {
        const created = await page.locator('.adv-history-action--created').count();
        if (created === 0) throw new Error('No CREATED badge');
        console.log('      created badges:', created);
    });

    await check('Version v1 shown', async () => {
        const badges = await page.locator('.adv-history-version').allTextContents();
        console.log('      version badges:', badges);
        if (!badges.some(b => b.includes('v1'))) throw new Error('v1 not found. Badges: ' + badges);
    });

    await check('No restore button on only entry', async () => {
        const count = await page.locator('.adv-history-restore-btn').count();
        if (count > 0) throw new Error('Restore button should not appear on the only (latest) entry');
        console.log('      restore buttons:', count);
    });

    // ── Edit #1: change description ─────────────────────────────────────
    await overlay.locator('vaadin-tab').filter({ hasText: /view|перегляд/i }).click();
    await page.waitForTimeout(400);
    await overlay.locator('vaadin-button').filter({ hasText: /edit|редагувати/i }).first().click();
    await page.waitForTimeout(600);
    await overlay.locator('vaadin-text-area textarea').fill('Version 2 content');
    await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await page.waitForTimeout(1500);

    // ── Edit #2: change title ────────────────────────────────────────────
    await page.locator('text=Deep History Ad').first().click();
    await page.waitForTimeout(800);
    await overlay.locator('vaadin-button').filter({ hasText: /edit|редагувати/i }).first().click();
    await page.waitForTimeout(600);
    await overlay.locator('vaadin-text-field input').fill('Deep History Ad v3');
    await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await page.waitForTimeout(1500);

    // Open updated ad and check history
    await page.locator('text=Deep History Ad v3').first().click();
    await page.waitForTimeout(800);
    await openHistoryTab(page, overlay);
    await screenshot(page, 'histdeep-02-three-versions', UX);

    await check('3 history rows after 2 edits', async () => {
        const rows = await page.locator('.adv-history-row').count();
        console.log('      rows:', rows);
        if (rows < 3) throw new Error(`Expected >=3 rows, got ${rows}`);
    });

    await check('Version badges go v3, v2, v1 (newest first)', async () => {
        const badges = await page.locator('.adv-history-version').allTextContents();
        console.log('      version badges:', badges);
        if (!badges[0].includes('v3')) throw new Error('Expected v3 first, got: ' + badges[0]);
        if (!badges[badges.length - 1].includes('v1')) throw new Error('Expected v1 last, got: ' + badges[badges.length - 1]);
    });

    await check('Latest row (v3) has NO restore button', async () => {
        const rows = page.locator('.adv-history-row');
        const firstRow = rows.first();
        const restoreInFirst = await firstRow.locator('.adv-history-restore-btn').count();
        if (restoreInFirst > 0) throw new Error('Latest entry should not have restore button');
        console.log('      latest row restore btn count:', restoreInFirst);
    });

    await check('Older rows have restore buttons', async () => {
        const rows = page.locator('.adv-history-row');
        const rowCount = await rows.count();
        let found = 0;
        for (let i = 1; i < rowCount; i++) {
            const btn = await rows.nth(i).locator('.adv-history-restore-btn').count();
            if (btn > 0) found++;
        }
        console.log('      older rows with restore btn:', found);
        if (found === 0) throw new Error('Expected restore buttons on non-latest rows');
    });

    await check('UPDATED badges present', async () => {
        const updated = await page.locator('.adv-history-action--updated').count();
        console.log('      updated badges:', updated);
        if (updated === 0) throw new Error('No UPDATED badges found');
    });

    await check('Title diff shown in v3 changes', async () => {
        const firstSummary = page.locator('.adv-history-changes').first();
        const text = await firstSummary.textContent();
        console.log('      v3 changes:', text);
        if (!text.includes('назва') && !text.includes('title')) throw new Error('Title diff not shown: ' + text);
    });

    // ── Restore to v1 and verify ──────────────────────────────────────────
    const lastRestoreBtn = page.locator('.adv-history-restore-btn').last();
    await lastRestoreBtn.click();
    await page.waitForTimeout(600);
    await screenshot(page, 'histdeep-03-restore-dialog', UX);

    await check('Restore dialog shown', async () => {
        const dialogOverlay = page.locator('vaadin-dialog-overlay');
        const visible = await dialogOverlay.isVisible();
        if (!visible) throw new Error('Restore dialog overlay not visible');
        const btns = await page.evaluate(() => {
            const d = document.querySelector('vaadin-dialog[opened]');
            return d ? [...d.querySelectorAll('vaadin-button')].map(b => b.textContent?.trim()) : [];
        });
        console.log('      dialog buttons:', btns);
        if (btns.length === 0) throw new Error('No buttons in dialog');
    });

    await page.evaluate(() => {
        const dialog = document.querySelector('vaadin-dialog[opened]');
        if (!dialog) return;
        const btn = [...dialog.querySelectorAll('vaadin-button')].find(b => /Оновити|Update/.test(b.textContent?.trim()));
        if (btn) btn.click();
    });
    await page.waitForTimeout(1500);
    await screenshot(page, 'histdeep-04-after-restore', UX);

    await check('Title restored to v1 value', async () => {
        const title = await page.locator('.overlay__view-title').textContent();
        console.log('      title after restore:', title);
        if (!title.includes('Deep History Ad')) throw new Error('Title not restored. Got: ' + title);
    });

    await check('History grows after restore (new UPDATED entry)', async () => {
        await openHistoryTab(page, overlay);
        const rows = await page.locator('.adv-history-row').count();
        console.log('      rows after restore:', rows);
        if (rows < 4) throw new Error(`Expected >=4 rows after restore, got ${rows}`);
    });

    console.log('history-deep: all checks passed');
    await browser.close();
})();
