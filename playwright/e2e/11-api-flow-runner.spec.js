/* ── Header ───────────────────────────────────────────────────────────────
 * Description: Browser check of the developer-only API flow runner page -- opens the page, runs the
 *   Postman collection's eight chained requests and asserts the live diagram colours every step,
 *   then runs it again with a wrong password and asserts the first failure stops the chain. The
 *   whole file skips itself when the page answers 404 (a stack deployed without --dev-tools).
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

  test('developer runs the default flow on the runner page — eight steps coloured passed, eight result rows, screenshots at start, running and end', async ({ page }) => {
    await page.goto(PAGE_URL);
    await screenshot(page, 'flow-runner-start');

    await page.click('#run-button');
    await expect(page.locator('#diagram g.step.passed').first()).toBeVisible();
    await screenshot(page, 'flow-runner-running');

    await expect(page.locator('#diagram g.step.passed')).toHaveCount(8, { timeout: 30000 });
    await expect(page.locator('#diagram g.step.failed')).toHaveCount(0);
    await expect(page.locator('#results li')).toHaveCount(8);
    await expect(page.locator('#run-button')).toBeEnabled();
    await screenshot(page, 'flow-runner-passed');
  });

  test('developer runs the flow with a wrong password — issueApiKey coloured failed, six later steps skipped, run button enabled again', async ({ page }) => {
    await page.route(COLLECTION_URL, async route => {
      const collection = await (await route.fetch()).json();
      collection.variable.find(entry => entry.key === 'password').value = 'wrongpassword';
      await route.fulfill({ json: collection });
    });
    await page.goto(PAGE_URL);

    await page.click('#run-button');

    await expect(page.locator('#diagram g.step.failed')).toHaveCount(1, { timeout: 30000 });
    await expect(page.locator('#diagram g.step.passed')).toHaveCount(1);
    await expect(page.locator('#diagram g.step.skipped')).toHaveCount(6);
    await expect(page.locator('#diagram g.step.failed')).toHaveAttribute('data-step', 'issueApiKey');
    await expect(page.locator('#run-button')).toBeEnabled();
    await screenshot(page, 'flow-runner-failed');
  });
});
