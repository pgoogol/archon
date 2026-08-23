// Lista transakcji z filtrami i stronicowaniem oraz ręczny zapis pojedynczej
// operacji. Filtry siedzą w adresie, więc odświeżenie strony wraca do tego
// samego widoku, a link da się komuś podesłać.

import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo } from 'react'

import { api, type TransactionType } from '@/features/finance/api'
import { TYPE_LABELS } from '@/features/finance/format'
import { useFinanceWorkspace } from '@/features/finance/state/FinanceWorkspace'
import { financeKeys } from '@/features/finance/state/queryKeys'
import { useQueryErrorToast } from '@/features/finance/state/useQueryErrorToast'
import { useHashRoute } from '@/shared/hooks/useHashRoute'
import { useToast } from '@/shared/ui/Toasts'
import { DASH, formatMinor } from '@/shared/format'

const PAGE_SIZE = 20
const TYPES: TransactionType[] = ['EXPENSE', 'INCOME', 'TRANSFER']

export default function TransakcjeRoute() {

  const { minorUnitOf } = useFinanceWorkspace()
  const { params, setParams } = useHashRoute()
  const { notify, reportError } = useToast()
  const queryClient = useQueryClient()

  const filters = useMemo(
    () => ({
      from: params.get('from') ?? '',
      to: params.get('to') ?? '',
      accountId: params.get('accountId') ?? '',
      categoryId: params.get('categoryId') ?? '',
      type: params.get('type') ?? '',
      page: Number(params.get('page') ?? '0'),
    }),
    [params],
  )

  // Słowniki kont i kategorii schodzą jednym hookiem — obydwa wypełniają listy
  // wyboru filtrów i żaden z nich nie zależy od drugiego.
  const dictionaries = useQueries({
    queries: [
      { queryKey: financeKeys.accounts(false), queryFn: () => api.listAccounts(false) },
      { queryKey: financeKeys.categories(), queryFn: () => api.listCategories() },
    ],
  })
  const [accountsQuery, categoriesQuery] = dictionaries
  useQueryErrorToast(
    accountsQuery.error ?? categoriesQuery.error,
    'Nie udało się pobrać słowników',
  )

  const search = useMemo(
    () => ({
      from: filters.from || undefined,
      to: filters.to || undefined,
      accountId: filters.accountId ? Number(filters.accountId) : undefined,
      categoryId: filters.categoryId ? Number(filters.categoryId) : undefined,
      type: filters.type || undefined,
      page: filters.page,
      size: PAGE_SIZE,
    }),
    [filters],
  )

  const transactionsQuery = useQuery({
    queryKey: financeKeys.transactions(search),
    queryFn: () => api.searchTransactions(search),
  })
  useQueryErrorToast(transactionsQuery.error, 'Nie udało się pobrać transakcji')

  const removeTransaction = useMutation({
    mutationFn: (id: number) => api.deleteTransaction(id),
    onSuccess: async () => {
      notify('Transakcja usunięta')
      // Usunięcie zmienia i listę, i salda, i każdy raport — unieważniamy całą
      // domenę, bo wyliczanie, których raportów dotyczyło, byłoby zgadywaniem.
      await queryClient.invalidateQueries({ queryKey: financeKeys.all })
    },
    onError: (error) => reportError(error, 'Nie udało się usunąć transakcji'),
  })

  const accounts = accountsQuery.data ?? []
  const categories = categoriesQuery.data ?? []
  const page = transactionsQuery.data ?? null

  return (
    <section className="panel">
      <h2>Transakcje</h2>

      <div className="filters-basic">
        <label className="filter-group">
          <span>Od</span>
          <input
            type="date"
            value={filters.from}
            onChange={(event) => setParams({ from: event.target.value, page: 0 })}
          />
        </label>
        <label className="filter-group">
          <span>Do</span>
          <input
            type="date"
            value={filters.to}
            onChange={(event) => setParams({ to: event.target.value, page: 0 })}
          />
        </label>
        <label className="filter-group">
          <span>Konto</span>
          <select
            value={filters.accountId}
            onChange={(event) => setParams({ accountId: event.target.value, page: 0 })}
          >
            <option value="">wszystkie</option>
            {accounts.map((account) => (
              <option key={account.id} value={account.id}>
                {account.name}
              </option>
            ))}
          </select>
        </label>
        <label className="filter-group">
          <span>Kategoria</span>
          <select
            value={filters.categoryId}
            onChange={(event) => setParams({ categoryId: event.target.value, page: 0 })}
          >
            <option value="">wszystkie</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </select>
        </label>
        <label className="filter-group">
          <span>Typ</span>
          <select
            value={filters.type}
            onChange={(event) => setParams({ type: event.target.value, page: 0 })}
          >
            <option value="">wszystkie</option>
            {TYPES.map((option) => (
              <option key={option} value={option}>
                {TYPE_LABELS[option]}
              </option>
            ))}
          </select>
        </label>
      </div>

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th scope="col">Data</th>
              <th scope="col">Typ</th>
              <th scope="col">Kwota</th>
              <th scope="col">Konto</th>
              <th scope="col">Kategoria</th>
              <th scope="col">Opis</th>
              <th scope="col">Działanie</th>
            </tr>
          </thead>
          <tbody>
            {!page || page.content.length === 0 ? (
              <tr className="empty-row">
                <td colSpan={7}>Brak transakcji dla tych filtrów.</td>
              </tr>
            ) : (
              page.content.map((transaction) => (
                <tr key={transaction.id}>
                  <td>{transaction.bookedOn}</td>
                  <td>{TYPE_LABELS[transaction.type]}</td>
                  <td>
                    {formatMinor(
                      transaction.amountMinor,
                      transaction.currency,
                      minorUnitOf(transaction.currency),
                    )}
                  </td>
                  <td>{transaction.accountName}</td>
                  <td>{transaction.categoryName ?? DASH}</td>
                  <td>{transaction.description ?? DASH}</td>
                  <td>
                    <button type="button" onClick={() => removeTransaction.mutate(transaction.id)}>
                      Usuń
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {page ? (
        <div className="pager">
          <button
            type="button"
            disabled={filters.page <= 0}
            onClick={() => setParams({ page: filters.page - 1 })}
          >
            Poprzednia
          </button>
          <span className="result-summary">
            strona {page.page + 1} z {Math.max(1, page.totalPages)} · {page.totalElements} operacji
          </span>
          <button
            type="button"
            disabled={filters.page + 1 >= page.totalPages}
            onClick={() => setParams({ page: filters.page + 1 })}
          >
            Następna
          </button>
        </div>
      ) : null}
    </section>
  )
}
