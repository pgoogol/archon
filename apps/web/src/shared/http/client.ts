// Warstwa transportowa HTTP — jedyne miejsce w aplikacji, które woła `fetch`.
// Nie zna żadnej domeny: przenosi JSON i zamienia odpowiedź błędu backendu
// (errorCode + message, format z backend-errors.md) na typowany wyjątek.

export class ApiError extends Error {
  constructor(
    message: string,
    readonly errorCode: string,
    readonly status: number,
  ) {
    super(message)
  }
}

export async function request<T>(url: string, init?: RequestInit): Promise<T> {

  const response = await fetch(url, init)
  if (!response.ok) {
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
