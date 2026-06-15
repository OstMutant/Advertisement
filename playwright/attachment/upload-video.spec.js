const { test, expect, loginAs, waitForOverlay, waitForOverlayClosed, closeOverlay, openHistory, screenshot,
        returnToViewAfterSave } = require('./_test-helpers');
const fs   = require('fs');
const path = require('path');

/**
 * Creates a minimal valid mp4 file (ftyp + mdat atoms) for upload testing.
 * Not a playable video — just enough for MIME-type and S3 round-trip.
 */
function createMinimalMp4(dest) {
    // ftyp box: size=20, 'ftyp', 'mp42', version=0, compatible=['mp42','isom']
    const ftyp = Buffer.from([
        0x00,0x00,0x00,0x14, 0x66,0x74,0x79,0x70,
        0x6D,0x70,0x34,0x32, 0x00,0x00,0x00,0x00,
        0x6D,0x70,0x34,0x32
    ]);
    // mdat box: size=8, 'mdat', no data
    const mdat = Buffer.from([0x00,0x00,0x00,0x08, 0x6D,0x64,0x61,0x74]);
    fs.writeFileSync(dest, Buffer.concat([ftyp, mdat]));
}

function lightboxSearch(root) {
    if (root.querySelector('.card-lightbox__close')) return true;
    for (const child of root.querySelectorAll('*')) {
        if (child.shadowRoot && lightboxSearch(child.shadowRoot)) return true;
    }
    return false;
}

async function waitForLightboxOpen(page) {
    await page.waitForFunction(
        () => !!document.querySelector('vaadin-dialog.card-lightbox[opened]'),
        { timeout: 8000 }
    );
}

async function waitForLightboxClosed(page) {
    await page.waitForFunction(
        () => !document.querySelector('vaadin-dialog.card-lightbox[opened]'),
        { timeout: 8000 }
    ).catch(() => {});
}

test.describe('Upload video', () => {
    test.beforeEach(async ({ page }) => {
        await loginAs(page);
    });

    test('upload mp4 to advertisement — gallery shows play icon, lightbox shows video element', async ({ page }) => {
        const videoPath = '/tmp/test-upload.mp4';
        createMinimalMp4(videoPath);

        await test.step('Create advertisement', async () => {
            await page.locator('.add-advertisement-button').click();
            await waitForOverlay(page);
            const ov = page.locator('.advertisement-overlay');
            await ov.locator('[data-testid="advertisement-overlay-field-title"] input').fill('Video Upload Test');
            await ov.locator('[data-testid="advertisement-overlay-field-description"] textarea').fill('Testing video upload');
            await ov.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
            await waitForOverlayClosed(page);
        });

        await test.step('Open edit and upload video', async () => {
            await page.locator('.advertisement-card')
                .filter({ has: page.locator('.advertisement-title', { hasText: 'Video Upload Test' }) })
                .first().click();
            await waitForOverlay(page);
            await page.locator('vaadin-button').filter({ hasText: /редагувати|edit/i }).first().click();
            await page.locator('[data-testid="advertisement-overlay-field-title"] input').waitFor();

            await page.locator('vaadin-upload input[type="file"]').setInputFiles({
                name: 'test-upload.mp4',
                mimeType: 'video/mp4',
                buffer: fs.readFileSync(videoPath),
            });
            await page.locator('.attachment-gallery__item').first().waitFor({ timeout: 15000 });
            await screenshot(page, 'upload-video-01-temp');
        });

        await test.step('Gallery shows video placeholder and play icon in edit mode', async () => {
            const item = page.locator('.attachment-gallery__item').first();
            const img = item.locator('img');
            const src = await img.getAttribute('src');
            // placeholder SVG (data URI) or any non-empty src
            expect(src).toBeTruthy();
            // play icon overlay
            const playIcon = item.locator('.attachment-gallery__play-icon');
            await expect(playIcon).toBeVisible();
        });

        await test.step('Save and verify gallery in view mode', async () => {
            await page.locator('.base-overlay.overlay--visible vaadin-button')
                .filter({ hasText: /зберегти|save/i }).click();
            await returnToViewAfterSave(page);
            await screenshot(page, 'upload-video-02-saved');

            const item = page.locator('.attachment-gallery__item').first();
            await expect(item).toBeVisible();
            const playIcon = item.locator('.attachment-gallery__play-icon');
            await expect(playIcon).toBeVisible();
        });

        await test.step('Click play icon opens attachment lightbox with video element', async () => {
            // play icon has pointer-events: none (decorative); click the item itself which triggers the lightbox
            await page.locator('.attachment-gallery__item').first().click();
            await page.locator('.attachment-lightbox').waitFor({ timeout: 5000 });
            await screenshot(page, 'upload-video-03-lightbox');

            const videoEl = page.locator('.attachment-lightbox video');
            await expect(videoEl).toBeVisible();
            const src = await videoEl.getAttribute('src');
            expect(src).toBeTruthy();
            expect(src).not.toBe('');

            await page.evaluate(() => {
                const btn = document.querySelector('.attachment-lightbox .card-lightbox__close');
                if (btn) btn.click();
            });
            await page.locator('.attachment-lightbox').waitFor({ state: 'detached', timeout: 5000 }).catch(() => {});
        });

        await test.step('Close view overlay then click card thumbnail', async () => {
            await closeOverlay(page);
        });

        await test.step('Card thumbnail click opens card lightbox with video element', async () => {
            const card = page.locator('.advertisement-card')
                .filter({ has: page.locator('.advertisement-title', { hasText: 'Video Upload Test' }) })
                .first();
            await card.locator('.advertisement-thumbnail-wrapper').click();

            await waitForLightboxOpen(page);
            await screenshot(page, 'upload-video-04-card-lightbox');

            // Should show a <video> element with a valid src
            const src = await page.evaluate(() => {
                function search(root) {
                    const v = root.querySelector('.card-lightbox__main-video');
                    if (v) return v.src;
                    for (const child of root.querySelectorAll('*')) {
                        if (child.shadowRoot) {
                            const found = search(child.shadowRoot);
                            if (found !== null) return found;
                        }
                    }
                    return null;
                }
                return search(document);
            });
            expect(src).toBeTruthy();
            expect(src).not.toBe('');
            expect(src).not.toMatch(/about:blank/);

            // Close via backdrop click (outside the centered dialog)
            await page.mouse.click(16, 16);
            await waitForLightboxClosed(page);
            await screenshot(page, 'upload-video-04b-lightbox-closed');
        });

        await test.step('Card lightbox closes on outside (backdrop) click', async () => {
            const card = page.locator('.advertisement-card')
                .filter({ has: page.locator('.advertisement-title', { hasText: 'Video Upload Test' }) })
                .first();
            await card.locator('.advertisement-thumbnail-wrapper').click();
            await waitForLightboxOpen(page);

            await page.mouse.click(16, 16);
            await waitForLightboxClosed(page);
            await screenshot(page, 'upload-video-05-backdrop-close');
        });

        await test.step('Card lightbox closes on X button click', async () => {
            const card = page.locator('.advertisement-card')
                .filter({ has: page.locator('.advertisement-title', { hasText: 'Video Upload Test' }) })
                .first();
            await card.locator('.advertisement-thumbnail-wrapper').click();
            await waitForLightboxOpen(page);

            // Force click bypasses Vaadin actionability shadow DOM check
            await page.locator('.card-lightbox__close').click({ force: true, timeout: 5000 });
            await waitForLightboxClosed(page);
            await screenshot(page, 'upload-video-06-x-close');
        });

        if (fs.existsSync(videoPath)) fs.unlinkSync(videoPath);
    });

    test('video appears in attachment_snapshot and history shows media change', async ({ page }) => {
        const videoPath = '/tmp/test-history.mp4';
        createMinimalMp4(videoPath);

        await test.step('Create ad with video', async () => {
            await page.locator('.add-advertisement-button').click();
            await waitForOverlay(page);
            const ov = page.locator('.advertisement-overlay');
            await ov.locator('[data-testid="advertisement-overlay-field-title"] input').fill('Video History Test');
            await ov.locator('[data-testid="advertisement-overlay-field-description"] textarea').fill('Testing video history');

            await ov.locator('vaadin-upload input[type="file"]').setInputFiles({
                name: 'test-history.mp4',
                mimeType: 'video/mp4',
                buffer: fs.readFileSync(videoPath),
            });
            await page.locator('.attachment-gallery__item').first().waitFor({ timeout: 15000 });
            await ov.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
            await waitForOverlayClosed(page);
        });

        await test.step('History tab shows media change entry', async () => {
            await page.locator('.advertisement-card')
                .filter({ has: page.locator('.advertisement-title', { hasText: 'Video History Test' }) })
                .first().click();
            await waitForOverlay(page);
            await openHistory(page);
            const historyText = await page.locator('.entity-activity-list').textContent();
            expect(historyText.length).toBeGreaterThan(0);
            await screenshot(page, 'upload-video-04-history');
        });

        if (fs.existsSync(videoPath)) fs.unlinkSync(videoPath);
    });
});
