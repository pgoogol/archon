// Kontrakt między powłoką a domeną. Powłoka zna wyłącznie ten typ — nigdy
// nazwy konkretnej domeny (reguła z .claude/rules/frontend-architecture.md).

import type { ComponentType, LazyExoticComponent, ReactNode } from 'react'

/** Trasa domeny. `path` jest WZGLĘDNY wobec `basePath` — pusty znaczy stronę główną domeny. */
export interface FeatureRoute {
  path: string
  Component: LazyExoticComponent<ComponentType> | ComponentType
}

/** Pozycja nawigacji. `to` jest względne wobec `basePath`, tak samo jak trasy. */
export interface FeatureNavItem {
  label: string
  to: string
}

export interface FeatureManifest {
  /** Stabilny identyfikator domeny — klucz w powłoce i w adresie. */
  id: string
  /** Nazwa pokazywana w nawigacji górnego poziomu. */
  title: string
  /**
   * Podpis pod nazwą domeny. Powłoka go tylko renderuje — treść należy do domeny,
   * bo inaczej hasło jednej domeny wisiałoby nad ekranami wszystkich pozostałych.
   */
  subtitle?: string
  /** Prefiks adresu wszystkich tras domeny, np. `/music`. */
  basePath: string
  routes: FeatureRoute[]
  nav: FeatureNavItem[]
  /**
   * Opcjonalne opakowanie tras domeny — miejsce na stan dzielony między jej
   * własnymi ekranami. Powłoka tylko je renderuje; nie wie, co jest w środku.
   */
  Provider?: ComponentType<{ children: ReactNode }>
}
