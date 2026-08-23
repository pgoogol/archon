// Formatery niezależne od domeny — czas, liczby, daty (M3.1).

export const DASH = '—'

/** Czas w formacie m:ss; brak danych → myślnik. */
export function formatDuration(durationMs: number | null | undefined): string {

  if (durationMs === null || durationMs === undefined || durationMs <= 0) return DASH
  const totalSeconds = Math.round(durationMs / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}

/** Dłuższy czas — godziny i minuty, bo sety liczy się w kwadransach. */
export function formatTotalDuration(durationMs: number): string {

  if (durationMs <= 0) return '0 min'
  const totalMinutes = Math.round(durationMs / 60000)
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60
  return hours > 0 ? `${hours} h ${minutes} min` : `${minutes} min`
}

/** Ułamek 0..1 na skalę 0-100. */
export function formatScore(value: number | null | undefined): string {
  return value === null || value === undefined ? DASH : String(Math.round(value * 100))
}

export function formatDateTime(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleString('pl') : DASH
}

/**
 * Kwota w jednostkach podrzędnych na tekst — bez ani jednego dzielenia
 * zmiennoprzecinkowego. `amountMinor / 100` wygląda niewinnie, ale przy
 * kwotach rzędu miliardów groszy zaczyna gubić ostatnią cyfrę, a to jest
 * dokładnie ta cyfra, o którą ludzie się kłócą z bankiem.
 *
 * Liczba miejsc po przecinku przychodzi z `/currencies` — PLN ma 2, JPY ma 0,
 * i żadna stała `× 100` w kodzie tego nie zastąpi.
 */
export function formatMinor(
  amountMinor: number | null | undefined,
  currency: string,
  minorUnit: number,
): string {

  if (amountMinor === null || amountMinor === undefined) return DASH
  const sign = amountMinor < 0 ? '-' : ''
  const absolute = Math.abs(amountMinor)
  const factor = 10 ** minorUnit
  const whole = Math.floor(absolute / factor)
  const fraction = absolute % factor
  const groupedWhole = new Intl.NumberFormat('pl-PL').format(whole)
  const decimals = minorUnit > 0 ? `,${String(fraction).padStart(minorUnit, '0')}` : ''
  return `${sign}${groupedWhole}${decimals} ${currency}`
}

/** Sam znak kwoty — do klasy CSS, nie do treści. */
export function amountTone(amountMinor: number): 'positive' | 'negative' | 'zero' {

  if (amountMinor > 0) return 'positive'
  if (amountMinor < 0) return 'negative'
  return 'zero'
}
