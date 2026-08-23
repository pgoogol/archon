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

export type ImportBatchResponse = Schemas['ImportBatchResponse']
export type ImportBatchDetailResponse = Schemas['ImportBatchDetailResponse']
export type ImportRowResponse = Schemas['ImportRowResponse']
export type ImportRowStatus = Schemas['ImportRowStatus']
export type CommitImportRequest = Schemas['CommitImportRequest']
export type CommitImportResponse = Schemas['CommitImportResponse']
export type ImportCategoryAssignment = Schemas['ImportCategoryAssignment']
export type ImportOccurrenceAssignment = Schemas['ImportOccurrenceAssignment']
export type ReconciliationResponse = Schemas['ReconciliationResponse']

export type RecurringRuleRequest = Schemas['RecurringRuleRequest']
export type RecurringRuleResponse = Schemas['RecurringRuleResponse']
export type RecurringFrequency = Schemas['RecurringFrequency']
export type OccurrenceResponse = Schemas['OccurrenceResponse']
export type OccurrenceStatus = Schemas['OccurrenceStatus']
export type PayOccurrenceRequest = Schemas['PayOccurrenceRequest']
export type GenerateOccurrencesResponse = Schemas['GenerateOccurrencesResponse']

export type Granularity = Schemas['Granularity']
export type ByCategoryReportResponse = Schemas['ByCategoryReportResponse']
export type ByCategoryRow = Schemas['ByCategoryRow']
export type PeriodTotal = Schemas['PeriodTotal']
export type CashflowReportResponse = Schemas['CashflowReportResponse']
export type CashflowRow = Schemas['CashflowRow']
export type BalancesReportResponse = Schemas['BalancesReportResponse']
export type AccountBalanceRow = Schemas['AccountBalanceRow']
export type ComparisonReportResponse = Schemas['ComparisonReportResponse']
export type CategoryComparisonRow = Schemas['CategoryComparisonRow']
export type TopSpendReportResponse = Schemas['TopSpendReportResponse']
export type TopExpenseRow = Schemas['TopExpenseRow']
export type TopCounterpartyRow = Schemas['TopCounterpartyRow']
export type FixedVsVariableReportResponse = Schemas['FixedVsVariableReportResponse']
export type FixedVsVariableRow = Schemas['FixedVsVariableRow']
export type UpcomingReportResponse = Schemas['UpcomingReportResponse']
export type UpcomingItem = Schemas['UpcomingItem']
export type ForecastReportResponse = Schemas['ForecastReportResponse']
export type ForecastPoint = Schemas['ForecastPoint']
export type YearlyMatrixReportResponse = Schemas['YearlyMatrixReportResponse']
export type YearlyMatrixRow = Schemas['YearlyMatrixRow']
export type CurrencyExposureReportResponse = Schemas['CurrencyExposureReportResponse']
export type CurrencyExposureRow = Schemas['CurrencyExposureRow']
export type DashboardResponse = Schemas['DashboardResponse']
export type AccountValuation = Schemas['AccountValuation']

export type CategoryRuleRequest = Schemas['CategoryRuleRequest']
export type CategoryRuleResponse = Schemas['CategoryRuleResponse']
export type MatchField = Schemas['MatchField']
export type TransferCandidate = Schemas['TransferCandidate']
export type MergeTransferRequest = Schemas['MergeTransferRequest']

export type FinanceErrorResponse = Schemas['ErrorResponse']

export type { components, paths } from './finance.generated'
