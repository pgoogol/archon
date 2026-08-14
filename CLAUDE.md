# CLAUDE.md — monorepo archon

Wiele niezależnych domen: serwisy Spring Boot w `services/`, jeden wielodomenowy
frontend w `apps/web`, kod dzielony w `libs/`.

## Struktura

| Katalog | Zawartość |
|---|---|
| `services/<nazwa>-service/` | serwis Spring Boot, pakiet `com.pgoogol.<nazwa>` |
| `apps/web/` | jedyny frontend — React + Vite, wszystkie domeny |
| `libs/java/` | startery Spring Boot dzielone między serwisami |
| `libs/ts/` | paczki TypeScript dzielone we froncie |
| `contracts/openapi/` | kontrakty API — źródło prawdy dla typów TS |
| `deploy/compose/` | środowisko lokalne, wyłącznie development |
| `e2e/` | testy Playwright przez całość |

## Komendy

| Cel | Komenda |
|---|---|
| Pełny build Javy | `./mvnw -T 1C verify` |
| Jeden moduł + zależności | `./mvnw -pl services/music-service -am verify` |
| Serwis lokalnie | `./mvnw -pl services/music-service spring-boot:run -Dspring-boot.run.profiles=local` |
| Front — build / testy / lint | `pnpm build` · `pnpm test` · `pnpm lint` |
| Front — tryb dev | `pnpm --filter web dev` |
| Środowisko lokalne | `docker compose -f deploy/compose/docker-compose.yml up -d` |
| Zatrzymanie środowiska | `docker compose -f deploy/compose/docker-compose.yml down` (z `-v` czyści dane) |
| E2E | `pnpm --filter e2e test` |

## Nienegocjowalne

- Każdy pakiet Javy zaczyna się od `com.pgoogol`. Bez wyjątków.
- Build Javy wyłącznie Mavenem, przez `./mvnw`. Nigdy Gradle.
- **Zero `<version>` w POM-ach modułów.** Wszystkie wersje zależności i wtyczek
  w root `pom.xml`.
- Root POM nie dziedziczy po `spring-boot-starter-parent` — Spring wchodzi
  wyłącznie jako importowany BOM.
- Kod dzielony między serwisami to starter z `@AutoConfiguration`, nie zwykły jar.
- Serwis nigdy nie zależy od innego serwisu — wyłącznie od `libs/`.
- Zmiany schematu bazy wyłącznie przez migracje Flyway. Nigdy `ddl-auto=update`.
- Żadnych sekretów w repo — tylko zmienne środowiskowe i `.env.example`
  z pustymi wartościami. Klucz, który wyciekł, jest spalony: zgłoś rotację.
- `contracts/openapi/` jest źródłem prawdy. Typy DTO we froncie wyłącznie
  generowane, nigdy pisane ręcznie.
- Dodanie domeny do frontu = nowy katalog w `src/features/` + jedna linia
  w `src/features/registry.ts`. Jeśli wymaga zmiany w `shell/` — zgłoś to
  zamiast obchodzić.
- Nie twórz README ani ADR-ów bez wyraźnej prośby.
- Komunikaty commitów po polsku, w trybie rozkazującym.

## Reguły szczegółowe

Ładują się automatycznie przy pracy nad odpowiednim katalogiem — indeksy:
[`.claude/rules/backend.md`](.claude/rules/backend.md) (`services/**`, `libs/java/**`),
[`.claude/rules/frontend.md`](.claude/rules/frontend.md) (`apps/web/**`, `libs/ts/**`).

Reguły pisane po angielsku. Wierna kopia po polsku: [`docs/rules-pl/`](docs/rules-pl/)
— zmiana reguły idzie do obu miejsc naraz.
