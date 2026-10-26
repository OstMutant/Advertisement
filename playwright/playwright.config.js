/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Playwright test-runner config -- a "setup" project (signs up the administrator on an
 *   empty database), then the "e2e" project (every spec isolated and fully parallel, one retry).
 *   60s per-test timeout, screenshot-on-failure, trace-on-failure, HTML report. Copied alongside the
 *   spec files into pw-runner by run.sh, read from there, not from this repo checkout directly.
 * Usage: npx playwright test --config playwright.config.js (always via run.sh, never invoked
 *   directly).
 * Uses: @playwright/test.
 * Env: APP_URL (default http://localhost:8081) -- forwarded explicitly by run.sh into the
 *   pw-runner container's own env, not read from this repo checkout's shell.
 *   PW_WORKERS (default 2) -- worker process count, read from the repo-root .env by run.sh.
 * Input: None.
 * Outputs: HTML report written to /reports/playwright inside the container -- the shared
 *   test-reports named Docker volume mounted into pw-runner, never a WSL/Windows-drive path (see
 *   .claude/nav/adr-index.md); run.sh copies it out to playwright/pw-report/ via `docker cp`.
 * Returns: N/A -- this file has no exit code of its own, Playwright's own test run does.
 * ──────────────────────────────────────────────────────────────────────────── */
const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  // Spec files are copied alongside this config into /tmp inside pw-runner.
  testDir: '.',

  // More than 2 workers did not shorten the run on a 4-core machine; PW_WORKERS in the repo-root .env changes it.
  workers: Number(process.env.PW_WORKERS) || 2,

  retries: 1,

  // Vaadin hydration can be slow — give each action 10 s, each test 60 s.
  use: {
    // Override via APP_URL in CI / docker-compose env.
    baseURL: process.env.APP_URL || 'http://localhost:8081',
    viewport: { width: 1280, height: 900 },
    actionTimeout: 10_000,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },

  timeout: 60_000,

  projects: [
    { name: 'setup', testMatch: /global\.setup\.js/ },
    {
      name: 'e2e',
      testMatch: '**/*.spec.js',
      dependencies: ['setup'],
      fullyParallel: true,
    },
  ],

  reporter: [
    ['list'],
    // HTML report written to the shared test-reports volume, flat alongside run.sh's own
    // run.log (both under /reports/playwright/) so one `docker cp .../reports/playwright/.` pulls
    // both into playwright/pw-report/ on the host in one shot.
    ['html', { outputFolder: '/reports/playwright', open: 'never' }],
  ],

  // webServer intentionally absent — app already running via docker-compose.
});
