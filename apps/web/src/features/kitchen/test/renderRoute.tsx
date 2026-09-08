import { render } from '@testing-library/react'
import type { ReactElement } from 'react'

import {
  KitchenWorkspaceContext,
  type KitchenWorkspace,
} from '@/features/kitchen/state/KitchenWorkspace'
import { EMPTY_DICTIONARIES } from '@/features/kitchen/test/server'

/**
 * Ekrany kuchni biorą słowniki z kontekstu. Test podaje go wprost, żeby stan
 * wczytywania i błędu dało się sprawdzić bez czekania na ponowienia zapytania —
 * drogę „API → zapytanie → kontekst" sprawdza osobny przypadek na prawdziwym
 * dostawcy.
 */
export function renderRoute(ui: ReactElement, overrides: Partial<KitchenWorkspace> = {}) {

  const workspace: KitchenWorkspace = {
    dictionaries: EMPTY_DICTIONARIES,
    loading: false,
    error: null,
    ...overrides,
  }
  const result = render(
    <KitchenWorkspaceContext.Provider value={workspace}>{ui}</KitchenWorkspaceContext.Provider>,
  )
  return { ...result, workspace }
}
