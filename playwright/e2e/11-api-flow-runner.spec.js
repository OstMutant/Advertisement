/* ── Header ───────────────────────────────────────────────────────────────
 * Description: Browser check of the developer-only API flow runner page and its MCP counterpart, in five independent tests. The
 *   first runs the default flow and asserts every diagram step is coloured passed or skipped with none
 *   failed; one card per flow, with the Authentication card checked and locked; the response code
 *   badges coloured per class; a node click opening the request and response card; and Clear and any
 *   selection change after a run asking for confirmation. The second unchecks the Moderation card,
 *   works with templates (Minimal, a rejected pasted configuration, a saved template that survives a
 *   reload and is deleted), then asserts every Moderation step is skipped and none failed. The third
 *   sets a too-short user password in the Authentication settings and asserts auth.user is the only
 *   failed step while auth.key passes. The fourth opens the MCP page, selects its Tools tab and asserts the catalog lists exactly
 *   the eleven tools, a listTaxons call returns a non-error result and a getMe call with the key the catalog prepared on open
 *   succeeds and, once the key field is cleared, shows HTTP 401, all through one detail panel opened by a card click and closed by clicking the card again. The fifth selects the Flows tab of the MCP page, runs the default MCP flow, asserts none failed and setup.init answered 200, and opens the account.settings step card to check its event-stream response is shown indented. The whole file skips itself when the page answers 404 or does
 *   not contain the run button (a stack deployed without --dev-tools).
 * Usage: run via the Playwright test runner -- `bash /app/playwright/run.sh 11-api-flow-runner --ux`
 *   after `bash scripts/deploy-and-run.sh --reset-only-db --dev-tools`, or as part of the full e2e
 *   suite (`bash /app/playwright/run.sh e2e --ux`).
 * Uses: @playwright/test (via ./_fixtures and ./_helpers).
 * Env: None.
 * Input: ./_fixtures (test, expect), ./_helpers (screenshot); the pages at /dev-tools/api-flows/ and /dev-tools/api-flows/mcp.html and the
 *   administrator the setup project signs up, whom the collection's default credentials point at.
 * Outputs: Playwright HTML report entries (one per test). The passing run creates only its own
 *   uniquely named test data in the app (users, API keys, taxons, provider profiles, advertisements,
 *   feedback), most of it removed again by the collection's delete steps. The MCP run creates one user,
 *   one API key, one advertisement and one provider profile, and deletes the last two.
 * Returns: exit code from the Playwright test runner -- 0 when every test in this file passes,
 *   non-zero otherwise.
 * ───────────────────────────────────────────────────────────────────────── */
const { test, expect } = require('./_fixtures');
const { screenshot } = require('./_helpers');

const PAGE_URL = '/dev-tools/api-flows/';

test.describe('API flow runner page', () => {

  test.beforeEach(async ({ request }) => {
    const response = await request.get(`${PAGE_URL}index.html`);
    test.skip(response.status() === 404 || !(await response.text()).includes('run-button'), 'dev tools are not enabled on this stack');
  });

  test('developer runs the default flow on the runner page — every step coloured passed or skipped (the administrator registration only when the default login fails), one result row per step, one card per flow, the Authentication flow opened with its auth.user response, the response code badges coloured per class, a node click opening the request and response card, Clear and any selection change after a run asking for confirmation first, screenshots at start, running and end', async ({ page }) => {
    await page.goto(PAGE_URL);
    await screenshot(page, 'flow-runner-start');
    const runButton = page.getByRole('button', { name: 'Run selected' });
    const clearButton = page.getByRole('button', { name: 'Clear', exact: true });
    const confirmDialog = page.getByRole('dialog', { name: 'Confirm clearing the last run' });
    const diagram = page.locator('#diagram');
    const includeBox = flow => page.getByRole('checkbox', { name: `Include ${flow}`, exact: true });
    await expect(runButton).toBeEnabled();
    const cards = await page.locator('#flow-map .flow-card').count();
    expect(cards).toBeGreaterThan(0);
    await expect(includeBox('Authentication')).toBeChecked();
    await expect(includeBox('Authentication')).toBeDisabled();
    const nodes = await diagram.getByRole('button').count();
    expect(nodes).toBeGreaterThan(0);
    await expect(diagram.locator('.step:not(.pending)')).toHaveCount(0);

    await runButton.click();
    await expect(diagram.locator('.step.passed').first()).toBeVisible();
    await screenshot(page, 'flow-runner-running');

    await expect(runButton).toBeEnabled({ timeout: 60000 });
    const rows = await diagram.getByRole('button').count();
    expect(rows).toBeGreaterThan(0);
    await expect(diagram.getByRole('button')).toHaveCount(nodes);
    await expect(diagram.locator('.step.failed')).toHaveCount(0);
    const keyCode = await diagram.locator('.step[data-step="auth.key"] .code').innerText();
    await expect(diagram.locator('.step[data-step="auth.admin"]')).toHaveClass(keyCode === '201' ? /skipped/ : /passed/);
    await expect(diagram.getByRole('heading', { level: 3 })).toHaveCount(cards);
    await page.locator('#flow-map .flow-card[data-flow="Authentication"]').getByRole('button').click();
    const authRows = await page.locator('#flow-detail li[data-step^="auth."]').count();
    expect(authRows).toBeGreaterThan(0);
    await expect(diagram.locator('.step[data-step^="auth."]')).toHaveCount(authRows);
    await page.locator('#flow-detail li[data-step="auth.user"]').getByRole('button').click();
    await expect(page.locator('#flow-detail .step-panel .response-head .code')).toHaveText('201');
    await expect(page.locator('#flow-detail .step-panel .response-head .code')).toHaveClass(/code-2xx/);
    await expect(diagram.locator('.step[data-step="auth.user"] .code')).toHaveText('201');
    await expect(diagram.locator('.step[data-step="auth.revoked"] .code')).toHaveClass(/code-4xx/);
    await diagram.locator('.step[data-step="auth.user"]').click();
    await expect(page.locator('#step-card')).toBeVisible();
    await expect(page.locator('#step-card .response-head .code')).toHaveText('201');
    await expect(page.locator('#step-card .response-head .code')).toHaveClass(/code-2xx/);
    await expect(page.locator('#step-card .urlrow .method-label')).toHaveText('POST');
    await expect(page.locator('#step-card .response-head')).toBeVisible();
    await screenshot(page, 'flow-runner-card');
    await page.keyboard.press('Escape');
    await expect(page.locator('#step-card')).toBeHidden();
    const passed = await diagram.locator('.step.passed').count();
    const skipped = await diagram.locator('.step.skipped').count();
    expect(passed + skipped).toBe(rows);
    expect(passed).toBeGreaterThan(0);
    await expect(page.locator('#flow-map .flow-card .pill.failed')).toHaveCount(0);
    await expect(page.locator('#flow-map .flow-card .pill')).toHaveCount(cards);
    await screenshot(page, 'flow-runner-passed');
    await clearButton.click();
    await expect(confirmDialog).toBeVisible();
    await confirmDialog.getByRole('button', { name: 'Cancel' }).click();
    await expect(diagram.locator('.step.passed').first()).toBeVisible();
    await includeBox('Moderation').click();
    await expect(confirmDialog).toBeVisible();
    await confirmDialog.getByRole('button', { name: 'Cancel' }).click();
    await expect(includeBox('Moderation')).toBeChecked();
    await page.getByRole('tab', { name: 'Minimal', exact: true }).click();
    await expect(confirmDialog).toBeVisible();
    await confirmDialog.getByRole('button', { name: 'Cancel' }).click();
    await clearButton.click();
    await confirmDialog.getByRole('button', { name: 'Clear and continue' }).click();
    await expect(diagram.locator('.step:not(.pending)')).toHaveCount(0);
    await expect(clearButton).toBeDisabled();
  });

  test('developer works with templates and unchecks the Moderation flow — the label follows the selection, Minimal and pasted configurations apply, an invalid one is rejected, a saved template survives a reload and is deleted, then every Moderation step is coloured skipped, nothing failed, the other flows passed, the opened Moderation flow says flow not selected', async ({ page }) => {
    await page.goto(PAGE_URL);
    const runButton = page.getByRole('button', { name: 'Run selected' });
    const templateLabel = page.locator('#template-label');
    const templateTab = name => page.getByRole('tab', { name, exact: true });
    const configBox = page.getByRole('textbox', { name: 'Run configuration as JSON' });
    const applyConfig = page.getByRole('button', { name: 'Apply configuration' });
    const diagram = page.locator('#diagram');
    await expect(runButton).toBeEnabled();
    await expect(templateLabel).toHaveText('Everything');
    const moderationBox = page.getByRole('checkbox', { name: 'Include Moderation', exact: true });
    await moderationBox.uncheck();
    await expect(templateLabel).toHaveText('Custom');

    await templateTab('Minimal').click();
    await expect(templateLabel).toHaveText('Minimal');
    await expect(page.locator('#flow-map').getByRole('checkbox', { checked: true })).toHaveCount(2);

    await configBox.fill('{"version": 2}');
    await applyConfig.click();
    await expect(page.locator('#templates').getByRole('status')).toContainText('version');
    await expect(templateLabel).toHaveText('Minimal');

    await templateTab('Everything').click();
    const everything = await configBox.inputValue();
    await moderationBox.uncheck();
    await configBox.fill(everything);
    await applyConfig.click();
    await expect(templateLabel).toHaveText('Everything');
    await expect(moderationBox).toBeChecked();

    await moderationBox.uncheck();
    await page.getByRole('textbox', { name: 'Template name' }).fill('check');
    await page.getByRole('button', { name: 'Save as' }).click();
    await expect(templateTab('check')).toBeVisible();
    await expect(templateLabel).toHaveText('check');
    await page.reload();
    await expect(runButton).toBeEnabled();
    await templateTab('check').click();
    await expect(moderationBox).not.toBeChecked();
    await page.getByRole('button', { name: 'Delete check' }).click();
    await expect(templateTab('check')).toHaveCount(0);

    await templateTab('Everything').click();
    await moderationBox.uncheck();

    await runButton.click();
    await expect(runButton).toBeEnabled({ timeout: 60000 });

    const moderation = await diagram.locator('.step[data-step^="moderation."]').count();
    expect(moderation).toBeGreaterThan(0);
    await expect(diagram.locator('.step[data-step^="moderation."].skipped')).toHaveCount(moderation);
    await expect(diagram.locator('.step.failed')).toHaveCount(0);
    await expect(page.locator('#flow-map .flow-card[data-flow="Moderation"] .pill.skipped')).toBeVisible();
    expect(await diagram.locator('.step.passed').count()).toBeGreaterThan(0);
    await page.locator('#flow-map .flow-card[data-flow="Moderation"]').getByRole('button').click();
    await page.locator('#flow-detail li[data-step^="moderation."]').getByRole('button').first().click();
    await expect(page.locator('#flow-detail .step-panel')).toContainText('flow not selected');
    await expect(page.locator('#flow-detail .step-panel .response-head')).toHaveCount(0);
    await screenshot(page, 'flow-runner-unchecked');
  });

  test('developer sets a too-short user password in the Authentication settings and runs — invalid JSON rejected without a run, auth.user coloured failed, auth.key passed, rest skipped, run button enabled again', async ({ page }) => {
    await page.goto(PAGE_URL);
    const runButton = page.getByRole('button', { name: 'Run selected' });
    const settingsBox = page.getByRole('textbox', { name: 'Settings for Authentication as JSON' });
    const applyButton = page.locator('#flow-detail').getByRole('button', { name: 'Apply', exact: true });
    const diagram = page.locator('#diagram');
    await expect(runButton).toBeEnabled();
    await page.locator('#flow-map .flow-card[data-flow="Authentication"]').getByRole('button').click();

    await settingsBox.fill('{');
    await applyButton.click();
    await expect(page.locator('#flow-detail').getByRole('status')).toContainText('not valid JSON');
    await expect(diagram.locator('.step:not(.pending)')).toHaveCount(0);

    await settingsBox.fill('{"userPassword": "short"}');
    await applyButton.click();
    await runButton.click();

    await expect(diagram.locator('.step.failed')).toHaveCount(1, { timeout: 30000 });
    await expect(runButton).toBeEnabled({ timeout: 60000 });
    await expect(diagram.locator('.step.failed')).toHaveCount(1);
    await expect(diagram.locator('.step.failed')).toHaveAttribute('data-step', 'auth.user');
    await expect(diagram.locator('.step[data-step="auth.key"]')).toHaveClass(/passed/);
    await expect(diagram.locator('.step[data-step="users.flowdelete"]')).toHaveClass(/skipped/);
    await screenshot(page, 'flow-runner-failed');
  });

  test('developer opens the MCP page — the catalog lists exactly the eleven tools, listTaxons with empty arguments returns a non-error result, getMe with the prepared key succeeds and without a key shows HTTP 401, getAdvertisement arguments hold a numeric id, listAdvertisements arguments carry page 0 and a locale and show the Optional hint, screenshot of the catalog', async ({ page, request }) => {
    const mcpResponse = await request.get(`${PAGE_URL}mcp.html`);
    test.skip(mcpResponse.status() === 404, 'the MCP page is not served by this stack');
    await page.goto(`${PAGE_URL}mcp.html`);
    await page.getByRole('tab', { name: 'Tools', exact: true }).click();
    const tools = page.locator('[data-tool]');
    await expect(tools).toHaveCount(11, { timeout: 30000 });
    const names = await tools.evaluateAll(entries => entries.map(entry => entry.dataset.tool).sort());
    expect(names).toEqual(['getAdvertisement', 'getMe', 'getMySettings', 'getProviderProfile', 'getProviderProfileFeedbackAggregate', 'getTaxon',
      'listAdvertisementMedia', 'listAdvertisements', 'listProviderProfileFeedback', 'listProviderProfiles', 'listTaxons']);

    const panel = page.locator('.tool-detail');
    const selectTool = name => page.locator(`[data-tool="${name}"]`).getByRole('button', { name: new RegExp(`^${name}`) }).click();

    await selectTool('listTaxons');
    await panel.getByLabel('Arguments of listTaxons').fill('{}');
    await panel.getByRole('button', { name: 'Call listTaxons' }).click();
    await expect(panel.locator('.tool-status')).toHaveAttribute('data-error', 'false');

    await selectTool('getMe');
    const keyField = page.locator('#catalog-api-key');
    await expect(keyField).not.toHaveValue('', { timeout: 30000 });
    await panel.getByRole('button', { name: 'Call getMe' }).click();
    await expect(panel.locator('.tool-status')).toHaveAttribute('data-error', 'false');
    await expect(panel.locator('.tool-result')).toContainText('"id"');
    await keyField.fill('');
    await panel.getByRole('button', { name: 'Call getMe' }).click();
    await expect(panel.locator('.tool-status')).toHaveAttribute('data-error', 'true');
    await expect(panel.locator('.tool-result')).toContainText('HTTP 401');

    await selectTool('getAdvertisement');
    const advertisementArguments = JSON.parse(await panel.getByLabel('Arguments of getAdvertisement').inputValue());
    expect(typeof advertisementArguments.id).toBe('number');
    await selectTool('listAdvertisements');
    const listArguments = JSON.parse(await panel.getByLabel('Arguments of listAdvertisements').inputValue());
    expect(listArguments.page).toBe(0);
    expect(typeof listArguments.locale).toBe('string');
    await expect(panel.locator('.tool-optional')).toContainText('Optional:');

    await selectTool('listTaxons');
    await expect(panel.getByLabel('Arguments of listTaxons')).toHaveValue('{}');
    await expect(panel.locator('.tool-status')).toHaveAttribute('data-error', 'false');
    await selectTool('listTaxons');
    await expect(panel).toBeHidden();
    await selectTool('getMe');
    await screenshot(page, 'mcp-catalog');
  });

  test('developer runs the default flow on the MCP page — none failed, every node passed or skipped, setup.init answered 200', async ({ page, request }) => {
    const mcpResponse = await request.get(`${PAGE_URL}mcp.html`);
    test.skip(mcpResponse.status() === 404, 'the MCP page is not served by this stack');
    await page.goto(`${PAGE_URL}mcp.html`);
    await page.getByRole('tab', { name: 'Flows', exact: true }).click();
    const runButton = page.getByRole('button', { name: 'Run selected' });
    const diagram = page.locator('#diagram');
    await expect(runButton).toBeEnabled();
    const nodes = await diagram.getByRole('button').count();
    expect(nodes).toBeGreaterThan(0);

    await runButton.click();
    await expect(diagram.locator('.step.passed').first()).toBeVisible();
    await expect(runButton).toBeEnabled({ timeout: 60000 });
    await expect(diagram.getByRole('button')).toHaveCount(nodes);
    await expect(diagram.locator('.step.failed')).toHaveCount(0);
    await expect(diagram.locator('.step[data-step="setup.init"] .code')).toHaveText('200');
    const passed = await diagram.locator('.step.passed').count();
    const skipped = await diagram.locator('.step.skipped').count();
    expect(passed + skipped).toBe(nodes);
    expect(passed).toBeGreaterThan(0);
    await diagram.locator('.step[data-step="account.settings"]').click();
    await expect(page.locator('#step-card')).toBeVisible();
    await expect(page.locator('#step-card pre').last()).toContainText('  "jsonrpc": "2.0"');
    await page.keyboard.press('Escape');
    await screenshot(page, 'mcp-flow-passed');
  });
});
