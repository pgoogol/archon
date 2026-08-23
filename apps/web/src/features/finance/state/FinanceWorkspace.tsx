// Stan dzielony między ekranami finansów: klient zapytań i słownik walut.
//
// Słownik jest tu, a nie w każdym ekranie z osobna, bo bez `minorUnit` nie da
// się wypisać ani jednej kwoty — a to jest jedna wartość na walutę, która nie
// zmienia się w trakcie sesji. Sześć ekranów pobierających ją samodzielnie to
// sześć zapytań o to samo; TanStack Query i tak zwróciłby je z cache'u, ale
// jeden hook czyta się lepiej niż sześć wywołań tego samego zapytania.
//
// QueryClientProvider stoi TUTAJ, a nie w powłoce. Powłoka nie wie o istnieniu
// finansów i nie powinna wiedzieć — a reguła frontowa każe zgłaszać zmiany
// w `shell/` zamiast je robić po cichu. Klient ograniczony do jednej domeny ma
// przy okazji tę zaletę, że domena muzyczna zostaje nietknięta.

import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query'
import { createContext, useContext, useMemo, useState } from 'react'
import type { ReactNode } from 'react'

import { api, type CurrencyResponse } from '@/features/finance/api'
import { financeKeys } from '@/features/finance/state/queryKeys'

export interface FinanceWorkspace {
  currencies: CurrencyResponse[]
  /** Liczba miejsc po przecinku waluty; nieznana waluta dostaje 2. */
  minorUnitOf: (currency: string) => number
}

export const FinanceWorkspaceContext = createContext<FinanceWorkspace | null>(null)

const FALLBACK_MINOR_UNIT = 2

/** Słownik walut nie zmienia się w trakcie sesji — nie ma czego odświeżać. */
const CURRENCIES_STALE_MS = Infinity

export function createFinanceQueryClient(): QueryClient {

  return new QueryClient({
    defaultOptions: {
      queries: {
        // Ekrany finansów pokazują kwoty; cicha podmiana liczb pod kursorem przy
        // powrocie do karty jest gorsza niż dane sprzed minuty.
        refetchOnWindowFocus: false,
        retry: 1,
        staleTime: 30_000,
      },
    },
  })
}

export function FinanceWorkspaceProvider({ children }: { children: ReactNode }) {

  const [client] = useState(createFinanceQueryClient)

  return (
    <QueryClientProvider client={client}>
      <CurrencyDictionary>{children}</CurrencyDictionary>
    </QueryClientProvider>
  )
}

/**
 * Słownik walut jest tłem, nie treścią ekranu: jego brak ma zostawić domyślne
 * dwa miejsca po przecinku, a nie wywalić cały moduł. Dlatego błąd tego zapytania
 * nigdzie nie idzie — `data` zostaje puste i `minorUnitOf` schodzi na wartość
 * domyślną.
 */
function CurrencyDictionary({ children }: { children: ReactNode }) {

  const { data } = useQuery({
    queryKey: financeKeys.currencies(),
    queryFn: () => api.listCurrencies(),
    staleTime: CURRENCIES_STALE_MS,
  })

  const value = useMemo<FinanceWorkspace>(() => {

    const currencies = data ?? []
    return {
      currencies,
      minorUnitOf: (currency: string) =>
        currencies.find((entry) => entry.code === currency)?.minorUnit ?? FALLBACK_MINOR_UNIT,
    }
  }, [data])

  return (
    <FinanceWorkspaceContext.Provider value={value}>{children}</FinanceWorkspaceContext.Provider>
  )
}

export function useFinanceWorkspace(): FinanceWorkspace {

  const value = useContext(FinanceWorkspaceContext)
  if (!value) {
    throw new Error('useFinanceWorkspace poza FinanceWorkspaceProvider')
  }
  return value
}
