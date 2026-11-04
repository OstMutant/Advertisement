/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Shared Playwright fixtures for isolated, parallel-safe specs -- unique names per
 *   run and worker, an administrator API key, REST-created accounts, one saved login session per
 *   worker and account, and a pageAs() helper that opens a logged-in page from a saved session.
 * Usage: None -- a library only, required by spec files that run in the parallel project.
 * Uses: ./_helpers (test, expect, TEST_USERS), ./_flows/seed.flow (issueApiKeyViaApi, postViaApi),
 *   ./_flows/auth.flow (runFillLoginFormFlow, runSubmitLoginFlow), Node's fs and path modules.
 * Env: None.
 * Input: required by every spec file converted to the parallel project.
 * Outputs: exports test (extended with the fixtures below) and expect. Fixtures:
 *   - runId, unique -- run and worker scoped name parts; unique(prefix) returns "<prefix> <runId>-<n>".
 *   - adminKey -- a raw API key of the seeded administrator, one per worker.
 *   - createUser -- registers a fresh account through the REST API and returns it in the TEST_USERS shape.
 *   - userAccount, moderatorAccount, adminAccount -- one account per worker and role, for tests that
 *     never change their own actor.
 *   - pageAs -- opens a page logged in as the given account, reusing the worker's saved session.
 * Returns: N/A
 * ──────────────────────────────────────────────────────────────────────────── */
const fs = require('fs');
const path = require('path');
const { test: base, expect, TEST_USERS } = require('./_helpers');
const { issueApiKeyViaApi, postViaApi } = require('./_flows/seed.flow');
const { runFillLoginFormFlow, runSubmitLoginFlow } = require('./_flows/auth.flow');

const PASSWORD = 'password';
const SETTINGS_BUTTON = /^(settings|налаштування)$/i;

const bearer = key => ({ Authorization: `Bearer ${key}` });

/**
 * Registers an account through the REST API, sets its role as the administrator and its locale as
 * the account itself, and returns it in the TEST_USERS shape.
 * @param {import('@playwright/test').APIRequestContext} request
 * @param {string} adminKey raw API key of the administrator.
 * @param {string} runId unique part of this run and worker.
 * @param {{n: number}} counter per-worker counter.
 * @param {{role?: string, locale?: string, name?: string}} [options]
 * @returns {Promise<{id: number, name: string, email: string, password: string, role: string, locale: string}>}
 */
async function makeAccount(request, adminKey, runId, counter, { role = 'USER', locale = 'en', name } = {}) {
  const n = ++counter.n;
  const email = `${role.toLowerCase()}.${runId}.${n}@example.com`;
  const accountName = name || `${role} ${runId}-${n}`;
  const created = await postViaApi(request, null, '/api/users', { name: accountName, email, password: PASSWORD });

  if (role !== 'USER') {
    const current = await request.get(`/api/users/${created.id}`, { headers: bearer(adminKey) });
    const update = await request.put(`/api/users/${created.id}`, {
      headers: { ...bearer(adminKey), 'If-Match': current.headers()['etag'] },
      data: { name: accountName, role },
    });
    if (!update.ok()) throw new Error(`setting role ${role} failed: ${update.status()} ${await update.text()}`);
  }

  if (locale !== 'en') {
    const ownKey = await issueApiKeyViaApi(request, { email, password: PASSWORD });
    const change = await request.put('/api/users/me/locale', { headers: bearer(ownKey), data: { locale } });
    if (!change.ok()) throw new Error(`setting locale ${locale} failed: ${change.status()} ${await change.text()}`);
  }

  return { id: created.id, name: accountName, email, password: PASSWORD, role, locale };
}

const test = base.extend({
  runId: [async ({}, use, workerInfo) => {
    await use(`${Date.now().toString(36)}w${workerInfo.parallelIndex}`);
  }, { scope: 'worker' }],

  counter: [async ({}, use) => {
    await use({ n: 0 });
  }, { scope: 'worker' }],

  sessions: [async ({}, use) => {
    await use(new Map());
  }, { scope: 'worker' }],

  workerRequest: [async ({ playwright }, use, workerInfo) => {
    const context = await playwright.request.newContext({ baseURL: workerInfo.project.use.baseURL });
    await use(context);
    await context.dispose();
  }, { scope: 'worker' }],

  adminKey: [async ({ workerRequest }, use) => {
    await use(await issueApiKeyViaApi(workerRequest, TEST_USERS.adminEn));
  }, { scope: 'worker' }],

  userAccount: [async ({ workerRequest, adminKey, runId, counter }, use) => {
    await use(await makeAccount(workerRequest, adminKey, runId, counter, { role: 'USER' }));
  }, { scope: 'worker' }],

  moderatorAccount: [async ({ workerRequest, adminKey, runId, counter }, use) => {
    await use(await makeAccount(workerRequest, adminKey, runId, counter, { role: 'MODERATOR' }));
  }, { scope: 'worker' }],

  adminAccount: [async ({ workerRequest, adminKey, runId, counter }, use) => {
    await use(await makeAccount(workerRequest, adminKey, runId, counter, { role: 'ADMIN' }));
  }, { scope: 'worker' }],

  unique: async ({ runId, counter }, use) => {
    await use(prefix => `${prefix} ${runId}-${++counter.n}`);
  },

  createUser: async ({ request, adminKey, runId, counter }, use) => {
    await use(options => makeAccount(request, adminKey, runId, counter, options));
  },

  pageAs: async ({ browser, sessions }, use, testInfo) => {
    const { baseURL, viewport } = testInfo.project.use;
    const opened = [];

    const openContext = async storageState => {
      const context = await browser.newContext({ baseURL, viewport, storageState });
      opened.push(context);
      return context;
    };

    const logIn = async account => {
      const file = path.join(testInfo.project.outputDir, '.auth', `${account.email}.json`);
      fs.mkdirSync(path.dirname(file), { recursive: true });
      const context = await browser.newContext({ baseURL, viewport });
      try {
        const page = await context.newPage();
        await page.goto('/');
        await runFillLoginFormFlow(page, account);
        await runSubmitLoginFlow(page, expect, account);
        await context.storageState({ path: file });
      } finally {
        await context.close();
      }
      sessions.set(account.email, file);
      return file;
    };

    const openPage = async file => {
      const context = await openContext(file);
      const page = await context.newPage();
      await page.goto('/');
      const loggedIn = await page.getByRole('button', { name: SETTINGS_BUTTON })
        .waitFor({ state: 'visible', timeout: 5000 }).then(() => true, () => false);
      return { context, page, loggedIn };
    };

    await use(async account => {
      let session = await openPage(sessions.get(account.email) ?? await logIn(account));
      if (!session.loggedIn) {
        await session.context.close();
        session = await openPage(await logIn(account));
        if (!session.loggedIn) throw new Error(`pageAs: ${account.email} is not logged in after a fresh login`);
      }
      return session.page;
    });

    await Promise.all(opened.map(context => context.close().catch(() => {})));
  },
});

module.exports = { test, expect };
