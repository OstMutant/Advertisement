const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  // Spec files are copied alongside this config into /tmp inside pw-runner.
  testDir: '.',
  testMatch: '*.spec.js',

  // Vaadin + shared DB — parallel runs cause race conditions.
  fullyParallel: false,
  workers: 1,

  retries: process.env.CI ? 2 : 1,

  // Vaadin hydration can be slow — give each action 10 s, each test 60 s.
  use: {
    // Override via APP_URL in CI / docker-compose env.
    baseURL: process.env.APP_URL || 'http://localhost:8080',
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
