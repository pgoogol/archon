// Konta i ich salda. Usunięcie konta to archiwizacja — historia transakcji
// musi zostać, więc konto znika z list wyboru, a nie z bazy.

import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'

import { api, type AccountBalanceResponse, type AccountType } from '@/features/finance/api'
import FieldError from '@/features/finance/components/FieldError'
import { ACCOUNT_TYPE_LABELS } from '@/features/finance/format'
import {
  accountFormSchema,
  type AccountFormOutput,
  type AccountFormValues,
} from '@/features/finance/forms/schemas'
import { useFinanceWorkspace } from '@/features/finance/state/FinanceWorkspace'
import { financeKeys } from '@/features/finance/state/queryKeys'
import { useQueryErrorToast } from '@/features/finance/state/useQueryErrorToast'
import { useToast } from '@/shared/ui/Toasts'
import { DASH, formatMinor } from '@/shared/format'

const ACCOUNT_TYPES: AccountType[] = ['BANK', 'CASH', 'CARD']

function today(): string {

  return new Date().toISOString().slice(0, 10)
}

export default function KontaRoute() {

  const { currencies, minorUnitOf } = useFinanceWorkspace()
  const { notify, reportError } = useToast()
  const queryClient = useQueryClient()

  const [includeArchived, setIncludeArchived] = useState(false)
  // Saldo liczy się na żądanie, per konto — trzymamy je poza cache'em zapytań,
  // bo to wynik kliknięcia w wiersz, a nie stan ekranu.
  const [balances, setBalances] = useState<Record<number, AccountBalanceResponse>>({})

  const accountsQuery = useQuery({
    queryKey: financeKeys.accounts(includeArchived),
    queryFn: () => api.listAccounts(includeArchived),
  })
  useQueryErrorToast(accountsQuery.error, 'Nie udało się pobrać kont')

  const form = useForm<AccountFormValues, unknown, AccountFormOutput>({
    resolver: zodResolver(accountFormSchema),
    defaultValues: {
      name: '',
      type: 'BANK',
      currency: 'PLN',
      openingBalanceMinor: '0',
      openingBalanceOn: today(),
    },
  })
  const currency = form.watch('currency')

  const invalidateAccounts = () =>
    queryClient.invalidateQueries({ queryKey: [...financeKeys.all, 'accounts'] })

  const createAccount = useMutation({
    mutationFn: (values: AccountFormOutput) => api.createAccount(values),
    onSuccess: async () => {
      notify('Konto zapisane')
      form.reset({ ...form.getValues(), name: '' })
      await invalidateAccounts()
    },
    onError: (error) => reportError(error, 'Nie udało się zapisać konta'),
  })

  const archiveAccount = useMutation({
    mutationFn: (accountId: number) => api.archiveAccount(accountId),
    onSuccess: async () => {
      notify('Konto zarchiwizowane')
      await invalidateAccounts()
    },
    onError: (error) => reportError(error, 'Nie udało się zarchiwizować konta'),
  })

  const loadBalance = useMutation({
    mutationFn: (accountId: number) => api.accountBalance(accountId),
    onSuccess: (balance) =>
      setBalances((current) => ({ ...current, [balance.accountId]: balance })),
    onError: (error) => reportError(error, 'Nie udało się policzyć salda'),
  })

  const accounts = accountsQuery.data ?? []
  const submit = form.handleSubmit((values) => createAccount.mutateAsync(values))

  return (
    <div className="panels">
      <section className="panel">
        <h2>Konta</h2>
        <div className="row">
          <label className="filter-check">
            <input
              type="checkbox"
              checked={includeArchived}
              onChange={(event) => setIncludeArchived(event.target.checked)}
            />
            pokaż zarchiwizowane
          </label>
        </div>
        <table>
          <thead>
            <tr>
              <th scope="col">Nazwa</th>
              <th scope="col">Rodzaj</th>
              <th scope="col">Waluta</th>
              <th scope="col">Saldo</th>
              <th scope="col">Działanie</th>
            </tr>
          </thead>
          <tbody>
            {accounts.length === 0 ? (
              <tr className="empty-row">
                <td colSpan={5}>Nie ma jeszcze żadnego konta.</td>
              </tr>
            ) : (
              accounts.map((account) => {
                const balance = balances[account.id]
                return (
                  <tr key={account.id}>
                    <td>{account.name}</td>
                    <td>{ACCOUNT_TYPE_LABELS[account.type]}</td>
                    <td>{account.currency}</td>
                    <td>
                      {balance
                        ? formatMinor(balance.balanceMinor, balance.currency, balance.minorUnit)
                        : DASH}
                    </td>
                    <td>
                      <button type="button" onClick={() => loadBalance.mutate(account.id)}>
                        Policz saldo
                      </button>
                      {account.archived ? null : (
                        <button type="button" onClick={() => archiveAccount.mutate(account.id)}>
                          Archiwizuj
                        </button>
                      )}
                    </td>
                  </tr>
                )
              })
            )}
          </tbody>
        </table>
      </section>

      <section className="panel">
        <h2>Nowe konto</h2>
        <form onSubmit={submit} noValidate>
          <label className="field">
            <span>Nazwa</span>
            <input {...form.register('name')} />
          </label>
          <FieldError message={form.formState.errors.name?.message} />

          <label className="field">
            <span>Rodzaj</span>
            <select {...form.register('type')}>
              {ACCOUNT_TYPES.map((option) => (
                <option key={option} value={option}>
                  {ACCOUNT_TYPE_LABELS[option]}
                </option>
              ))}
            </select>
          </label>

          <label className="field">
            <span>Waluta</span>
            <select {...form.register('currency')}>
              {currencies.map((entry) => (
                <option key={entry.code} value={entry.code}>
                  {entry.code} — {entry.name}
                </option>
              ))}
            </select>
          </label>
          <FieldError message={form.formState.errors.currency?.message} />

          <label className="field">
            <span>Saldo otwarcia (w jednostkach podrzędnych, {minorUnitOf(currency)} miejsc)</span>
            {/* Pole tekstowe, nie type="number": przeglądarka po cichu wycina
                przecinek, więc „12,50" dojechałoby do serwera jako 1250 — sto razy
                za dużo. Tekst dociera do schematu w takiej postaci, w jakiej go
                wpisano, i schemat może go odrzucić. */}
            <input inputMode="numeric" {...form.register('openingBalanceMinor')} />
          </label>
          <FieldError message={form.formState.errors.openingBalanceMinor?.message} />

          <label className="field">
            <span>Data salda otwarcia</span>
            <input type="date" {...form.register('openingBalanceOn')} />
          </label>
          <FieldError message={form.formState.errors.openingBalanceOn?.message} />

          <button type="submit" disabled={form.formState.isSubmitting}>
            {form.formState.isSubmitting ? 'Zapisywanie…' : 'Zapisz konto'}
          </button>
        </form>
      </section>
    </div>
  )
}
