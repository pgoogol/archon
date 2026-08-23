import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement } from 'react'

import { ToastProvider } from '@/shared/ui/Toasts'
import {
  FinanceWorkspaceContext,
  type FinanceWorkspace,
} from '@/features/finance/state/FinanceWorkspace'

/**
 * Ekrany finansów biorą słownik walut z kontekstu, a dane z TanStack Query.
 * Test dostaje własny `QueryClient`, bo cache współdzielony między testami
 * pokazywałby dane poprzedniego przypadku zamiast tych, które ustawił bieżący.
 *
 * `retry: false` jest konieczne, nie kosmetyczne: domyślny retry sprawia, że test
 * błędu czeka na kolejne próby i kończy się timeoutem zamiast asercją.
 */
export function renderRoute(ui: ReactElement, overrides: Partial<FinanceWorkspace> = {}) {

  const workspace: FinanceWorkspace = {
    currencies: [
      { code: 'PLN', name: 'złoty polski', minorUnit: 2 },
      { code: 'EUR', name: 'euro', minorUnit: 2 },
      { code: 'JPY', name: 'jen japoński', minorUnit: 0 },
    ],
    minorUnitOf: (currency: string) => (currency === 'JPY' ? 0 : 2),
    ...overrides,
  }
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const result = render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <FinanceWorkspaceContext.Provider value={workspace}>{ui}</FinanceWorkspaceContext.Provider>
      </ToastProvider>
    </QueryClientProvider>,
  )
  return { ...result, workspace, client }
}
