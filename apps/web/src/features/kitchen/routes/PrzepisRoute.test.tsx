import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'

import PrzepisRoute from './PrzepisRoute'
import { renderRoute } from '@/features/kitchen/test/renderRoute'
import {
  BASE,
  HttpResponse,
  http,
  kitchenServer,
  useKitchenApi,
} from '@/features/kitchen/test/server'

useKitchenApi()

beforeEach(() => {
  window.location.hash = '#/kitchen/przepis?id=7'
})

describe('PrzepisRoute', () => {

  it('pokazuje składniki w grupach i kroki z czasem', async () => {

    renderRoute(<PrzepisRoute />)

    expect(await screen.findByRole('heading', { name: 'Sernik' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'na spód' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'na masę' })).toBeInTheDocument()
    expect(screen.getByText(/250 gram/)).toBeInTheDocument()
    expect(screen.getByText(/15 min/)).toBeInTheDocument()
  })

  it('wysyła nową uwagę na serwer', async () => {

    const bodies: unknown[] = []
    kitchenServer.use(
      http.post(`${BASE}/recipes/:id/notes`, async ({ request }) => {
        bodies.push(await request.json())
        return HttpResponse.json({ id: 1, body: 'za słone' }, { status: 201 })
      }),
    )
    renderRoute(<PrzepisRoute />)
    await screen.findByRole('heading', { name: 'Sernik' })

    await userEvent.type(screen.getByRole('textbox', { name: 'Nowa uwaga' }), 'za słone')
    await userEvent.click(screen.getByRole('button', { name: 'Dodaj uwagę' }))

    expect(bodies).toEqual([{ body: 'za słone' }])
  })
})
