---
paths:
  - "apps/web/**"
  - "libs/ts/**"
---

# Architektura frontendu

## Warstwy

| Warstwa | Odpowiedzialność | Czego tam NIE ma |
|---|---|---|
| `src/shell/` | layout, nawigacja, host routingu, auth, error boundary | nazwy jakiejkolwiek domeny |
| `src/shared/` | ui-kit, tokeny, http client, hooki, i18n, kontrakt manifestu | logiki domenowej |
| `src/features/<domena>/` | routes, components, api, state, testy | wiedzy o innych domenach |
| `src/registry/` | lista manifestów | czegokolwiek poza importami i tablicą |

Nigdy nie wpisuj nazwy domeny do `shell/` ani `shared/`. Shell czyta rejestr
i manifesty, nic więcej. Jeśli dodanie domeny wymaga zmiany w `shell/`, przerwij
i zgłoś to jako błąd projektu shella — nie obchodź problemu lokalną poprawką.

Rejestr siedzi na najwyższym poziomie, nie w `features/`: to jedyny moduł, który
ma prawo znać wszystkie domeny, więc nie może sam wyglądać jak domena.

`FeatureManifest` mieszka w `shared/`, nie w `shell/`. Zależą od niego i shell,
i każda domena, a `shared/` jest warstwą, od której wolno zależeć wszystkim —
trzymanie go w `shell/` wymuszałoby krawędź `domena → shell`, której nic innego
nie potrzebuje.

## Granice modułów

Wymuszaj je `eslint-plugin-boundaries` na poziomie **`error`**, konfiguracja
w `apps/web/eslint.config.js`:

```js
const element = (type, captured) => ({ element: captured ? { type, captured } : { type } })

settings: {
  'boundaries/include': ['src/**/*.{ts,tsx}'],
  // bez resolvera alias @/ nie zostaje rozwiązany, żaden import nie jest
  // klasyfikowany i cała reguła po cichu przepuszcza wszystko
  'import/resolver': { typescript: { project: './tsconfig.json' } },
  'boundaries/elements': [
    { type: 'registry', pattern: 'src/registry/**' },
    { type: 'shell',    pattern: 'src/shell/**' },
    { type: 'shared',   pattern: 'src/shared/**' },
    { type: 'feature',  pattern: 'src/features/*', capture: ['domain'] },
    { type: 'root',     pattern: 'src/*.{ts,tsx}', partialMatch: false },
  ],
},
rules: {
  'boundaries/dependencies': ['error', {
    default: 'disallow',
    policies: [
      { from: [element('shell')],
        allow: [{ to: element('shared') }, { to: element('registry') }, { to: element('shell') }] },
      { from: [element('shared')],
        allow: [{ to: element('shared') }] },
      { from: [element('feature')],
        allow: [{ to: element('shared') },
                { to: element('feature', { domain: '{{from.domain}}' }) }] },
      { from: [element('registry')],
        allow: [{ to: element('feature') }, { to: element('shared') }] },
      { from: [element('root')],
        allow: [{ to: element('shell') }, { to: element('shared') }] },
    ],
  }],
}
```

To daje cztery zakazy, każdy jako błąd builda: domeny nigdy nie importują się
nawzajem · `shared/` nigdy nie importuje z `features/` ani `shell/` · `shell/`
nigdy nie importuje z `features/` inaczej niż przez rejestr · nic nie sięga
do wnętrza cudzej domeny z pominięciem jej `index.ts` (głęboka ścieżka i tak
klasyfikuje się jako ta domena, więc reguła ją łapie).

**Elementy dopasowują się jako katalogi.** `pattern` wskazujący pojedynczy plik
nie klasyfikuje tego pliku — każda warstwa musi być katalogiem i właśnie dlatego
rejestr oraz kontrakt manifestu leżą tam, gdzie leżą.

**Konfiguracja granic, która niczego nie rozwiązuje, niczego nie zgłasza.** Po jej
zmianie udowodnij, że nadal gryzie: dopisz import, który ma paść, uruchom lint,
usuń go. Reguła, której nigdy nie widziałeś czerwonej, jest dekoracją, nie egzekucją.

Naruszenie granicy to błąd, nigdy ostrzeżenie. Nigdy nie dodawaj `eslint-disable`
do importu przez granicę — to znaczy, że podział warstw jest zły.

## Manifest domeny

Każda domena eksportuje dokładnie jeden publiczny byt — manifest w `index.ts`.
Reszta katalogu jest prywatna.

```ts
// src/features/music/index.ts
import { lazy } from 'react'
import type { FeatureManifest } from '@/shared/featureManifest'
import { MusicWorkspaceProvider } from '@/features/music/state/MusicWorkspace'

export const musicFeature: FeatureManifest = {
  id: 'music',
  title: 'music-view',
  basePath: '/music',
  Provider: MusicWorkspaceProvider,
  routes: [
    { path: '', Component: lazy(() => import('./routes/LibraryRoute')) },
    { path: 'sets', Component: lazy(() => import('./routes/SetsRoute')) },
  ],
  nav: [
    { label: 'Biblioteka', to: '' },
    { label: 'Sety', to: 'sets' },
  ],
}
```

```ts
// src/registry/index.ts — jedyne miejsce znające wszystkie domeny
import { musicFeature } from '@/features/music'

export const features: FeatureManifest[] = [musicFeature]
```

- Ładuj każdą trasę przez `lazy()` — nigdy statycznym importem. Statycznie
  zaimportowana trasa wciąga całą domenę do głównego bundla i kasuje sens rejestru.
- Pisz ścieżki w `routes` i `nav` **względem `basePath`**, nigdy absolutnie.
  Domena, która zna swój absolutny prefiks, przestaje być przenośna.
- Stan dzielony między ekranami jednej domeny idzie do jej `Provider`, nigdy
  do powłoki. Powłoka renderuje providera, nie wiedząc, co jest w środku.
- Dodanie domeny to jeden nowy katalog plus jedna linia w rejestrze. Nic więcej.

## Routing

Adres niesie domenę i ekran: `#/<domena>/<ekran>?<filtry>`. Powłoka rozwiązuje
domenę z rejestru, a ekran z manifestu tej domeny; nieznana domena cofa się
do pierwszej pozycji rejestru.

Hook routingu mieszka w `shared/hooks/`, nie w `shell/` — domeny czytają i zapisują
przez niego własny stan filtrów, a domenie nie wolno importować z powłoki.
