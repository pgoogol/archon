// Etykiety domenowe finansów. Sam formater kwot jest bezdomenowy i siedzi
// w shared/format.ts — kwota w jednostkach podrzędnych to sprawa całej
// aplikacji, nie tylko tego katalogu.

import type {
  AccountType,
  CategoryDirection,
  Granularity,
  ImportRowStatus,
  MatchField,
  OccurrenceStatus,
  RecurringFrequency,
  TransactionType,
} from '@/features/finance/api'

export const TYPE_LABELS: Record<TransactionType, string> = {
  EXPENSE: 'wydatek',
  INCOME: 'przychód',
  TRANSFER: 'transfer',
}

export const ACCOUNT_TYPE_LABELS: Record<AccountType, string> = {
  BANK: 'rachunek',
  CASH: 'gotówka',
  CARD: 'karta',
}

export const DIRECTION_LABELS: Record<CategoryDirection, string> = {
  EXPENSE: 'wydatki',
  INCOME: 'przychody',
}

export const FREQUENCY_LABELS: Record<RecurringFrequency, string> = {
  MONTHLY: 'co miesiąc',
  QUARTERLY: 'co kwartał',
  YEARLY: 'co rok',
}

export const OCCURRENCE_LABELS: Record<OccurrenceStatus, string> = {
  PENDING: 'czeka',
  PAID: 'zapłacone',
  SKIPPED: 'pominięte',
  OVERDUE: 'po terminie',
}

export const ROW_STATUS_LABELS: Record<ImportRowStatus, string> = {
  NEW: 'nowy',
  DUPLICATE: 'duplikat',
  COMMITTED: 'zapisany',
}

export const MATCH_FIELD_LABELS: Record<MatchField, string> = {
  DESCRIPTION: 'opis',
  COUNTERPARTY: 'kontrahent',
  ANY: 'opis lub kontrahent',
}

export const GRANULARITY_LABELS: Record<Granularity, string> = {
  DAY: 'dzień',
  MONTH: 'miesiąc',
  YEAR: 'rok',
}

/** Pierwszy dzień bieżącego miesiąca w zapisie ISO. */
export function firstDayOfMonth(today = new Date()): string {

  const year = today.getFullYear()
  const month = String(today.getMonth() + 1).padStart(2, '0')
  return `${year}-${month}-01`
}

export function isoDate(date: Date): string {

  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

/** Dzień dzisiejszy w zapisie ISO — domyślny koniec każdego zakresu. */
export function today(): string {

  return isoDate(new Date())
}

/** Początek zakresu domyślnego: dwanaście miesięcy wstecz od pierwszego dnia. */
export function yearAgo(from = new Date()): string {

  const start = new Date(from.getFullYear() - 1, from.getMonth(), 1)
  return isoDate(start)
}

/**
 * Etykieta okresu na osi raportu. Dzień pokazujemy w całości, miesiąc bez dnia,
 * rok samą liczbą — inaczej oś roczna powtarza dwanaście razy „-01-01".
 */
export function periodLabel(period: string, granularity: Granularity): string {

  if (granularity === 'YEAR') return period.slice(0, 4)
  if (granularity === 'MONTH') return period.slice(0, 7)
  return period
}
