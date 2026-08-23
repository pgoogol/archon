// Klient API domeny finansowej. Typy DTO NIE są tu pisane — pochodzą z kontraktu
// (contracts/openapi/finance.yaml) przez @archon/api-client/finance. Ten plik
// trzyma wyłącznie wywołania endpointów i kształty wejściowe samego frontu.

import type {
  AccountBalanceResponse,
  AccountRequest,
  AccountResponse,
  BalancesReportResponse,
  ByCategoryReportResponse,
  CashflowReportResponse,
  CategoryRequest,
  CategoryResponse,
  CategoryRuleRequest,
  CategoryRuleResponse,
  CommitImportRequest,
  CommitImportResponse,
  ComparisonReportResponse,
  CurrencyExposureReportResponse,
  CurrencyResponse,
  DashboardResponse,
  FixedVsVariableReportResponse,
  ForecastReportResponse,
  GenerateOccurrencesResponse,
  ImportBatchDetailResponse,
  ImportBatchResponse,
  MergeTransferRequest,
  OccurrenceResponse,
  PayOccurrenceRequest,
  RecurringRuleRequest,
  RecurringRuleResponse,
  TopSpendReportResponse,
  TransactionPageResponse,
  TransactionRequest,
  TransactionResponse,
  TransferCandidate,
  UpcomingReportResponse,
  YearlyMatrixReportResponse,
} from '@archon/api-client/finance'

export type {
  AccountBalanceResponse,
  AccountRequest,
  AccountResponse,
  AccountType,
  AccountValuation,
  BalancesReportResponse,
  ByCategoryReportResponse,
  ByCategoryRow,
  CashflowReportResponse,
  CashflowRow,
  CategoryComparisonRow,
  CategoryDirection,
  CategoryRequest,
  CategoryResponse,
  CategoryRuleRequest,
  CategoryRuleResponse,
  CommitImportRequest,
  CommitImportResponse,
  ComparisonReportResponse,
  CurrencyExposureReportResponse,
  CurrencyExposureRow,
  CurrencyResponse,
  DashboardResponse,
  FixedVsVariableReportResponse,
  FixedVsVariableRow,
  ForecastPoint,
  ForecastReportResponse,
  GenerateOccurrencesResponse,
  Granularity,
  ImportBatchDetailResponse,
  ImportBatchResponse,
  ImportCategoryAssignment,
  ImportOccurrenceAssignment,
  ImportRowResponse,
  ImportRowStatus,
  MatchField,
  MergeTransferRequest,
  OccurrenceResponse,
  OccurrenceStatus,
  PayOccurrenceRequest,
  PeriodTotal,
  ReconciliationResponse,
  RecurringFrequency,
  RecurringRuleRequest,
  RecurringRuleResponse,
  TopCounterpartyRow,
  TopExpenseRow,
  TopSpendReportResponse,
  TransactionPageResponse,
  TransactionRequest,
  TransactionResponse,
  TransactionType,
  TransferCandidate,
  UpcomingItem,
  UpcomingReportResponse,
  YearlyMatrixReportResponse,
  YearlyMatrixRow,
} from '@archon/api-client/finance'

import { jsonInit, request } from '@/shared/http/client'

export { ApiError } from '@/shared/http/client'

const BASE = '/finance/api/v1'

/** Filtry listy transakcji; puste pola nie trafiają do adresu. */
export interface TransactionFilters {
  from?: string
  to?: string
  accountId?: number
  categoryId?: number
  type?: string
  currency?: string
  page?: number
  size?: number
}

/** Wspólne parametry raportów — te same dla każdego z jedenastu. */
export interface ReportParams {
  from: string
  to: string
  granularity?: string
  accountIds?: number[]
  categoryIds?: number[]
  direction?: string
}

function query(params: Record<string, unknown>): string {

  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return
    if (Array.isArray(value)) {
      value.forEach((item) => search.append(key, String(item)))
      return
    }
    search.set(key, String(value))
  })
  return search.toString()
}

export const api = {
  listCurrencies(): Promise<CurrencyResponse[]> {
    return request(`${BASE}/currencies`)
  },

  listAccounts(includeArchived = false): Promise<AccountResponse[]> {
    return request(`${BASE}/accounts?${query({ includeArchived })}`)
  },

  createAccount(body: AccountRequest): Promise<AccountResponse> {
    return request(`${BASE}/accounts`, jsonInit('POST', body))
  },

  updateAccount(id: number, body: AccountRequest): Promise<AccountResponse> {
    return request(`${BASE}/accounts/${id}`, jsonInit('PUT', body))
  },

  archiveAccount(id: number): Promise<void> {
    return request(`${BASE}/accounts/${id}`, { method: 'DELETE' })
  },

  accountBalance(id: number): Promise<AccountBalanceResponse> {
    return request(`${BASE}/accounts/${id}/balance`)
  },

  listCategories(direction?: string): Promise<CategoryResponse[]> {
    return request(`${BASE}/categories?${query({ direction })}`)
  },

  createCategory(body: CategoryRequest): Promise<CategoryResponse> {
    return request(`${BASE}/categories`, jsonInit('POST', body))
  },

  searchTransactions(filters: TransactionFilters): Promise<TransactionPageResponse> {
    return request(`${BASE}/transactions?${query({ ...filters })}`)
  },

  createTransaction(body: TransactionRequest): Promise<TransactionResponse> {
    return request(`${BASE}/transactions`, jsonInit('POST', body))
  },

  deleteTransaction(id: number): Promise<void> {
    return request(`${BASE}/transactions/${id}`, { method: 'DELETE' })
  },

  listImports(accountId?: number): Promise<ImportBatchResponse[]> {
    return request(`${BASE}/imports?${query({ accountId })}`)
  },

  /**
   * Wgranie pliku. Bez nagłówka Content-Type — przeglądarka musi dopisać
   * granicę multipartu sama, a ustawiona ręcznie psuje żądanie.
   */
  uploadStatement(accountId: number, file: File): Promise<ImportBatchResponse> {
    const form = new FormData()
    form.append('file', file)
    return request(`${BASE}/imports?${query({ accountId })}`, { method: 'POST', body: form })
  },

  getImport(id: number): Promise<ImportBatchDetailResponse> {
    return request(`${BASE}/imports/${id}`)
  },

  commitImport(id: number, body: CommitImportRequest): Promise<CommitImportResponse> {
    return request(`${BASE}/imports/${id}/commit`, jsonInit('POST', body))
  },

  listRecurringRules(activeOnly = false): Promise<RecurringRuleResponse[]> {
    return request(`${BASE}/recurring-rules?${query({ activeOnly })}`)
  },

  createRecurringRule(body: RecurringRuleRequest): Promise<RecurringRuleResponse> {
    return request(`${BASE}/recurring-rules`, jsonInit('POST', body))
  },

  deactivateRecurringRule(id: number): Promise<void> {
    return request(`${BASE}/recurring-rules/${id}`, { method: 'DELETE' })
  },

  generateOccurrences(): Promise<GenerateOccurrencesResponse> {
    return request(`${BASE}/recurring-rules/generate`, { method: 'POST' })
  },

  listOccurrences(params: {
    from?: string
    to?: string
    status?: string
    ruleId?: number
  }): Promise<OccurrenceResponse[]> {
    return request(`${BASE}/occurrences?${query({ ...params })}`)
  },

  payOccurrence(id: number, body: PayOccurrenceRequest): Promise<OccurrenceResponse> {
    return request(`${BASE}/occurrences/${id}/pay`, jsonInit('POST', body))
  },

  skipOccurrence(id: number): Promise<OccurrenceResponse> {
    return request(`${BASE}/occurrences/${id}/skip`, { method: 'POST' })
  },

  dashboard(): Promise<DashboardResponse> {
    return request(`${BASE}/reports/dashboard`)
  },

  reportByCategory(params: ReportParams): Promise<ByCategoryReportResponse> {
    return request(`${BASE}/reports/by-category?${query({ ...params })}`)
  },

  reportCashflow(params: ReportParams): Promise<CashflowReportResponse> {
    return request(`${BASE}/reports/cashflow?${query({ ...params })}`)
  },

  reportBalances(params: ReportParams): Promise<BalancesReportResponse> {
    return request(`${BASE}/reports/balances?${query({ ...params })}`)
  },

  reportComparison(params: ReportParams): Promise<ComparisonReportResponse> {
    return request(`${BASE}/reports/comparison?${query({ ...params })}`)
  },

  reportTopSpend(params: ReportParams & { limit?: number }): Promise<TopSpendReportResponse> {
    return request(`${BASE}/reports/top-spend?${query({ ...params })}`)
  },

  reportFixedVsVariable(params: ReportParams): Promise<FixedVsVariableReportResponse> {
    return request(`${BASE}/reports/fixed-vs-variable?${query({ ...params })}`)
  },

  reportUpcoming(horizonDays?: number): Promise<UpcomingReportResponse> {
    return request(`${BASE}/reports/upcoming?${query({ horizonDays })}`)
  },

  reportForecast(horizonDays?: number, accountIds?: number[]): Promise<ForecastReportResponse> {
    return request(`${BASE}/reports/forecast?${query({ horizonDays, accountIds })}`)
  },

  reportYearlyMatrix(year: number, direction?: string): Promise<YearlyMatrixReportResponse> {
    return request(`${BASE}/reports/yearly-matrix?${query({ year, direction })}`)
  },

  reportCurrencyExposure(): Promise<CurrencyExposureReportResponse> {
    return request(`${BASE}/reports/currency-exposure`)
  },

  listCategoryRules(activeOnly = false): Promise<CategoryRuleResponse[]> {
    return request(`${BASE}/category-rules?${query({ activeOnly })}`)
  },

  createCategoryRule(body: CategoryRuleRequest): Promise<CategoryRuleResponse> {
    return request(`${BASE}/category-rules`, jsonInit('POST', body))
  },

  deleteCategoryRule(id: number): Promise<void> {
    return request(`${BASE}/category-rules/${id}`, { method: 'DELETE' })
  },

  transferCandidates(from: string, to: string): Promise<TransferCandidate[]> {
    return request(`${BASE}/transfers/candidates?${query({ from, to })}`)
  },

  mergeTransfer(body: MergeTransferRequest): Promise<TransactionResponse> {
    return request(`${BASE}/transfers/merge`, jsonInit('POST', body))
  },
}
