import react from '@vitejs/plugin-react'
import { fileURLToPath } from 'node:url'
import { loadEnv } from 'vite'
import { defineConfig } from 'vitest/config'

// The backend sets no CORS headers and its refresh cookie is SameSite=Strict, so in development
// the browser talks only to Vite and Vite forwards /api to the backend (same origin).
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiTarget = env.VITE_DEV_API_TARGET || 'http://localhost:8080'
  return {
    plugins: [react()],
    resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
    server: {
      port: 5173,
      proxy: { '/api': { target: apiTarget, changeOrigin: false, timeout: 240_000, proxyTimeout: 240_000 } },
    },
    preview: {
      port: 4173,
      proxy: { '/api': { target: apiTarget, changeOrigin: false, timeout: 240_000, proxyTimeout: 240_000 } },
    },
    // Pre-bundle every runtime dependency up front. Vite otherwise finds one the first time a lazy route imports it,
    // re-optimizes and reloads the page, which loses state and makes end-to-end runs flaky.
    optimizeDeps: { include: ['react', 'react-dom', 'react-dom/client', 'react/jsx-runtime', 'react/jsx-dev-runtime', 'react-router', 'react-router/dom', '@tanstack/react-query', 'zod'] },
    build: { sourcemap: false, target: 'es2023' },
    test: {
      globals: true,
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      include: ['src/**/*.test.{ts,tsx}'],
      css: { modules: { classNameStrategy: 'non-scoped' } },
      restoreMocks: true,
      env: { TZ: 'Asia/Kolkata' },
    },
  }
})
