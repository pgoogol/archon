// Serwer MSW domeny kuchennej.
//
// Testy idą przez MSW, a nie przez podstawiony `globalThis.fetch`: sprawdzamy
// to, co naprawdę pójdzie po sieci, razem z adresem i metodą. Podstawiony
// `fetch` przepuszcza literówkę w ścieżce — MSW na nieobsłużone żądanie krzyczy.

import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { afterAll, afterEach, beforeAll } from 'vitest'

import type {
  DictionariesResponse,
  RecipePageResponse,
  RecipeResponse,
} from '@/features/kitchen/api'

export const BASE = '/kitchen/api/v1'

export const DICTIONARIES: DictionariesResponse = {
  units: [
    { id: 1, code: 'g', name: 'gram', kind: 'MASS' },
    { id: 2, code: 'szt', name: 'sztuka', kind: 'COUNT' },
  ],
  cuisines: [{ id: 10, name: 'polska' }],
  categories: [{ id: 20, name: 'deser' }],
  diets: [{ id: 30, name: 'wegetariańska' }],
  tags: [],
  equipment: [],
}

export const EMPTY_DICTIONARIES: DictionariesResponse = {
  units: [],
  cuisines: [],
  categories: [],
  diets: [],
  tags: [],
  equipment: [],
}

export const EMPTY_PAGE: RecipePageResponse = {
  items: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
}

export const SERNIK: RecipeResponse = {
  id: 7,
  title: 'Sernik',
  description: 'Klasyczny, na kruchym spodzie',
  servingsAmount: 12,
  servingsUnit: 'porcje',
  totalMinutes: 90,
  cuisine: 'polska',
  category: 'deser',
  status: 'ACTIVE',
  currentRevisionNo: 3,
  tags: ['na święta'],
  diets: [],
  ingredients: [
    {
      id: 100,
      position: 0,
      group: 'na spód',
      displayName: 'mąka',
      quantityMin: 250,
      quantityMax: 250,
      unit: 'g',
      unitName: 'gram',
      optional: false,
      alternatives: [],
    },
    {
      id: 101,
      position: 1,
      group: 'na masę',
      displayName: 'twaróg',
      quantityMin: 1,
      quantityMax: 1,
      unit: 'kg',
      unitName: 'kilogram',
      optional: false,
      alternatives: [],
    },
  ],
  steps: [
    {
      id: 200,
      position: 0,
      text: 'Zagnieć spód',
      durationMinutes: 15,
      ingredientIds: [100],
      equipment: ['piekarnik'],
    },
  ],
}

export const kitchenServer = setupServer(
  http.get(`${BASE}/dictionaries`, () => HttpResponse.json(DICTIONARIES)),
  http.get(`${BASE}/recipes`, () => HttpResponse.json(EMPTY_PAGE)),
  http.get(`${BASE}/recipes/:id`, () => HttpResponse.json(SERNIK)),
  http.get(`${BASE}/recipes/:id/notes`, () => HttpResponse.json([])),
)

/** Podpina serwer do cyklu życia pliku testowego. */
export function useKitchenApi() {

  beforeAll(() => kitchenServer.listen({ onUnhandledRequest: 'error' }))
  afterEach(() => kitchenServer.resetHandlers())
  afterAll(() => kitchenServer.close())
}

export { http, HttpResponse }
