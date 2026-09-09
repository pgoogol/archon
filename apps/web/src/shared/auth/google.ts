// Cienka warstwa nad biblioteką „Sign in with Google".
//
// Skrypt ładujemy dopiero, gdy bramka faktycznie go potrzebuje — aplikacja
// uruchomiona z wyłączonym logowaniem albo zalogowana kontem lokalnym nie
// pobiera niczego z zewnątrz.

const SCRIPT_URL = 'https://accounts.google.com/gsi/client'

export interface GoogleCredentialResponse {
  credential?: string
}

export interface GoogleButtonOptions {
  theme: 'outline' | 'filled_black' | 'filled_blue'
  size: 'small' | 'medium' | 'large'
  text: 'signin_with' | 'continue_with'
  shape: 'rectangular' | 'pill'
  width: number
}

export interface GoogleIdentityApi {
  initialize(config: {
    client_id: string
    callback: (response: GoogleCredentialResponse) => void
  }): void
  renderButton(parent: HTMLElement, options: GoogleButtonOptions): void
  disableAutoSelect(): void
}

declare global {
  interface Window {
    google?: { accounts: { id: GoogleIdentityApi } }
  }
}

let loading: Promise<GoogleIdentityApi> | null = null

/**
 * Ładuje skrypt Google raz na dokument i oddaje jego API. Kolejne wywołania
 * dostają tę samą obietnicę — bramka potrafi zamontować się ponownie po
 * wylogowaniu, a drugi znacznik skryptu psułby inicjalizację.
 */
export function loadGoogleIdentity(): Promise<GoogleIdentityApi> {

  if (window.google !== undefined) {

    return Promise.resolve(window.google.accounts.id)
  }
  if (loading !== null) {

    return loading
  }
  loading = new Promise<GoogleIdentityApi>((resolve, reject) => {

    const script = document.createElement('script')
    script.src = SCRIPT_URL
    script.async = true
    script.defer = true
    script.onload = () => {

      if (window.google === undefined) {

        reject(new Error('Biblioteka Google wczytana, ale nie udostępniła API'))
        return
      }
      resolve(window.google.accounts.id)
    }
    script.onerror = () => reject(new Error('Nie udało się wczytać biblioteki Google'))
    document.head.appendChild(script)
  })
  return loading
}
