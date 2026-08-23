import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { vi } from 'vitest'

import { ToastProvider } from '@/shared/ui/Toasts'
import {
  FinanceWorkspaceContext,
  type FinanceWorkspace,
} from '@/features/finance/state/FinanceWorkspace'

/**
 * Ekrany finansów biorą słownik walut i licznik odświeżeń z kontekstu, nie
 * z propsów. Test podstawia własny warsztat, żeby dało się sprawdzić, co ekran
 * z nim robi — i żeby nie musiał czekać na pobranie słownika.
 */
export function renderRoute(ui: ReactElement, overrides: Partial<FinanceWorkspace> = {}) {

  const workspace: FinanceWorkspace = {
    refreshKey: 0,
    refresh: vi.fn(),
    currencies: [
      { code: 'PLN', name: 'złoty polski', minorUnit: 2 },
      { code: 'EUR', name: 'euro', minorUnit: 2 },
      { code: 'JPY', name: 'jen japoński', minorUnit: 0 },
    ],
    minorUnitOf: (currency: string) => (currency === 'JPY' ? 0 : 2),
    ...overrides,
  }
  const result = render(
    <ToastProvider>
      <FinanceWorkspaceContext.Provider value={workspace}>{ui}</FinanceWorkspaceContext.Provider>
    </ToastProvider>,
  )
  return { ...result, workspace }
}
