// Przychody i wydatki miesiąc po miesiącu — słupki w parach, z linią zera
// w środku. Inline SVG, bez biblioteki wykresów: reszta repozytorium rysuje
// tak samo, a dokładanie zależności pod dwa wykresy byłoby kosztem bez zysku.
//
// Wysokość słupka liczy się z kwoty w jednostkach podrzędnych. Skala jest
// wspólna dla obu stron, więc pasek wydatków i pasek przychodów da się
// porównać wzrokiem — osobne skale wyglądałyby ładniej i kłamały.

import type { CashflowRow, Granularity } from '@/features/finance/api'
import { periodLabel } from '@/features/finance/format'
import { formatMinor } from '@/shared/format'

interface Props {
  rows: readonly CashflowRow[]
  granularity: Granularity
  currency: string
  minorUnit: number
}

const WIDTH = 720
const HEIGHT = 220
const PADDING = { top: 16, right: 14, bottom: 34, left: 8 }

export default function CashflowBars({ rows, granularity, currency, minorUnit }: Props) {

  if (rows.length === 0) {
    return <p className="muted">Brak operacji w tym zakresie.</p>
  }

  const max = rows.reduce(
    (highest, row) => Math.max(highest, row.incomeMinor, row.expenseMinor),
    0,
  )
  const scale = max || 1
  const plotWidth = WIDTH - PADDING.left - PADDING.right
  const plotHeight = HEIGHT - PADDING.top - PADDING.bottom
  const baseline = PADDING.top + plotHeight
  const slot = plotWidth / rows.length
  const barWidth = Math.max(4, slot / 3)

  return (
    <figure className="chart">
      <svg
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        role="img"
        aria-label={`Przepływy: ${rows.length} okresów, największa kwota ${formatMinor(max, currency, minorUnit)}`}
      >
        <line
          x1={PADDING.left}
          y1={baseline}
          x2={WIDTH - PADDING.right}
          y2={baseline}
          stroke="var(--border)"
        />
        {rows.map((row, index) => {
          const left = PADDING.left + index * slot
          const incomeHeight = (row.incomeMinor / scale) * plotHeight
          const expenseHeight = (row.expenseMinor / scale) * plotHeight
          const label = periodLabel(row.period, granularity)
          return (
            <g key={row.period}>
              <rect
                x={left + slot / 2 - barWidth - 2}
                y={baseline - incomeHeight}
                width={barWidth}
                height={incomeHeight}
                fill="var(--ok)"
              >
                <title>{`${label}: przychody ${formatMinor(row.incomeMinor, currency, minorUnit)}`}</title>
              </rect>
              <rect
                x={left + slot / 2 + 2}
                y={baseline - expenseHeight}
                width={barWidth}
                height={expenseHeight}
                fill="var(--accent)"
              >
                <title>{`${label}: wydatki ${formatMinor(row.expenseMinor, currency, minorUnit)}`}</title>
              </rect>
              <text
                x={left + slot / 2}
                y={baseline + 18}
                textAnchor="middle"
                fill="var(--muted)"
                fontSize="11"
              >
                {label}
              </text>
            </g>
          )
        })}
      </svg>
      <figcaption className="muted">
        Zielone słupki to przychody, pomarańczowe wydatki. Transfery nie są liczone.
      </figcaption>
    </figure>
  )
}
