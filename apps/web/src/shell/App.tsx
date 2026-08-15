// Powłoka aplikacji: nagłówek, nawigacja i host toastów. Nie zna żadnej domeny
// — czyta rejestr i manifesty (reguła z .claude/rules/frontend-architecture.md).
// Dodanie domeny to nowy katalog w src/features/ plus jedna linia w registry.ts.

import { Suspense, useMemo } from 'react'

import { features } from '@/registry'
import { ToastProvider } from '@/shared/ui/Toasts'
import type { FeatureManifest } from '@/shared/featureManifest'
import { useHashRoute } from '@/shared/hooks/useHashRoute'

export default function App() {

  return (
    <ToastProvider>
      <AppShell />
    </ToastProvider>
  )
}

/**
 * Poświata kineskopu pod krzywą tempa — rozmyta kopia kreski udaje jarzenie
 * luminoforu. Filtr musi żyć w dokumencie raz, dlatego siedzi w powłoce.
 */
function CrtDefs() {

  return (
    <svg className="crt-defs" aria-hidden="true" focusable="false">
      <filter id="crt-glow" x="-20%" y="-40%" width="140%" height="180%">
        <feGaussianBlur stdDeviation="4" />
      </filter>
    </svg>
  )
}

function AppShell() {

  const { featureId, path, params, navigate } = useHashRoute()

  const feature = useMemo<FeatureManifest>(
    () => features.find((candidate) => candidate.id === featureId) ?? features[0],
    [featureId],
  )
  const route = useMemo(
    () => feature.routes.find((candidate) => candidate.path === path) ?? feature.routes[0],
    [feature, path],
  )

  const Provider = feature.Provider ?? PassThrough
  const Screen = route.Component

  return (
    <div className="app">
      <CrtDefs />

      <header className="app-header">
        <div className="brand">
          <h1>{feature.title}</h1>
          <span className="subtitle">konsola DJ-a — Sabor Latino</span>
        </div>

        {features.length > 1 && (
          <nav className="features" aria-label="domeny">
            {features.map((candidate) => (
              <button
                key={candidate.id}
                className={candidate.id === feature.id ? 'feature active' : 'feature'}
                aria-current={candidate.id === feature.id ? 'true' : undefined}
                onClick={() => navigate(candidate.id, '')}
              >
                {candidate.title}
              </button>
            ))}
          </nav>
        )}

        <nav className="tabs" aria-label="widoki">
          {feature.nav.map((item) => (
            <button
              key={item.to}
              className={item.to === route.path ? 'tab active' : 'tab'}
              aria-current={item.to === route.path ? 'page' : undefined}
              // powrót na tę samą zakładkę zachowuje filtry z adresu
              onClick={() =>
                navigate(feature.id, item.to, item.to === route.path ? params : undefined)
              }
              data-testid={`tab-${item.to || 'start'}`}
            >
              {item.label}
            </button>
          ))}
        </nav>
      </header>

      <Provider>
        <main>
          <Suspense fallback={<p className="loading">Wczytywanie…</p>}>
            <Screen />
          </Suspense>
        </main>
      </Provider>
    </div>
  )
}

function PassThrough({ children }: { children: React.ReactNode }) {
  return <>{children}</>
}
