---
paths:
  - "apps/web/**"
  - "libs/ts/**"
---

# Architektura frontendu

## Warstwy

| Warstwa | Odpowiedzialność | Czego tam NIE ma |
|---|---|---|
| `src/shell/` | layout, nawigacja, routing, auth, error boundary | nazwy jakiejkolwiek domeny |
| `src/shared/` | ui-kit, tokeny, http client, hooki, i18n | logiki domenowej |
| `src/features/<domena>/` | routes, components, api, state, testy | wiedzy o innych domenach |
| `src/features/registry.ts` | lista manifestów | czegokolwiek poza importami i tablicą |

Nigdy nie wpisuj nazwy domeny do `shell/` ani `shared/`. Shell czyta rejestr
i manifesty, nic więcej. Jeśli dodanie domeny wymaga zmiany w `shell/`, przerwij
i zgłoś to jako błąd projektu shella — nie obchodź problemu lokalną poprawką.

## Granice modułów

Wymuszaj je `eslint-plugin-boundaries` na poziomie **`error`**, konfiguracja
w `apps/web/eslint.config.js`:

```js
settings: {
  'boundaries/elements': [
    { type: 'registry', pattern: 'src/features/registry.ts', mode: 'file' },
    { type: 'shell',    pattern: 'src/shell/**' },
    { type: 'shared',   pattern: 'src/shared/**' },
    { type: 'feature',  pattern: 'src/features/*', capture: ['domain'] },
  ],
},
rules: {
  'boundaries/element-types': ['error', {
    default: 'disallow',
    rules: [
      { from: 'shell',    allow: ['shared', 'registry'] },
      { from: 'shared',   allow: ['shared'] },
      { from: 'feature',  allow: ['shared', ['feature', { domain: '${from.domain}' }]] },
      { from: 'registry', allow: ['feature'] },
    ],
  }],
  'boundaries/no-private': 'error',
}
```

To daje cztery zakazy, każdy jako błąd builda: domeny nigdy nie importują się
nawzajem · `shared/` nigdy nie importuje z `features/` ani `shell/` · `shell/`
nigdy nie importuje z `features/` inaczej niż przez rejestr · nic nie sięga
do wnętrza cudzej domeny z pominięciem jej `index.ts`.

Naruszenie granicy to błąd, nigdy ostrzeżenie. Nigdy nie dodawaj `eslint-disable`
do importu przez granicę — to znaczy, że podział warstw jest zły.

## Manifest domeny

Każda domena eksportuje dokładnie jeden publiczny byt — manifest w `index.ts`.
Reszta katalogu jest prywatna.

```ts
// src/features/music/index.ts
import { lazy } from 'react'
import type { FeatureManifest } from '@/shell/featureManifest'
import { DiscIcon } from '@/shared/ui/icons'

export const musicFeature: FeatureManifest = {
  id: 'music',
  title: 'Muzyka',
  icon: DiscIcon,
  basePath: '/music',
  routes: [
    { path: '', Component: lazy(() => import('./routes/OverviewRoute')) },
    { path: 'library', Component: lazy(() => import('./routes/LibraryRoute')) },
  ],
  nav: [
    { label: 'Przegląd', to: '' },
    { label: 'Biblioteka', to: 'library' },
  ],
}
```

```ts
// src/features/registry.ts — jedyne miejsce znające wszystkie domeny
import { musicFeature } from './music'

export const features = [musicFeature] as const
```

- Ładuj każdą trasę przez `lazy()` — nigdy statycznym importem. Statycznie
  zaimportowana trasa wciąga całą domenę do głównego bundla i kasuje sens rejestru.
- Pisz ścieżki w `routes` i `nav` **względem `basePath`**, nigdy absolutnie.
  Domena, która zna swój absolutny prefiks, przestaje być przenośna.
- Dodanie domeny to jeden nowy katalog plus jedna linia w rejestrze. Nic więcej.
