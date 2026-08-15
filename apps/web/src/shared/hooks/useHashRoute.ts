// Routing na hashu (M3.1, uogólniony na wiele domen): adres niesie domenę,
// ekran wewnątrz niej i stan filtrów. Bez biblioteki routera — aplikacja jest
// serwowana statycznie, a odświeżenie strony ma wracać do tego samego ekranu.
//
// Postać adresu: `#/<domena>/<ekran>?<filtry>`; sama domena znaczy jej ekran
// domyślny (pierwsza trasa manifestu).

import { useCallback, useMemo, useSyncExternalStore } from 'react'

export interface ParsedRoute {
  /** Identyfikator domeny z adresu; pusty, gdy adres go nie niesie. */
  featureId: string
  /** Ekran wewnątrz domeny, względny wobec jej `basePath`. */
  path: string
  params: URLSearchParams
}

export function parseHash(hash: string): ParsedRoute {

  const withoutPrefix = hash.replace(/^#\/?/, '')
  const [fullPath, query = ''] = withoutPrefix.split('?')
  const [featureId = '', ...rest] = fullPath.split('/').filter(Boolean)
  return { featureId, path: rest.join('/'), params: new URLSearchParams(query) }
}

export function buildHash(featureId: string, path: string, params: URLSearchParams): string {

  const query = params.toString()
  const target = path ? `${featureId}/${path}` : featureId
  return query ? `#/${target}?${query}` : `#/${target}`
}

/** Puste wartości usuwają parametr, żeby adres nie puchł od domyślnych filtrów. */
export function applyParams(
  params: URLSearchParams,
  patch: Record<string, string | number | undefined | null>,
): URLSearchParams {

  const next = new URLSearchParams(params)
  Object.entries(patch).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') next.delete(key)
    else next.set(key, String(value))
  })
  return next
}

const subscribe = (onChange: () => void) => {
  window.addEventListener('hashchange', onChange)
  return () => window.removeEventListener('hashchange', onChange)
}

export function useHashRoute() {

  const hash = useSyncExternalStore(
    subscribe,
    () => window.location.hash,
    () => '',
  )
  const route = useMemo(() => parseHash(hash), [hash])

  const navigate = useCallback(
    (featureId: string, path: string, params?: URLSearchParams) => {
      window.location.hash = buildHash(featureId, path, params ?? new URLSearchParams())
    },
    [],
  )

  const setParams = useCallback(
    (patch: Record<string, string | number | undefined | null>) => {
      const current = parseHash(window.location.hash)
      window.location.hash = buildHash(
        current.featureId,
        current.path,
        applyParams(current.params, patch),
      )
    },
    [],
  )

  return { ...route, navigate, setParams }
}
