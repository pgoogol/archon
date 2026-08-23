import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'

import KontaRoute from './KontaRoute'
import { renderRoute } from '@/features/finance/test/renderRoute'
import {
  BASE,
  HttpResponse,
  financeServer,
  http,
  respondJson,
  useFinanceApi,
} from '@/features/finance/test/server'

useFinanceApi()

/** Ciała żądań zapisu — asercje sprawdzają, CO poszło na serwer. */
let createdBodies: unknown[]

const ACCOUNT = {
  id: 1,
  name: 'Bieżące',
  type: 'BANK',
  currency: 'PLN',
  iban: null,
  openingBalanceMinor: 0,
  openingBalanceOn: '2026-01-01',
  archived: false,
}

beforeEach(() => {
  createdBodies = []
  financeServer.use(
    respondJson('/accounts', [ACCOUNT]),
    http.post(`${BASE}/accounts`, async ({ request }) => {
      createdBodies.push(await request.json())
      return HttpResponse.json(ACCOUNT)
    }),
  )
})

describe('KontaRoute', () => {

  it('pokazuje konto z listy', async () => {

    renderRoute(<KontaRoute />)

    expect(await screen.findByRole('cell', { name: 'Bieżące' })).toBeInTheDocument()
  })

  it('pusta nazwa zatrzymuje zapis i pokazuje komunikat przy polu', async () => {

    // formularz ma `noValidate`, więc to walidacja ze schematu, a nie przeglądarki
    const user = userEvent.setup()
    renderRoute(<KontaRoute />)
    await screen.findByRole('cell', { name: 'Bieżące' })

    await user.click(screen.getByRole('button', { name: 'Zapisz konto' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Pole jest wymagane')
    expect(createdBodies).toHaveLength(0)
  })

  it('kwota z przecinkiem jest odrzucana, bo grosz nie jest ułamkiem', async () => {

    // saldo otwarcia wchodzi w jednostkach podrzędnych i musi być całkowite —
    // „12,50" w tym polu znaczyłoby coś innego, niż użytkownik myśli
    const user = userEvent.setup()
    renderRoute(<KontaRoute />)
    await screen.findByRole('cell', { name: 'Bieżące' })

    await user.type(screen.getByRole('textbox', { name: 'Nazwa' }), 'Oszczędnościowe')
    const amount = screen.getByRole('textbox', { name: /Saldo otwarcia/ })
    await user.clear(amount)
    await user.type(amount, '12,50')
    await user.click(screen.getByRole('button', { name: 'Zapisz konto' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Podaj liczbę całkowitą')
    expect(createdBodies).toHaveLength(0)
  })

  it('poprawny formularz wysyła kwotę jako liczbę, nie tekst', async () => {

    const user = userEvent.setup()
    renderRoute(<KontaRoute />)
    await screen.findByRole('cell', { name: 'Bieżące' })

    await user.type(screen.getByRole('textbox', { name: 'Nazwa' }), 'Oszczędnościowe')
    const amount = screen.getByRole('textbox', { name: /Saldo otwarcia/ })
    await user.clear(amount)
    await user.type(amount, '2500')
    await user.click(screen.getByRole('button', { name: 'Zapisz konto' }))

    await screen.findByText('Konto zapisane')
    expect(createdBodies[0]).toMatchObject({
      name: 'Oszczędnościowe',
      currency: 'PLN',
      openingBalanceMinor: 2500,
    })
  })
})
