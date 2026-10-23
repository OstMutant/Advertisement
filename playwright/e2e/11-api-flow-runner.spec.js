/* ── Header ───────────────────────────────────────────────────────────────
 * Description: Browser check of the developer-only API flow runner page -- opens the page, runs the
 *   Postman collection's flows and asserts the live diagram colours every step (the steps that are
 *   off by default or whose condition is not met stay skipped; the administrator registration runs
 *   only when the default login fails), then runs it again with a too-short user password and
 *   asserts the first failure stops the chain. Each flow has a card with a checkbox (Authentication
 *   is required and locked); a test unchecks the Moderation card and asserts its steps are all
 *   skipped. Clicking a card opens the flow with its settings text area and step rows; a test sets
 *   a too-short user password there and runs. The whole file skips itself when the page answers 404
 *   (a stack deployed without --dev-tools).
 * Usage: run via the Playwright test runner -- `bash /app/playwright/run.sh 11-api-flow-runner --ux`
 *   after `bash scripts/deploy-and-run.sh --reset-only-db --dev-tools`, or as part of the full e2e
 *   suite (`bash /app/playwright/run.sh e2e --ux`).
 * Uses: @playwright/test (via ./_helpers).
 * Env: None.
 * Input: ./_helpers (test, expect, screenshot); the page at /dev-tools/api-flows/ and the seeded
 *   admin account the collection's default credentials point at.
 * Outputs: Playwright HTML report entries (one per test). The passing run creates one
 *   advertisement owned by the seeded admin.
 * Returns: exit code from the Playwright test runner -- 0 when every test in this file passes,
 *   non-zero otherwise.
 * ───────────────────────────────────────────────────────────────────────── */
const { test, expect, screenshot } = require('./_helpers');

const PAGE_URL = '/dev-tools/api-flows/';

test.describe.serial('API flow runner page', () => {

  test.beforeEach(async ({ request }) => {
    const response = await request.get(`${PAGE_URL}index.html`);
    test.skip(response.status() === 404, 'dev tools are not enabled on this stack');
  });

  test('developer runs the default flow on the runner page — every step coloured passed or skipped (the administrator registration only when the default login fails), one result row per step, one card per flow, the Authentication flow opened with its auth.user response, screenshots at start, running and end', async ({ page }) => {
    await page.goto(PAGE_URL);
    await screenshot(page, 'flow-runner-start');
    await expect(page.locator('#run-button')).toBeEnabled();
    const cards = await page.locator('#flow-map .flow-card').count();
    expect(cards).toBeGreaterThan(0);
    await expect(page.locator('#flow-map .flow-card[data-flow="Authentication"] input[type="checkbox"]')).toBeChecked();
    await expect(page.locator('#flow-map .flow-card[data-flow="Authentication"] input[type="checkbox"]')).toBeDisabled();

    await page.click('#run-button');
    await expect(page.locator('#diagram g.step.passed').first()).toBeVisible();
    await screenshot(page, 'flow-runner-running');

    await expect(page.locator('#run-button')).toBeEnabled({ timeout: 60000 });
    const rows = await page.locator('#results li').count();
    expect(rows).toBeGreaterThan(0);
    await expect(page.locator('#diagram g.step.failed')).toHaveCount(0);
    expect(cards).toBe(await page.locator('#diagram text.flow-name').count());
    await page.click('#flow-map .flow-card[data-flow="Authentication"] button.open');
    const authRows = await page.locator('#flow-detail li[data-step^="auth."]').count();
    expect(authRows).toBeGreaterThan(0);
    expect(authRows).toBe(await page.locator('#diagram g.step[data-step^="auth."]').count());
    await page.click('#flow-detail li[data-step="auth.user"] button.main');
    await page.click('#flow-detail button[data-tab="response"]');
    await expect(page.locator('#flow-detail .step-panel .meta .pill')).toHaveText('201');
    const passed = await page.locator('#diagram g.step.passed').count();
    const skipped = await page.locator('#diagram g.step.skipped').count();
    expect(passed + skipped).toBe(rows);
    expect(passed).toBeGreaterThan(0);
    await expect(page.locator('#flow-map .flow-card .pill.failed')).toHaveCount(0);
    await expect(page.locator('#flow-map .flow-card .pill')).toHaveCount(cards);
    await screenshot(page, 'flow-runner-passed');
  });

  test('developer unchecks the Moderation flow and runs — every Moderation step coloured skipped, nothing failed, the other flows passed, the opened Moderation flow says flow not selected', async ({ page }) => {
    await page.goto(PAGE_URL);
    await page.locator('#flow-map .flow-card[data-flow="Moderation"] input[type="checkbox"]').uncheck();

    await page.click('#run-button');
    await expect(page.locator('#run-button')).toBeEnabled({ timeout: 60000 });

    const moderation = await page.locator('#diagram g.step[data-step^="moderation."]').count();
    expect(moderation).toBeGreaterThan(0);
    await expect(page.locator('#diagram g.step[data-step^="moderation."].skipped')).toHaveCount(moderation);
    await expect(page.locator('#diagram g.step.failed')).toHaveCount(0);
    await expect(page.locator('#flow-map .flow-card[data-flow="Moderation"] .pill.skipped')).toBeVisible();
    await page.click('#flow-map .flow-card[data-flow="Moderation"] button.open');
    await page.locator('#flow-detail li[data-step^="moderation."] button.main').first().click();
    await page.click('#flow-detail button[data-tab="response"]');
    await expect(page.locator('#flow-detail .step-panel')).toContainText('flow not selected');
    await screenshot(page, 'flow-runner-unchecked');
  });

  test('developer sets a too-short user password in the Authentication settings and runs — invalid JSON rejected without a run, auth.user coloured failed, auth.key passed, rest skipped, run button enabled again', async ({ page }) => {
    await page.goto(PAGE_URL);
    await expect(page.locator('#run-button')).toBeEnabled();
    await page.click('#flow-map .flow-card[data-flow="Authentication"] button.open');

    await page.fill('#flow-settings', '{');
    await page.click('#apply-button');
    await expect(page.locator('#flow-detail .msg')).toContainText('not valid JSON');
    await expect(page.locator('#diagram g.step')).toHaveCount(0);

    await page.fill('#flow-settings', '{"userPassword": "short"}');
    await page.click('#apply-button');

    await expect(page.locator('#diagram g.step.failed')).toHaveCount(1, { timeout: 30000 });
    await expect(page.locator('#run-button')).toBeEnabled({ timeout: 60000 });
    await expect(page.locator('#diagram g.step.failed')).toHaveCount(1);
    await expect(page.locator('#diagram g.step.failed')).toHaveAttribute('data-step', 'auth.user');
    await expect(page.locator('#diagram g.step[data-step="auth.key"]')).toHaveClass(/passed/);
    await screenshot(page, 'flow-runner-failed');
  });
});
