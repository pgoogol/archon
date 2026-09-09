import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement } from 'react'

import {
  KitchenWorkspaceContext,
  type KitchenWorkspace,
} from '@/features/kitchen/state/KitchenWorkspace'
import { DICTIONARIES } from '@/features/kitchen/test/server'

/**
 * Ekrany kuchni biorą słowniki z kontekstu, a dane z TanStack Query. Test
 * dostaje własny `QueryClient`, bo cache współdzielony między testami pokazywałby
 * dane poprzedniego przypadku.
 *
 * `retry: false` jest konieczne, nie kosmetyczne: domyślne ponowienie sprawia,
 * że test błędu czeka na kolejne próby i kończy się timeoutem zamiast asercją.
 */
export function renderRoute(ui: ReactElement, overrides: Partial<KitchenWorkspace> = {}) {

  const workspace: KitchenWorkspace = {
    dictionaries: DICTIONARIES,
    loading: false,
    error: null,
    ...overrides,
  }
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const result = render(
    <QueryClientProvider client={client}>
      <KitchenWorkspaceContext.Provider value={workspace}>{ui}</KitchenWorkspaceContext.Provider>
    </QueryClientProvider>,
  )
  return { ...result, workspace, client }
}
