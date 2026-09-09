import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'

import EdycjaRoute from './EdycjaRoute'
import { renderRoute } from '@/features/kitchen/test/renderRoute'
import {
  BASE,
  HttpResponse,
  SERNIK,
  http,
  kitchenServer,
  useKitchenApi,
} from '@/features/kitchen/test/server'

useKitchenApi()

/** Ciała żądań zapisu — asercje sprawdzają, CO poszło na serwer. */
let saved: Record<string, unknown>[]

beforeEach(() => {
  saved = []
  window.location.hash = '#/kitchen/edycja'
  kitchenServer.use(
    http.post(`${BASE}/recipes`, async ({ request }) => {
      saved.push((await request.json()) as Record<string, unknown>)
      return HttpResponse.json({ recipe: { ...SERNIK, id: 9 }, changed: true, revisionNo: 1 }, { status: 201 })
    }),
    http.put(`${BASE}/recipes/:id`, async ({ request }) => {
      saved.push((await request.json()) as Record<string, unknown>)
      return HttpResponse.json({ recipe: SERNIK, changed: true, revisionNo: 4 })
    }),
  )
})

describe('EdycjaRoute', () => {

  it('nie zapisuje przepisu bez tytułu', async () => {

    renderRoute(<EdycjaRoute />)

    await userEvent.click(screen.getByRole('button', { name: 'Zapisz' }))

    expect(await screen.findByText('Pole jest wymagane')).toBeInTheDocument()
    expect(saved).toHaveLength(0)
  })

  it('wysyła nowy przepis ze składnikiem i krokiem', async () => {

    renderRoute(<EdycjaRoute />)

    await userEvent.type(screen.getByLabelText('Tytuł'), 'Naleśniki')
    await userEvent.click(screen.getByRole('button', { name: 'Dodaj składnik' }))
    await userEvent.type(screen.getByRole('textbox', { name: 'Nazwa składnika 1' }), 'mąka')
    await userEvent.type(screen.getByRole('textbox', { name: 'Ilość składnika 1' }), '250')
    await userEvent.click(screen.getByRole('button', { name: 'Dodaj krok' }))
    await userEvent.type(screen.getByRole('textbox', { name: 'Treść kroku 1' }), 'Wymieszaj')
    await userEvent.click(screen.getByRole('button', { name: 'Zapisz' }))

    await waitFor(() => expect(saved).toHaveLength(1))
    const draft = saved[0].draft as Record<string, unknown>
    expect(draft.title).toBe('Naleśniki')
    expect(draft.ingredients).toEqual([
      expect.objectContaining({ displayName: 'mąka', quantityMin: 250, quantityMax: 250 }),
    ])
    expect(draft.steps).toEqual([expect.objectContaining({ text: 'Wymieszaj' })])
  })

  it('przy edycji odsyła identyfikatory wierszy i numer rewizji', async () => {

    window.location.hash = '#/kitchen/edycja?id=7'
    renderRoute(<EdycjaRoute />)

    await waitFor(() => expect(screen.getByLabelText('Tytuł')).toHaveValue('Sernik'))
    await userEvent.click(screen.getByRole('button', { name: 'Zapisz' }))

    await waitFor(() => expect(saved).toHaveLength(1))
    // bez identyfikatorów wierszy historia zapisałaby edycję jako skasowanie
    // wszystkiego i dodanie od nowa
    const draft = saved[0].draft as { ingredients: { id: number }[] }
    expect(draft.ingredients.map((line) => line.id)).toEqual([100, 101])
    expect(saved[0].expectedRevisionNo).toBe(3)
  })

  it('mówi wprost, gdy ktoś zapisał przepis w międzyczasie', async () => {

    window.location.hash = '#/kitchen/edycja?id=7'
    kitchenServer.use(
      http.put(`${BASE}/recipes/:id`, () =>
        HttpResponse.json({ errorCode: 'RECIPE_MODIFIED', message: 'zmieniony' }, { status: 409 }),
      ),
    )
    renderRoute(<EdycjaRoute />)

    await waitFor(() => expect(screen.getByLabelText('Tytuł')).toHaveValue('Sernik'))
    await userEvent.click(screen.getByRole('button', { name: 'Zapisz' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/zmienił się w międzyczasie/i)
  })
})
