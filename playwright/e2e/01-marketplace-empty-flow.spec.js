/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Verifies the unauthenticated (no-login) UI: default English locale loads with no
 *   admin controls visible, the locale switch to Ukrainian and back keeps the same
 *   admin-controls-hidden state, and the advertisement filter panel is usable (open, fill title,
 *   apply, verify status, clear, close) without being logged in. Each test opens its own page.
 *   Per test:
 *   - "app loads -- English locale, no admin controls visible": load -> UI in EN, no admin
 *     controls visible.
 *   - "language switch -- Ukrainian locale active, English restored": load -> language switch ->
 *     UI in UK with no admin controls -> language switch back -> UI in EN.
 *   - "unauthenticated user -- filter panel accessible, title filter, apply and clear": open
 *     filter -> fill a unique title -> apply -> zero results -> clear.
 * Usage: run via the Playwright test runner -- bash /app/playwright/run.sh 01-marketplace-empty-flow
 *   --ux (runs alone or with any other spec), or as part of the full suite:
 *   bash /app/playwright/run.sh e2e --ux.
 * Uses: @playwright/test (test, expect) via ./_fixtures.
 * Env: None.
 * Input: ./_fixtures (test, expect, unique), ./_flows/language-switch.flow (runOpenDefaultLocaleFlow,
 *   runSwitchToUkrainianFlow, runSwitchToEnglishFlow), ./_flows/advertisement-filter.flow
 *   (runOpenFilterPanelFlow, runFillTitleFilterFlow, runApplyFilterFlow, runVerifyFilterStatusFlow,
 *   runClearFilterFlow, runCloseFilterPanelFlow). Holds on any database for an anonymous visitor.
 * Outputs: Playwright HTML report entries for each test; this spec never logs in or creates any
 *   entity.
 * Returns: Playwright's own pass/fail exit code convention -- 0 when every test in this file
 *   passes, non-zero otherwise.
 * ──────────────────────────────────────────────────────────────────────────── */
const { test, expect } = require('./_fixtures');
const { runOpenDefaultLocaleFlow, runSwitchToUkrainianFlow, runSwitchToEnglishFlow } = require('./_flows/language-switch.flow');
const { runOpenFilterPanelFlow, runFillTitleFilterFlow, runApplyFilterFlow, runVerifyFilterStatusFlow, runClearFilterFlow, runCloseFilterPanelFlow } = require('./_flows/advertisement-filter.flow');
const { setDateRange } = require('./_flows/filter.flow');

test.describe('Language switch (no auth)', () => {
  test('app loads — English locale, no admin controls visible', async ({ page }) => {
    await runOpenDefaultLocaleFlow(page, expect);
    const bodyFont = await page.locator('body').evaluate(el => getComputedStyle(el).fontFamily);
    expect(bodyFont).toContain('Inter');
    await expect(page.getByRole('button', { name: /add advertisement|додати оголошення/i })).toBeHidden();
    await expect(page.getByRole('button', { name: /^(Edit|Редагувати)$/ }).first()).toBeHidden();
    await expect(page.getByRole('button', { name: /^(Delete|Видалити)$/ }).first()).toBeHidden();
    await expect(page.locator('.pagination-count')).toBeVisible();
    await expect(page.getByRole('tab', { name: 'Users' }).first()).toBeHidden();
    await expect(page.getByRole('tab', { name: 'Reference Data' }).first()).toBeHidden();
  });

  test('language switch — Ukrainian locale active, English restored', async ({ page }) => {
    await runOpenDefaultLocaleFlow(page, expect);

    await test.step('Ukrainian locale active', async () => {
      await runSwitchToUkrainianFlow(page, expect);
      await expect(page.getByRole('button', { name: /add advertisement|додати оголошення/i })).toBeHidden();
      await expect(page.getByRole('button', { name: /^(Edit|Редагувати)$/ }).first()).toBeHidden();
      await expect(page.getByRole('button', { name: /^(Delete|Видалити)$/ }).first()).toBeHidden();
      await expect(page.locator('.pagination-count')).toBeVisible();
      await expect(page.getByRole('tab', { name: 'Користувачі' }).first()).toBeHidden();
      await expect(page.getByRole('tab', { name: 'Довідники' }).first()).toBeHidden();
    });

    await test.step('English locale restored', async () => {
      await runSwitchToEnglishFlow(page, expect);
    });
  });

  test('unauthenticated user — filter panel accessible, title filter, apply and clear', async ({ page, unique }) => {
    const title = unique('no-match');
    await runOpenDefaultLocaleFlow(page, expect);
    await runOpenFilterPanelFlow(page, expect);
    const queryBlockBorder = await page.locator('.advertisement-query-block').evaluate(el => getComputedStyle(el).borderTopColor);
    expect(queryBlockBorder).toBe('rgb(59, 130, 246)');
    await runFillTitleFilterFlow(page, title);
    const titleField = page.locator('.advertisement-query-block .query-text').first();
    await expect(titleField).toHaveClass(/highlight-dirty/);
    await setDateRange(page, '.advertisement-query-block', 0, new Date().toISOString().slice(0, 10), undefined);
    const dateSubField = page.locator('.advertisement-query-block .query-datetime-date').first();
    const dateOutline = await dateSubField.evaluate(el => getComputedStyle(el).outlineStyle);
    expect(dateOutline).toBe('none');
    await runApplyFilterFlow(page, expect, false);
    await runVerifyFilterStatusFlow(page, expect, title);
    await runClearFilterFlow(page, expect);
    await runCloseFilterPanelFlow(page, expect);
  });
});
