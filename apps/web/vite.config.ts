import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// Proxy dev/preview na backendy Spring — front woła względne /api i nie zna
// żadnego adresu. Klucze są dopasowywane po prefiksie, a bardziej szczegółowy
// musi stać pierwszy: przy odwrotnej kolejności całe /api poszłoby na 8080
// i finance-service nigdy nie dostałby żadnego żądania.
const apiProxy = {
  '/api/finance': {
    target: 'http://localhost:8081',
    changeOrigin: true,
  },
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true,
  },
}

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: { port: 5173, proxy: apiProxy },
  preview: { port: 5173, proxy: apiProxy },
  // testy komponentów i logiki frontu w jsdom (M3.1) — `npm test`
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    restoreMocks: true,
  },
})
