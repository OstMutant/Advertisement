const { check, screenshot, withBrowser, waitForOverlay, waitForOverlayClosed, openSettings, openActivityTab } = require('./_common');

// Smoke test: user activity tab in user overlay + activity in settings
withBrowser(async (page) => {
  // ── Create an advertisement so there's activity to show ──────────────
  await check('Go to advertisements tab', async () => {
    await page.locator('vaadin-tab').filter({ hasText: /advertisement|оголошен/i }).first().click();
    await page.waitForSelector('.advertisement-container', { timeout: 5000 });
  });

  await check('Open new advertisement form', async () => {
    await page.locator('vaadin-button').filter({ hasText: /додати|add/i }).first().click();
    await waitForOverlay(page);
  });

  await check('Fill and save advertisement', async () => {
    await page.locator('.base-overlay.overlay--visible vaadin-text-field input').fill('Activity Test Ad');
    await page.locator('.base-overlay.overlay--visible vaadin-text-area textarea').fill('Created for activity smoke test');
    await page.locator('.base-overlay.overlay--visible vaadin-button').filter({ hasText: /зберегти|save/i }).first().click();
    await waitForOverlayClosed(page);
  });
  await screenshot(page, 'activity-01-ad-created');

  // ── Open Users tab ───────────────────────────────────────────────────
  await check('Go to users tab', async () => {
    await page.locator('vaadin-tab').filter({ hasText: /users|юзер|користувач/i }).first().click();
    await page.waitForSelector('vaadin-grid.user-grid', { timeout: 8000 });
  });

  // ── Open view overlay for a user ─────────────────────────────────────
  await check('Open user view overlay', async () => {
    await page.locator('vaadin-grid.user-grid vaadin-grid-cell-content .user-grid-name').first().click();
    await waitForOverlay(page);
  });
  await screenshot(page, 'activity-02-user-overlay-profile');

  await check('Activity tab is present', async () => {
    const tabs = await page.locator('.base-overlay.overlay--visible vaadin-tab').allTextContents();
    console.log('      tabs:', tabs);
    const hasActivity = tabs.some(t => /activ|активн/i.test(t));
    if (!hasActivity) throw new Error('Activity tab not found. Tabs: ' + tabs.join(', '));
  });

  await check('Click Activity tab', async () => {
    await openActivityTab(page);
  });
  await screenshot(page, 'activity-03-activity-tab');

  await check('Activity list rendered', async () => {
    const content = await page.locator('.base-overlay.overlay--visible .user-activity-content').isVisible();
    if (!content) throw new Error('Activity content not visible');
    const body = await page.textContent('.base-overlay.overlay--visible .user-activity-content');
    console.log('      activity content:', body.replace(/\s+/g, ' ').trim().slice(0, 300));
  });

  await check('Advertisement entries show fields (title/description)', async () => {
    const advRows = page.locator('.user-activity-row').filter({
      has: page.locator('.user-activity-type--advertisement')
    });
    const advCount = await advRows.count();
    console.log('      advertisement rows:', advCount);
    if (advCount > 0) {
      let foundChanges = false;
      for (let i = 0; i < advCount; i++) {
        const changes = await advRows.nth(i).locator('.user-activity-changes').count();
        if (changes > 0) {
          foundChanges = true;
          const text = await advRows.nth(i).locator('.user-activity-changes').textContent();
          console.log('      ad row', i, 'changes:', text.replace(/\s+/g, ' ').trim().slice(0, 150));
          const hasTitleOrDesc = /назва|title|опис|description/i.test(text);
          if (!hasTitleOrDesc) throw new Error('Ad activity row missing title/description: ' + text);
          break;
        }
      }
      if (!foundChanges) throw new Error('No advertisement rows had .user-activity-changes');
    } else {
      console.log('      no advertisement rows found - skip check');
    }
  });

  await check('Close user overlay', async () => {
    await page.locator('.base-overlay.overlay--visible vaadin-button')
      .filter({ hasText: /закрити|close|користувачі|users/i }).last().click();
    await waitForOverlayClosed(page);
  });

  // ── Settings overlay: my activity ────────────────────────────────────
  await check('Open settings overlay', async () => {
    await openSettings(page);
  });
  await screenshot(page, 'activity-04-settings-with-activity');

  await check('My activity section visible in settings', async () => {
    const body = await page.textContent('.base-overlay.overlay--visible');
    console.log('      settings content snippet:', body.replace(/\s+/g, ' ').trim().slice(0, 300));
    if (!/activ|активн/i.test(body)) throw new Error('Activity section not found in settings');
  });

  await check('Close settings overlay', async () => {
    await page.locator('.base-overlay.overlay--visible vaadin-button')
      .filter({ hasText: /додому|home|закрити|close/i }).last().click();
    await waitForOverlayClosed(page);
  });
  await screenshot(page, 'activity-05-done');
}, { email: 'user3@example.com' });
