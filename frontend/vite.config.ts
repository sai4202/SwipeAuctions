import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Dev server proxies API + WebSocket to the Spring Boot backend on :8080.
// `global: globalThis` is the polyfill sockjs-client needs to run in the browser.
export default defineConfig({
  plugins: [react()],
  define: { global: 'globalThis' },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/ws': { target: 'http://localhost:8080', changeOrigin: true, ws: true },
    },
  },
})
