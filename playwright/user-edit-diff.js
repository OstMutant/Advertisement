const { chromium } = require('playwright');
const { check, screenshot, login, waitForOverlay } = require('./_common');

const UX = process.argv.includes('--ux');

(async () => {
    const browser = await chromium.launch();
    const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });

    await login(page, 'user3@example.com', 'password');

    // Go to Users tab
    await page.locator('vaadin-tab').filter({ hasText: /users|юзер|користувач/i }).first().click();
    await page.waitForSelector('vaadin-grid.user-grid', { timeout: 8000 });
    await screenshot(page, 'userdiff-01-users-list', UX);

    // Open first user
    const nameCells = page.locator('vaadin-grid.user-grid vaadin-grid-cell-content .user-grid-name');
    await nameCells.first().click();
    await waitForOverlay(page);
    await screenshot(page, 'userdiff-02-user-opened', UX);

    // Click Edit (if visible — admin can edit others)
    const editBtn = page.locator('.base-overlay.overlay--visible vaadin-button').filter({ hasText: /edit|редагувати/i });
    const editBtnCount = await editBtn.count();
    if (editBtnCount === 0) {
        console.log('No edit button — try next user');
        await page.keyboard.press('Escape');
        await page.waitForTimeout(500);
        await nameCells.nth(1).click();
        await waitForOverlay(page);
        await page.locator('.base-overlay.overlay--visible vaadin-button').filter({ hasText: /edit|редагувати/i }).first().click();
    } else {
        await editBtn.first().click();
    }
    await page.waitForSelector('.base-overlay.overlay--visible vaadin-text-field input', { timeout: 5000 });
    await screenshot(page, 'userdiff-03-edit-form', UX);

    // Change the name — toggle between " Edited" and " Updated" to ensure actual diff
    const nameInput = page.locator('.base-overlay.overlay--visible vaadin-text-field input').first();
    const oldName = await nameInput.inputValue();
    const base = oldName.replace(/ (Edited|Updated)$/, '');
    const newName = oldName.endsWith(' Edited') ? base + ' Updated' : base + ' Edited';
    await nameInput.fill(newName);
    console.log('Renamed:', oldName, '->', newName);

    // Save
    await page.locator('.base-overlay.overlay--visible vaadin-button').filter({ hasText: /зберегти|save/i }).first().click();
    // After save from VIEW → returns to VIEW mode; wait for overlay still visible
    await page.waitForSelector('.base-overlay.overlay--visible', { timeout: 5000 });
    await page.waitForTimeout(400);
    await screenshot(page, 'userdiff-04-saved', UX);

    // Close the user overlay (save returns to VIEW, not list)
    await page.locator('.base-overlay.overlay--visible vaadin-button').filter({ hasText: /користувач|users/i }).first().click();
    await page.waitForTimeout(400);

    // Open Settings and switch to Activity tab
    await page.locator('vaadin-button').filter({ hasText: /налаштування|settings/i }).first().click();
    await waitForOverlay(page);
    await page.locator('.base-overlay.overlay--visible vaadin-tab')
        .filter({ hasText: /activ|активн/i }).click();
    await page.waitForSelector('.user-activity-list', { timeout: 5000 });
    await screenshot(page, 'userdiff-05-settings-activity', UX);

    await check('Changes summary in activity', async () => {
        const changes = page.locator('.user-activity-changes');
        const count = await changes.count();
        console.log('      changes elements:', count);
        if (count === 0) throw new Error('No changes summary found');
        const texts = await changes.allTextContents();
        console.log('      changes texts:', texts);
    });

    console.log('user-edit-diff: all checks passed');
    await browser.close();
})();
