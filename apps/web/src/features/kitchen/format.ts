// Formatowanie wartości przepisu do wyświetlenia. Ilość jest zakresem, a nie
// liczbą: „2", „2–3" albo sam opis („szczypta"), więc składanie jej w każdym
// komponencie z osobna kończyłoby się czterema wariantami tego samego zdania.

import type { IngredientLineResponse } from '@/features/kitchen/api'

/** Ułamki zapisujemy tak, jak w przepisie: 0.5 to „½", nie „0,5”. */
const FRACTIONS: Record<string, string> = {
  '0.25': '¼',
  '0.5': '½',
  '0.75': '¾',
  '0.33': '⅓',
  '0.67': '⅔',
}

export function formatAmount(value: number | undefined | null): string {

  if (value === undefined || value === null) {
    return ''
  }
  const whole = Math.floor(value)
  const rest = Number((value - whole).toFixed(2))
  const fraction = FRACTIONS[String(rest)]
  if (!fraction) {
    return String(Number(value.toFixed(3)))
  }
  return whole === 0 ? fraction : `${whole} ${fraction}`
}

/**
 * Ilość składnika w jednym kawałku: liczba albo zakres, jednostka, a na końcu
 * opis dla tego, czego nie da się zważyć („do smaku”).
 */
export function formatQuantity(line: IngredientLineResponse): string {

  const parts: string[] = []
  const min = formatAmount(line.quantityMin)
  const max = formatAmount(line.quantityMax)
  if (min && max && min !== max) {
    parts.push(`${min}–${max}`)
  } else if (min) {
    parts.push(min)
  }
  if (line.unitName && parts.length > 0) {
    parts.push(line.unitName)
  }
  if (line.quantityText) {
    parts.push(line.quantityText)
  }
  return parts.join(' ')
}

/** Czas w formie, w jakiej mówi się o gotowaniu: „1 h 20 min”, nie „80 min”. */
export function formatMinutes(minutes: number | undefined | null): string {

  if (minutes === undefined || minutes === null) {
    return ''
  }
  if (minutes < 60) {
    return `${minutes} min`
  }
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest === 0 ? `${hours} h` : `${hours} h ${rest} min`
}

/** Grupy zachowują kolejność z przepisu — „na spód” ma iść przed „na masę”. */
export function groupBy<T extends { group?: string | null }>(lines: T[]): [string, T[]][] {

  const groups = new Map<string, T[]>()
  lines.forEach((line) => {
    const key = line.group ?? ''
    const bucket = groups.get(key)
    if (bucket) {
      bucket.push(line)
    } else {
      groups.set(key, [line])
    }
  })
  return [...groups.entries()]
}
