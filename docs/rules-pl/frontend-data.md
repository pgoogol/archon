---
paths:
  - "apps/web/**"
  - "libs/ts/**"
---

# Dane i stan

## HTTP

- Zero `fetch` w komponentach. Cały HTTP idzie przez klienta z `shared/http` albo
  przez `libs/ts/api-client`.
- Nigdy nie buduj URL-a konkatenacją stringów w komponencie.

## Stan serwerowy

- Trzymaj stan serwerowy w **TanStack Query** — nigdy w `useState` + `useEffect`.
- Buduj klucze zapytań z każdego parametru wpływającego na wynik.
- Unieważniaj jawnie po mutacji. Nigdy nie odświeżaj danych przez remontowanie
  komponentu.

## Stan lokalny

- Trzymaj stan UI w komponencie. Nigdy nie wynoś go do globalnego store'a
  „na przyszłość".

## Typy DTO

- Typy DTO są generowane z `contracts/openapi/`. Nigdy nie pisz ręcznie interfejsu
  odpowiedzi API — wygenerowany typ jest jedyną prawdą.
- Regeneruj przez `pnpm --filter @archon/api-client generate`. CI regeneruje
  i wywala się na diffie, więc nieaktualny typ w repo psuje build.
- Kontrakt jest pisany ręcznie i zmienia się przed implementacją; patrz
  [backend-api.md](backend-api.md).
