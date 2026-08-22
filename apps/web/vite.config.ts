import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// Proxy dev/preview na backendy Spring — front woła adresy względne i nie zna
// żadnego hosta. Każdy serwis ma własny prefiks najwyższego poziomu z wersją
// API w środku, więc prefiksy się nie zawierają i kolejność kluczy nic tu nie
// zmienia — dołożenie serwisu to jedna pozycja, nie przestawianie reguł.
const apiProxy = {
  '/music/api/v1': {
    target: 'http://localhost:8080',
    changeOrigin: true,
  },
  '/finance/api/v1': {
    target: 'http://localhost:8081',
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
