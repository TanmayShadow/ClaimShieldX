import { defineConfig } from '@playwright/test'

const port = 5173
// End-to-end runs talk to a real backend through the Vite proxy; see docs/frontend-development-tracker.md.
const apiTarget = process.env.E2E_API_TARGET ?? 'http://localhost:18080'

export default defineConfig({
  testDir: './e2e',
  // The production-server suite needs the built app and its own server; it has its own config.
  testIgnore: '**/production/**',
  outputDir: './e2e/.artifacts/results',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  // The machine is shared with the backend's own test runs; a page that is merely slow gets time, one that is wrong still fails.
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [['list']],
  use: {
    baseURL: `http://localhost:${port}`,
    channel: 'chrome',
    headless: true,
    locale: 'en-IN',
    timezoneId: 'Asia/Kolkata',
    trace: 'retain-on-failure',
  },
  webServer: {
    command: `npx vite --port ${port} --strictPort`,
    url: `http://localhost:${port}`,
    reuseExistingServer: true,
    env: { VITE_DEV_API_TARGET: apiTarget },
    timeout: 60_000,
  },
})
