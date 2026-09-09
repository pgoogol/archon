// Typy DTO generowane z contracts/openapi/kitchen.yaml. Nigdy nie pisz ich ręcznie
// i nigdy nie edytuj kitchen.generated.ts — regeneruj:
//
//   pnpm --filter @archon/api-client generate
//
// CI regeneruje i wywala się na diffie, więc nieaktualny plik w repo psuje build.
//
// Punkt wejścia @archon/api-client/kitchen, symetryczny do /music i /finance:
// każdy kontrakt ma własny ErrorResponse i własne stronicowanie, więc eksport
// pod jedną nazwą kończyłby się kolizją.

import type { components } from './kitchen.generated'

type Schemas = components['schemas']

export type RecipeDraft = Schemas['RecipeDraft']
export type DraftIngredient = Schemas['DraftIngredient']
export type DraftAlternative = Schemas['DraftAlternative']
export type DraftStep = Schemas['DraftStep']
export type SaveRecipeRequest = Schemas['SaveRecipeRequest']
export type SaveResponse = Schemas['SaveResponse']

export type RecipeResponse = Schemas['RecipeResponse']
export type RecipeSummaryResponse = Schemas['RecipeSummaryResponse']
export type RecipePageResponse = Schemas['RecipePageResponse']
export type IngredientLineResponse = Schemas['IngredientLineResponse']
export type AlternativeResponse = Schemas['AlternativeResponse']
export type StepResponse = Schemas['StepResponse']
export type SourceResponse = Schemas['SourceResponse']

export type NoteRequest = Schemas['NoteRequest']
export type NoteResponse = Schemas['NoteResponse']

export type IngredientRequest = Schemas['IngredientRequest']
export type IngredientResponse = Schemas['IngredientResponse']
export type IngredientPageResponse = Schemas['IngredientPageResponse']
export type AliasRequest = Schemas['AliasRequest']
export type AliasResponse = Schemas['AliasResponse']

export type DictionariesResponse = Schemas['DictionariesResponse']
export type DictionaryEntryResponse = Schemas['DictionaryEntryResponse']
export type UnitResponse = Schemas['UnitResponse']

export type UnitKind = Schemas['UnitKind']
export type Difficulty = Schemas['Difficulty']
export type RecipeStatus = Schemas['RecipeStatus']
export type SourceKind = Schemas['SourceKind']
export type IngredientStatus = Schemas['IngredientStatus']

export type KitchenErrorResponse = Schemas['ErrorResponse']

export type { components, paths } from './kitchen.generated'
