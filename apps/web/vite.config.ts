import { fileURLToPath, URL } from 'node:url'

import { defineConfig, type Plugin } from 'vitest/config'
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
  // tożsamość zalogowanego podmiotu; adres stoi poza prefiksem domenowym, bo
  // wystawia go wspólny starter, nie domena — kierujemy na dowolny serwis,
  // wszystkie odpowiadają tak samo
  '/auth/whoami': {
    target: 'http://localhost:8080',
    changeOrigin: true,
  },
}

const AUTH_CONFIG_PATH = '/auth-config.json'

/**
 * Konfiguracja logowania podawana w czasie działania, tak samo jak w obrazie
 * produkcyjnym — tam robi to nginx z podstawionych zmiennych środowiskowych.
 *
 * Świadomie nie przez `import.meta.env`: identyfikator klienta wypaliłby się
 * wtedy w pakiecie JS i ten sam obraz przestałby działać na dwóch środowiskach.
 * Ta sama zasada rządzi adresami backendów, które siedzą w konfiguracji proxy.
 */
function authConfigPlugin(): Plugin {

  const body = () =>
    JSON.stringify({
      enabled: process.env.AUTH_ENABLED !== 'false',
      googleClientId: process.env.GOOGLE_CLIENT_ID ?? '',
      localAccountEnabled: process.env.AUTH_LOCAL_ENABLED !== 'false',
    })

  // Uwaga: nic nie zwracamy. Vite traktuje funkcję zwróconą z `configureServer`
  // jako hook uruchamiany po wpięciu własnych warstw, a `use()` oddaje samą
  // aplikację connect — czyli funkcję. Zwrócenie jej wywalało start serwera.
  const serve = (server: { middlewares: Connect }): void => {

    server.middlewares.use(AUTH_CONFIG_PATH, (_request, response) => {

      response.setHeader('Content-Type', 'application/json')
      response.end(body())
    })
  }

  return {
    name: 'archon-auth-config',
    configureServer: serve,
    configurePreviewServer: serve,
  }
}

interface Connect {
  use(path: string, handler: (request: unknown, response: ServerResponse) => void): unknown
}

interface ServerResponse {
  setHeader(name: string, value: string): void
  end(chunk: string): void
}

export default defineConfig({
  plugins: [react(), authConfigPlugin()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: { port: 5173, proxy: apiProxy },
  preview: { port: 5173, proxy: apiProxy },
  // testy komponentów i logiki frontu w jsdom — `pnpm test`
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    restoreMocks: true,
  },
})
