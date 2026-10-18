import { defineConfig } from 'vitest/config'

// The production server is plain Node: no browser environment and no request mocking, because its tests make real local requests.
export default defineConfig({ test: { include: ['server/**/*.test.ts'], environment: 'node' } })
