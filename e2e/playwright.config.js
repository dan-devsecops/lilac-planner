import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  globalSetup: './global-setup.js',
  globalTeardown: './global-teardown.js',
  testDir: './tests',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [['html', { open: 'never' }], ['list']],
  use: {
    // The e2e stack's frontend is published on 5174 (see docker-compose-e2e.yml),
    // so it never collides with a dev stack on 5173.
    baseURL: process.env.BASE_URL ?? 'http://localhost:5174',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'setup',
      testDir: './auth',
      testMatch: /keycloak\.setup\.js/,
    },
    {
      name: 'e2e',
      use: {
        ...devices['Desktop Chrome'],
        storageState: 'auth/.storage-state.json',
      },
      dependencies: ['setup'],
    },
  ],
});
