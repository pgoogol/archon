// Jedyny publiczny byt domeny finansowej. Reszta katalogu jest prywatna —
// nic spoza features/finance/ nie ma prawa sięgać głębiej niż ten plik.

import { lazy } from 'react'

import type { FeatureManifest } from '@/shared/featureManifest'
import { FinanceWorkspaceProvider } from '@/features/finance/state/FinanceWorkspace'

export const financeFeature: FeatureManifest = {
  id: 'finance',
  title: 'finanse',
  subtitle: 'konta, wyciągi, rachunki cykliczne i raporty',
  basePath: '/finance',
  Provider: FinanceWorkspaceProvider,
  // każdy ekran przez lazy(): statyczny import wciągnąłby całą domenę
  // do głównego bundla i skasował sens rejestru
  routes: [
    { path: '', Component: lazy(() => import('./routes/PulpitRoute')) },
    { path: 'konta', Component: lazy(() => import('./routes/KontaRoute')) },
    { path: 'transakcje', Component: lazy(() => import('./routes/TransakcjeRoute')) },
    { path: 'import', Component: lazy(() => import('./routes/ImportRoute')) },
    { path: 'terminarz', Component: lazy(() => import('./routes/TerminarzRoute')) },
    { path: 'raporty', Component: lazy(() => import('./routes/RaportyRoute')) },
  ],
  nav: [
    { label: 'Pulpit', to: '' },
    { label: 'Konta', to: 'konta' },
    { label: 'Transakcje', to: 'transakcje' },
    { label: 'Import', to: 'import' },
    { label: 'Terminarz', to: 'terminarz' },
    { label: 'Raporty', to: 'raporty' },
  ],
}
