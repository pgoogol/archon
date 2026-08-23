// Terminarz rachunków cyklicznych: reguły i wynikające z nich terminy.
//
// Status „po terminie" nie jest przechowywany — backend wylicza go przy
// odczycie z porównania terminu z dzisiejszą datą, więc lista nigdy nie
// rozjedzie się z kalendarzem.

import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueries, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'

import { api, type OccurrenceResponse, type RecurringFrequency } from '@/features/finance/api'
import FieldError from '@/features/finance/components/FieldError'
import { FREQUENCY_LABELS, OCCURRENCE_LABELS, today } from '@/features/finance/format'
import {
  recurringRuleFormSchema,
  type RecurringRuleFormOutput,
  type RecurringRuleFormValues,
} from '@/features/finance/forms/schemas'
import { useFinanceWorkspace } from '@/features/finance/state/FinanceWorkspace'
import { financeKeys } from '@/features/finance/state/queryKeys'
import { useQueryErrorToast } from '@/features/finance/state/useQueryErrorToast'
import { useToast } from '@/shared/ui/Toasts'
import { formatMinor } from '@/shared/format'

const FREQUENCIES: RecurringFrequency[] = ['MONTHLY', 'QUARTERLY', 'YEARLY']

export default function TerminarzRoute() {

  const { minorUnitOf } = useFinanceWorkspace()
  const { notify, reportError } = useToast()
  const queryClient = useQueryClient()

  const [rulesQuery, occurrencesQuery, accountsQuery, categoriesQuery] = useQueries({
    queries: [
      { queryKey: financeKeys.recurringRules(false), queryFn: () => api.listRecurringRules(false) },
      { queryKey: financeKeys.occurrences({}), queryFn: () => api.listOccurrences({}) },
      { queryKey: financeKeys.accounts(false), queryFn: () => api.listAccounts(false) },
      { queryKey: financeKeys.categories('EXPENSE'), queryFn: () => api.listCategories('EXPENSE') },
    ],
  })
  const loadError =
    rulesQuery.error ?? occurrencesQuery.error ?? accountsQuery.error ?? categoriesQuery.error
  useQueryErrorToast(loadError, 'Nie udało się pobrać terminarza')

  const rules = rulesQuery.data ?? []
  const occurrences = occurrencesQuery.data ?? []
  const accounts = accountsQuery.data ?? []
  const categories = categoriesQuery.data ?? []

  const form = useForm<RecurringRuleFormValues, unknown, RecurringRuleFormOutput>({
    resolver: zodResolver(recurringRuleFormSchema),
    defaultValues: {
      name: '',
      type: 'EXPENSE',
      amountMinor: '',
      currency: 'PLN',
      frequency: 'MONTHLY',
      dayOfMonth: '10',
      startsOn: today(),
      accountId: '',
      categoryId: '',
      matchPattern: '',
    },
  })

  /** Terminarz zmienia salda i raporty, więc unieważniamy całą domenę. */
  const invalidateFinance = () => queryClient.invalidateQueries({ queryKey: financeKeys.all })

  const createRule = useMutation({
    mutationFn: (values: RecurringRuleFormOutput) =>
      api.createRecurringRule({
        name: values.name,
        accountId: values.accountId,
        categoryId: values.categoryId,
        type: values.type,
        amountMinor: values.amountMinor,
        frequency: values.frequency,
        dayOfMonth: values.dayOfMonth,
        startsOn: values.startsOn,
        matchPattern: values.matchPattern || null,
      }),
    onSuccess: async () => {
      notify('Reguła zapisana wraz z terminarzem')
      form.reset({ ...form.getValues(), name: '', amountMinor: '', matchPattern: '' })
      await invalidateFinance()
    },
    onError: (error) => reportError(error, 'Nie udało się zapisać reguły'),
  })

  const payOccurrence = useMutation({
    // kwota faktyczna bywa inna niż oczekiwana; tu domyślnie bierzemy
    // oczekiwaną, a poprawia się ją w transakcjach
    mutationFn: (occurrence: OccurrenceResponse) =>
      api.payOccurrence(occurrence.id, {
        paidOn: today(),
        paidAmountMinor: occurrence.expectedAmountMinor,
      }),
    onSuccess: async () => {
      notify('Rachunek oznaczony jako zapłacony')
      await invalidateFinance()
    },
    onError: (error) => reportError(error, 'Nie udało się rozliczyć rachunku'),
  })

  const skipOccurrence = useMutation({
    mutationFn: (occurrenceId: number) => api.skipOccurrence(occurrenceId),
    onSuccess: async () => {
      notify('Pozycja pominięta')
      await invalidateFinance()
    },
    onError: (error) => reportError(error, 'Nie udało się pominąć pozycji'),
  })

  const generateOccurrences = useMutation({
    mutationFn: () => api.generateOccurrences(),
    onSuccess: async (result) => {
      notify(`Dołożono ${result.createdCount} pozycji, horyzont do ${result.horizonTo}`)
      await invalidateFinance()
    },
    onError: (error) => reportError(error, 'Nie udało się uzupełnić terminarza'),
  })

  const deactivateRule = useMutation({
    mutationFn: (ruleId: number) => api.deactivateRecurringRule(ruleId),
    onSuccess: async () => {
      notify('Reguła wyłączona; historia została')
      await invalidateFinance()
    },
    onError: (error) => reportError(error, 'Nie udało się wyłączyć reguły'),
  })

  const submitRule = form.handleSubmit((values) => createRule.mutateAsync(values))

  return (
    <div className="panels">
      <section className="panel">
        <h2>Terminarz</h2>
        <div className="row">
          <button type="button" onClick={() => generateOccurrences.mutate()}>
            Uzupełnij terminarz
          </button>
          <span className="muted">Wywołanie jest idempotentne — drugi przebieg daje zero.</span>
        </div>
        <table>
          <thead>
            <tr>
              <th scope="col">Termin</th>
              <th scope="col">Rachunek</th>
              <th scope="col">Kwota</th>
              <th scope="col">Status</th>
              <th scope="col">Działanie</th>
            </tr>
          </thead>
          <tbody>
            {occurrences.length === 0 ? (
              <tr className="empty-row">
                <td colSpan={5}>Terminarz jest pusty.</td>
              </tr>
            ) : (
              occurrences.map((occurrence) => (
                <tr key={occurrence.id}>
                  <td>{occurrence.dueDate}</td>
                  <td>{occurrence.ruleName}</td>
                  <td>
                    {formatMinor(
                      occurrence.expectedAmountMinor,
                      occurrence.currency,
                      minorUnitOf(occurrence.currency),
                    )}
                  </td>
                  <td>
                    {occurrence.status === 'OVERDUE' ? (
                      <span className="badge badge-warn">
                        {OCCURRENCE_LABELS[occurrence.status]}
                      </span>
                    ) : (
                      OCCURRENCE_LABELS[occurrence.status]
                    )}
                  </td>
                  <td>
                    {occurrence.status === 'PAID' || occurrence.status === 'SKIPPED' ? null : (
                      <>
                        <button type="button" onClick={() => payOccurrence.mutate(occurrence)}>
                          Zapłacone
                        </button>
                        <button type="button" onClick={() => skipOccurrence.mutate(occurrence.id)}>
                          Pomiń
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </section>

      <section className="panel">
        <h2>Rachunki cykliczne</h2>
        <ul className="finance-list">
          {rules.map((rule) => (
            <li key={rule.id}>
              <span className="finance-list-main">
                {rule.name}
                {rule.active ? null : <span className="badge">wyłączona</span>}
              </span>
              <span className="muted">
                {FREQUENCY_LABELS[rule.frequency]}, {rule.dayOfMonth} dnia · {rule.accountName}
              </span>
              <span className="finance-list-value">
                {formatMinor(rule.amountMinor, rule.currency, minorUnitOf(rule.currency))}
              </span>
              {rule.active ? (
                <button type="button" onClick={() => deactivateRule.mutate(rule.id)}>
                  Wyłącz
                </button>
              ) : null}
            </li>
          ))}
        </ul>

        <h3>Nowy rachunek cykliczny</h3>
        <form onSubmit={submitRule} noValidate>
          <label className="field">
            <span>Nazwa</span>
            <input {...form.register('name')} />
          </label>
          <FieldError message={form.formState.errors.name?.message} />

          <label className="field">
            <span>Konto</span>
            <select {...form.register('accountId')}>
              <option value="">wybierz…</option>
              {accounts.map((account) => (
                <option key={account.id} value={account.id}>
                  {account.name}
                </option>
              ))}
            </select>
          </label>
          <FieldError message={form.formState.errors.accountId?.message} />

          <label className="field">
            <span>Kategoria</span>
            <select {...form.register('categoryId')}>
              <option value="">wybierz…</option>
              {categories.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </select>
          </label>
          <FieldError message={form.formState.errors.categoryId?.message} />

          <label className="field">
            <span>Kwota oczekiwana (w jednostkach podrzędnych)</span>
            {/* Tekst, nie liczba — patrz komentarz przy saldzie otwarcia w KontaRoute. */}
            <input inputMode="numeric" {...form.register('amountMinor')} />
          </label>
          <FieldError message={form.formState.errors.amountMinor?.message} />

          <label className="field">
            <span>Częstotliwość</span>
            <select {...form.register('frequency')}>
              {FREQUENCIES.map((option) => (
                <option key={option} value={option}>
                  {FREQUENCY_LABELS[option]}
                </option>
              ))}
            </select>
          </label>

          <label className="field">
            <span>Dzień miesiąca (31 w lutym wypada ostatniego dnia)</span>
            <input inputMode="numeric" {...form.register('dayOfMonth')} />
          </label>
          <FieldError message={form.formState.errors.dayOfMonth?.message} />

          <label className="field">
            <span>Obowiązuje od</span>
            <input type="date" {...form.register('startsOn')} />
          </label>
          <FieldError message={form.formState.errors.startsOn?.message} />

          <label className="field">
            <span>Fragment opisu do rozpoznania przy imporcie</span>
            <input {...form.register('matchPattern')} />
          </label>

          <button type="submit" disabled={form.formState.isSubmitting}>
            {form.formState.isSubmitting ? 'Zapisywanie…' : 'Zapisz rachunek'}
          </button>
        </form>
      </section>
    </div>
  )
}
