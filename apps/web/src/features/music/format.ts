// Etykiety PL i reguły czytania danych muzycznych (M3.1).

import { DASH } from '@/shared/format'

export const SLOT_ORDER = ['WARMUP', 'MIDDLE', 'PEAK', 'CLOSING', 'BREAK'] as const

export type SlotKey = (typeof SLOT_ORDER)[number]

export const SLOT_LABELS: Record<SlotKey, string> = {
  WARMUP: 'rozgrzewka',
  MIDDLE: 'środek',
  PEAK: 'szczyt',
  CLOSING: 'zamknięcie',
  BREAK: 'przerwa',
}

export const ENERGY_LABELS: Record<string, string> = {
  low: 'niska',
  medium: 'średnia',
  high: 'wysoka',
}

export const TEMPO_LABELS: Record<string, string> = {
  SLOW: 'wolne',
  MEDIUM: 'średnie',
  FAST: 'szybkie',
  VERY_FAST: 'bardzo szybkie',
}

export const SOURCE_LABELS: Record<string, string> = {
  FILE: 'plik CSV',
  PLAYLIST: 'własna playlista',
  FOREIGN_PLAYLIST: 'cudza playlista',
}

export function slotLabel(slot: string | null | undefined): string {
  if (!slot) return 'brak danych'
  return SLOT_LABELS[slot as SlotKey] ?? slot
}

export function energyLabel(energy: string | null | undefined): string {
  if (!energy) return DASH
  return ENERGY_LABELS[energy.toLowerCase()] ?? energy
}

export function tempoLabel(tempoClass: string | null | undefined): string {
  if (!tempoClass) return DASH
  return TEMPO_LABELS[tempoClass] ?? tempoClass
}

export function spotifyTrackUrl(spotifyId: string): string {
  return `https://open.spotify.com/track/${encodeURIComponent(spotifyId)}`
}

/** Utwór bez kompletu pól D5 — front oznacza go jako „do wzbogacenia". */
export function isEnriched(track: { genreFamily: string | null; bpm: number | null }): boolean {
  return track.genreFamily !== null && track.bpm !== null
}
