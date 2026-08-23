// Stan dzielony między ekranami finansów: słownik walut i licznik odświeżeń.
//
// Słownik jest tu, a nie w każdym ekranie z osobna, bo bez `minorUnit` nie da
// się wypisać ani jednej kwoty — a to jest jedna wartość na walutę, która nie
// zmienia się w trakcie sesji. Sześć ekranów pobierających ją samodzielnie to
// sześć zapytań o to samo.

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'

import { api, type CurrencyResponse } from '@/features/finance/api'

export interface FinanceWorkspace {
  /** Rośnie po każdej zmianie danych — ekrany trzymają go w zależnościach efektu. */
  refreshKey: number
  refresh: () => void
  currencies: CurrencyResponse[]
  /** Liczba miejsc po przecinku waluty; nieznana waluta dostaje 2. */
  minorUnitOf: (currency: string) => number
}

export const FinanceWorkspaceContext = createContext<FinanceWorkspace | null>(null)

const FALLBACK_MINOR_UNIT = 2

export function FinanceWorkspaceProvider({ children }: { children: ReactNode }) {

  const [refreshKey, setRefreshKey] = useState(0)
  const [currencies, setCurrencies] = useState<CurrencyResponse[]>([])

  const refresh = useCallback(() => setRefreshKey((key) => key + 1), [])

  useEffect(() => {
    let current = true
    api
      .listCurrencies()
      .then((loaded) => {
        if (current) setCurrencies(loaded)
      })
      .catch(() => {
        // słownik walut jest tłem, nie treścią ekranu: jego brak ma zostawić
        // domyślne dwa miejsca po przecinku, a nie wywalić cały moduł
        if (current) setCurrencies([])
      })
    return () => {
      current = false
    }
  }, [])

  const minorUnitOf = useCallback(
    (currency: string) =>
      currencies.find((entry) => entry.code === currency)?.minorUnit ?? FALLBACK_MINOR_UNIT,
    [currencies],
  )

  const value = useMemo<FinanceWorkspace>(
    () => ({ refreshKey, refresh, currencies, minorUnitOf }),
    [refreshKey, refresh, currencies, minorUnitOf],
  )

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
