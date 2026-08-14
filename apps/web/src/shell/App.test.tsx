import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import App from '@/shell/App'
import { features } from '@/registry'

// Test powłoki nie zna żadnej domeny — wszystkie oczekiwania czyta z rejestru.
// Import fixtures konkretnej domeny byłby tu naruszeniem granicy warstw
// i ESLint odrzuciłby go jako błąd.
const feature = features[0]

beforeEach(() => {
  window.location.hash = ''
  // ekrany domen pobierają dane na starcie; powłoce wystarczy, że fetch nie wybucha
  globalThis.fetch = vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    statusText: 'OK',
    json: async () => ({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
  }) as unknown as typeof fetch
})

describe('powłoka', () => {

  it('bez domeny w adresie otwiera pierwszą domenę z rejestru', async () => {

    render(<App />)

    expect(await screen.findByRole('heading', { name: feature.title })).toBeInTheDocument()
  })

  it('buduje nawigację z manifestu domeny, a nie z własnej listy ekranów', async () => {

    render(<App />)
    await screen.findByRole('heading', { name: feature.title })

    const tabs = screen.getByRole('navigation', { name: 'widoki' })
    feature.nav.forEach((item) => {
      expect(tabs).toHaveTextContent(item.label)
    })
  })

  it('przełączenie zakładką zapisuje w adresie domenę razem z ekranem', async () => {

    const user = userEvent.setup()
    const target = feature.nav.find((item) => item.to !== '')
    render(<App />)
    await screen.findByRole('heading', { name: feature.title })

    await user.click(screen.getByTestId(`tab-${target!.to}`))

    await waitFor(() =>
      expect(window.location.hash).toBe(`#/${feature.id}/${target!.to}`),
    )
  })

  it('nieznana domena w adresie cofa do pierwszej z rejestru', async () => {

    window.location.hash = '#/nieistniejaca'

    render(<App />)

    expect(await screen.findByRole('heading', { name: feature.title })).toBeInTheDocument()
  })

  it('przy jednej domenie nie pokazuje przełącznika domen', async () => {

    render(<App />)
    await screen.findByRole('heading', { name: feature.title })

    expect(screen.queryByRole('navigation', { name: 'domeny' })).not.toBeInTheDocument()
  })
})
