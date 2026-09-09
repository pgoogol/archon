// Poświadczenia bieżącej sesji — wyłącznie w pamięci modułu.
//
// Świadomie nie w `localStorage`: token przetrwałby zamknięcie karty, a nic tu
// nie musi go przechowywać dłużej niż trwa praca. Trzymamy gotową wartość
// nagłówka `Authorization`, więc obie drogi logowania — token Google (`Bearer`)
// i konto lokalne (`Basic`) — wyglądają dla warstwy HTTP tak samo.
//
// Moduł żyje w `shared/`, bo woła go klient HTTP, a `shared/` nie może
// importować z `shell/` (granice modułów pilnowane przez ESLint).

let credential: string | null = null

let unauthorizedHandler: (() => void) | null = null

export function setCredential(value: string | null): void {

  credential = value
}

export function currentCredential(): string | null {

  return credential
}

/** Rejestruje reakcję na odmowę serwisu — w praktyce powrót do ekranu logowania. */
export function setUnauthorizedHandler(handler: (() => void) | null): void {

  unauthorizedHandler = handler
}

/**
 * Serwis odrzucił poświadczenia. Czyścimy je od razu: token Google żyje godzinę,
 * więc pierwsze 401 po wygaśnięciu ma zaprowadzić z powrotem do logowania,
 * a nie zostawić aplikacji w stanie, w którym każde kolejne żądanie pada.
 */
export function reportUnauthorized(): void {

  credential = null
  if (unauthorizedHandler !== null) {

    unauthorizedHandler()
  }
}

export function basicCredential(username: string, password: string): string {

  return `Basic ${btoa(`${username}:${password}`)}`
}

export function bearerCredential(token: string): string {

  return `Bearer ${token}`
}
