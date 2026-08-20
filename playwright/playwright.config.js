/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Playwright test-runner config -- single worker, no parallelism (Vaadin + a shared
 *   DB would race under concurrent tests), 60s per-test timeout, screenshot-on-failure, HTML
 *   report. Copied alongside the spec files into pw-runner by run.sh, read from there, not from
 *   this repo checkout directly.
 * Usage: npx playwright test --config playwright.config.js (always via run.sh, never invoked
 *   directly).
 * Uses: @playwright/test.
 * Env: APP_URL (default http://localhost:8081) -- forwarded explicitly by run.sh into the
 *   pw-runner container's own env, not read from this repo checkout's shell.
 * Input: None.
 * Outputs: HTML report written to /tmp/pw-report inside the container; run.sh copies it out to
 *   playwright/pw-report/.
 * Returns: N/A -- this file has no exit code of its own, Playwright's own test run does.
 * ──────────────────────────────────────────────────────────────────────────── */
const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  // Spec files are copied alongside this config into /tmp inside pw-runner.
  testDir: '.',
  testMatch: '**/*.spec.js',

  // Vaadin + shared DB — parallel runs cause race conditions.
  fullyParallel: false,
  workers: 1,

  retries: 0,

  // Vaadin hydration can be slow — give each action 10 s, each test 60 s.
  use: {
    // Override via APP_URL in CI / docker-compose env.
    baseURL: process.env.APP_URL || 'http://localhost:8081',
    viewport: { width: 1280, height: 900 },
    actionTimeout: 10_000,
    screenshot: 'only-on-failure',
    trace: 'on-first-retry',
  },

  timeout: 60_000,

  reporter: [
    ['list'],
    // HTML report written to /tmp/pw-report; run.sh docker-cp's it out afterward.
    ['html', { outputFolder: '/tmp/pw-report', open: 'never' }],
  ],

  // webServer intentionally absent — app already running via docker-compose.
});
