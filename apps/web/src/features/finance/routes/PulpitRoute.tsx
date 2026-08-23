// Pulpit: salda kont, bilans bieżącego miesiąca i to, co zaraz trzeba zapłacić.
// Jeden ekran, jedno zapytanie — backend składa go po swojej stronie, żeby
// front nie robił sześciu wywołań i nie sklejał ich w przeglądarce.

import { useQuery } from '@tanstack/react-query'

import { api } from '@/features/finance/api'
import UpcomingList from '@/features/finance/components/UpcomingList'
import { useFinanceWorkspace } from '@/features/finance/state/FinanceWorkspace'
import { financeKeys } from '@/features/finance/state/queryKeys'
import { useQueryErrorToast } from '@/features/finance/state/useQueryErrorToast'
import { DASH, formatMinor } from '@/shared/format'

export default function PulpitRoute() {

  const { minorUnitOf } = useFinanceWorkspace()
  const { data: dashboard, error } = useQuery({
    queryKey: financeKeys.dashboard(),
    queryFn: () => api.dashboard(),
  })
  useQueryErrorToast(error, 'Nie udało się pobrać pulpitu')

  if (!dashboard) {
    return <p className="muted">Wczytywanie pulpitu…</p>
  }

  const base = dashboard.baseCurrency
  const baseUnit = minorUnitOf(base)

  return (
    <div className="panels">
      <section className="panel">
        <h2>Salda kont</h2>
        {dashboard.accountBalances.length === 0 ? (
          <p className="muted">Nie ma jeszcze żadnego konta.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th scope="col">Konto</th>
                <th scope="col">Saldo</th>
                <th scope="col">Wartość w {base}</th>
              </tr>
            </thead>
            <tbody>
              {dashboard.accountBalances.map((account) => (
                <tr key={account.accountId}>
                  <td>{account.accountName}</td>
                  <td>
                    {formatMinor(account.balanceMinor, account.currency, account.minorUnit)}
                  </td>
                  <td>
                    {account.baseValueMinor === null || account.baseValueMinor === undefined
                      ? DASH
                      : formatMinor(account.baseValueMinor, base, baseUnit)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <p className="muted">
          Pusta wartość w walucie bazowej znaczy, że dla tej waluty nie ma jeszcze kursu.
        </p>
      </section>

      <section className="panel">
        <h2>Bilans miesiąca</h2>
        <dl className="track-facts">
          <dt>Przychody</dt>
          <dd>{formatMinor(dashboard.monthIncomeMinor, base, baseUnit)}</dd>
          <dt>Wydatki</dt>
          <dd>{formatMinor(dashboard.monthExpenseMinor, base, baseUnit)}</dd>
          <dt>Bilans</dt>
          <dd>{formatMinor(dashboard.monthNetMinor, base, baseUnit)}</dd>
        </dl>
        <p className="muted">Transfery między własnymi kontami nie są tu liczone.</p>
      </section>

      <section className="panel">
        <h2>Po terminie</h2>
        <UpcomingList items={dashboard.overdue} emptyText="Nic nie jest przeterminowane." />
      </section>

      <section className="panel">
        <h2>Najbliższe płatności</h2>
        <UpcomingList items={dashboard.upcoming} emptyText="Nic nie wypada w najbliższych dniach." />
      </section>
    </div>
  )
}
