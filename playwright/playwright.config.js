const { defineConfig, devices } = require('@playwright/test');

module.exports = defineConfig({
  testDir: '/tmp',
  fullyParallel: false,
  retries: process.env.CI ? 2 : 0,
  timeout: 30_000,
  reporter: [
    ['list'],
    ['html', { outputFolder: '/tmp/pw-report', open: 'never' }],
  ],
  use: {
    baseURL: process.env.APP_URL || 'http://localhost:8080',
    viewport: { width: 1280, height: 900 },
    screenshot: 'only-on-failure',
    trace: 'on-first-retry',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
});
