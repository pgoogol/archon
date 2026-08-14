---
paths:
  - "apps/web/**"
  - "libs/ts/**"
---

# Reguły frontendu

`apps/web` to JEDNA aplikacja React obsługująca wiele niezależnych domen.
Nie dziel jej na osobne aplikacje, nie stawiaj drugiego frontu obok.

## Warstwy

| Warstwa | Odpowiedzialność | Czego tam NIE ma |
|---|---|---|
| `src/shell/` | layout, nawigacja, routing, auth, error boundary | nazwy jakiejkolwiek domeny |
| `src/shared/` | ui-kit, tokeny, http client, hooki, i18n | logiki domenowej |
| `src/features/<domena>/` | routes, components, api, state, testy | wiedzy o innych domenach |
| `src/features/registry.ts` | lista manifestów | czegokolwiek poza importami i tablicą |

Nigdy nie wpisuj nazwy domeny do `shell/` ani `shared/`. Shell czyta wyłącznie
rejestr i manifesty. Jeśli dodanie domeny wymaga zmiany w `shell/` — przerwij
i zgłoś to jako błąd projektu shella, nie obchodź problemu lokalną poprawką.

## Granice modułów

Wymuszaj je `eslint-plugin-boundaries` na poziomie **`error`**. Konfiguracja
w `apps/web/eslint.config.js`:

```js
settings: {
  'boundaries/elements': [
    { type: 'registry', pattern: 'src/features/registry.ts', mode: 'file' },
    { type: 'shell',    pattern: 'src/shell/**' },
    { type: 'shared',   pattern: 'src/shared/**' },
    { type: 'feature',  pattern: 'src/features/*', capture: ['domena'] },
  ],
},
rules: {
  'boundaries/element-types': ['error', {
    default: 'disallow',
    rules: [
      { from: 'shell',    allow: ['shared', 'registry'] },
      { from: 'shared',   allow: ['shared'] },
      { from: 'feature',  allow: ['shared', ['feature', { domena: '${from.domena}' }]] },
      { from: 'registry', allow: ['feature'] },
    ],
  }],
  'boundaries/no-private': 'error',
}
```

To daje cztery zakazy, każdy jako błąd builda:
`features/*` nie importują się nawzajem · `shared/` nie importuje z `features/`
ani z `shell/` · `shell/` nie importuje z `features/` inaczej niż przez rejestr ·
nic nie sięga do wnętrza cudzej domeny z pominięciem jej `index.ts`.

Naruszenie granicy to błąd, nigdy ostrzeżenie. Nie dodawaj `eslint-disable`
do importu przez granicę — to sygnał, że podział warstw jest zły.

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

Ładuj każdą trasę przez `lazy()` — nigdy statycznym importem. Statyczny import
trasy wciąga całą domenę do głównego bundla i kasuje sens rejestru.

Ścieżki w `routes` i `nav` podawaj **względem `basePath`**, nigdy absolutnie.
Domena, która zna swój absolutny prefiks, przestaje być przenośna.

## Nazwy plików

| Rodzaj | Konwencja | Przykład |
|---|---|---|
| Komponent | PascalCase.tsx | `LibraryTable.tsx` |
| Trasa | PascalCase + `Route.tsx` | `LibraryRoute.tsx` |
| Hook | `use` + camelCase.ts | `useDebouncedParam.ts` |
| Moduł nie-komponentowy | camelCase.ts | `setPlanner.ts` |
| Test | `<nazwa>.test.ts(x)` obok kodu | `setPlanner.test.ts` |
| Katalog | lowercase, kebab-case przy wielu słowach | `features/music/`, `shared/ui-kit/` |

Trzymaj test obok testowanego pliku, nigdy w osobnym drzewie `__tests__`.

## Dane i stan

- Zero `fetch` w komponentach. Cały HTTP idzie przez klienta z `shared/http`
  albo z `libs/ts/api-client`.
- Stan serwerowy trzymaj w **TanStack Query** — nigdy w `useState` + `useEffect`.
  Klucze zapytań buduj z wszystkich parametrów wpływających na wynik.
- Stan lokalny UI trzymaj w komponencie. Nie wynoś go do globalnego store'a
  „na przyszłość".
- Typy DTO wyłącznie generowane z `contracts/openapi/`. Nigdy nie pisz interfejsu
  odpowiedzi API ręcznie — wygenerowany typ jest jedyną prawdą.
  Wymusza to `pnpm --filter @archon/api-client generate` + diff w CI.

## TypeScript

- `strict: true` w każdym `tsconfig.json`. Nie wyłączaj żadnej flagi ze `strict`.
- Zakaz `any` — `@typescript-eslint/no-explicit-any: error`.
  Gdy typ jest naprawdę nieznany, użyj `unknown` i zawęź go.
- Zakaz `as` do obejścia błędu typu. `as const` i zawężanie po walidacji są w porządku.
- Zakaz `@ts-ignore`; `@ts-expect-error` tylko z komentarzem wyjaśniającym.

## Wygląd

- Domena nie definiuje własnych kolorów, odstępów, promieni ani cieni.
  Bierz je z tokenów w `shared/`. Wartość zapisana wprost w domenie
  (`#3b82f6`, `padding: 13px`) to błąd.
- Nowy token dodawaj do `shared/`, nie do domeny.
- Ikony i prymitywy UI bierz z ui-kitu. Nie duplikuj przycisku w domenie.

## Formularze

- Buduj na `react-hook-form`, schemat walidacji w `zod`, spięte resolverem.
- Trzymaj schemat obok formularza, w domenie. Waliduj na schemacie,
  nigdy warunkami rozsianymi po `onChange`.
- Wyprowadzaj typ wartości formularza ze schematu (`z.infer`), nie pisz go drugi raz.
- Komunikaty błędów bierz z i18n w `shared/`, nie zapisuj ich wprost w komponencie.
- Nigdy nie blokuj przycisku submit samym `isValid` — obsłuż też `isSubmitting`.

## Wydajność

- Dziel bundle po domenach — `lazy()` na trasach załatwia to automatycznie.
- `memo`, `useMemo` i `useCallback` dodawaj po zmierzeniu problemu, nie prewencyjnie.
- Nie twórz komponentów wewnątrz renderu rodzica — remontują się przy każdym renderze.
- Listy powyżej ~200 wierszy wirtualizuj zamiast renderować w całości.
- Nie importuj całej biblioteki po jeden symbol (`import _ from 'lodash'`).

## Testy

- Vitest + Testing Library, obok testowanego kodu.
- HTTP zawsze przez **MSW**. Nigdy nie mockuj `fetch` ręcznie ani nie stubuj
  modułu `api` — testuj to, co pojedzie po sieci.
- Odpytuj po roli i dostępnej nazwie (`getByRole`), nie po `data-testid`
  ani po klasie CSS.
- Zamiast `waitFor` z ręcznym warunkiem używaj `findBy*`.
- Test domeny nie importuje niczego z innej domeny — dotyczy go ta sama granica
  co kodu produkcyjnego.

## Czego pilnują narzędzia

| Reguła | Narzędzie |
|---|---|
| granice modułów | `eslint-plugin-boundaries` (`error`) |
| zakaz `any`, `@ts-ignore` | `@typescript-eslint` (`error`) |
| `strict`, brak błędów typów | `tsc --noEmit` w `pnpm build` |
| typy DTO zgodne z kontraktem | regeneracja + `git diff --exit-code` w CI |
| jednolity styl | Prettier (`pnpm format:check`) |
