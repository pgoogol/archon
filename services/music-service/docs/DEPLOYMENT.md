# M2.5 — wdrożenie (opcjonalne)

PLAN.md oznacza ten kamień jako opcjonalny i to nadal obowiązuje: **dla narzędzia
jednego DJ-a lokalny `docker compose` w zupełności wystarcza** — dane siedzą na
własnym dysku, nie ma kosztów hostingu ani powierzchni ataku (aplikacja nie ma
auth — D2/D14). Poniżej wariant „w chmurze", gdyby biblioteka miała być dostępna
z telefonu na imprezie.

## Wariant lokalny (domyślny)

Dwie aplikacje w kontenerach, jedną komendą (M5.3/D30):

```bash
docker compose --profile full up -d --build   # front :5173, API :8080, baza :5432
```

Albo w trybie pracy nad kodem — sama baza w Dockerze, reszta z konsoli:

```bash
docker compose up -d
set -a && source .env && set +a
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
cd frontend && npm run dev
```

Kopia zapasowa = zrzut bazy; jedyne, czego nie odtworzysz z niczego innego, to
`library_entry` (dane prywatne DJ-a) i playlisty:

```bash
docker exec music-view-postgres pg_dump -U musicview musicview > backup.sql
```

## Wariant hostowany (D12: Neon + Railway/Fly.io + Vercel)

1. **Baza — Neon.** Utwórz projekt Postgres 16, weź connection string.
   Flyway (V1–V4) sam założy schemat przy pierwszym starcie aplikacji.
   Rozszerzenie `pg_trgm` z migracji V1 jest na Neonie dostępne.
2. **Backend — Railway albo Fly.io.** Obraz z `./mvnw spring-boot:build-image`
   albo buildpack z repo. Zmienne środowiskowe: `SPRING_DATASOURCE_URL`,
   `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`,
   `SPOTIFY_CLIENT_ID`, `SPOTIFY_CLIENT_SECRET`, `SPOTIFY_REDIRECT_URI`
   (adres publiczny!), `LLM_PROVIDER`, `LLM_API_KEY`, `LLM_MODEL`, `MB_USER_AGENT`.
3. **Front — Vercel** (albo dowolny hosting statyków; obraz `frontend/Dockerfile`
   robi to samo nginksem). Katalog `frontend/`, build `npm run build`, katalog
   wyjściowy `dist`. Front woła względne `/music/api/v1`, więc dodaj rewrite na
   backend (`vercel.json` → `rewrites: [{ "source": "/music/api/v1/:path*",
   "destination": "https://<backend>/music/api/v1/:path*" }]`) — inaczej trzeba
   by wprowadzać CORS.
   Rozdział na dwie aplikacje (D30) jest właśnie po to, żeby ten wariant
   pozostał możliwy: front da się wystawić na statycznym hostingu niezależnie
   od backendu.
4. **Spotify:** dopisz publiczny Redirect URI w dashboardzie aplikacji;
   ten z kroku 2 musi być z nim identyczny znak w znak.

### Zanim wystawisz to publicznie

- **Aplikacja wymaga logowania** — każde żądanie do API niesie poświadczenia:
  token identyfikacyjny Google albo konto lokalne. Publiczna zostaje wyłącznie
  sonda `/actuator/health`. Proxy pytające o hasło nie jest już potrzebne.
- **Zanim wystawisz adres, zamknij konto domyślne.** Świeża instalacja wpuszcza
  na `admin`/`admin` — podmień `AUTH_LOCAL_PASSWORD_HASH` (hash BCrypt o sile 12,
  np. `htpasswd -nbBC 12 admin haslo`) albo ustaw `AUTH_LOCAL_ENABLED=false`
  i zostaw samo logowanie Google. Ta para jest pierwszą, jaką sprawdzają skanery.
- **Logowanie Google wymaga dwóch zmiennych:** `GOOGLE_CLIENT_ID` (identyfikator
  klienta OAuth typu „Aplikacja internetowa" z Google Cloud) oraz
  `AUTH_ALLOWED_EMAILS` z adresami, które mają wstęp. Bez listy adresów serwis
  nie wstanie — inaczej ważny token miałoby każde konto Google na świecie.
- **Nigdy nie ustawiaj `SPRING_PROFILES_ACTIVE=no-auth` na publicznym adresie.**
  Ten profil zdejmuje uwierzytelnianie i istnieje wyłącznie dla pracy lokalnej
  i testów E2E.
- Sekrety wyłącznie w zmiennych środowiskowych platformy, nigdy w repo (D14);
  klucz, który gdzieś wyciekł, traktuj jak spalony i zrotuj.
- Job wzbogacania jest restartowalny (D10), ale długi — platformy z usypianiem
  instancji potrafią go przerwać. Po restarcie dokończ przez
  `POST /music/api/v1/enrich/jobs/{id}/restart`.
