// Jedyny publiczny byt domeny kuchennej. Reszta katalogu jest prywatna —
// nic spoza features/kitchen/ nie ma prawa sięgać głębiej niż ten plik.

import { lazy } from 'react'

import type { FeatureManifest } from '@/shared/featureManifest'
import { KitchenWorkspaceProvider } from '@/features/kitchen/state/KitchenWorkspace'

export const kitchenFeature: FeatureManifest = {
  id: 'kitchen',
  title: 'kuchnia',
  subtitle: 'przepisy z linków, zdjęć i czatów, rozbite na składniki i kroki',
  basePath: '/kitchen',
  Provider: KitchenWorkspaceProvider,
  // każdy ekran przez lazy(): statyczny import wciągnąłby całą domenę
  // do głównego bundla i skasował sens rejestru
  routes: [{ path: '', Component: lazy(() => import('./routes/PrzepisyRoute')) }],
  nav: [{ label: 'Przepisy', to: '' }],
}
