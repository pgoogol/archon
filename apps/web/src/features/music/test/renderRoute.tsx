import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { vi } from 'vitest'

import { ToastProvider } from '@/shared/ui/Toasts'
import {
  MusicWorkspaceContext,
  type MusicWorkspace,
} from '@/features/music/state/MusicWorkspace'

/**
 * Ekrany domeny biorą zaznaczenie i licznik odświeżeń z kontekstu, nie z propsów.
 * Test podstawia własny stan warsztatu, żeby dało się sprawdzić, co ekran z nim robi.
 */
export function renderRoute(ui: ReactElement, overrides: Partial<MusicWorkspace> = {}) {

  const workspace: MusicWorkspace = {
    refreshKey: 0,
    refresh: vi.fn(),
    selectedIds: new Set<string>(),
    setSelectedIds: vi.fn(),
    clearSelection: vi.fn(),
    ...overrides,
  }
  const result = render(
    <ToastProvider>
      <MusicWorkspaceContext.Provider value={workspace}>{ui}</MusicWorkspaceContext.Provider>
    </ToastProvider>,
  )
  return { ...result, workspace }
}
