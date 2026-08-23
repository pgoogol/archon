// Schematy formularzy domeny finansowej.
//
// Walidacja siedzi w schemacie, nigdy w warunkach rozsypanych po `onChange`,
// a typ wartości formularza bierze się z `z.infer` — nie pisze się go drugi raz.
//
// Kwoty wchodzą w JEDNOSTKACH PODRZĘDNYCH i jako liczby całkowite. To nie jest
// wygoda implementacji, tylko ta sama reguła co po stronie serwera: grosz nigdy
// nie przechodzi przez liczbę zmiennoprzecinkową. Pole formularza jest tekstem,
// więc schemat robi jedyną konwersję w całej domenie i odrzuca wszystko, co nie
// jest liczbą całkowitą.

import { z } from 'zod'

import { validationMessages } from '@/shared/validationMessages'

/** Tekst z pola liczbowego na `number`, wyłącznie całkowity. */
function integerFromInput(message: string) {

  return z
    .string()
    .trim()
    .min(1, validationMessages.required)
    .refine((text) => /^-?\d+$/.test(text), message)
    .transform((text) => Number(text))
}

const requiredText = z.string().trim().min(1, validationMessages.required)
const isoDate = z.string().trim().min(1, validationMessages.requiredDate)

export const accountFormSchema = z.object({
  name: requiredText,
  type: z.enum(['BANK', 'CASH', 'CARD']),
  currency: z.string().regex(/^[A-Z]{3}$/, validationMessages.currencyCode),
  openingBalanceMinor: integerFromInput(validationMessages.integer),
  openingBalanceOn: isoDate,
})

export type AccountFormValues = z.input<typeof accountFormSchema>
export type AccountFormOutput = z.output<typeof accountFormSchema>

export const transactionFormSchema = z
  .object({
    type: z.enum(['EXPENSE', 'INCOME', 'TRANSFER']),
    bookedOn: isoDate,
    amountMinor: integerFromInput(validationMessages.integer).refine(
      (amount) => amount > 0,
      validationMessages.positiveAmount,
    ),
    currency: z.string().regex(/^[A-Z]{3}$/, validationMessages.currencyCode),
    accountId: integerFromInput(validationMessages.selectOption),
    categoryId: z.string().trim(),
    description: z.string().trim(),
  })

export type TransactionFormValues = z.input<typeof transactionFormSchema>
export type TransactionFormOutput = z.output<typeof transactionFormSchema>

export const recurringRuleFormSchema = z.object({
  name: requiredText,
  type: z.enum(['EXPENSE', 'INCOME']),
  amountMinor: integerFromInput(validationMessages.integer).refine(
    (amount) => amount > 0,
    validationMessages.positiveAmount,
  ),
  currency: z.string().regex(/^[A-Z]{3}$/, validationMessages.currencyCode),
  frequency: z.enum(['MONTHLY', 'QUARTERLY', 'YEARLY']),
  dayOfMonth: integerFromInput(validationMessages.integer).refine(
    (day) => day >= 1 && day <= 31,
    validationMessages.dayOfMonth,
  ),
  startsOn: isoDate,
  accountId: integerFromInput(validationMessages.selectOption),
  categoryId: integerFromInput(validationMessages.selectOption),
  /** Pusty wzorzec znaczy „nie dopasowuj przy imporcie", nie „dopasuj wszystko". */
  matchPattern: z.string().trim(),
})

export type RecurringRuleFormValues = z.input<typeof recurringRuleFormSchema>
export type RecurringRuleFormOutput = z.output<typeof recurringRuleFormSchema>

export const categoryRuleFormSchema = z.object({
  pattern: requiredText,
  matchField: z.enum(['DESCRIPTION', 'COUNTERPARTY', 'ANY']),
  categoryId: integerFromInput(validationMessages.selectOption),
  priority: integerFromInput(validationMessages.integer),
})

export type CategoryRuleFormValues = z.input<typeof categoryRuleFormSchema>
export type CategoryRuleFormOutput = z.output<typeof categoryRuleFormSchema>

/**
 * Zakres dat raportu. Odwrócony zakres odrzucamy tutaj, a nie dopiero na
 * serwerze — inaczej użytkownik dostaje 400 zamiast podkreślonego pola.
 */
export const dateRangeSchema = z
  .object({
    from: isoDate,
    to: isoDate,
  })
  .refine((range) => range.from <= range.to, {
    message: validationMessages.rangeReversed,
    path: ['to'],
  })

export type DateRangeValues = z.infer<typeof dateRangeSchema>
