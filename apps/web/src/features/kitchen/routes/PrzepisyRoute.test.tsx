import { render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import PrzepisyRoute from './PrzepisyRoute'
import { KitchenWorkspaceProvider } from '@/features/kitchen/state/KitchenWorkspace'
import { renderRoute } from '@/features/kitchen/test/renderRoute'
import {
  BASE,
  EMPTY_DICTIONARIES,
  HttpResponse,
  http,
  kitchenServer,
  useKitchenApi,
} from '@/features/kitchen/test/server'

useKitchenApi()

describe('PrzepisyRoute', () => {

  it('pokazuje pustą książkę, dopóki nie ma przepisów', () => {

    renderRoute(<PrzepisyRoute />)

    expect(screen.getByText(/Książka jest pusta/i)).toBeInTheDocument()
  })

  it('mówi wprost, gdy serwis nie odpowiada', () => {

    renderRoute(<PrzepisyRoute />, { dictionaries: null, error: new Error('padło') })

    expect(screen.getByRole('alert')).toHaveTextContent(/Nie udało się połączyć/i)
  })

  it('wczytuje słowniki z serwisu przez dostawcę domeny', async () => {

    kitchenServer.use(
      http.get(`${BASE}/dictionaries`, () =>
        HttpResponse.json({
          ...EMPTY_DICTIONARIES,
          units: [
            { id: 1, code: 'g', name: 'gram', kind: 'MASS' },
            { id: 2, code: 'ml', name: 'mililitr', kind: 'VOLUME' },
            { id: 3, code: 'lyzka', name: 'łyżka', kind: 'VOLUME' },
          ],
        }),
      ),
    )

    render(
      <KitchenWorkspaceProvider>
        <PrzepisyRoute />
      </KitchenWorkspaceProvider>,
    )

    await waitFor(() => expect(screen.getByText(/3 jednostek/)).toBeInTheDocument())
  })
})
