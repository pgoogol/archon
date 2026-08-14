---
paths:
  - "apps/web/**"
  - "libs/ts/**"
---

# Testy frontendu

- Vitest + Testing Library, obok testowanego kodu.
- Kieruj HTTP zawsze przez **MSW**. Nigdy nie mockuj `fetch` ręcznie i nigdy nie
  stubuj modułu `api` — testuj to, co faktycznie pojedzie po sieci.
- Odpytuj po roli i dostępnej nazwie (`getByRole`), nigdy po `data-testid` ani
  po klasie CSS.
- Używaj `findBy*` zamiast `waitFor` z ręcznie napisanym warunkiem.
- Używaj `userEvent`, nie `fireEvent`, do interakcji użytkownika.
- Testy domeny nigdy nie importują z innej domeny — tę samą granicę co kod
  produkcyjny stosuj do kodu testowego.
- Testuj zachowanie przez wyrenderowany UI, nie przez wołanie wnętrza hooka.
