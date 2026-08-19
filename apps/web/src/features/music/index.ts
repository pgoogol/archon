// Jedyny publiczny byt domeny muzycznej. Reszta katalogu jest prywatna —
// nic spoza features/music/ nie ma prawa sięgać głębiej niż ten plik.

import { lazy } from 'react'

import type { FeatureManifest } from '@/shared/featureManifest'
import { MusicWorkspaceProvider } from '@/features/music/state/MusicWorkspace'

export const musicFeature: FeatureManifest = {
  id: 'music',
  title: 'music-view',
  subtitle: 'konsola DJ-a — Sabor Latino',
  basePath: '/music',
  Provider: MusicWorkspaceProvider,
  // każdy ekran przez lazy(): statyczny import wciągnąłby całą domenę
  // do głównego bundla i skasował sens rejestru
  routes: [
    { path: '', Component: lazy(() => import('./routes/LibraryRoute')) },
    { path: 'overview', Component: lazy(() => import('./routes/OverviewRoute')) },
    { path: 'playlists', Component: lazy(() => import('./routes/PlaylistsRoute')) },
    { path: 'sets', Component: lazy(() => import('./routes/SetsRoute')) },
    { path: 'import', Component: lazy(() => import('./routes/ImportRoute')) },
    { path: 'enrich', Component: lazy(() => import('./routes/EnrichRoute')) },
  ],
  nav: [
    { label: 'Biblioteka', to: '' },
    { label: 'Przegląd', to: 'overview' },
    { label: 'Playlisty', to: 'playlists' },
    { label: 'Sety', to: 'sets' },
    { label: 'Import', to: 'import' },
    { label: 'Wzbogacanie', to: 'enrich' },
  ],
}
