import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'

import App from '@/shell/App'
import { features } from '@/registry'
import {
  AUTH_LOCAL_ONLY,
  authConfig,
  shellServer,
  useShellApi,
  whoAmI,
  whoAmIRejects,
} from '@/shell/test/server'

const feature = features[0]

useShellApi()

beforeEach(() => {
  window.location.hash = ''
  shellServer.use(authConfig(AUTH_LOCAL_ONLY))
})

describe('bramka logowania', () => {

  it('przy włączonym logowaniu zasłania aplikację ekranem dostępu', async () => {

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'archon' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: feature.title })).not.toBeInTheDocument()
  })

  it('poprawne poświadczenia wpuszczają do aplikacji', async () => {

    const user = userEvent.setup()
    shellServer.use(whoAmI('admin'))
    render(<App />)
    await screen.findByRole('heading', { name: 'archon' })

    await user.type(screen.getByLabelText('Login'), 'admin')
    await user.type(screen.getByLabelText('Hasło'), 'admin')
    await user.click(screen.getByRole('button', { name: 'Zaloguj' }))

    expect(await screen.findByRole('heading', { name: feature.title })).toBeInTheDocument()
  })

  it('odrzucone poświadczenia zostawiają na bramce z komunikatem', async () => {

    const user = userEvent.setup()
    shellServer.use(whoAmIRejects())
    render(<App />)
    await screen.findByRole('heading', { name: 'archon' })

    await user.type(screen.getByLabelText('Login'), 'admin')
    await user.type(screen.getByLabelText('Hasło'), 'zle-haslo')
    await user.click(screen.getByRole('button', { name: 'Zaloguj' }))

    expect(await screen.findByText('Nieprawidłowy login lub hasło')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: feature.title })).not.toBeInTheDocument()
  })

  it('puste pola nie wysyłają żądania', async () => {

    const user = userEvent.setup()
    // brak handlera whoami: gdyby formularz wysłał żądanie, serwer MSW
    // przerwałby test na nieobsłużonym adresie
    render(<App />)
    await screen.findByRole('heading', { name: 'archon' })

    await user.click(screen.getByRole('button', { name: 'Zaloguj' }))

    expect(await screen.findAllByText('Pole jest wymagane')).toHaveLength(2)
  })

  it('wylogowanie wraca na bramkę', async () => {

    const user = userEvent.setup()
    shellServer.use(whoAmI('admin'))
    render(<App />)
    await screen.findByRole('heading', { name: 'archon' })
    await user.type(screen.getByLabelText('Login'), 'admin')
    await user.type(screen.getByLabelText('Hasło'), 'admin')
    await user.click(screen.getByRole('button', { name: 'Zaloguj' }))
    await screen.findByRole('heading', { name: feature.title })

    await user.click(screen.getByRole('button', { name: 'Wyloguj' }))

    expect(await screen.findByRole('heading', { name: 'archon' })).toBeInTheDocument()
  })
})
