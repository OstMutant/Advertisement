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
    ['./reporter.js', { outputFile: '/tmp/pw-live.log' }],
    ['list'],
    // HTML report written to /tmp/pw-report; run.sh docker-cp's it out afterward.
    ['html', { outputFolder: '/tmp/pw-report', open: 'never' }],
  ],

  // webServer intentionally absent — app already running via docker-compose.
});
