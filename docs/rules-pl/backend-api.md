---
paths:
  - "services/**"
  - "libs/java/**"
---

# Kontrakt API

- `contracts/openapi/` jest źródłem prawdy i jest pisany ręcznie.
- Zmieniaj kontrakt przed implementacją, nie po.
- Test kontraktowy w serwisie porównuje specyfikację wygenerowaną z kodu z plikiem
  w `contracts/openapi/` i wywala build przy rozjeździe.
- Nigdy nie „naprawiaj" tego testu przez nadpisanie pliku kontraktu pod kod —
  zdecyduj, która strona jest błędna.
- Typy DTO frontu są generowane z kontraktu i nigdy nie są pisane ręcznie;
  patrz [frontend-data.md](frontend-data.md).
