---
paths:
  - "apps/web/**"
  - "libs/ts/**"
---

# Reguły frontendu

`apps/web` to JEDNA aplikacja React obsługująca wiele niezależnych domen. Nigdy
nie dziel jej na osobne aplikacje, nigdy nie stawiaj drugiego frontu obok.

| Plik | Zakres |
|---|---|
| [frontend-architecture.md](frontend-architecture.md) | Warstwy, granice modułów, manifest domeny, rejestr |
| [frontend-codestyle.md](frontend-codestyle.md) | Nazwy plików, TypeScript, wygląd, formularze, wydajność |
| [frontend-data.md](frontend-data.md) | HTTP, stan serwerowy, generowane typy DTO |
| [frontend-testing.md](frontend-testing.md) | Vitest, Testing Library, MSW |

## Co wymusza którą regułę

| Reguła | Narzędzie |
|---|---|
| granice modułów | `eslint-plugin-boundaries` (`error`) |
| zakaz `any`, zakaz `@ts-ignore` | `@typescript-eslint` (`error`) |
| `strict`, brak błędów typów | `tsc --noEmit` w `pnpm build` |
| typy DTO zgodne z kontraktem | regeneracja + `git diff --exit-code` w CI |
| jednolite formatowanie | Prettier (`pnpm format:check`) |

To jest polska kopia reguł. Wersja obowiązująca, ładowana automatycznie, leży
w `.claude/rules/`. Zmieniasz regułę — zmieniasz w obu miejscach.
