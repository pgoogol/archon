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
