---
paths:
  - "services/**"
  - "libs/java/**"
---

# Kontrakt API

- `contracts/openapi/` jest źródłem prawdy i jest pisany ręcznie.
- Zmieniaj kontrakt przed implementacją, nie po.
- **Każda ścieżka zaczyna się od `/<serwis>/api/v<n>`** — `/music/api/v1/library`,
  `/finance/api/v1/accounts`. Nazwa serwisu na początku daje proxy prefiks, który
  nie zawiera się w prefiksie sąsiada: dołożenie serwisu to jedna reguła, a nie
  przegląd kolejności pozostałych. Wersja jest częścią ścieżki od pierwszego
  endpointu, nie dokładaną wtedy, gdy zajdzie potrzeba zmiany łamiącej zgodność —
  wprowadzenie jej później wymaga ruszenia każdego klienta naraz.
- Podniesienie wersji jest wyłącznie dla zmiany łamiącej zgodność: usunięte pole,
  zawężony typ, zmienione znaczenie odpowiedzi. Nowe pole opcjonalne i nowy
  endpoint mieszczą się w wersji, która już jest.
- `/actuator` i `/v3/api-docs` zostają poza tym prefiksem — to nie jest API domeny.
- Test kontraktowy w serwisie porównuje specyfikację wygenerowaną z kodu z plikiem
  w `contracts/openapi/` i wywala build przy rozjeździe.
- Nigdy nie „naprawiaj" tego testu przez nadpisanie pliku kontraktu pod kod —
  zdecyduj, która strona jest błędna.
- Typy DTO frontu są generowane z kontraktu i nigdy nie są pisane ręcznie;
  patrz [frontend-data.md](frontend-data.md).
