// Serwer MSW domeny finansowej.
//
// Testy idą przez MSW, a nie przez podstawiony `globalThis.fetch`: sprawdzamy
// to, co naprawdę pójdzie po sieci, razem z adresem, metodą i kształtem
// odpowiedzi. Podstawiony `fetch` przepuszczał literówkę w ścieżce — MSW na
// nieobsłużone żądanie krzyczy.
//
// Serwer stoi tylko w testach finansów: domena muzyczna ma własny sposób i nie
// ma powodu, żeby migracja jednej domeny wywracała drugą.

import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import type { JsonBodyType } from 'msw'
import { afterAll, afterEach, beforeAll } from 'vitest'

import { aDashboard } from '@/features/finance/test/fixtures'

const BASE = '/finance/api/v1'

/** Słownik walut jest tłem każdego ekranu — bez niego nie da się wypisać kwoty. */
const CURRENCIES = [
  { code: 'PLN', name: 'złoty polski', minorUnit: 2 },
  { code: 'EUR', name: 'euro', minorUnit: 2 },
  { code: 'JPY', name: 'jen japoński', minorUnit: 0 },
]

/**
 * Domyślne odpowiedzi: puste listy i jeden sensowny pulpit. Test, którego to nie
 * dotyczy, nie musi ich wymieniać; test, którego dotyczy, nadpisuje je przez
 * `financeServer.use(...)`.
 */
export const defaultHandlers = [
  http.get(`${BASE}/currencies`, () => HttpResponse.json(CURRENCIES)),
  http.get(`${BASE}/accounts`, () => HttpResponse.json([])),
  http.get(`${BASE}/categories`, () => HttpResponse.json([])),
  http.get(`${BASE}/reports/dashboard`, () => HttpResponse.json(aDashboard())),
]

export const financeServer = setupServer(...defaultHandlers)

/**
 * Podpina serwer do cyklu życia pliku testowego.
 *
 * `onUnhandledRequest: 'error'` jest tu celowe: ekran, który strzela pod adres
 * bez handlera, ma wywalić test, a nie po cichu dostać `undefined` i pokazać
 * pusty stan wyglądający jak poprawny.
 */
export function useFinanceApi(): void {

  beforeAll(() => financeServer.listen({ onUnhandledRequest: 'error' }))
  afterEach(() => financeServer.resetHandlers())
  afterAll(() => financeServer.close())
}

/** Skrót na najczęstszy przypadek: jeden adres, jedna odpowiedź JSON. */
export function respondJson(path: string, body: JsonBodyType) {

  return http.get(`${BASE}${path}`, () => HttpResponse.json(body))
}

export { http, HttpResponse, BASE }
