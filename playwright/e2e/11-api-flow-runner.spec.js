/* ── Header ───────────────────────────────────────────────────────────────
 * Description: Browser check of the developer-only API flow runner page -- opens the page, runs the
 *   Postman collection's flows and asserts the live diagram colours every step (the steps that are
 *   off by default or whose condition is not met stay skipped; the administrator registration runs
 *   only when the default login fails), then runs it again with a too-short user password and
 *   asserts the first failure stops the chain. Each flow has a card with a checkbox (Authentication
 *   is required and locked); a test unchecks the Moderation card and asserts its steps are all
 *   skipped. The whole file skips itself when the page answers 404 (a stack deployed without
 *   --dev-tools).
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
const COLLECTION_URL = '**/postman/marketplace-rest-api.postman_collection.json';

test.describe.serial('API flow runner page', () => {

  test.beforeEach(async ({ request }) => {
    const response = await request.get(`${PAGE_URL}index.html`);
    test.skip(response.status() === 404, 'dev tools are not enabled on this stack');
  });

  test('developer runs the default flow on the runner page — every step coloured passed or skipped (the administrator registration only when the default login fails), one result row per step, screenshots at start, running and end', async ({ page }) => {
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
    const passed = await page.locator('#diagram g.step.passed').count();
    const skipped = await page.locator('#diagram g.step.skipped').count();
    expect(passed + skipped).toBe(rows);
    expect(passed).toBeGreaterThan(0);
    await expect(page.locator('#flow-map .flow-card .pill.failed')).toHaveCount(0);
    await expect(page.locator('#flow-map .flow-card .pill')).toHaveCount(cards);
    await screenshot(page, 'flow-runner-passed');
  });

  test('developer unchecks the Moderation flow and runs — every Moderation step coloured skipped, nothing failed, the other flows passed', async ({ page }) => {
    await page.goto(PAGE_URL);
    await page.locator('#flow-map .flow-card[data-flow="Moderation"] input[type="checkbox"]').uncheck();

    await page.click('#run-button');
    await expect(page.locator('#run-button')).toBeEnabled({ timeout: 60000 });

    const moderation = await page.locator('#diagram g.step[data-step^="moderation."]').count();
    expect(moderation).toBeGreaterThan(0);
    await expect(page.locator('#diagram g.step[data-step^="moderation."].skipped')).toHaveCount(moderation);
    await expect(page.locator('#diagram g.step.failed')).toHaveCount(0);
    await expect(page.locator('#flow-map .flow-card[data-flow="Moderation"] .pill.skipped')).toBeVisible();
    await screenshot(page, 'flow-runner-unchecked');
  });

  test('developer runs the flow with a wrong user password — auth.user coloured failed, auth.key passed, rest skipped, run button enabled again', async ({ page }) => {
    await page.route(COLLECTION_URL, async route => {
      const collection = await (await route.fetch()).json();
      collection.variable.find(entry => entry.key === 'userPassword').value = 'short';
      await route.fulfill({ json: collection });
    });
    await page.goto(PAGE_URL);

    await page.click('#run-button');

    await expect(page.locator('#diagram g.step.failed')).toHaveCount(1, { timeout: 30000 });
    await expect(page.locator('#run-button')).toBeEnabled({ timeout: 60000 });
    await expect(page.locator('#diagram g.step.failed')).toHaveCount(1);
    await expect(page.locator('#diagram g.step.failed')).toHaveAttribute('data-step', 'auth.user');
    await expect(page.locator('#diagram g.step[data-step="auth.key"]')).toHaveClass(/passed/);
    await screenshot(page, 'flow-runner-failed');
  });
});
