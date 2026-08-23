import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import PulpitRoute from './PulpitRoute'
import { aDashboard, anUpcomingItem } from '@/features/finance/test/fixtures'
import { renderRoute } from '@/features/finance/test/renderRoute'
import { jsonResponse } from '@/shared/test/renderWithToasts'

let fetchMock: ReturnType<typeof vi.fn>

function respondWith(body: unknown) {

  fetchMock = vi.fn().mockResolvedValue(jsonResponse(body))
  globalThis.fetch = fetchMock as unknown as typeof fetch
}

beforeEach(() => {
  respondWith(aDashboard())
})

describe('PulpitRoute', () => {

  it('pokazuje saldo konta w walucie konta', async () => {

    renderRoute(<PulpitRoute />)

    // 123 456 groszy to 1 234,56 zł — grosz nie może zginąć po drodze
    const row = await screen.findByRole('row', { name: /Bieżące/ })
    expect(row).toHaveTextContent('1 234,56 PLN')
  })

  it('pokazuje bilans miesiąca rozbity na przychody i wydatki', async () => {

    renderRoute(<PulpitRoute />)

    expect(await screen.findByText('Bilans miesiąca')).toBeInTheDocument()
    expect(screen.getByText(/9\s*000,00/)).toBeInTheDocument()
    expect(screen.getByText(/6\s*500,00/)).toBeInTheDocument()
  })

  it('gdy waluta konta nie ma kursu, zostawia pustą wycenę zamiast zera', async () => {

    // given: konto walutowe bez pobranego kursu — zero w tej kolumnie
    // wyglądałoby jak „nic nie masz", a to nieprawda
    respondWith(
      aDashboard({
        accountBalances: [
          {
            accountId: 2,
            accountName: 'Walutowe',
            currency: 'EUR',
            minorUnit: 2,
            balanceMinor: 5_000,
            baseValueMinor: null,
          },
        ],
      }),
    )

    renderRoute(<PulpitRoute />)

    const row = await screen.findByRole('row', { name: /Walutowe/ })
    expect(row).toHaveTextContent('50,00')
    expect(row).toHaveTextContent('—')
  })

  it('wyróżnia pozycje po terminie', async () => {

    respondWith(
      aDashboard({
        overdue: [anUpcomingItem({ occurrenceId: 9, ruleName: 'Czynsz', overdue: true })],
      }),
    )

    renderRoute(<PulpitRoute />)

    expect(await screen.findByText('Czynsz')).toBeInTheDocument()
    expect(screen.getByText('po terminie')).toBeInTheDocument()
  })

  it('gdy nic nie jest przeterminowane, mówi to wprost', async () => {

    renderRoute(<PulpitRoute />)

    expect(await screen.findByText('Nic nie jest przeterminowane.')).toBeInTheDocument()
  })
})
