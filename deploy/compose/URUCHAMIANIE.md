# Uruchamianie środowiska lokalnego

Cztery komponenty — `music-service`, `finance-service`, `kitchen-service`, front —
i każdy z osobna działa albo w kontenerze, albo na hoście. Baza stoi zawsze
w kontenerze.

**Wyłącznie development.** Hasło bazy jest jawne i stałe, port 5432 wystawiony na
wszystkie interfejsy (baza jest widoczna z całej sieci lokalnej), aplikacje bez
replik i bez limitów zasobów. Nie podnoś tego zestawu w sieci, której nie ufasz.

Wszystkie komendy uruchamiaj z katalogu głównego repo.

## Skrót

| Chcę | Komenda |
|---|---|
| tylko bazę | `docker compose -f deploy/compose/docker-compose.yml up -d` |
| wszystko w kontenerach | `docker compose -f deploy/compose/docker-compose.yml --profile full up -d --build` |
| zgasić | `docker compose -f deploy/compose/docker-compose.yml --profile full down` (`down -v` czyści dane) |

## Profile

Wybierają, co wchodzi w kontenerze. Sumują się, więc kombinacje pisze się wprost:
`--profile music --profile web`.

| Profil | Kontenery |
|---|---|
| *(brak)* | sama baza |
| `music` | music-service |
| `finance` | finance-service |
| `kitchen` | kitchen-service |
| `services` | wszystkie serwisy backendowe |
| `web` | front |
| `full` | wszystko |

## Nakładki

Front w kontenerze woła serwisy po nazwie kontenera. Serwis, który akurat chodzi
na hoście, wskazuje się nakładką — jedna na serwis, składają się ze sobą:

| Plik | Efekt |
|---|---|
| `docker-compose.music-on-host.yml` | nginx frontu kieruje `/music/api/v1` na host:8080 |
| `docker-compose.finance-on-host.yml` | nginx frontu kieruje `/finance/api/v1` na host:8081 |
| `docker-compose.kitchen-on-host.yml` | nginx frontu kieruje `/kitchen/api/v1` na host:8082 |

Nakładka ma sens tylko przy froncie w kontenerze. Front z Vite proxuje na
`localhost:8080`, `localhost:8081` i `localhost:8082` niezależnie od tego, gdzie
stoją serwisy.

## Sekrety — `.env`

Plik `.env` leży w katalogu głównym repo (wzór: `.env.example`) i jest potrzebny
dwóm serwisom: music-service (Spotify, LLM, MusicBrainz) oraz kitchen-service
(provider LLM do czytania przepisów z tekstu i zdjęć). Reszta zestawu wstaje bez
niego, a i te dwa startują — bez kluczy działają wszystkie funkcje poza tymi,
które wołają zewnętrzne API.

**Nie trzeba nic dopisywać do komend.** Oba serwisy mają w compose `env_file`
z twardą ścieżką `../../.env`, więc plik z katalogu głównego wchodzi sam, przy
każdym wariancie. Brak pliku nie jest błędem (`required: false`) — serwis wstaje,
a wartości domyślne bierze z `application.yml`.

Uwaga na dwie rzeczy przy zmianach w compose:

- `environment` ma pierwszeństwo przed `env_file`. Dlatego są tam wyłącznie
  ustawienia bazy — wpisanie tam zmiennej z `.env` z pustą wartością domyślną
  skasowałoby to, co przyszło z pliku.
- Compose sam z siebie szuka `.env` obok pliku compose, czyli w `deploy/compose/`,
  a nie w katalogu, z którego uruchamiasz komendę. Stąd jawna ścieżka
  w `env_file`. Nie próbuj obejść tego przez `--project-directory .` —
  przestawia rozwiązywanie ścieżek względnych i psuje kontekst builda oraz
  montowanie skryptów init bazy.

**Na hoście też nic nie trzeba dopisywać.** Profil `local` music-service
importuje ten sam plik przez `spring.config.import`, więc `spring-boot:run`
i uruchomienie z IDE biorą sekrety stamtąd co kontener. Import jest opcjonalny
i obejmuje dwie ścieżki — z katalogu modułu i z katalogu głównego — bo tyle jest
sensownych katalogów roboczych. Zmienna ustawiona w środowisku procesu ma
pierwszeństwo przed wpisem z pliku.

**Pusta wartość w `.env` to nie to samo co brak wpisu.** `LLM_MODEL=` dociera do
aplikacji jako pusty łańcuch i przykrywa wartość domyślną z `application.yml`,
która wchodzi wyłącznie przy zmiennej nieustawionej. Zmienną, której nie
ustawiasz, zakomentuj. Wyjątkiem jest `LLM_PROVIDER` — pusty traktujemy tam jak
brak, bo `.env` skopiowany z `.env.example` inaczej wywracał start serwisu.

## Komponent na hoście

```bash
./mvnw -pl services/music-service spring-boot:run -Dspring-boot.run.profiles=local
```

```bash
./mvnw -pl services/finance-service spring-boot:run -Dspring-boot.run.profiles=local
```

```bash
pnpm --filter web dev
```

Profil `local` kieruje serwis na Postgresa z tego zestawu po `localhost:5432`.
Po zmianie w `libs/java/logging-starter` najpierw
`./mvnw -pl libs/java/logging-starter -am -DskipTests install`.

## Wszystkie kombinacje

Adresy niezależnie od wariantu: front `http://localhost:5173` (Vite tak samo),
music `http://localhost:8080`, finance `http://localhost:8081`, kitchen
`http://localhost:8082`, baza `5432`.

Kombinacje niżej opisują music i finance; `kitchen-service` wchodzi do każdej
z nich tak samo — profilem `kitchen` w kontenerze albo `./mvnw -pl
services/kitchen-service spring-boot:run -Dspring-boot.run.profiles=local`
na hoście, z nakładką `docker-compose.kitchen-on-host.yml`, gdy front stoi
w kontenerze.

Sekrety z `.env` wchodzą same tam, gdzie music-service jedzie w kontenerze —
patrz sekcja wyżej.

### 1. Wszystko na hoście

```bash
docker compose -f deploy/compose/docker-compose.yml up -d
```

Do tego oba `spring-boot:run` i `pnpm --filter web dev`.

### 2. Music w kontenerze, finance i front na hoście

```bash
docker compose -f deploy/compose/docker-compose.yml --profile music up -d --build
```

Do tego finance przez `spring-boot:run` i `pnpm --filter web dev`.

### 3. Finance w kontenerze, music i front na hoście

```bash
docker compose -f deploy/compose/docker-compose.yml --profile finance up -d --build
```

Do tego music przez `spring-boot:run` i `pnpm --filter web dev`.

### 4. Oba serwisy w kontenerach, front z Vite

```bash
docker compose -f deploy/compose/docker-compose.yml --profile services up -d --build
```

Do tego `pnpm --filter web dev`.

### 5. Front w kontenerze, oba serwisy na hoście

```bash
docker compose -f deploy/compose/docker-compose.yml -f deploy/compose/docker-compose.music-on-host.yml -f deploy/compose/docker-compose.finance-on-host.yml --profile web up -d --build
```

Do tego oba `spring-boot:run`.

### 6. Front i music w kontenerach, finance na hoście

```bash
docker compose -f deploy/compose/docker-compose.yml -f deploy/compose/docker-compose.finance-on-host.yml --profile music --profile web up -d --build
```

Do tego finance przez `spring-boot:run`.

### 7. Front i finance w kontenerach, music na hoście

```bash
docker compose -f deploy/compose/docker-compose.yml -f deploy/compose/docker-compose.music-on-host.yml --profile finance --profile web up -d --build
```

Do tego music przez `spring-boot:run`.

### 8. Wszystko w kontenerach

```bash
docker compose -f deploy/compose/docker-compose.yml --profile full up -d --build
```

## Dlaczego to działa tak, a nie inaczej

- **Postgres 16 — ta sama wersja co w testach integracyjnych.** Rozjazd major
  wersji między testami a środowiskiem lokalnym daje błędy, których nie widać w CI.
- **Bazy per serwis** (`music`, `finance`, `kitchen`) zakłada skrypt z `postgres/init` przy
  inicjalizacji pustego wolumenu. `POSTGRES_USER: postgres` to rola
  administracyjna — dane serwisów nie trafiają do bazy `postgres`.
- **Kontekst builda serwisów to katalog główny repo.** Moduł nie zbuduje się bez
  reaktora ani bez startera z `libs/java`.
- **Sekrety wyłącznie ze środowiska**, jednym `env_file` zamiast listy zmiennych
  przepisanej do compose. Nazwy i wartości domyślne stoją w `application.yml`
  serwisu — powtórka w compose potrafiłaby się z nimi rozjechać. Zestaw wstaje
  bez `.env` i mówi wprost, czego mu brakuje. Wzór: `.env.example`.
- **Front nigdy nie zna adresu backendu.** Woła adresy względne, a nginx rozdziela
  je po prefiksie ścieżki: `/music/api/v1` do music, `/finance/api/v1` do finance,
  `/kitchen/api/v1` do kitchen. Ten sam obraz działa lokalnie i na serwerze.
- **`required: false` przy `depends_on` frontu** jest sednem mieszania wariantów:
  serwis wyłączony profilem (bo chodzi na hoście) przestaje być warunkiem startu
  frontu, zamiast wywracać całe `up`. Gdy serwis wchodzi w kontenerze, front nadal
  czeka na jego zdrowie.
- **`host.docker.internal` z wpisem `host-gateway`** — na Docker Desktop rozwiązuje
  się z automatu, na Linuksie ten wpis jest jedynym sposobem, żeby kontener trafił
  na porty hosta. Przy wszystkim w kontenerach nic nie psuje.
- **Nazwa upstreamu musi być rozwiązywalna przy starcie kontenera frontu.** nginx
  podstawia zmienne adresów i wtedy rozwiązuje nazwę; sam serwis nie musi jeszcze
  słuchać — dostaniesz 502 na żądanie, nie martwy kontener.

## Uwagi

- **Ten sam serwis nie może stać naraz w kontenerze i na hoście** — oba biorą ten
  sam port. Docker odmówi startu i powie o tym wprost.
- **Front w kontenerze wymaga przebudowy obrazu po każdej zmianie w `apps/web`.**
  Do pracy nad samym frontem szybszy jest `pnpm --filter web dev`.
- **Spotify** wymaga zgodności redirect URI znak w znak i nie przyjmie adresu
  w sieci lokalnej po HTTP, więc konto łączy się raz z laptopa po loopbacku.
  Telefon w LAN korzysta z konta już połączonego — tokeny żyją server-side.
- **Baza dołożona do istniejącego wolumenu** nie powstanie ze skryptu init
  (wykonuje się tylko na pustym wolumenie). Trzeba ją założyć ręcznie:
  `docker compose -f deploy/compose/docker-compose.yml exec -T postgres psql -U postgres -c "create database finance"`.
