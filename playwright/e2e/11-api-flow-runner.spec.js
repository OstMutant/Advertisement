/* ── Header ───────────────────────────────────────────────────────────────
 * Description: Browser check of the developer-only API flow runner page -- opens the page, runs the
 *   Postman collection's flows and asserts the live diagram colours every step (the steps that are
 *   off by default or whose condition is not met stay skipped; the administrator registration runs
 *   only when the default login fails), then runs it again with a too-short user password and
 *   asserts the first failure stops the chain. Each flow has a card with a checkbox (Authentication
 *   is required and locked); a test unchecks the Moderation card and asserts its steps are all
 *   skipped. Clicking a card opens the flow with its settings text area and step rows; a test sets
 *   a too-short user password there and runs.
 *   A template bar offers Everything, Minimal and templates saved in the browser; a test applies, rejects, saves, reloads and deletes them.
 *   The whole file skips itself when the page answers 404
 *   (a stack deployed without --dev-tools).
 *   Diagram nodes show the response code badge coloured per class; a click on a node opens a card with the request and the response.
 *   The diagram nodes exist before a run and are grey; Clear and any change of the selection after a run ask for confirmation before clearing the results.
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

  test('developer runs the default flow on the runner page — every step coloured passed or skipped (the administrator registration only when the default login fails), one result row per step, one card per flow, the Authentication flow opened with its auth.user response, the response code badges coloured per class, a node click opening the request and response card, Clear and any selection change after a run asking for confirmation first, screenshots at start, running and end', async ({ page }) => {
    await page.goto(PAGE_URL);
    await screenshot(page, 'flow-runner-start');
    await expect(page.locator('#run-button')).toBeEnabled();
    const cards = await page.locator('#flow-map .flow-card').count();
    expect(cards).toBeGreaterThan(0);
    await expect(page.locator('#flow-map .flow-card[data-flow="Authentication"] input[type="checkbox"]')).toBeChecked();
    await expect(page.locator('#flow-map .flow-card[data-flow="Authentication"] input[type="checkbox"]')).toBeDisabled();
    const nodes = await page.locator('#diagram .step').count();
    expect(nodes).toBeGreaterThan(0);
    await expect(page.locator('#diagram .step:not(.pending)')).toHaveCount(0);

    await page.click('#run-button');
    await expect(page.locator('#diagram .step.passed').first()).toBeVisible();
    await screenshot(page, 'flow-runner-running');

    await expect(page.locator('#run-button')).toBeEnabled({ timeout: 60000 });
    const rows = await page.locator('#diagram .step').count();
    expect(rows).toBeGreaterThan(0);
    expect(rows).toBe(nodes);
    await expect(page.locator('#diagram .step.failed')).toHaveCount(0);
    expect(cards).toBe(await page.locator('#diagram .flow-title').count());
    await page.click('#flow-map .flow-card[data-flow="Authentication"] button.open');
    const authRows = await page.locator('#flow-detail li[data-step^="auth."]').count();
    expect(authRows).toBeGreaterThan(0);
    expect(authRows).toBe(await page.locator('#diagram .step[data-step^="auth."]').count());
    await page.click('#flow-detail li[data-step="auth.user"] button.main');
    await expect(page.locator('#flow-detail .step-panel .response-head .code')).toHaveText('201');
    await expect(page.locator('#flow-detail .step-panel .response-head .code')).toHaveClass(/code-2xx/);
    await expect(page.locator('#diagram .step[data-step="auth.user"] .code')).toHaveText('201');
    await expect(page.locator('#diagram .step[data-step="auth.revoked"] .code')).toHaveClass(/code-4xx/);
    await page.click('#diagram .step[data-step="auth.user"]');
    await expect(page.locator('#step-card')).toBeVisible();
    await expect(page.locator('#step-card .response-head .code')).toHaveText('201');
    await expect(page.locator('#step-card .response-head .code')).toHaveClass(/code-2xx/);
    await expect(page.locator('#step-card .urlrow .method-label')).toHaveText('POST');
    await expect(page.locator('#step-card .response-head')).toBeVisible();
    await screenshot(page, 'flow-runner-card');
    await page.keyboard.press('Escape');
    await expect(page.locator('#step-card')).toBeHidden();
    const passed = await page.locator('#diagram .step.passed').count();
    const skipped = await page.locator('#diagram .step.skipped').count();
    expect(passed + skipped).toBe(rows);
    expect(passed).toBeGreaterThan(0);
    await expect(page.locator('#flow-map .flow-card .pill.failed')).toHaveCount(0);
    await expect(page.locator('#flow-map .flow-card .pill')).toHaveCount(cards);
    await screenshot(page, 'flow-runner-passed');
    await page.click('#clear-button');
    await expect(page.locator('#confirm-dialog')).toBeVisible();
    await page.click('#confirm-cancel');
    await expect(page.locator('#diagram .step.passed').first()).toBeVisible();
    await page.click('#flow-map .flow-card[data-flow="Moderation"] input[type="checkbox"]');
    await expect(page.locator('#confirm-dialog')).toBeVisible();
    await page.click('#confirm-cancel');
    await expect(page.locator('#flow-map .flow-card[data-flow="Moderation"] input[type="checkbox"]')).toBeChecked();
    await page.click('#templates button[data-template="Minimal"]');
    await expect(page.locator('#confirm-dialog')).toBeVisible();
    await page.click('#confirm-cancel');
    await page.click('#clear-button');
    await page.click('#confirm-ok');
    await expect(page.locator('#diagram .step:not(.pending)')).toHaveCount(0);
    await expect(page.locator('#clear-button')).toBeDisabled();
  });

  test('developer works with templates and unchecks the Moderation flow — the label follows the selection, Minimal and pasted configurations apply, an invalid one is rejected, a saved template survives a reload and is deleted, then every Moderation step is coloured skipped, nothing failed, the other flows passed, the opened Moderation flow says flow not selected', async ({ page }) => {
    await page.goto(PAGE_URL);
    await expect(page.locator('#run-button')).toBeEnabled();
    await expect(page.locator('#template-label')).toHaveText('Everything');
    const moderationBox = page.locator('#flow-map .flow-card[data-flow="Moderation"] input[type="checkbox"]');
    await moderationBox.uncheck();
    await expect(page.locator('#template-label')).toHaveText('Custom');

    await page.click('#templates button[data-template="Minimal"]');
    await expect(page.locator('#template-label')).toHaveText('Minimal');
    await expect(page.locator('#flow-map .flow-card input[type="checkbox"]:checked')).toHaveCount(2);

    await page.fill('#template-config', '{"version": 2}');
    await page.click('#template-apply');
    await expect(page.locator('#templates .msg')).toContainText('version');
    await expect(page.locator('#template-label')).toHaveText('Minimal');

    await page.click('#templates button[data-template="Everything"]');
    const everything = await page.inputValue('#template-config');
    await moderationBox.uncheck();
    await page.fill('#template-config', everything);
    await page.click('#template-apply');
    await expect(page.locator('#template-label')).toHaveText('Everything');
    await expect(moderationBox).toBeChecked();

    await moderationBox.uncheck();
    await page.fill('#template-name', 'check');
    await page.click('#template-save');
    await expect(page.locator('#templates button[data-template="check"]')).toBeVisible();
    await expect(page.locator('#template-label')).toHaveText('check');
    await page.reload();
    await expect(page.locator('#run-button')).toBeEnabled();
    await page.click('#templates button[data-template="check"]');
    await expect(moderationBox).not.toBeChecked();
    await page.click('#templates button[aria-label="Delete check"]');
    await expect(page.locator('#templates button[data-template="check"]')).toHaveCount(0);

    await page.click('#templates button[data-template="Everything"]');
    await moderationBox.uncheck();

    await page.click('#run-button');
    await expect(page.locator('#run-button')).toBeEnabled({ timeout: 60000 });

    const moderation = await page.locator('#diagram .step[data-step^="moderation."]').count();
    expect(moderation).toBeGreaterThan(0);
    await expect(page.locator('#diagram .step[data-step^="moderation."].skipped')).toHaveCount(moderation);
    await expect(page.locator('#diagram .step.failed')).toHaveCount(0);
    await expect(page.locator('#flow-map .flow-card[data-flow="Moderation"] .pill.skipped')).toBeVisible();
    await page.click('#flow-map .flow-card[data-flow="Moderation"] button.open');
    await page.locator('#flow-detail li[data-step^="moderation."] button.main').first().click();
    await expect(page.locator('#flow-detail .step-panel')).toContainText('flow not selected');
    await expect(page.locator('#flow-detail .step-panel .response-head')).toHaveCount(0);
    await screenshot(page, 'flow-runner-unchecked');
  });

  test('developer sets a too-short user password in the Authentication settings and runs — invalid JSON rejected without a run, auth.user coloured failed, auth.key passed, rest skipped, run button enabled again', async ({ page }) => {
    await page.goto(PAGE_URL);
    await expect(page.locator('#run-button')).toBeEnabled();
    await page.click('#flow-map .flow-card[data-flow="Authentication"] button.open');

    await page.fill('#flow-settings', '{');
    await page.click('#apply-button');
    await expect(page.locator('#flow-detail .msg')).toContainText('not valid JSON');
    await expect(page.locator('#diagram .step:not(.pending)')).toHaveCount(0);

    await page.fill('#flow-settings', '{"userPassword": "short"}');
    await page.click('#apply-button');
    await page.click('#run-button');

    await expect(page.locator('#diagram .step.failed')).toHaveCount(1, { timeout: 30000 });
    await expect(page.locator('#run-button')).toBeEnabled({ timeout: 60000 });
    await expect(page.locator('#diagram .step.failed')).toHaveCount(1);
    await expect(page.locator('#diagram .step.failed')).toHaveAttribute('data-step', 'auth.user');
    await expect(page.locator('#diagram .step[data-step="auth.key"]')).toHaveClass(/passed/);
    await screenshot(page, 'flow-runner-failed');
  });
});
