// Stan zalogowania dla całej aplikacji.
//
// Kontekst mieszka w `shared/`, a nie w `shell/`, bo poświadczeń używa klient
// HTTP, a `shared/` nie może importować z `shell/`. Powłoka tylko renderuje
// providera i bramkę — o żadnej domenie nadal nie wie.

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'

import type { WhoAmIResponse } from '@archon/api-client/identity'
import { loadAuthConfig, type AuthConfig } from '@/shared/auth/authConfig'
import {
  basicCredential,
  bearerCredential,
  setCredential,
  setUnauthorizedHandler,
} from '@/shared/auth/credentialStore'
import { request } from '@/shared/http/client'

/** `checking` trwa, dopóki nie wiadomo, czy bramka ma się w ogóle pokazać. */
export type AuthStatus = 'checking' | 'anonymous' | 'authenticated'

export interface AuthState {
  status: AuthStatus
  config: AuthConfig | null
  subject: string | null
  signInWithPassword: (username: string, password: string) => Promise<void>
  signInWithGoogleToken: (idToken: string) => Promise<void>
  signOut: () => void
}

const AuthContext = createContext<AuthState | null>(null)

const WHOAMI_URL = '/auth/whoami'

/**
 * Sprawdza poświadczenia, zanim trafią do pamięci: adres stoi poza prefiksem
 * domenowym, więc powłoka może go zawołać, nie znając żadnej domeny. Dla konta
 * lokalnego to jedyny sposób weryfikacji, bo uwierzytelnianie Basic nie ma
 * własnego endpointu logowania.
 */
async function verify(credential: string): Promise<WhoAmIResponse> {

  return request<WhoAmIResponse>(WHOAMI_URL, {
    headers: { Authorization: credential },
  })
}

export function AuthProvider({ children }: { children: ReactNode }) {

  const [status, setStatus] = useState<AuthStatus>('checking')
  const [config, setConfig] = useState<AuthConfig | null>(null)
  const [subject, setSubject] = useState<string | null>(null)

  useEffect(() => {

    let active = true
    loadAuthConfig().then((loaded) => {

      if (!active) {

        return
      }
      setConfig(loaded)
      setStatus(loaded.enabled ? 'anonymous' : 'authenticated')
    })
    return () => {

      active = false
    }
  }, [])

  const signOut = useCallback(() => {

    setCredential(null)
    setSubject(null)
    setStatus('anonymous')
    window.google?.accounts.id.disableAutoSelect()
  }, [])

  useEffect(() => {

    // wygaśnięcie tokenu Google (po godzinie) objawia się pierwszym 401 —
    // wracamy wtedy do bramki zamiast zostawiać aplikację, w której każde
    // kolejne żądanie pada
    setUnauthorizedHandler(() => {

      setSubject(null)
      setStatus('anonymous')
    })
    return () => setUnauthorizedHandler(null)
  }, [])

  const signIn = useCallback(async (credential: string) => {

    const identity = await verify(credential)
    setCredential(credential)
    setSubject(identity.subject ?? null)
    setStatus('authenticated')
  }, [])

  const signInWithPassword = useCallback(
    async (username: string, password: string) => {

      await signIn(basicCredential(username, password))
    },
    [signIn],
  )

  const signInWithGoogleToken = useCallback(
    async (idToken: string) => {

      await signIn(bearerCredential(idToken))
    },
    [signIn],
  )

  const value = useMemo<AuthState>(
    () => ({ status, config, subject, signInWithPassword, signInWithGoogleToken, signOut }),
    [status, config, subject, signInWithPassword, signInWithGoogleToken, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {

  const value = useContext(AuthContext)
  if (value === null) {

    throw new Error('useAuth poza AuthProvider')
  }
  return value
}
