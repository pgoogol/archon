import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'

import ImportRoute from './ImportRoute'
import { anImportBatch, anImportRow } from '@/features/finance/test/fixtures'
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

let batch: ReturnType<typeof anImportBatch>
/** Ciała żądań zatwierdzenia — asercje sprawdzają, CO poszło na serwer. */
let commitBodies: unknown[]

const ACCOUNTS = [
  {
    id: 1,
    name: 'Bieżące',
    type: 'BANK',
    currency: 'PLN',
    iban: null,
    openingBalanceMinor: 0,
    openingBalanceOn: '2026-01-01',
    archived: false,
  },
]

const CATEGORIES = [
  { id: 2, parentId: null, name: 'Jedzenie', direction: 'EXPENSE', archived: false, children: [] },
]

beforeEach(() => {
  commitBodies = []
  batch = anImportBatch([
    anImportRow({
      id: 11,
      suggestedCategoryId: 2,
      suggestedCategoryName: 'Jedzenie',
      suggestedOccurrenceId: 77,
      suggestedOccurrenceName: 'Prąd',
    }),
  ])
  financeServer.use(
    respondJson('/accounts', ACCOUNTS),
    respondJson('/categories', CATEGORIES),
    // wgranie pliku zwraca samą partię; podgląd dociąga ją z wierszami
    http.post(`${BASE}/imports`, () => HttpResponse.json(batch.batch)),
    http.get(`${BASE}/imports/:id`, () => HttpResponse.json(batch)),
    http.post(`${BASE}/imports/:id/commit`, async ({ request }) => {
      commitBodies.push(await request.json())
      return HttpResponse.json({
        batchId: 5,
        committedCount: 1,
        skippedDuplicateCount: 0,
        reconciliation: { matched: true },
      })
    }),
  )
})

async function uploadStatement() {

  const user = userEvent.setup()
  renderRoute(<ImportRoute />)
  const input = await screen.findByLabelText('Plik wyciągu')
  const file = new File(['x'], 'luty.csv', { type: 'text/csv' })
  await user.upload(input, file)
  await user.click(screen.getByRole('button', { name: 'Wczytaj i pokaż podgląd' }))
  await screen.findByText('Podgląd — nic jeszcze nie zapisano')
  return user
}

describe('ImportRoute', () => {

  it('podgląd pokazuje wiersze i nie zapisuje żadnej transakcji', async () => {

    await uploadStatement()

    expect(screen.getByText('ZAKUP BIEDRONKA')).toBeInTheDocument()
    expect(commitBodies).toHaveLength(0)
  })

  it('podpowiedziana kategoria wchodzi jako wartość domyślna', async () => {

    await uploadStatement()

    const select = screen.getByRole('combobox', { name: 'Kategoria dla wiersza 1' })
    expect(select).toHaveValue('2')
  })

  it('dopasowanie do rachunku cyklicznego zostaje odznaczone', async () => {

    // to jest cała ostrożność tego ekranu: oznaczenie rachunku jako opłaconego
    // bez świadomego kliknięcia byłoby cichym błędem przy pieniądzach
    await uploadStatement()

    expect(screen.getByRole('checkbox', { name: /rozlicz „Prąd”|rozlicz „Prąd"/ })).not.toBeChecked()
  })

  it('zatwierdzenie bez zaznaczenia nie rozlicza rachunku cyklicznego', async () => {

    const user = await uploadStatement()

    await user.click(screen.getByRole('button', { name: 'Zatwierdź wyciąg' }))

    await screen.findByText(/Zapisano 1 transakcji/)
    expect(commitBodies[0]).toEqual({
      categoryAssignments: [{ rowId: 11, categoryId: 2 }],
      occurrenceAssignments: [],
    })
  })

  it('po zaznaczeniu rozliczenia wysyła potwierdzenie pozycji terminarza', async () => {

    const user = await uploadStatement()

    await user.click(screen.getByRole('checkbox', { name: /rozlicz/ }))
    await user.click(screen.getByRole('button', { name: 'Zatwierdź wyciąg' }))

    await screen.findByText(/Zapisano 1 transakcji/)
    expect(commitBodies[0]).toMatchObject({
      occurrenceAssignments: [{ rowId: 11, occurrenceId: 77 }],
    })
  })

  it('wiersz rozpoznany jako duplikat nie prosi o kategorię', async () => {

    batch = anImportBatch([anImportRow({ id: 12, status: 'DUPLICATE' })])

    await uploadStatement()

    const row = screen.getByRole('row', { name: /ZAKUP BIEDRONKA/ })
    expect(within(row).queryByRole('combobox')).not.toBeInTheDocument()
    expect(within(row).getByText('duplikat')).toBeInTheDocument()
  })
})
