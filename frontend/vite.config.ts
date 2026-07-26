import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// Dev server proxies API + WebSocket to the Spring Boot backend on :8080.
// `global: globalThis` is the polyfill sockjs-client needs to run in the browser.
// `defineConfig` comes from 'vitest/config' (not 'vite') so the same file can carry both the Vite
// dev-server config above and the `test` block below, instead of a parallel vitest.config.ts.
export default defineConfig({
  plugins: [react()],
  define: { global: 'globalThis' },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/ws': { target: 'http://localhost:8080', changeOrigin: true, ws: true },
      '/uploads': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/setupTests.ts'],
    globals: true,
  },
})
