// Raporty. Zakres i podział osi są wspólne dla wszystkich zestawień i siedzą
// w adresie — przełączenie zakładki nie gubi tego, co użytkownik ustawił.
//
// Wszystkie kwoty są w walucie bazowej, po kursie zapisanym na transakcji.
// Żaden z tych raportów nie przelicza historii kursem bieżącym.

import { useEffect, useMemo, useState } from 'react'

import {
  api,
  type ByCategoryReportResponse,
  type CashflowReportResponse,
  type ComparisonReportResponse,
  type CurrencyExposureReportResponse,
  type Granularity,
  type TopSpendReportResponse,
} from '@/features/finance/api'
import CashflowBars from '@/features/finance/viz/CashflowBars'
import CategoryBars from '@/features/finance/viz/CategoryBars'
import { GRANULARITY_LABELS, today, yearAgo } from '@/features/finance/format'
import { useFinanceWorkspace } from '@/features/finance/state/FinanceWorkspace'
import { useHashRoute } from '@/shared/hooks/useHashRoute'
import { useToast } from '@/shared/ui/Toasts'
import { DASH, formatMinor } from '@/shared/format'

const GRANULARITIES: Granularity[] = ['DAY', 'MONTH', 'YEAR']

export default function RaportyRoute() {

  const { refreshKey, minorUnitOf } = useFinanceWorkspace()
  const { params, setParams } = useHashRoute()
  const { reportError } = useToast()

  const range = useMemo(
    () => ({
      from: params.get('from') ?? yearAgo(),
      to: params.get('to') ?? today(),
      granularity: (params.get('granularity') ?? 'MONTH') as Granularity,
    }),
    [params],
  )

  const [cashflow, setCashflow] = useState<CashflowReportResponse | null>(null)
  const [byCategory, setByCategory] = useState<ByCategoryReportResponse | null>(null)
  const [comparison, setComparison] = useState<ComparisonReportResponse | null>(null)
  const [topSpend, setTopSpend] = useState<TopSpendReportResponse | null>(null)
  const [exposure, setExposure] = useState<CurrencyExposureReportResponse | null>(null)

  useEffect(() => {
    let current = true
    Promise.all([
      api.reportCashflow(range),
      api.reportByCategory(range),
      api.reportComparison(range),
      api.reportTopSpend({ ...range, limit: 5 }),
      api.reportCurrencyExposure(),
    ])
      .then(([loadedCashflow, loadedCategories, loadedComparison, loadedTop, loadedExposure]) => {
        if (!current) return
        setCashflow(loadedCashflow)
        setByCategory(loadedCategories)
        setComparison(loadedComparison)
        setTopSpend(loadedTop)
        setExposure(loadedExposure)
      })
      .catch((error) => reportError(error, 'Nie udało się pobrać raportów'))
    return () => {
      current = false
    }
  }, [range, refreshKey, reportError])

  const base = cashflow?.baseCurrency ?? 'PLN'
  const baseUnit = minorUnitOf(base)

  return (
    <>
      <section className="panel">
        <h2>Zakres</h2>
        <div className="filters-basic">
          <label className="filter-group">
            <span>Od</span>
            <input
              type="date"
              value={range.from}
              onChange={(event) => setParams({ from: event.target.value })}
            />
          </label>
          <label className="filter-group">
            <span>Do</span>
            <input
              type="date"
              value={range.to}
              onChange={(event) => setParams({ to: event.target.value })}
            />
          </label>
          <label className="filter-group">
            <span>Podział</span>
            <select
              value={range.granularity}
              onChange={(event) => setParams({ granularity: event.target.value })}
            >
              {GRANULARITIES.map((option) => (
                <option key={option} value={option}>
                  {GRANULARITY_LABELS[option]}
                </option>
              ))}
            </select>
          </label>
        </div>
      </section>

      <section className="panel">
        <h2>Przychody i wydatki</h2>
        {cashflow ? (
          <CashflowBars
            rows={cashflow.rows}
            granularity={range.granularity}
            currency={base}
            minorUnit={baseUnit}
          />
        ) : (
          <p className="muted">Wczytywanie…</p>
        )}
      </section>

      <div className="panels">
        <section className="panel">
          <h2>Wydatki wg kategorii</h2>
          {byCategory ? (
            <CategoryBars rows={byCategory.rows} currency={base} minorUnit={baseUnit} />
          ) : (
            <p className="muted">Wczytywanie…</p>
          )}
        </section>

        <section className="panel">
          <h2>Okres wobec poprzedniego</h2>
          {comparison ? (
            <>
              <p className="muted">
                Poprzedni okres: {comparison.previousFrom} — {comparison.previousTo}
              </p>
              <table>
                <thead>
                  <tr>
                    <th scope="col">Kategoria</th>
                    <th scope="col">Teraz</th>
                    <th scope="col">Poprzednio</th>
                    <th scope="col">Zmiana</th>
                  </tr>
                </thead>
                <tbody>
                  {comparison.rows.length === 0 ? (
                    <tr className="empty-row">
                      <td colSpan={4}>Brak danych do porównania.</td>
                    </tr>
                  ) : (
                    comparison.rows.map((row) => (
                      <tr key={row.categoryId}>
                        <td>{row.categoryName}</td>
                        <td>{formatMinor(row.currentMinor, base, baseUnit)}</td>
                        <td>{formatMinor(row.previousMinor, base, baseUnit)}</td>
                        <td>{row.changePercent ? `${row.changePercent} %` : DASH}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
              <p className="muted">
                Pusta zmiana znaczy, że w poprzednim okresie nie było czego porównywać —
                wzrost z zera nie ma procentu.
              </p>
            </>
          ) : (
            <p className="muted">Wczytywanie…</p>
          )}
        </section>

        <section className="panel">
          <h2>Największe wydatki</h2>
          {topSpend ? (
            <ul className="finance-list">
              {topSpend.transactions.map((row) => (
                <li key={row.transactionId}>
                  <span className="finance-list-main">{row.description ?? row.accountName}</span>
                  <span className="muted">
                    {row.bookedOn} · {row.categoryName ?? DASH}
                  </span>
                  <span className="finance-list-value">
                    {formatMinor(row.amountMinor, base, baseUnit)}
                  </span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="muted">Wczytywanie…</p>
          )}
        </section>

        <section className="panel">
          <h2>Waluty</h2>
          {exposure ? (
            <>
              <table>
                <thead>
                  <tr>
                    <th scope="col">Waluta</th>
                    <th scope="col">Saldo</th>
                    <th scope="col">Wartość w {exposure.baseCurrency}</th>
                  </tr>
                </thead>
                <tbody>
                  {exposure.rows.map((row) => (
                    <tr key={row.currency}>
                      <td>{row.currency}</td>
                      <td>{formatMinor(row.balanceMinor, row.currency, row.minorUnit)}</td>
                      <td>
                        {row.baseValueMinor === null || row.baseValueMinor === undefined
                          ? DASH
                          : formatMinor(row.baseValueMinor, exposure.baseCurrency, baseUnit)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <p className="muted">
                Ten jeden raport używa kursu bieżącego — pokazuje stan majątku na dziś, a nie
                historię operacji.
              </p>
            </>
          ) : (
            <p className="muted">Wczytywanie…</p>
          )}
        </section>
      </div>
    </>
  )
}
