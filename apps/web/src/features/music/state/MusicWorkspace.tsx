// Stan dzielony między ekranami domeny muzycznej: zaznaczenie utworów wędrujące
// z biblioteki do setów i wzbogacania oraz licznik odświeżeń wymuszający refetch
// po imporcie, edycji i zakończonym jobie.
//
// Siedzi w domenie, nie w powłoce: powłoka nie ma prawa wiedzieć, że istnieje
// coś takiego jak „zaznaczone utwory" (reguła z frontend-architecture.md).

import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import type { ReactNode } from 'react'

import SelectionBar from '@/features/music/components/SelectionBar'

export interface MusicWorkspace {
  /** Rośnie po każdej zmianie danych — ekrany trzymają go w zależnościach efektu. */
  refreshKey: number
  refresh: () => void
  selectedIds: ReadonlySet<string>
  setSelectedIds: (ids: ReadonlySet<string>) => void
  clearSelection: () => void
}

export const MusicWorkspaceContext = createContext<MusicWorkspace | null>(null)

export function MusicWorkspaceProvider({ children }: { children: ReactNode }) {

  const [refreshKey, setRefreshKey] = useState(0)
  const [selectedIds, setSelectedIds] = useState<ReadonlySet<string>>(new Set())

  const refresh = useCallback(() => setRefreshKey((key) => key + 1), [])
  const clearSelection = useCallback(() => setSelectedIds(new Set()), [])

  const value = useMemo<MusicWorkspace>(
    () => ({ refreshKey, refresh, selectedIds, setSelectedIds, clearSelection }),
    [refreshKey, refresh, selectedIds, clearSelection],
  )

  return (
    <MusicWorkspaceContext.Provider value={value}>
      {children}
      <SelectionBar selectedIds={selectedIds} onClear={clearSelection} onChanged={refresh} />
    </MusicWorkspaceContext.Provider>
  )
}

export function useMusicWorkspace(): MusicWorkspace {

  const value = useContext(MusicWorkspaceContext)
  if (!value) {
    throw new Error('useMusicWorkspace poza MusicWorkspaceProvider')
  }
  return value
}
