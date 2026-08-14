---
paths:
  - "apps/web/**"
  - "libs/ts/**"
---

# Styl kodu frontendu

## Nazwy plików

| Rodzaj | Konwencja | Przykład |
|---|---|---|
| Komponent | PascalCase.tsx | `LibraryTable.tsx` |
| Trasa | PascalCase + `Route.tsx` | `LibraryRoute.tsx` |
| Hook | `use` + camelCase.ts | `useDebouncedParam.ts` |
| Moduł nie-komponentowy | camelCase.ts | `setPlanner.ts` |
| Test | `<nazwa>.test.ts(x)` obok kodu | `setPlanner.test.ts` |
| Katalog | lowercase, kebab-case przy wielu słowach | `features/music/`, `shared/ui-kit/` |

Trzymaj test obok pliku, który testuje, nigdy w osobnym drzewie `__tests__`.

## TypeScript

- `strict: true` w każdym `tsconfig.json`. Nigdy nie wyłączaj flagi ze `strict`.
- Zakaz `any` — `@typescript-eslint/no-explicit-any: error`. Gdy typ jest naprawdę
  nieznany, użyj `unknown` i zawęź go.
- Nigdy nie używaj `as` do uciszenia błędu typu. `as const` i zawężanie
  po walidacji są w porządku.
- Zakaz `@ts-ignore`; `@ts-expect-error` tylko z komentarzem wyjaśniającym dlaczego.
- Preferuj `type` dla unii i `interface` dla kształtów obiektów, które są rozszerzane.

## Wygląd

- Domena nigdy nie definiuje własnych kolorów, odstępów, promieni ani cieni. Bierz
  je z tokenów w `shared/`. Wartość zapisana wprost w domenie (`#3b82f6`,
  `padding: 13px`) to błąd.
- Nowy token dodawaj do `shared/`, nigdy do domeny.
- Bierz ikony i prymitywy UI z ui-kitu. Nigdy nie duplikuj przycisku w domenie.

## Formularze

- Buduj na `react-hook-form` ze schematem `zod` spiętym przez resolver.
- Trzymaj schemat obok formularza, wewnątrz domeny. Waliduj na schemacie, nigdy
  warunkami rozsianymi po `onChange`.
- Wyprowadzaj typ wartości formularza ze schematu (`z.infer`) — nigdy nie pisz go
  drugi raz.
- Bierz komunikaty błędów z i18n w `shared/`, nigdy nie wpisuj ich wprost
  w komponencie.
- Nigdy nie blokuj przycisku submit samym `isValid` — obsłuż też `isSubmitting`.

## Wydajność

- Dziel bundle po domenach — `lazy()` na trasach robi to automatycznie.
- Dodawaj `memo`, `useMemo` i `useCallback` po zmierzeniu problemu, nigdy
  prewencyjnie.
- Nigdy nie definiuj komponentu wewnątrz renderu rodzica — remontuje się przy
  każdym renderze.
- Wirtualizuj listy powyżej mniej więcej 200 wierszy zamiast renderować je w całości.
- Nigdy nie importuj całej biblioteki po jeden symbol (`import _ from 'lodash'`).
