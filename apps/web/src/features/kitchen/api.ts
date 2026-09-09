// Klient API domeny kuchennej. Typy DTO NIE są tu pisane — pochodzą z kontraktu
// (contracts/openapi/kitchen.yaml) przez @archon/api-client/kitchen. Ten plik
// trzyma wyłącznie wywołania endpointów.

import type {
  AliasRequest,
  AliasResponse,
  DictionariesResponse,
  DictionaryEntryResponse,
  Difficulty,
  DraftAlternative,
  DraftIngredient,
  DraftStep,
  IngredientLineResponse,
  IngredientPageResponse,
  IngredientRequest,
  IngredientResponse,
  IngredientStatus,
  NoteRequest,
  NoteResponse,
  RecipeDraft,
  RecipePageResponse,
  RecipeResponse,
  RecipeSummaryResponse,
  SaveRecipeRequest,
  SaveResponse,
  StepResponse,
  UnitResponse,
} from '@archon/api-client/kitchen'

import { jsonInit, request } from '@/shared/http/client'

export type {
  AliasRequest,
  AliasResponse,
  DictionariesResponse,
  DictionaryEntryResponse,
  Difficulty,
  DraftAlternative,
  DraftIngredient,
  DraftStep,
  IngredientLineResponse,
  IngredientPageResponse,
  IngredientRequest,
  IngredientResponse,
  IngredientStatus,
  NoteRequest,
  NoteResponse,
  RecipeDraft,
  RecipePageResponse,
  RecipeResponse,
  RecipeSummaryResponse,
  SaveRecipeRequest,
  SaveResponse,
  StepResponse,
  UnitResponse,
}

/** Prefiks niesie nazwę serwisu i wersję API — proxy rozdziela po nim ruch. */
export const BASE = '/kitchen/api/v1'

export const api = {

  dictionaries: (): Promise<DictionariesResponse> => request(`${BASE}/dictionaries`),

  listRecipes: (page = 0, size = 20): Promise<RecipePageResponse> =>
    request(`${BASE}/recipes?page=${page}&size=${size}`),

  getRecipe: (id: number): Promise<RecipeResponse> => request(`${BASE}/recipes/${id}`),

  createRecipe: (body: SaveRecipeRequest): Promise<SaveResponse> =>
    request(`${BASE}/recipes`, jsonInit('POST', body)),

  updateRecipe: (id: number, body: SaveRecipeRequest): Promise<SaveResponse> =>
    request(`${BASE}/recipes/${id}`, jsonInit('PUT', body)),

  archiveRecipe: (id: number): Promise<void> =>
    request(`${BASE}/recipes/${id}`, { method: 'DELETE' }),

  listNotes: (recipeId: number): Promise<NoteResponse[]> =>
    request(`${BASE}/recipes/${recipeId}/notes`),

  addNote: (recipeId: number, body: NoteRequest): Promise<NoteResponse> =>
    request(`${BASE}/recipes/${recipeId}/notes`, jsonInit('POST', body)),

  deleteNote: (recipeId: number, noteId: number): Promise<void> =>
    request(`${BASE}/recipes/${recipeId}/notes/${noteId}`, { method: 'DELETE' }),

  searchIngredients: (query: string, status?: IngredientStatus): Promise<IngredientPageResponse> => {
    const params = new URLSearchParams()
    if (query) {
      params.set('q', query)
    }
    if (status) {
      params.set('status', status)
    }
    return request(`${BASE}/ingredients?${params.toString()}`)
  },
}
