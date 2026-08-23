// Klucze zapytań domeny finansowej — jedno miejsce zamiast literałów rozsypanych
// po ekranach.
//
// Każdy klucz zaczyna się od 'finance', więc unieważnienie całej domeny to jeden
// prefiks, a sąsiednia domena nigdy nie trafi w nasz cache. Do klucza wchodzi
// KAŻDY parametr wpływający na wynik: filtr wpisany w formularzu zmienia klucz,
// więc dwa zestawy filtrów nie mogą zobaczyć swoich odpowiedzi.

import type { ReportParams, TransactionFilters } from '@/features/finance/api'

const ROOT = 'finance' as const

export const financeKeys = {
  all: [ROOT] as const,

  currencies: () => [ROOT, 'currencies'] as const,

  accounts: (includeArchived: boolean) => [ROOT, 'accounts', { includeArchived }] as const,
  accountBalance: (id: number) => [ROOT, 'accounts', id, 'balance'] as const,

  categories: (direction?: string) => [ROOT, 'categories', { direction }] as const,

  transactions: (filters: TransactionFilters) => [ROOT, 'transactions', filters] as const,

  imports: (accountId?: number) => [ROOT, 'imports', { accountId }] as const,
  importDetail: (id: number) => [ROOT, 'imports', id] as const,

  recurringRules: (activeOnly: boolean) => [ROOT, 'recurring-rules', { activeOnly }] as const,
  occurrences: (params: Record<string, unknown>) => [ROOT, 'occurrences', params] as const,

  categoryRules: (activeOnly: boolean) => [ROOT, 'category-rules', { activeOnly }] as const,
  transferCandidates: (from: string, to: string) =>
    [ROOT, 'transfers', 'candidates', { from, to }] as const,

  dashboard: () => [ROOT, 'reports', 'dashboard'] as const,
  report: (name: string, params: ReportParams | Record<string, unknown>) =>
    [ROOT, 'reports', name, params] as const,
}
