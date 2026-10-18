import { defineConfig } from '@playwright/test'

// The same browser checks, against what is actually deployed: the built app served by server/server.mjs with its security policy,
// forwarding /api to a real backend. Run `npm run build` first.
const port = 4173
const apiTarget = process.env.E2E_API_TARGET ?? 'http://localhost:18080'

export default defineConfig({
  testDir: './e2e/production',
  outputDir: './e2e/.artifacts/results-production',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 90_000,
  expect: { timeout: 10_000 },
  reporter: [['list']],
  use: { baseURL: `http://localhost:${port}`, channel: 'chrome', headless: true, locale: 'en-IN', timezoneId: 'Asia/Kolkata', trace: 'retain-on-failure' },
  webServer: {
    command: 'node server/server.mjs',
    url: `http://localhost:${port}/healthz`,
    reuseExistingServer: false,
    env: { BACKEND_URL: apiTarget, PORT: String(port) },
    timeout: 30_000,
  },
})
