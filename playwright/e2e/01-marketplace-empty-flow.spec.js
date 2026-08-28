/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: First spec in the e2e suite -- verifies the unauthenticated (no-login) UI: default
 *   English locale loads with no admin controls visible, locale switch to Ukrainian and back keeps
 *   the same admin-controls-hidden state, and the advertisement filter panel is usable (open, fill
 *   title, apply, verify status, clear, close) without being logged in. Per test:
 *   - "app loads -- English locale, no admin controls visible": load -> UI in EN, no admin
 *     controls visible.
 *   - "language switch -- Ukrainian locale active": language switch -> UI in UK.
 *   - "unauthenticated user -- filter panel accessible, title filter, apply and clear": open
 *     filter -> fill title -> apply -> clear.
 *   - "language switch -- English locale restored": language switch -> UI in EN.
 * Usage: run via the Playwright test runner -- bash /app/playwright/run.sh 01-marketplace-empty-flow
 *   --ux, or as the first spec of the full suite: bash /app/playwright/run.sh e2e --ux.
 * Uses: @playwright/test (test, expect).
 * Env: None.
 * Input: ./_flows/language-switch.flow (runOpenDefaultLocaleFlow, runSwitchToUkrainianFlow,
 *   runSwitchToEnglishFlow), ./_flows/advertisement-filter.flow (runOpenFilterPanelFlow,
 *   runFillTitleFilterFlow, runApplyFilterFlow, runVerifyFilterStatusFlow, runClearFilterFlow,
 *   runCloseFilterPanelFlow). Assumes a freshly reset database (no seeded users/ads) -- run.sh
 *   resets the app tables before the suite runs.
 * Outputs: Playwright HTML report entries for each test; no data is seeded for later specs (this
 *   spec never logs in or creates any entity) -- locale ends back on English so spec 02 starts
 *   from the same default state.
 * Returns: Playwright's own pass/fail exit code convention -- 0 when every test in this file
 *   passes, non-zero otherwise.
 * ──────────────────────────────────────────────────────────────────────────── */
const { test, expect } = require('@playwright/test');
const { runOpenDefaultLocaleFlow, runSwitchToUkrainianFlow, runSwitchToEnglishFlow } = require('./_flows/language-switch.flow');
const { runOpenFilterPanelFlow, runFillTitleFilterFlow, runApplyFilterFlow, runVerifyFilterStatusFlow, runClearFilterFlow, runCloseFilterPanelFlow } = require('./_flows/advertisement-filter.flow');

test.describe.configure({ mode: 'serial' });

test.describe('Language switch (no auth)', () => {
  let page;

  test.beforeAll(async ({ browser }) => {
    page = await browser.newPage();
  });

  test.afterAll(async () => {
    await page.close();
  });

  test('app loads — English locale, no admin controls visible', async () => {
    await runOpenDefaultLocaleFlow(page, expect);
    await expect(page.locator('.add-advertisement-button')).not.toBeVisible();
    await expect(page.locator('.advertisement-edit').first()).not.toBeVisible();
    await expect(page.locator('.advertisement-delete').first()).not.toBeVisible();
    await expect(page.locator('.pagination-count')).toBeVisible();
    await expect(page.locator('vaadin-tab').filter({ hasText: 'Users' }).first()).not.toBeVisible();
    await expect(page.locator('vaadin-tab').filter({ hasText: 'Reference Data' }).first()).not.toBeVisible();
  });

  test('language switch — Ukrainian locale active', async () => {
    await runSwitchToUkrainianFlow(page, expect);
    await expect(page.locator('.add-advertisement-button')).not.toBeVisible();
    await expect(page.locator('.advertisement-edit').first()).not.toBeVisible();
    await expect(page.locator('.advertisement-delete').first()).not.toBeVisible();
    await expect(page.locator('.pagination-count')).toBeVisible();
    await expect(page.locator('vaadin-tab').filter({ hasText: 'Користувачі' }).first()).not.toBeVisible();
    await expect(page.locator('vaadin-tab').filter({ hasText: 'Довідники' }).first()).not.toBeVisible();
  });

  test('unauthenticated user — filter panel accessible, title filter, apply and clear', async () => {
    await runOpenFilterPanelFlow(page, expect);
    await runFillTitleFilterFlow(page, 'Test');
    await runApplyFilterFlow(page, expect);
    await runVerifyFilterStatusFlow(page, expect, 'Test');
    await runClearFilterFlow(page, expect);
    await runCloseFilterPanelFlow(page, expect);
  });

  test('language switch — English locale restored', async () => {
    await runSwitchToEnglishFlow(page, expect);
  });
});
