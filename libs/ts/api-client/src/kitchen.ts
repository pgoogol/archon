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

export type DictionariesResponse = Schemas['DictionariesResponse']
export type DictionaryEntryResponse = Schemas['DictionaryEntryResponse']
export type UnitResponse = Schemas['UnitResponse']
export type UnitKind = Schemas['UnitKind']

export type { components, paths } from './kitchen.generated'
