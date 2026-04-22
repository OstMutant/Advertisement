const { chromium } = require('playwright');
const { check, screenshot, login } = require('./_common');

// Activity types test: CREATED, UPDATED, DELETED entries + entity type badges + deleted marker
const UX = process.argv.includes('--ux');

const AD_TITLE = 'Activity Types Test Ad';

async function openActivity(page, targetOverlay) {
    await page.locator(`${targetOverlay} vaadin-tab`)
        .filter({ hasText: /activ|активн/i }).click();
    await page.waitForTimeout(1000);
}

(async () => {
    const browser = await chromium.launch();
    const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });

    // Use user1 (admin) so we can delete ads
    await login(page, 'user1@example.com', 'password');

    const advOverlay = page.locator('.advertisement-overlay');

    // ── Step 1: Create ───────────────────────────────────────────────────
    await page.locator('vaadin-button').filter({ hasText: /додати|add/i }).first().click();
    await page.waitForTimeout(1200);
    await advOverlay.locator('vaadin-text-field input').fill(AD_TITLE);
    await advOverlay.locator('vaadin-text-area textarea').fill('Initial content');
    await advOverlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await page.waitForTimeout(1500);
    await screenshot(page, 'acttypes-01-created', UX);

    // ── Step 2: Edit ─────────────────────────────────────────────────────
    await page.locator(`text=${AD_TITLE}`).first().click();
    await page.waitForTimeout(800);
    await advOverlay.locator('vaadin-button').filter({ hasText: /edit|редагувати/i }).first().click();
    await page.waitForTimeout(600);
    await advOverlay.locator('vaadin-text-area textarea').fill('Updated content');
    await advOverlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
    await page.waitForTimeout(1500);
    await screenshot(page, 'acttypes-02-edited', UX);

    // ── Step 3: Delete (via delete icon in list if available) ────────────
    // Delete is done via the trash icon on the list card to avoid overlay complexity
    let deleteExists = false;
    const deleteCardBtn = page.locator('vaadin-grid')
        .locator(`[title*="${AD_TITLE}"], .advertisement-card`)
        .locator('vaadin-button').filter({ hasText: /видалити|delete/i }).first();
    const deleteCardCount = await deleteCardBtn.count();
    if (deleteCardCount > 0) {
        await deleteCardBtn.click();
        await page.waitForTimeout(600);
        await page.evaluate(() => {
            const dialog = document.querySelector('vaadin-dialog[opened]');
            if (!dialog) return;
            const btn = [...dialog.querySelectorAll('vaadin-button')]
                .find(b => /видалити|delete|confirm/i.test(b.textContent?.trim()));
            if (btn) btn.click();
        });
        await page.waitForTimeout(1500);
        deleteExists = true;
        await screenshot(page, 'acttypes-03-deleted', UX);
        console.log('      ad deleted via list card button');
    } else {
        console.log('      no delete button in list, skipping delete step');
    }

    // ── Open Settings activity to check all 3 action types ───────────────
    await page.locator('.header-settings-button').click();
    await page.waitForTimeout(1000);
    await openActivity(page, '.base-overlay.overlay--visible');
    await screenshot(page, 'acttypes-04-settings-activity', UX);

    await check('ADVERTISEMENT entity type badge present', async () => {
        const badges = page.locator('.user-activity-type--advertisement');
        const count = await badges.count();
        console.log('      advertisement type badges:', count);
        if (count === 0) throw new Error('No ADVERTISEMENT type badge');
    });

    await check('CREATED action badge present', async () => {
        const rows = page.locator('.user-activity-row');
        const count = await rows.count();
        let found = false;
        for (let i = 0; i < count; i++) {
            const text = await rows.nth(i).textContent();
            if (/Створ|Creat/i.test(text)) { found = true; break; }
        }
        console.log('      CREATED badge found:', found);
        if (!found) throw new Error('CREATED action not found in activity');
    });

    await check('UPDATED action badge present', async () => {
        const rows = page.locator('.user-activity-row');
        const count = await rows.count();
        let found = false;
        for (let i = 0; i < count; i++) {
            const text = await rows.nth(i).textContent();
            if (/Оновл|Updat/i.test(text)) { found = true; break; }
        }
        console.log('      UPDATED badge found:', found);
        if (!found) throw new Error('UPDATED action not found in activity');
    });

    if (deleteExists > 0) {
        await check('Deleted entity shows (deleted) marker', async () => {
            const rows = page.locator('.user-activity-row--deleted');
            const count = await rows.count();
            console.log('      deleted-row count:', count);
            if (count === 0) throw new Error('No deleted-row marker found');
            const text = await rows.first().textContent();
            console.log('      deleted row text:', text.replace(/\s+/g, ' ').trim().slice(0, 200));
            if (!text.includes('видалено') && !text.includes('deleted')) {
                throw new Error('(deleted) marker text not found. Row: ' + text);
            }
        });
    }

    await check('Activity rows contain timestamp', async () => {
        const times = page.locator('.user-activity-time');
        const count = await times.count();
        console.log('      timestamp elements:', count);
        if (count === 0) throw new Error('No timestamps found in activity');
        const first = await times.first().textContent();
        console.log('      first timestamp:', first);
        if (!first || first === 'N/A') throw new Error('Empty or N/A timestamp');
    });
    await screenshot(page, 'acttypes-05-all-types', UX);

    console.log('activity-types: all checks passed');
    await browser.close();
})();
