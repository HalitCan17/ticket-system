import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end tests against the running Compose stack (web on :3000, see infra/docker-compose.yml).
 * The stack is not started here; Keycloak only allows redirects back to :3000 and :5173.
 */
export default defineConfig({
  testDir: './e2e',
  // All tests share one database and the test users' notifications.
  workers: 1,
  fullyParallel: false,
  retries: 0,
  forbidOnly: !!process.env.CI,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3000',
    locale: 'tr-TR',
    // Bundled Chromium by default (`npx playwright install chromium`); E2E_BROWSER_CHANNEL=chrome uses the installed Chrome.
    channel: process.env.E2E_BROWSER_CHANNEL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'setup', testMatch: /auth\.setup\.ts/ },
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
      dependencies: ['setup'],
    },
  ],
});
