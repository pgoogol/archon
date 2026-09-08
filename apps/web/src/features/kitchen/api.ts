// Klient API domeny kuchennej. Typy DTO NIE są tu pisane — pochodzą z kontraktu
// (contracts/openapi/kitchen.yaml) przez @archon/api-client/kitchen. Ten plik
// trzyma wyłącznie wywołania endpointów.

import type {
  DictionariesResponse,
  DictionaryEntryResponse,
  UnitKind,
  UnitResponse,
} from '@archon/api-client/kitchen'

import { request } from '@/shared/http/client'

export type { DictionariesResponse, DictionaryEntryResponse, UnitKind, UnitResponse }

/** Prefiks niesie nazwę serwisu i wersję API — proxy rozdziela po nim ruch. */
export const BASE = '/kitchen/api/v1'

export const api = {

  dictionaries: (): Promise<DictionariesResponse> => request(`${BASE}/dictionaries`),
}
