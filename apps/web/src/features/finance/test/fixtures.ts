// Object Mother dla testów domeny finansowej — minimalne obiekty zgodne z DTO
// z kontraktu, z nadpisywaniem tylko tego, co w danym teście istotne.

import type {
  DashboardResponse,
  ImportBatchDetailResponse,
  ImportRowResponse,
  OccurrenceResponse,
  UpcomingItem,
} from '@/features/finance/api'

export function anUpcomingItem(overrides: Partial<UpcomingItem> = {}): UpcomingItem {

  return {
    occurrenceId: 1,
    ruleId: 7,
    ruleName: 'Prąd',
    accountId: 1,
    accountName: 'Bieżące',
    dueDate: '2026-03-10',
    expectedAmountMinor: 10_000,
    currency: 'PLN',
    type: 'EXPENSE',
    overdue: false,
    ...overrides,
  }
}

export function aDashboard(overrides: Partial<DashboardResponse> = {}): DashboardResponse {

  return {
    baseCurrency: 'PLN',
    month: '2026-03-01',
    accountBalances: [
      {
        accountId: 1,
        accountName: 'Bieżące',
        currency: 'PLN',
        minorUnit: 2,
        balanceMinor: 123_456,
        baseValueMinor: 123_456,
      },
    ],
    monthIncomeMinor: 900_000,
    monthExpenseMinor: 250_000,
    monthNetMinor: 650_000,
    overdue: [],
    upcoming: [anUpcomingItem()],
    ...overrides,
  }
}

export function anImportRow(overrides: Partial<ImportRowResponse> = {}): ImportRowResponse {

  return {
    id: 11,
    ordinal: 0,
    bookedOn: '2026-03-05',
    amountMinor: -4_500,
    currency: 'PLN',
    originalAmountMinor: null,
    originalCurrency: null,
    description: 'ZAKUP BIEDRONKA',
    counterparty: 'Sklep',
    bankReference: null,
    status: 'NEW',
    suggestedCategoryId: null,
    suggestedCategoryName: null,
    suggestedOccurrenceId: null,
    suggestedOccurrenceName: null,
    transactionId: null,
    ...overrides,
  }
}

export function anImportBatch(
  rows: ImportRowResponse[] = [anImportRow()],
): ImportBatchDetailResponse {

  return {
    batch: {
      id: 5,
      accountId: 1,
      accountName: 'Bieżące',
      fileName: 'luty.csv',
      uploadedAt: '2026-03-06T10:00:00Z',
      periodFrom: '2026-02-01',
      periodTo: '2026-02-28',
      openingBalanceMinor: 0,
      closingBalanceMinor: -4_500,
      rowCount: rows.length,
      status: 'PARSED',
      committedAt: null,
      newCount: rows.filter((row) => row.status === 'NEW').length,
      duplicateCount: rows.filter((row) => row.status === 'DUPLICATE').length,
    },
    rows,
  }
}

export function anOccurrence(overrides: Partial<OccurrenceResponse> = {}): OccurrenceResponse {

  return {
    id: 1,
    ruleId: 7,
    ruleName: 'Prąd',
    accountId: 1,
    categoryId: 2,
    dueDate: '2026-03-10',
    expectedAmountMinor: 10_000,
    currency: 'PLN',
    status: 'PENDING',
    paidOn: null,
    paidAmountMinor: null,
    transactionId: null,
    ...overrides,
  }
}
