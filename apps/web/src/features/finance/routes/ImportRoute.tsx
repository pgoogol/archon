// Import wyciągu w dwóch krokach: wgranie i podgląd, a dopiero potem
// zatwierdzenie. Rozdzielenie jest tu istotą sprawy, nie wygodą interfejsu —
// nikt nie wycofa ręcznie stu transakcji, więc użytkownik musi zobaczyć,
// co się stanie, zanim się stanie.
//
// Podpowiedzi z backendu (kategoria z reguł, pozycja terminarza) wchodzą do
// formularza jako wartości domyślne. Żadna z nich nie zapisuje się sama.

import { useMutation, useQueries, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'

import { api, type ImportBatchDetailResponse } from '@/features/finance/api'
import { ROW_STATUS_LABELS } from '@/features/finance/format'
import { useFinanceWorkspace } from '@/features/finance/state/FinanceWorkspace'
import { financeKeys } from '@/features/finance/state/queryKeys'
import { useQueryErrorToast } from '@/features/finance/state/useQueryErrorToast'
import { useToast } from '@/shared/ui/Toasts'
import { DASH, formatMinor } from '@/shared/format'

export default function ImportRoute() {

  const { minorUnitOf } = useFinanceWorkspace()
  const { notify, reportError } = useToast()
  const queryClient = useQueryClient()

  const [accountId, setAccountId] = useState('')
  const [file, setFile] = useState<File | null>(null)
  // Podgląd partii to stan tego ekranu, nie zasób do cache'owania: dotyczy
  // jednego wgranego pliku i znika po zatwierdzeniu.
  const [batch, setBatch] = useState<ImportBatchDetailResponse | null>(null)
  const [categoryByRow, setCategoryByRow] = useState<Record<number, string>>({})
  const [settleRow, setSettleRow] = useState<Record<number, boolean>>({})

  const [accountsQuery, categoriesQuery] = useQueries({
    queries: [
      { queryKey: financeKeys.accounts(false), queryFn: () => api.listAccounts(false) },
      { queryKey: financeKeys.categories(), queryFn: () => api.listCategories() },
    ],
  })
  useQueryErrorToast(
    accountsQuery.error ?? categoriesQuery.error,
    'Nie udało się pobrać słowników',
  )

  const accounts = accountsQuery.data ?? []
  const categories = categoriesQuery.data ?? []

  // Pierwsze konto z listy jako wybór domyślny — ustawiane raz, żeby nie
  // nadpisywać wyboru użytkownika przy każdym odświeżeniu słownika.
  useEffect(() => {
    if (accounts.length === 0) return
    setAccountId((chosen) => chosen || String(accounts[0].id))
  }, [accounts])

  const uploadStatement = useMutation({
    mutationFn: async (input: { accountId: number; file: File }) => {
      const created = await api.uploadStatement(input.accountId, input.file)
      return api.getImport(created.id)
    },
    onSuccess: (detail) => {
      setBatch(detail)
      setCategoryByRow(defaultCategories(detail))
      setSettleRow(defaultSettlements(detail))
      notify(`Wczytano ${detail.rows.length} wierszy — nic jeszcze nie zapisano`)
    },
    onError: (error) => reportError(error, 'Nie udało się wczytać wyciągu'),
  })

  const commitImport = useMutation({
    mutationFn: (loaded: ImportBatchDetailResponse) => {
      const pending = loaded.rows.filter((row) => row.status === 'NEW')
      return api.commitImport(loaded.batch.id, {
        categoryAssignments: pending.map((row) => ({
          rowId: row.id,
          categoryId: Number(categoryByRow[row.id]),
        })),
        occurrenceAssignments: pending
          .filter((row) => settleRow[row.id] && row.suggestedOccurrenceId)
          .map((row) => ({ rowId: row.id, occurrenceId: Number(row.suggestedOccurrenceId) })),
      })
    },
    onSuccess: async (result) => {
      notify(`Zapisano ${result.committedCount} transakcji`)
      setBatch(null)
      await queryClient.invalidateQueries({ queryKey: financeKeys.all })
    },
    onError: (error) => reportError(error, 'Nie udało się zatwierdzić wyciągu'),
  })

  const busy = uploadStatement.isPending || commitImport.isPending

  const upload = (event: React.FormEvent) => {
    event.preventDefault()
    if (!file || !accountId) return
    uploadStatement.mutate({ accountId: Number(accountId), file })
  }

  const commit = () => {
    if (!batch) return
    commitImport.mutate(batch)
  }

  return (
    <div className="panels">
      <section className="panel">
        <h2>Wgranie wyciągu</h2>
        <form onSubmit={upload}>
          <label className="field">
            <span>Konto</span>
            <select value={accountId} onChange={(event) => setAccountId(event.target.value)}>
              {accounts.map((account) => (
                <option key={account.id} value={account.id}>
                  {account.name} ({account.currency})
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Plik wyciągu</span>
            <input
              type="file"
              accept=".csv,text/csv"
              onChange={(event) => setFile(event.target.files?.[0] ?? null)}
            />
          </label>
          <button type="submit" disabled={busy || !file}>
            {busy ? 'Pracuję…' : 'Wczytaj i pokaż podgląd'}
          </button>
        </form>
        <p className="muted">
          Ten sam plik wgrany drugi raz odpada po skrócie zawartości, zanim ktokolwiek go
          sparsuje.
        </p>
      </section>

      {batch ? (
        <section className="panel">
          <h2>Podgląd — nic jeszcze nie zapisano</h2>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th scope="col">Data</th>
                  <th scope="col">Kwota</th>
                  <th scope="col">Opis</th>
                  <th scope="col">Status</th>
                  <th scope="col">Kategoria</th>
                  <th scope="col">Rachunek cykliczny</th>
                </tr>
              </thead>
              <tbody>
                {batch.rows.map((row) => (
                  <tr key={row.id}>
                    <td>{row.bookedOn}</td>
                    <td>
                      {formatMinor(row.amountMinor, row.currency, minorUnitOf(row.currency))}
                    </td>
                    <td>{row.description ?? DASH}</td>
                    <td>{ROW_STATUS_LABELS[row.status]}</td>
                    <td>
                      {row.status === 'NEW' ? (
                        <select
                          aria-label={`Kategoria dla wiersza ${row.ordinal + 1}`}
                          value={categoryByRow[row.id] ?? ''}
                          onChange={(event) =>
                            setCategoryByRow((current) => ({
                              ...current,
                              [row.id]: event.target.value,
                            }))
                          }
                        >
                          <option value="">wybierz…</option>
                          {categories.map((category) => (
                            <option key={category.id} value={category.id}>
                              {category.name}
                            </option>
                          ))}
                        </select>
                      ) : (
                        DASH
                      )}
                    </td>
                    <td>
                      {row.suggestedOccurrenceId ? (
                        <label className="filter-check">
                          <input
                            type="checkbox"
                            checked={settleRow[row.id] ?? false}
                            onChange={(event) =>
                              setSettleRow((current) => ({
                                ...current,
                                [row.id]: event.target.checked,
                              }))
                            }
                          />
                          rozlicz „{row.suggestedOccurrenceName}"
                        </label>
                      ) : (
                        DASH
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="row">
            <button type="button" onClick={commit} disabled={busy}>
              {busy ? 'Zapisywanie…' : 'Zatwierdź wyciąg'}
            </button>
            <button type="button" onClick={() => setBatch(null)} disabled={busy}>
              Odrzuć podgląd
            </button>
          </div>
        </section>
      ) : null}
    </div>
  )
}

/** Sugestia z backendu wchodzi jako wartość domyślna, nie jako decyzja. */
function defaultCategories(detail: ImportBatchDetailResponse): Record<number, string> {

  const chosen: Record<number, string> = {}
  detail.rows.forEach((row) => {
    chosen[row.id] = row.suggestedCategoryId ? String(row.suggestedCategoryId) : ''
  })
  return chosen
}

/**
 * Dopasowanie do terminarza zostaje **odznaczone**. Oznaczenie rachunku jako
 * opłaconego bez świadomego kliknięcia to najgorszy możliwy rodzaj cichego
 * błędu w tej aplikacji.
 */
function defaultSettlements(detail: ImportBatchDetailResponse): Record<number, boolean> {

  const chosen: Record<number, boolean> = {}
  detail.rows.forEach((row) => {
    chosen[row.id] = false
  })
  return chosen
}
