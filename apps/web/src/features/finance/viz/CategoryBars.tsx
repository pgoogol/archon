// Udział kategorii — poziome paski uszeregowane malejąco. Poziomo, bo nazwy
// kategorii są długie i pionowa oś tekstowa albo je ucina, albo obraca
// o dziewięćdziesiąt stopni.

import type { ByCategoryRow } from '@/features/finance/api'
import { formatMinor } from '@/shared/format'

interface Props {
  rows: readonly ByCategoryRow[]
  currency: string
  minorUnit: number
  /** Ile pozycji pokazać; reszta i tak jest w tabeli pod spodem. */
  limit?: number
}

export default function CategoryBars({ rows, currency, minorUnit, limit = 10 }: Props) {

  if (rows.length === 0) {
    return <p className="muted">Brak wydatków w tym zakresie.</p>
  }

  const ranked = [...rows].sort((first, second) => second.amountMinor - first.amountMinor)
  const shown = ranked.slice(0, limit)
  const max = shown[0].amountMinor || 1

  return (
    <ul className="finance-bars">
      {shown.map((row) => {
        const share = Math.round((row.amountMinor / max) * 100)
        return (
          <li key={`${row.period}-${row.categoryId}`}>
            <span className="finance-bar-label">{row.categoryName}</span>
            <span className="finance-bar-track">
              <span className="finance-bar-fill" style={{ width: `${share}%` }} />
            </span>
            <span className="finance-bar-value">
              {formatMinor(row.amountMinor, currency, minorUnit)}
            </span>
          </li>
        )
      })}
    </ul>
  )
}
