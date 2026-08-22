// Typy DTO generowane z contracts/openapi/finance.yaml. Nigdy nie pisz ich ręcznie
// i nigdy nie edytuj finance.generated.ts — regeneruj:
//
//   pnpm --filter @archon/api-client generate
//
// CI regeneruje i wywala się na diffie, więc nieaktualny plik w repo psuje build.
//
// Punkt wejścia @archon/api-client/finance, symetryczny do /music:
// obydwa kontrakty mają własny ErrorResponse i własne stronicowanie, więc
// eksport pod jedną nazwą kończyłby się kolizją.

import type { components } from './finance.generated'

type Schemas = components['schemas']

export type CurrencyResponse = Schemas['CurrencyResponse']
export type CurrencyRequest = Schemas['CurrencyRequest']
export type ExchangeRateResponse = Schemas['ExchangeRateResponse']
export type ExchangeRateRequest = Schemas['ExchangeRateRequest']
export type SyncExchangeRatesRequest = Schemas['SyncExchangeRatesRequest']
export type SyncExchangeRatesResponse = Schemas['SyncExchangeRatesResponse']
export type RateSource = Schemas['RateSource']

export type AccountRequest = Schemas['AccountRequest']
export type AccountResponse = Schemas['AccountResponse']
export type AccountBalanceResponse = Schemas['AccountBalanceResponse']
export type AccountType = Schemas['AccountType']

export type CategoryRequest = Schemas['CategoryRequest']
export type CategoryResponse = Schemas['CategoryResponse']
export type CategoryDirection = Schemas['CategoryDirection']

export type TransactionRequest = Schemas['TransactionRequest']
export type TransactionResponse = Schemas['TransactionResponse']
export type TransactionPageResponse = Schemas['TransactionPageResponse']
export type TransactionType = Schemas['TransactionType']

export type FinanceErrorResponse = Schemas['ErrorResponse']

export type { components, paths } from './finance.generated'
