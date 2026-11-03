/* ── Header ───────────────────────────────────────────────────────────────
 * Description: Browser check of the developer-only API flow runner page and its MCP counterpart,
 *   in six independent tests. The first runs the default flow and asserts every diagram step is
 *   coloured passed or skipped with none failed; one card per flow, with the Authentication card
 *   checked and locked; the response code badges coloured per class; a node click opening the
 *   request and response card; the flows that need an administrator carry the tag "needs an
 *   administrator" and no administrator note is shown, and the Administrator account block shows its
 *   explanation with empty e-mail and password fields and the e-mail placeholder from the served collection; and Clear and any selection change after a
 *   run asking for confirmation. The second unchecks the Moderation card, works with templates
 *   (the label follows the selection -- Custom, Administrator, Moderator or User; the Administrator,
 *   Moderator and User templates with growing sets of flows; a pasted configuration applies, an
 *   invalid one is rejected; a saved template that survives a reload and is deleted), then asserts
 *   every Moderation step is skipped and none failed, and the opened Moderation flow says "flow not
 *   selected"; typed administrator credentials stay out of the copied configuration and the saved
 *   templates. The third sets a too-short user password in the Authentication settings and asserts
 *   auth.user is the only failed step while auth.key passes. The fourth opens the MCP page, selects
 *   its Tools tab and asserts the catalog lists exactly one tool for every operation id of the live
 *   OpenAPI, the key field is empty on open and opening the page posts no /api/users request, a
 *   listTaxons call returns a non-error result, a getMe call with a pasted key succeeds and, once the
 *   key field is cleared, shows HTTP 401; the
 *   getAdvertisement arguments hold a numeric id, the listAdvertisements arguments carry page 0 and
 *   a locale and show the "Optional:" hint, and a screenshot of the catalog is taken, all through
 *   one detail panel opened by a card click and closed by clicking the card again. The fifth selects
 *   the Flows tab of the MCP page, checks the fresh page is labelled "Administrator" and that User, Moderator and Administrator switch on growing sets of flows, runs
 *   the default MCP flow, asserts none failed, setup.init answered 200 and passed plus skipped steps
 *   equal the number of nodes, and opens the account.settings step card to check its event-stream
 *   response is shown indented. The sixth runs both pages with a plain user and with a moderator whose e-mail and password
 *   are typed in the Administrator account block and asserts nothing failed, the flows that need an administrator are
 *   skipped, and the administrator note names the account and those flows. The whole file skips
 *   itself when the page answers 404 or does not contain the run button (a stack deployed without
 *   --dev-tools).
 * Usage: run via the Playwright test runner -- `bash /app/playwright/run.sh 11-api-flow-runner --ux`
 *   after `bash scripts/deploy-and-run.sh --reset-only-db --dev-tools`, or as part of the full e2e
 *   suite (`bash /app/playwright/run.sh e2e --ux`).
 * Uses: @playwright/test (via ./_fixtures and ./_helpers).
 * Env: None.
 * Input: ./_fixtures (test, expect), ./_helpers (screenshot); the pages at /dev-tools/api-flows/ and /dev-tools/api-flows/mcp.html and the
 *   administrator the setup project signs up, whom the collection's default credentials point at.
 * Outputs: Playwright HTML report entries (one per test). The passing run creates only its own
 *   uniquely named test data in the app (users, API keys, taxons, provider profiles, advertisements,
 *   feedback), most of it removed again by the collection's delete steps.
 * Returns: exit code from the Playwright test runner -- 0 when every test in this file passes,
 *   non-zero otherwise.
 * ───────────────────────────────────────────────────────────────────────── */
const { test, expect } = require('./_fixtures');
const { screenshot } = require('./_helpers');
const { issueApiKeyViaApi } = require('./_flows/seed.flow');

const PAGE_URL = '/dev-tools/api-flows/';

test.describe('API flow runner page', () => {

  test.beforeEach(async ({ request }) => {
    const response = await request.get(`${PAGE_URL}index.html`);
    test.skip(response.status() === 404 || !(await response.text()).includes('run-button'), 'dev tools are not enabled on this stack');
  });

  test('developer runs the default flow on the runner page — every step coloured passed or skipped (the administrator registration only when the default login fails), one result row per step, one card per flow, the Authentication flow opened with its auth.user response, the response code badges coloured per class, a node click opening the request and response card, Clear and any selection change after a run asking for confirmation first, flows that need an administrator tagged and no administrator note shown, the Administrator account block with its explanation and empty fields, screenshots at start, running and end', async ({ page, request }) => {
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
    await test.step('flows that need an administrator carry the tag and no note is shown', async () => {
      const collectionJson = await (await request.get(`${PAGE_URL}postman/marketplace-rest-api.postman_collection.json`)).json();
      const gated = collectionJson.item.filter(flow => String(flow.description || '').trim().startsWith('when: adminRights=yes')).map(flow => flow.name);
      expect(gated.length).toBeGreaterThan(0);
      await expect(page.locator('#flow-map .flow-card .tag', { hasText: 'needs an administrator' })).toHaveCount(gated.length);
      for (const name of gated) {
        await expect(page.locator(`#flow-map .flow-card[data-flow="${name}"] .tag`, { hasText: 'needs an administrator' })).toHaveCount(1);
      }
      await expect(page.locator('#admin-note')).toBeHidden();
      const defaultEmail = collectionJson.variable.find(entry => entry.key === 'email').value;
      const adminEmail = page.getByRole('textbox', { name: 'Administrator e-mail' });
      await expect(adminEmail).toHaveValue('');
      await expect(adminEmail).toHaveAttribute('placeholder', defaultEmail);
      await expect(page.getByLabel('Administrator password')).toHaveValue('');
      await expect(page.getByText('only when it is the first account of the database')).toBeVisible();
    });
    await clearButton.click();
    await expect(confirmDialog).toBeVisible();
    await confirmDialog.getByRole('button', { name: 'Cancel' }).click();
    await expect(diagram.locator('.step.passed').first()).toBeVisible();
    await includeBox('Moderation').click();
    await expect(confirmDialog).toBeVisible();
    await confirmDialog.getByRole('button', { name: 'Cancel' }).click();
    await expect(includeBox('Moderation')).toBeChecked();
    await page.getByRole('tab', { name: 'User', exact: true }).click();
    await expect(confirmDialog).toBeVisible();
    await confirmDialog.getByRole('button', { name: 'Cancel' }).click();
    await clearButton.click();
    await confirmDialog.getByRole('button', { name: 'Clear and continue' }).click();
    await expect(diagram.locator('.step:not(.pending)')).toHaveCount(0);
    await expect(clearButton).toBeDisabled();
  });

  test('developer works with templates and unchecks the Moderation flow — the label follows the selection, the Administrator, Moderator and User templates switch on growing sets of flows, pasted configurations apply, an invalid one is rejected, a saved template survives a reload and is deleted, then every Moderation step is coloured skipped, nothing failed, the other flows passed, the opened Moderation flow says flow not selected, credentials typed in the Administrator account block kept out of the copied configuration and the saved templates', async ({ page }) => {
    await page.goto(PAGE_URL);
    const runButton = page.getByRole('button', { name: 'Run selected' });
    const templateLabel = page.locator('#template-label');
    const templateTab = name => page.getByRole('tab', { name, exact: true });
    const configBox = page.getByRole('textbox', { name: 'Run configuration as JSON' });
    const applyConfig = page.getByRole('button', { name: 'Apply configuration' });
    const diagram = page.locator('#diagram');
    await expect(runButton).toBeEnabled();
    await expect(templateLabel).toHaveText('Administrator');
    const moderationBox = page.getByRole('checkbox', { name: 'Include Moderation', exact: true });
    await moderationBox.uncheck();
    await expect(templateLabel).toHaveText('Custom');

    const checkedFlows = () => page.locator('#flow-map').getByRole('checkbox', { checked: true });
    const flowBox = name => page.getByRole('checkbox', { name: `Include ${name}`, exact: true });
    await moderationBox.check();
    const cardCount = await page.locator('#flow-map .flow-card').count();
    await expect(checkedFlows()).toHaveCount(cardCount);
    await templateTab('User').click();
    await expect(templateLabel).toHaveText('User');
    const userFlows = await checkedFlows().count();
    await expect(flowBox('Taxons')).not.toBeChecked();
    await expect(flowBox('Moderation')).not.toBeChecked();
    await expect(flowBox('Users')).not.toBeChecked();
    await templateTab('Moderator').click();
    await expect(templateLabel).toHaveText('Moderator');
    const moderatorFlows = await checkedFlows().count();
    await expect(flowBox('Taxons')).toBeChecked();
    await expect(flowBox('Moderation')).toBeChecked();
    await expect(flowBox('Users')).not.toBeChecked();
    await templateTab('Administrator').click();
    await expect(templateLabel).toHaveText('Administrator');
    await expect(flowBox('Users')).toBeChecked();
    expect(userFlows).toBeLessThan(moderatorFlows);
    expect(moderatorFlows).toBeLessThan(cardCount);

    await configBox.fill('{"version": 2}');
    await applyConfig.click();
    await expect(page.locator('#templates').getByRole('status')).toContainText('version');
    await expect(templateLabel).toHaveText('Administrator');

    await templateTab('Administrator').click();
    const administrator = await configBox.inputValue();
    await moderationBox.uncheck();
    await configBox.fill(administrator);
    await applyConfig.click();
    await expect(templateLabel).toHaveText('Administrator');
    await expect(moderationBox).toBeChecked();

    await moderationBox.uncheck();
    const secretEmail = 'secret.admin@example.com';
    const secretPassword = 'S3cret-Passw0rd-check';
    await page.getByRole('textbox', { name: 'Administrator e-mail' }).fill(secretEmail);
    await page.getByLabel('Administrator password').fill(secretPassword);
    await page.getByRole('textbox', { name: 'Template name' }).fill('check');
    await page.getByRole('button', { name: 'Save as' }).click();
    await expect(templateTab('check')).toBeVisible();
    await expect(templateLabel).toHaveText('check');
    await test.step('credentials typed in the Administrator account block stay out of the copied configuration and the saved templates', async () => {
      const configText = await configBox.inputValue();
      expect(configText).not.toContain(secretPassword);
      expect(configText).not.toContain(secretEmail);
      const stored = await page.evaluate(() => JSON.stringify(Object.entries(window.localStorage)));
      expect(stored).not.toContain(secretPassword);
      expect(stored).not.toContain(secretEmail);
    });
    await page.reload();
    await expect(runButton).toBeEnabled();
    await expect(page.getByLabel('Administrator password')).toHaveValue('');
    await expect(page.getByRole('textbox', { name: 'Administrator e-mail' })).toHaveValue('');
    await templateTab('check').click();
    await expect(moderationBox).not.toBeChecked();
    await page.getByRole('button', { name: 'Delete check' }).click();
    await expect(templateTab('check')).toHaveCount(0);

    await templateTab('Administrator').click();
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

  test('developer opens the MCP page — the catalog lists one tool for every operation id of the live OpenAPI, opening the page creates no account, listTaxons with empty arguments returns a non-error result, getMe with a pasted key succeeds and without a key shows HTTP 401, getAdvertisement arguments hold a numeric id, listAdvertisements arguments carry page 0 and a locale and show the Optional hint, screenshot of the catalog', async ({ page, request, createUser }) => {
    const mcpResponse = await request.get(`${PAGE_URL}mcp.html`);
    test.skip(mcpResponse.status() === 404, 'the MCP page is not served by this stack');
    const accountRequests = [];
    page.on('request', pageRequest => { if (pageRequest.method() === 'POST' && new URL(pageRequest.url()).pathname === '/api/users') accountRequests.push(pageRequest.url()); });
    await page.goto(`${PAGE_URL}mcp.html`);
    await page.getByRole('tab', { name: 'Tools', exact: true }).click();
    const apiDocs = await (await request.get('/v3/api-docs')).json();
    const operationIds = Object.values(apiDocs.paths).flatMap(item => Object.values(item).map(operation => operation.operationId))
      .filter(id => typeof id === 'string').sort();
    const tools = page.locator('[data-tool]');
    await expect(tools).toHaveCount(operationIds.length, { timeout: 30000 });
    const names = await tools.evaluateAll(entries => entries.map(entry => entry.dataset.tool).sort());
    expect(names).toEqual(operationIds);
    expect(accountRequests).toEqual([]);
    await expect(page.getByText('The key field stays empty')).toBeVisible();

    const panel = page.locator('.tool-detail');
    const selectTool = name => page.locator(`[data-tool="${name}"]`).getByRole('button', { name: new RegExp(`^${name}`) }).click();

    await selectTool('listTaxons');
    await panel.getByLabel('Arguments of listTaxons').fill('{}');
    await panel.getByRole('button', { name: 'Call listTaxons' }).click();
    await expect(panel.locator('.tool-status')).toHaveAttribute('data-error', 'false');

    await selectTool('getMe');
    const keyField = page.locator('#catalog-api-key');
    await expect(keyField).toHaveValue('');
    const account = await createUser({ role: 'USER' });
    const key = await issueApiKeyViaApi(request, account);
    await keyField.fill(key);
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

  test('developer runs the default flow on the MCP page — none failed, every node passed or skipped, setup.init answered 200, the fresh page labelled Administrator and the templates User, Moderator, Administrator switching on growing sets of flows', async ({ page, request }) => {
    const mcpResponse = await request.get(`${PAGE_URL}mcp.html`);
    test.skip(mcpResponse.status() === 404, 'the MCP page is not served by this stack');
    await page.goto(`${PAGE_URL}mcp.html`);
    await page.getByRole('tab', { name: 'Flows', exact: true }).click();
    const runButton = page.getByRole('button', { name: 'Run selected' });
    const diagram = page.locator('#diagram');
    await expect(runButton).toBeEnabled();
    const nodes = await diagram.getByRole('button').count();
    expect(nodes).toBeGreaterThan(0);

    await test.step('the fresh page is labelled Administrator', async () => {
      await expect(page.locator('#template-label')).toHaveText('Administrator');
    });
    await test.step('the templates switch on growing sets of flows', async () => {
      const templateLabel = page.locator('#template-label');
      const templateTab = name => page.getByRole('tab', { name, exact: true });
      const checkedFlows = () => page.locator('#flow-map').getByRole('checkbox', { checked: true });
      const flowBox = name => page.getByRole('checkbox', { name: `Include ${name}`, exact: true });
      await templateTab('User').click();
      await expect(templateLabel).toHaveText('User');
      const userFlows = await checkedFlows().count();
      await expect(flowBox('Taxons')).not.toBeChecked();
      await expect(flowBox('Moderation')).not.toBeChecked();
      await expect(flowBox('Administration')).not.toBeChecked();
      await templateTab('Moderator').click();
      await expect(templateLabel).toHaveText('Moderator');
      const moderatorFlows = await checkedFlows().count();
      await expect(flowBox('Taxons')).toBeChecked();
      await expect(flowBox('Moderation')).toBeChecked();
      await expect(flowBox('Administration')).not.toBeChecked();
      await templateTab('Administrator').click();
      await expect(templateLabel).toHaveText('Administrator');
      await expect(flowBox('Administration')).toBeChecked();
      const administratorFlows = await checkedFlows().count();
      expect(userFlows).toBeLessThan(moderatorFlows);
      expect(moderatorFlows).toBeLessThan(administratorFlows);
    });
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

  test('developer runs both pages with a plain user and with a moderator whose credentials are typed in the Administrator account block — nothing failed, the flows that need an administrator skipped, the note naming the account and those flows, screenshot of the note', async ({ page, request, createUser }) => {
    const accounts = [await createUser({ role: 'USER' }), await createUser({ role: 'MODERATOR' })];
    const targets = [
      { name: 'REST', url: `${PAGE_URL}index.html`, collection: 'marketplace-rest-api', shot: 'flow-runner-no-admin' },
      { name: 'MCP', url: `${PAGE_URL}mcp.html`, collection: 'marketplace-mcp', shot: 'mcp-flow-runner-no-admin' }
    ];
    for (const account of accounts) {
      for (const target of targets) {
        await test.step(`${target.name} page, ${account.role.toLowerCase()} account as the default account`, async () => {
          const served = await request.get(target.url);
          test.skip(served.status() === 404, `the ${target.name} page is not served by this stack`);
          const collectionJson = await (await request.get(`${PAGE_URL}postman/${target.collection}.postman_collection.json`)).json();
          const gated = collectionJson.item.filter(flow => String(flow.description || '').trim().startsWith('when: adminRights=yes')).map(flow => flow.name);
          expect(gated.length).toBeGreaterThan(0);
          await page.goto(target.url);
          await page.getByRole('tab', { name: 'Flows', exact: true }).click();
          const runButton = page.getByRole('button', { name: 'Run selected' });
          await expect(runButton).toBeEnabled();
          await page.getByRole('textbox', { name: 'Administrator e-mail' }).fill(account.email);
          await page.getByLabel('Administrator password').fill(account.password);
          await runButton.click();
          await expect(runButton).toBeEnabled({ timeout: 90000 });
          await expect(page.locator('#diagram .step.failed')).toHaveCount(0);
          for (const name of gated) {
            await expect(page.locator(`#flow-map .flow-card[data-flow="${name}"] .pill.skipped`)).toBeVisible();
          }
          const note = page.locator('#admin-note');
          await expect(note).toBeVisible();
          await expect(note).toContainText('Administrator account block');
          await expect(note).toContainText(account.email);
          for (const name of gated) {
            await expect(note).toContainText(name);
          }
          await screenshot(page, `${target.shot}-${account.role.toLowerCase()}`);
        });
      }
    }
  });
});
