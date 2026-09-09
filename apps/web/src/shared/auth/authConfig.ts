// Konfiguracja logowania pobierana w czasie działania, nie wbudowana w pakiet.
//
// Front jest serwowany jako statyk z nginksa i ten sam obraz ma działać
// lokalnie i na serwerze — dokładnie tak jak adresy backendów, które siedzą
// w konfiguracji proxy, a nie w zbudowanym JS-ie. Identyfikator klienta Google
// przychodzi więc tą samą drogą: nginx podstawia go do odpowiedzi, a w trybie
// deweloperskim i w podglądzie robi to wtyczka Vite.

import { request } from '@/shared/http/client'

export interface AuthConfig {
  /** Czy bramka logowania ma się w ogóle pokazać. */
  enabled: boolean
  /** Identyfikator klienta OAuth; pusty = logowanie wyłącznie kontem lokalnym. */
  googleClientId: string
  localAccountEnabled: boolean
}

const OPEN: AuthConfig = {
  enabled: false,
  googleClientId: '',
  localAccountEnabled: false,
}

export async function loadAuthConfig(): Promise<AuthConfig> {

  try {

    const config = await request<Partial<AuthConfig>>('/auth-config.json')
    return {
      enabled: config.enabled === true,
      googleClientId: config.googleClientId ?? '',
      localAccountEnabled: config.localAccountEnabled !== false,
    }
  } catch {

    // Bramka jest wygodą, nie zabezpieczeniem — zabezpieczeniem są serwisy,
    // które i tak odpowiedzą 401 na żądanie bez poświadczeń. Gdy konfiguracja
    // jest nieczytelna, wpuszczamy do aplikacji i pozwalamy odpowiedzieć API.
    return OPEN
  }
}
