// Warstwa transportowa HTTP — jedyne miejsce w aplikacji, które woła `fetch`.
// Nie zna żadnej domeny: przenosi JSON, dokleja poświadczenia bieżącej sesji
// i zamienia odpowiedź błędu backendu (errorCode + message, format
// z backend-errors.md) na typowany wyjątek.

import { currentCredential, reportUnauthorized } from '@/shared/auth/credentialStore'

export class ApiError extends Error {
  constructor(
    message: string,
    readonly errorCode: string,
    readonly status: number,
  ) {
    super(message)
  }
}

/**
 * Dokleja nagłówek `Authorization` do żądania.
 *
 * Nagłówki scalamy, nigdy nie podmieniamy: `jsonInit` wnosi `Content-Type`,
 * a wysyłki plików podają samo `FormData` bez nagłówków — dopisanie im
 * `Content-Type` zepsułoby granicę multipart. Nagłówek podany jawnie wygrywa
 * z zapamiętanym, bo tą drogą sprawdzamy poświadczenia przy logowaniu, zanim
 * trafią do pamięci.
 */
function withCredential(init?: RequestInit): RequestInit | undefined {

  const credential = currentCredential()
  if (credential === null) {

    return init
  }
  const headers = new Headers(init?.headers)
  if (headers.has('Authorization')) {

    return { ...init, headers }
  }
  headers.set('Authorization', credential)
  return { ...init, headers }
}

export async function request<T>(url: string, init?: RequestInit): Promise<T> {

  const response = await fetch(url, withCredential(init))
  if (!response.ok) {

    if (response.status === 401) {

      reportUnauthorized()
    }
    let errorCode = 'HTTP_' + response.status
    let message = response.statusText
    try {
      const body = await response.json()
      errorCode = body.errorCode ?? errorCode
      message = body.message ?? message
    } catch {
      /* odpowiedź bez JSON-a — zostają wartości domyślne */
    }
    throw new ApiError(message, errorCode, response.status)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return response.json() as Promise<T>
}

export const jsonInit = (method: string, body: unknown): RequestInit => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
})
