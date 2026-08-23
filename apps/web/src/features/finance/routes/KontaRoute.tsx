// Konta i ich salda. Usunięcie konta to archiwizacja — historia transakcji
// musi zostać, więc konto znika z list wyboru, a nie z bazy.

import { useCallback, useEffect, useState } from 'react'

import {
  api,
  type AccountResponse,
  type AccountBalanceResponse,
  type AccountType,
} from '@/features/finance/api'
import { ACCOUNT_TYPE_LABELS } from '@/features/finance/format'
import { useFinanceWorkspace } from '@/features/finance/state/FinanceWorkspace'
import { useToast } from '@/shared/ui/Toasts'
import { DASH, formatMinor } from '@/shared/format'

const ACCOUNT_TYPES: AccountType[] = ['BANK', 'CASH', 'CARD']

export default function KontaRoute() {

  const { refreshKey, refresh, currencies, minorUnitOf } = useFinanceWorkspace()
  const { notify, reportError } = useToast()

  const [accounts, setAccounts] = useState<AccountResponse[]>([])
  const [includeArchived, setIncludeArchived] = useState(false)
  const [balances, setBalances] = useState<Record<number, AccountBalanceResponse>>({})

  const [name, setName] = useState('')
  const [type, setType] = useState<AccountType>('BANK')
  const [currency, setCurrency] = useState('PLN')
  const [openingBalance, setOpeningBalance] = useState('0')
  const [openingOn, setOpeningOn] = useState(() => new Date().toISOString().slice(0, 10))
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    let current = true
    api
      .listAccounts(includeArchived)
      .then((loaded) => {
        if (current) setAccounts(loaded)
      })
      .catch((error) => reportError(error, 'Nie udało się pobrać kont'))
    return () => {
      current = false
    }
  }, [refreshKey, includeArchived, reportError])

  const loadBalance = useCallback(
    (accountId: number) => {
      api
        .accountBalance(accountId)
        .then((balance) => setBalances((current) => ({ ...current, [accountId]: balance })))
        .catch((error) => reportError(error, 'Nie udało się policzyć salda'))
    },
    [reportError],
  )

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    setSaving(true)
    try {
      // kwota wchodzi w jednostkach podrzędnych — pole przyjmuje je wprost,
      // żeby nie zgadywać, ile miejsc po przecinku ma waluta
      await api.createAccount({
        name,
        type,
        currency,
        openingBalanceMinor: Number(openingBalance),
        openingBalanceOn: openingOn,
      })
      notify('Konto zapisane')
      setName('')
      refresh()
    } catch (error) {
      reportError(error, 'Nie udało się zapisać konta')
    } finally {
      setSaving(false)
    }
  }

  const archive = async (accountId: number) => {
    try {
      await api.archiveAccount(accountId)
      notify('Konto zarchiwizowane')
      refresh()
    } catch (error) {
      reportError(error, 'Nie udało się zarchiwizować konta')
    }
  }

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
                      <button type="button" onClick={() => loadBalance(account.id)}>
                        Policz saldo
                      </button>
                      {account.archived ? null : (
                        <button type="button" onClick={() => archive(account.id)}>
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
        <form onSubmit={submit}>
          <label className="field">
            <span>Nazwa</span>
            <input value={name} onChange={(event) => setName(event.target.value)} required />
          </label>
          <label className="field">
            <span>Rodzaj</span>
            <select value={type} onChange={(event) => setType(event.target.value as AccountType)}>
              {ACCOUNT_TYPES.map((option) => (
                <option key={option} value={option}>
                  {ACCOUNT_TYPE_LABELS[option]}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Waluta</span>
            <select value={currency} onChange={(event) => setCurrency(event.target.value)}>
              {currencies.map((entry) => (
                <option key={entry.code} value={entry.code}>
                  {entry.code} — {entry.name}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Saldo otwarcia (w jednostkach podrzędnych, {minorUnitOf(currency)} miejsc)</span>
            <input
              type="number"
              value={openingBalance}
              onChange={(event) => setOpeningBalance(event.target.value)}
            />
          </label>
          <label className="field">
            <span>Data salda otwarcia</span>
            <input
              type="date"
              value={openingOn}
              onChange={(event) => setOpeningOn(event.target.value)}
              required
            />
          </label>
          <button type="submit" disabled={saving}>
            {saving ? 'Zapisywanie…' : 'Zapisz konto'}
          </button>
        </form>
      </section>
    </div>
  )
}
