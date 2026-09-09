// Serwer MSW powłoki.
//
// Powłoka nie zna żadnej domeny, więc i jej serwer testowy nie może znać:
// obsługuje adresy własne (konfiguracja logowania, tożsamość), a wszystko
// pozostałe łapie regułą ogólną i oddaje pustą stronę wyniku. Ekrany domen
// pobierają dane przy montowaniu i powłoce wystarczy, że te żądania nie padają.

import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { afterAll, afterEach, beforeAll } from 'vitest'

import type { AuthConfig } from '@/shared/auth/authConfig'

export const AUTH_DISABLED: AuthConfig = {
  enabled: false,
  googleClientId: '',
  localAccountEnabled: false,
}

export const AUTH_LOCAL_ONLY: AuthConfig = {
  enabled: true,
  googleClientId: '',
  localAccountEnabled: true,
}

const EMPTY_PAGE = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }

export function authConfig(config: AuthConfig) {

  return http.get('/auth-config.json', () => HttpResponse.json(config))
}

/** Poświadczenia uznane — zwraca tożsamość, jak prawdziwy serwis. */
export function whoAmI(subject: string) {

  return http.get('/auth/whoami', () => HttpResponse.json({ subject, authentication: 'local' }))
}

/** Poświadczenia odrzucone — kształt błędu ten sam co w serwisach. */
export function whoAmIRejects() {

  return http.get('/auth/whoami', () =>
    HttpResponse.json(
      { errorCode: 'UNAUTHORIZED', message: 'Brak poświadczeń', timestamp: '', traceId: '' },
      { status: 401 },
    ),
  )
}

const catchAll = http.get(/.*/, () => HttpResponse.json(EMPTY_PAGE))

export const shellServer = setupServer(authConfig(AUTH_DISABLED), catchAll)

export function useShellApi(): void {

  beforeAll(() => shellServer.listen({ onUnhandledRequest: 'error' }))
  afterEach(() => shellServer.resetHandlers())
  afterAll(() => shellServer.close())
}
