import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import PrzepisyRoute from './PrzepisyRoute'
import { renderRoute } from '@/features/kitchen/test/renderRoute'
import {
  BASE,
  EMPTY_PAGE,
  HttpResponse,
  http,
  kitchenServer,
  useKitchenApi,
} from '@/features/kitchen/test/server'

useKitchenApi()

describe('PrzepisyRoute', () => {

  it('pokazuje pustą książkę, dopóki nie ma przepisów', async () => {

    expect(await screenAfterRender(/Książka jest pusta/i)).toBeInTheDocument()
  })

  it('wypisuje przepisy z serwera razem z czasem i licznikami', async () => {

    kitchenServer.use(
      http.get(`${BASE}/recipes`, () =>
        HttpResponse.json({
          ...EMPTY_PAGE,
          items: [
            {
              id: 7,
              title: 'Sernik',
              cuisine: 'polska',
              category: 'deser',
              totalMinutes: 90,
              ingredientCount: 8,
              stepCount: 5,
            },
          ],
          totalElements: 1,
        }),
      ),
    )
    renderRoute(<PrzepisyRoute />)

    expect(await screen.findByText('Sernik')).toBeInTheDocument()
    expect(screen.getByText(/1 h 30 min/)).toBeInTheDocument()
    expect(screen.getByText(/8 skł/)).toBeInTheDocument()
  })

  it('mówi wprost, gdy serwis nie odpowiada', async () => {

    kitchenServer.use(http.get(`${BASE}/recipes`, () => new HttpResponse(null, { status: 500 })))
    renderRoute(<PrzepisyRoute />)

    expect(await screen.findByRole('alert')).toHaveTextContent(/Nie udało się połączyć/i)
  })

  async function screenAfterRender(pattern: RegExp) {

    renderRoute(<PrzepisyRoute />)
    return screen.findByText(pattern)
  }
})
