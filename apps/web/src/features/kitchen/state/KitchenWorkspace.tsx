// Stan dzielony między ekranami kuchni: klient zapytań i słowniki.
//
// Słowniki (jednostki, kuchnie, kategorie, diety, tagi, sprzęt) są tłem każdego
// ekranu — formularz przepisu, filtry wyszukiwarki i przegląd szkicu wszystkie
// pokazują te same listy. Zmieniają się rzadko, więc pobiera się je raz na
// sesję zamiast osobno na każdym ekranie.
//
// QueryClientProvider stoi TUTAJ, a nie w powłoce: powłoka nie wie o istnieniu
// kuchni i nie powinna wiedzieć (reguła z .claude/rules/frontend-architecture.md).
// Klient ograniczony do jednej domeny zostawia pozostałe domeny nietknięte.

import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query'
import { createContext, useContext, useMemo, useState } from 'react'
import type { ReactNode } from 'react'

import { api, type DictionariesResponse } from '@/features/kitchen/api'
import { kitchenKeys } from '@/features/kitchen/state/queryKeys'

export interface KitchenWorkspace {
  /** Null, dopóki słowniki się nie wczytają albo gdy wczytanie padło. */
  dictionaries: DictionariesResponse | null
  loading: boolean
  error: Error | null
}

export const KitchenWorkspaceContext = createContext<KitchenWorkspace | null>(null)

/** Słowniki nie zmieniają się w trakcie sesji — nie ma czego odświeżać. */
const DICTIONARIES_STALE_MS = Infinity

export function createKitchenQueryClient(): QueryClient {

  return new QueryClient({
    defaultOptions: {
      queries: {
        // Powrót do karty nie ma prawa podmieniać treści przepisu pod kursorem
        // w trakcie gotowania.
        refetchOnWindowFocus: false,
        retry: 1,
        staleTime: 30_000,
      },
    },
  })
}

export function KitchenWorkspaceProvider({ children }: { children: ReactNode }) {

  const [client] = useState(createKitchenQueryClient)

  return (
    <QueryClientProvider client={client}>
      <Dictionaries>{children}</Dictionaries>
    </QueryClientProvider>
  )
}

function Dictionaries({ children }: { children: ReactNode }) {

  const query = useQuery({
    queryKey: kitchenKeys.dictionaries(),
    queryFn: api.dictionaries,
    staleTime: DICTIONARIES_STALE_MS,
  })

  const value = useMemo<KitchenWorkspace>(
    () => ({
      dictionaries: query.data ?? null,
      loading: query.isPending,
      error: query.error,
    }),
    [query.data, query.isPending, query.error],
  )

  return <KitchenWorkspaceContext.Provider value={value}>{children}</KitchenWorkspaceContext.Provider>
}

export function useKitchenWorkspace(): KitchenWorkspace {

  const workspace = useContext(KitchenWorkspaceContext)
  if (workspace === null) {
    throw new Error('useKitchenWorkspace poza KitchenWorkspaceProvider')
  }
  return workspace
}
