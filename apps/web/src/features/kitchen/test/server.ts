// Serwer MSW domeny kuchennej.
//
// Testy idą przez MSW, a nie przez podstawiony `globalThis.fetch`: sprawdzamy
// to, co naprawdę pójdzie po sieci, razem z adresem i metodą. Podstawiony
// `fetch` przepuszcza literówkę w ścieżce — MSW na nieobsłużone żądanie krzyczy.

import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { afterAll, afterEach, beforeAll } from 'vitest'

import type { DictionariesResponse } from '@/features/kitchen/api'

export const BASE = '/kitchen/api/v1'

export const EMPTY_DICTIONARIES: DictionariesResponse = {
  units: [],
  cuisines: [],
  categories: [],
  diets: [],
  tags: [],
  equipment: [],
}

export const kitchenServer = setupServer(
  http.get(`${BASE}/dictionaries`, () => HttpResponse.json(EMPTY_DICTIONARIES)),
)

/** Podpina serwer do cyklu życia pliku testowego. */
export function useKitchenApi() {

  beforeAll(() => kitchenServer.listen({ onUnhandledRequest: 'error' }))
  afterEach(() => kitchenServer.resetHandlers())
  afterAll(() => kitchenServer.close())
}

export { http, HttpResponse }
