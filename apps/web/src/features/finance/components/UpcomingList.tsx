// Lista zobowiązań z terminarza — ta sama na pulpicie i w raporcie
// nadchodzących płatności. Pozycja po terminie jest wyróżniona, bo to jedyna
// rzecz na tym ekranie, na którą trzeba zareagować dzisiaj.

import type { UpcomingItem } from '@/features/finance/api'
import { useFinanceWorkspace } from '@/features/finance/state/FinanceWorkspace'
import { formatMinor } from '@/shared/format'

interface Props {
  items: readonly UpcomingItem[]
  emptyText: string
}

export default function UpcomingList({ items, emptyText }: Props) {

  const { minorUnitOf } = useFinanceWorkspace()

  if (items.length === 0) {
    return <p className="muted">{emptyText}</p>
  }

  return (
    <ul className="finance-list">
      {items.map((item) => (
        <li key={item.occurrenceId}>
          <span className="finance-list-main">
            {item.ruleName}
            {item.overdue ? <span className="badge badge-warn">po terminie</span> : null}
          </span>
          <span className="muted">
            {item.dueDate} · {item.accountName}
          </span>
          <span className="finance-list-value">
            {formatMinor(item.expectedAmountMinor, item.currency, minorUnitOf(item.currency))}
          </span>
        </li>
      ))}
    </ul>
  )
}
