// Terminarz rachunków cyklicznych: reguły i wynikające z nich terminy.
//
// Status „po terminie" nie jest przechowywany — backend wylicza go przy
// odczycie z porównania terminu z dzisiejszą datą, więc lista nigdy nie
// rozjedzie się z kalendarzem.

import { useEffect, useState } from 'react'

import {
  api,
  type AccountResponse,
  type CategoryResponse,
  type OccurrenceResponse,
  type RecurringFrequency,
  type RecurringRuleResponse,
} from '@/features/finance/api'
import { FREQUENCY_LABELS, OCCURRENCE_LABELS, today } from '@/features/finance/format'
import { useFinanceWorkspace } from '@/features/finance/state/FinanceWorkspace'
import { useToast } from '@/shared/ui/Toasts'
import { formatMinor } from '@/shared/format'

const FREQUENCIES: RecurringFrequency[] = ['MONTHLY', 'QUARTERLY', 'YEARLY']

export default function TerminarzRoute() {

  const { refreshKey, refresh, minorUnitOf } = useFinanceWorkspace()
  const { notify, reportError } = useToast()

  const [rules, setRules] = useState<RecurringRuleResponse[]>([])
  const [occurrences, setOccurrences] = useState<OccurrenceResponse[]>([])
  const [accounts, setAccounts] = useState<AccountResponse[]>([])
  const [categories, setCategories] = useState<CategoryResponse[]>([])
  const [busy, setBusy] = useState(false)

  const [name, setName] = useState('')
  const [accountId, setAccountId] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [amountMinor, setAmountMinor] = useState('')
  const [frequency, setFrequency] = useState<RecurringFrequency>('MONTHLY')
  const [dayOfMonth, setDayOfMonth] = useState('10')
  const [startsOn, setStartsOn] = useState(today)
  const [matchPattern, setMatchPattern] = useState('')

  useEffect(() => {
    let current = true
    Promise.all([
      api.listRecurringRules(false),
      api.listOccurrences({}),
      api.listAccounts(false),
      api.listCategories('EXPENSE'),
    ])
      .then(([loadedRules, loadedOccurrences, loadedAccounts, loadedCategories]) => {
        if (!current) return
        setRules(loadedRules)
        setOccurrences(loadedOccurrences)
        setAccounts(loadedAccounts)
        setCategories(loadedCategories)
      })
      .catch((error) => reportError(error, 'Nie udało się pobrać terminarza'))
    return () => {
      current = false
    }
  }, [refreshKey, reportError])

  const createRule = async (event: React.FormEvent) => {
    event.preventDefault()
    setBusy(true)
    try {
      await api.createRecurringRule({
        name,
        accountId: Number(accountId),
        categoryId: Number(categoryId),
        type: 'EXPENSE',
        amountMinor: Number(amountMinor),
        frequency,
        dayOfMonth: Number(dayOfMonth),
        startsOn,
        matchPattern: matchPattern || null,
      })
      notify('Reguła zapisana wraz z terminarzem')
      setName('')
      setAmountMinor('')
      refresh()
    } catch (error) {
      reportError(error, 'Nie udało się zapisać reguły')
    } finally {
      setBusy(false)
    }
  }

  const pay = async (occurrence: OccurrenceResponse) => {
    try {
      // kwota faktyczna bywa inna niż oczekiwana; tu domyślnie bierzemy
      // oczekiwaną, a poprawia się ją w transakcjach
      await api.payOccurrence(occurrence.id, {
        paidOn: today(),
        paidAmountMinor: occurrence.expectedAmountMinor,
      })
      notify('Rachunek oznaczony jako zapłacony')
      refresh()
    } catch (error) {
      reportError(error, 'Nie udało się rozliczyć rachunku')
    }
  }

  const skip = async (occurrenceId: number) => {
    try {
      await api.skipOccurrence(occurrenceId)
      notify('Pozycja pominięta')
      refresh()
    } catch (error) {
      reportError(error, 'Nie udało się pominąć pozycji')
    }
  }

  const generate = async () => {
    try {
      const result = await api.generateOccurrences()
      notify(`Dołożono ${result.createdCount} pozycji, horyzont do ${result.horizonTo}`)
      refresh()
    } catch (error) {
      reportError(error, 'Nie udało się uzupełnić terminarza')
    }
  }

  const deactivate = async (ruleId: number) => {
    try {
      await api.deactivateRecurringRule(ruleId)
      notify('Reguła wyłączona; historia została')
      refresh()
    } catch (error) {
      reportError(error, 'Nie udało się wyłączyć reguły')
    }
  }

  return (
    <div className="panels">
      <section className="panel">
        <h2>Terminarz</h2>
        <div className="row">
          <button type="button" onClick={generate}>
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
                        <button type="button" onClick={() => pay(occurrence)}>
                          Zapłacone
                        </button>
                        <button type="button" onClick={() => skip(occurrence.id)}>
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
                <button type="button" onClick={() => deactivate(rule.id)}>
                  Wyłącz
                </button>
              ) : null}
            </li>
          ))}
        </ul>

        <h3>Nowy rachunek cykliczny</h3>
        <form onSubmit={createRule}>
          <label className="field">
            <span>Nazwa</span>
            <input value={name} onChange={(event) => setName(event.target.value)} required />
          </label>
          <label className="field">
            <span>Konto</span>
            <select
              value={accountId}
              onChange={(event) => setAccountId(event.target.value)}
              required
            >
              <option value="">wybierz…</option>
              {accounts.map((account) => (
                <option key={account.id} value={account.id}>
                  {account.name}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Kategoria</span>
            <select
              value={categoryId}
              onChange={(event) => setCategoryId(event.target.value)}
              required
            >
              <option value="">wybierz…</option>
              {categories.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Kwota oczekiwana (w jednostkach podrzędnych)</span>
            <input
              type="number"
              min="1"
              value={amountMinor}
              onChange={(event) => setAmountMinor(event.target.value)}
              required
            />
          </label>
          <label className="field">
            <span>Częstotliwość</span>
            <select
              value={frequency}
              onChange={(event) => setFrequency(event.target.value as RecurringFrequency)}
            >
              {FREQUENCIES.map((option) => (
                <option key={option} value={option}>
                  {FREQUENCY_LABELS[option]}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Dzień miesiąca (31 w lutym wypada ostatniego dnia)</span>
            <input
              type="number"
              min="1"
              max="31"
              value={dayOfMonth}
              onChange={(event) => setDayOfMonth(event.target.value)}
              required
            />
          </label>
          <label className="field">
            <span>Obowiązuje od</span>
            <input
              type="date"
              value={startsOn}
              onChange={(event) => setStartsOn(event.target.value)}
              required
            />
          </label>
          <label className="field">
            <span>Fragment opisu do rozpoznania przy imporcie</span>
            <input
              value={matchPattern}
              onChange={(event) => setMatchPattern(event.target.value)}
            />
          </label>
          <button type="submit" disabled={busy}>
            {busy ? 'Zapisywanie…' : 'Zapisz rachunek'}
          </button>
        </form>
      </section>
    </div>
  )
}
