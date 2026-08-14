---
paths:
  - "services/**"
  - "libs/java/**"
---

# Reguły backendu

Java 21, Spring Boot 4, Maven. Każdy serwis stoi osobno; wszystko, co dzielone,
mieszka w `libs/java/`.

Sekcje oznaczone **[warunkowa]** obowiązują dopiero, gdy serwis faktycznie używa
danej technologii. Nie wciągaj ich do serwisu na zapas.

---

## Build i wersje

- Buduj wyłącznie Mavenem przez `./mvnw`. Nigdy Gradle, nigdy `mvn` z systemu.
- **Nie wpisuj `<version>` do POM-u modułu** — ani przy zależności, ani przy
  wtyczce. Wersja idzie do root `pom.xml`: `<properties>` + `<dependencyManagement>`
  + `<pluginManagement>`.
- Nie dodawaj `<parent>` na `spring-boot-starter-parent`. Spring wchodzi
  wyłącznie jako `spring-boot-dependencies` z `<scope>import</scope>`.
  Konsekwencja: `spring-boot-maven-plugin` nie dostaje celu `repackage` sam —
  deklaruj `<execution>` jawnie w module, który produkuje wykonywalny jar.
- Nową zależność dopisz najpierw do root POM-u, dopiero potem użyj w module.
- Konflikt wersji między modułami rozstrzygaj na wersję wyższą i zapisz w root
  POM, nigdy lokalnym nadpisaniem w module.
- Uruchom `./mvnw -T 1C verify` przed uznaniem zadania za skończone.
  Czerwony build to zadanie niezrobione, niezależnie od tego, ile kodu powstało.

## Pakiety i granice

- Każdy pakiet zaczyna się od `com.pgoogol`. Bez wyjątków.
- Serwis `services/<nazwa>-service` ma pakiet bazowy `com.pgoogol.<nazwa>`.
  Nigdy nie zakładaj pakietu bezpośrednio pod `com.pgoogol` — kolidowałby
  z kolejnym serwisem.
- **Dziel pakiety po funkcji, nigdy po warstwie.** Nie rób `controller/`,
  `service/`, `repository/` na szczycie serwisu:

  ```
  com.pgoogol.<nazwa>/
    <domena>/            ← np. catalog, library, playlist
      <Domena>Service.java
      <Domena>Repository.java
      dto/
      domain/
    api/                 ← kontrolery REST, DTO żądań/odpowiedzi, mappery
    common/              ← rzeczy dzielone wewnątrz serwisu (np. rate limiting)
    shared/exception/    ← hierarchia wyjątków serwisu
  ```

- Kod domenowy trzymaj w module, do którego należy. Nie rozsypuj go po `common/`.
- **Serwis nigdy nie zależy od innego serwisu.** Jedyne dozwolone zależności
  wewnątrz reaktora to moduły z `libs/java/`. Potrzeba sięgnięcia do cudzej
  domeny oznacza wywołanie po HTTP albo przeniesienie kodu do `libs/`.

## Kod dzielony = starter

Kod wspólny dla serwisów pisz jako starter Spring Boota, nie zwykły jar:

- klasa oznaczona `@AutoConfiguration`,
- wpis w `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`,
- konfiguracja przez `@ConfigurationProperties` z prefiksem, nie przez `@Value`,
- każdy bean pod `@ConditionalOnMissingBean`, żeby serwis mógł go nadpisać,
- funkcjonalność włącza się przez samo dodanie zależności — zero kroków ręcznych
  w serwisie, zero `@Import` po stronie konsumenta.

Zależności startera na Springa oznaczaj `<optional>true</optional>`.
Nie publikuj starterów na zewnątrz — zależność zostaje wewnątrz reaktora.

---

## Java 21

- Używaj **rekordów** dla DTO i obiektów wartości — nigdy gołych POJO
  z getterami i setterami jako nośników danych. Encje JPA są wyjątkiem:
  Hibernate wymaga klas mutowalnych.
- Używaj klas **sealed** dla typów wynikowych domeny (`Success | Failure | NotFound`).
- Używaj **pattern matchingu** (`instanceof`, `switch`) zamiast kaskad if-else.
- Używaj **wątków wirtualnych** (`Executors.newVirtualThreadPerTaskExecutor()`)
  dla blokującego I/O.
- **Nie używaj `var`** — zawsze deklaruj jawny typ zmiennej lokalnej.
- Używaj text blocków dla wielolinijkowego SQL-a, JSON-a i HTML-a.
- Jackson to **Jackson 3** (`tools.jackson.*`). Nigdy nie importuj
  `com.fasterxml.jackson.databind` ani `com.fasterxml.jackson.core` — adnotacje
  z `com.fasterxml.jackson.annotation` zostają, bo Jackson 3 zachował ten pakiet.

## Nazewnictwo

| Element | Konwencja | Przykład |
|---|---|---|
| Klasa | PascalCase, rzeczownik | `EnrichmentService`, `SpotifyClient` |
| Interfejs | PascalCase, rzeczownik lub przymiotnik | `Auditable`, `TrackCatalogRepository` |
| Metoda | camelCase, czasownik | `findById`, `resolveBpm` |
| Stała | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |
| Pakiet | lowercase, liczba pojedyncza | `com.pgoogol.music.catalog` |
| DTO | sufiks `Request` / `Response` | `IngestFileRequest`, `TrackResponse` |
| Wyjątek | sufiks `Exception` | `TrackNotFoundException` |
| Klasa konfiguracji | sufiks `Config` | `EnrichmentJobConfig` |
| Encja | goły rzeczownik, bez sufiksu | `TrackCatalog`, `Playlist` |

## Struktura kodu

- Metoda maksymalnie **30 linii** — dłuższą wydziel.
- Klasa maksymalnie **300 linii** — dłuższą rozbij po odpowiedzialnościach.
- Nie zwracaj `null` z metody publicznej — `Optional<T>` albo typowany wyjątek.
- Nie pisz statycznych klas narzędziowych — używaj beanów.
  Wyjątek: fixtures testowe (Object Mother).
- Preferuj `List.of()`, `Map.of()`, `Set.of()` dla kolekcji niemodyfikowalnych.
- Oznaczaj `@NonNull` / `@Nullable` (`org.springframework.lang`) na parametrach
  i typach zwracanych metod publicznych.

## Pętle i łańcuchy wywołań

- Unikaj gołych pętli `for` — zarówno indeksowanych, jak i `for (x : xs)`.
  Preferuj `Stream` / `forEach` / konstrukcje deklaratywne. Wyjątek: pętla jest
  realnie tańsza czasowo lub prostsza — wtedy napisz w komentarzu dlaczego.
- Unikaj łańcuchów `a().b().c()`. Wyjątek: Builder, `Optional`, `Stream`
  i API Mockito.

## Porównania i null

- `Objects.equals(a, b)` zamiast `a.equals(b)` i `a == b`.
- `Objects.isNull(x)` / `Objects.nonNull(x)` zamiast `x == null` / `x != null`.
- `Objects.requireNonNull(x, "komunikat")` w strażnikach na początku metody.
- `Objects.requireNonNullElse(x, domyślna)` zamiast ternarnych domyślek.
- `Objects.toString(x, "fallback")` zamiast `x != null ? x.toString() : "fallback"`.
- Wyjątek: `== null` wolno w `equals()` i na początku łańcucha null-checków.

```java
// ŹLE
if (track.getBpm() == null || track.getBpm().equals(other.getBpm())) { ... }
if (entry != null) return entry.getDjNotes();

// DOBRZE
if (Objects.isNull(track.getBpm()) || Objects.equals(track.getBpm(), other.getBpm())) { ... }
return Objects.toString(entry, "unknown");
```

## Kolekcje

Używaj Apache Commons `CollectionUtils` (`org.apache.commons.collections4`),
nie okrojonego wariantu ze Springa.

- `CollectionUtils.isEmpty(col)` / `isNotEmpty(col)` zamiast `col == null || col.isEmpty()`.
- `CollectionUtils.emptyIfNull(col)` zamiast ternarnych strażników null→pusta lista.
- `CollectionUtils.containsAny(col, kandydaci)` zamiast ręcznego `stream().anyMatch()`
  przy prostym sprawdzeniu przynależności.
- `CollectionUtils.intersection(a, b)` / `union(a, b)` / `subtract(a, b)` zamiast
  ręcznych operacji na zbiorach.

```java
// ŹLE
if (tracks == null || tracks.isEmpty()) { ... }
List<String> tags = entry.getCustomTags() != null ? entry.getCustomTags() : Collections.emptyList();
boolean maRole = role.stream().anyMatch(dozwolone::contains);

// DOBRZE
if (CollectionUtils.isEmpty(tracks)) { ... }
List<String> tags = CollectionUtils.emptyIfNull(entry.getCustomTags());
boolean maRole = CollectionUtils.containsAny(role, dozwolone);
```

## Formatowanie

- Wcięcie 4 spacje, nigdy tabulatory.
- Klamra otwierająca w tej samej linii.
- Zostaw jedną pustą linię po klamrze otwierającej ciało klasy lub metody.
- Nie formatuj ręcznie tego, co ma formatować narzędzie — Spotless.

---

## Spring

- Wstrzykuj przez konstruktor. Nigdy `@Autowired` na polu.
  Nie stawiaj `@Autowired` na jedynym konstruktorze — Spring wstrzyknie sam.
- `@RestController` trzymaj cienki: walidacja wejścia i delegacja do serwisu,
  zero logiki biznesowej.
- `@Transactional` na poziomie klasy zakładaj tylko wtedy, gdy WSZYSTKIE metody
  go potrzebują. W przeciwnym razie oznaczaj pojedyncze metody.
- Grupy powiązanych ustawień czytaj przez `@ConfigurationProperties`;
  `@Value` zostaw dla pojedynczych skalarów.

## Konfiguracja

- Konfiguruj przez `application.yml` + profile. Profil `local` opisuje pracę
  ze środowiskiem z `deploy/compose`.
- Wartości zależne od środowiska (adresy, klucze, modele, limity) trzymaj
  w konfiguracji, nigdy w kodzie. Dotyczy to zwłaszcza providera i modelu LLM
  oraz wersji promptu.
- Do konfiguracji odwołuj się przez `${ZMIENNA:domyślna}`, żeby aplikacja
  wstawała bez kompletu zmiennych tam, gdzie to sensowne.

## Klienci zewnętrznych API

- Izoluj każdego klienta w dedykowanej klasie (`SpotifyClient`, `DeezerClient`).
  Nigdy nie wołaj obcego API bezpośrednio z serwisu domenowego.
- Każdy klient ma limiter i retry z backoffem — bierz je ze wspólnego
  `common/ratelimit`, nie pisz własnych za każdym razem.
- Limity narzucone przez dostawcę egzekwuj twardo w kodzie klienta, nie licz
  na konfigurację. MusicBrainz: 1 req/s, bez wyjątków — retry też podlega limitowi.
- Nagłówek `User-Agent` z kontaktem ustawiaj tam, gdzie dostawca tego wymaga.

---

## Baza — Flyway

- Każda zmiana schematu idzie przez migrację Flyway. Nigdy ręcznie, nigdy `ddl-auto`.
- W konfiguracji ma być `spring.jpa.hibernate.ddl-auto=validate` — Flyway zarządza
  schematem, Hibernate go wyłącznie weryfikuje.
- Nazewnictwo: sekwencyjne `V{n}__opis.sql`, np. `V1__schemat_bazowy.sql`.
- Pisz migracje idempotentnie tam, gdzie się da (`IF NOT EXISTS`, `IF EXISTS`).
- **Nigdy nie modyfikuj zaaplikowanej migracji** — dopisz następną.
- Migracje muszą przechodzić w CI na Testcontainers, na czystej bazie,
  zanim zmiana zostanie scalona.

## Baza — JPA i Hibernate

- Ustawiaj `fetch = FetchType.LAZY` na asocjacjach. `@ManyToOne` jest domyślnie
  EAGER — ustawiaj jawnie.
- Przeciw N+1 używaj `@EntityGraph` albo `JOIN FETCH`.
- Włącz logowanie SQL w profilu deweloperskim, żeby wyłapywać N+1:
  ```yaml
  spring.jpa.show-sql: true
  spring.jpa.properties.hibernate.format_sql: true
  ```
- Do zapytań tylko-do-odczytu preferuj projekcje (interfejsy lub rekordy)
  zamiast pobierania pełnych encji.
- Zakładaj `@Version` na encjach modyfikowanych współbieżnie — blokowanie optymistyczne.

## Baza — zapytania

- Proste wyszukiwania (1–2 warunki) rób **derived queries** Spring Data.
- Złączenia i projekcje pisz w **JPQL przez `@Query`**.
- **Natywnego SQL-a** (`@Query(nativeQuery = true)`) używaj tylko do złożonych
  agregacji i cech specyficznych dla bazy (`pg_trgm`, `tsvector`).
- Masowe update'y i delete'y rób przez `@Modifying` + `@Transactional` —
  nigdy nie ładuj encji po to, żeby je skasować.
- Zakładaj indeksy na: klucze obce, kolumny w `WHERE` / `ORDER BY` / `JOIN`,
  ograniczenia unikalności.

## Baza — pula połączeń

Strojenie HikariCP pod obciążenie serwisu; domyślne wartości bywają złe:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10        # narzędzie lokalne; serwis pod ruchem: zacznij od 20
      minimum-idle: 2
      connection-timeout: 3000     # 3s — fail fast
      max-lifetime: 1800000        # 30min, mniej niż timeout połączenia po stronie bazy
      leak-detection-threshold: 5000
```

## Baza — paginacja

- Nigdy nie zwracaj nieograniczonej listy z API — paginuj przez `Pageable`.
- Domyślny rozmiar strony **20**, maksymalny **100**; limit egzekwuj w kontrolerze.
- Paginacja offsetowa wystarcza do kilku tysięcy rekordów. Przy zbiorach większych
  o rząd wielkości przejdź na keyset (kursor).

## Baza — cache

- Używaj abstrakcji Spring Cache (`@Cacheable`, `@CacheEvict`) — nie wpisuj
  wywołań cache'a wprost w serwis.
- Cache'uj dane często czytane i rzadko zmieniane.
- Zawsze ustawiaj TTL — nigdy nie cache'uj bezterminowo. Wyjątek: fakty
  deterministyczne cache'owane trwale w bazie.
- Klucz cache'a musi zawierać wszystkie parametry wpływające na wynik.
- Testuj unieważnianie cache'a jawnie — brak eviction to częsty błąd.

---

## Błędy — hierarchia

Trzymaj typowaną hierarchię wyjątków na domenę:

```
AppException (abstract, RuntimeException)
  ├── NotFoundException          → 404
  ├── ValidationException        → 400
  ├── ConflictException          → 409
  ├── UnauthorizedException      → 401   [warunkowa: serwis z auth]
  ├── ForbiddenException         → 403   [warunkowa: serwis z auth]
  └── ExternalServiceException   → 502
```

- Każdy wyjątek ma pole `errorCode` czytelne maszynowo (`TRACK_NOT_FOUND`),
  nie sam komunikat tekstowy.
- Nigdy nie rzucaj gołego `RuntimeException` ani `Exception` z kodu biznesowego.

## Błędy — obsługa i format

- Obsługę zbieraj w jednym `@RestControllerAdvice`. Nie rozsypuj
  `@ExceptionHandler` po kontrolerach.
- Odpowiedź błędu ma stały kształt:
  ```json
  {
    "errorCode": "TRACK_NOT_FOUND",
    "message": "Track with spotify id 4uLU6hMCjMI75M1A2tKUQC does not exist",
    "timestamp": "2026-07-03T12:00:00Z",
    "traceId": "abc-123"
  }
  ```
- `traceId` bierz z MDC / Micrometer tracing.
- Nigdy nie wypuszczaj stack trace'a, SQL-a ani nazw klas wewnętrznych do body.

## Błędy — transakcje

- Oznacz metodę `@Transactional(rollbackFor = Exception.class)`, jeśli ma się
  wycofać także przy wyjątku kontrolowanym.
- Domyślnie Spring wycofuje tylko na `RuntimeException` — bądź jawny, gdy trzeba inaczej.
- Nie połykaj wyjątków wewnątrz metod `@Transactional` — przerzuć dalej albo opakuj.

## Logowanie

- Loguj przez **SLF4J** z Logbackiem. Nigdy `System.out.println`.

  | Poziom | Do czego |
  |---|---|
  | `ERROR` | Awarie nie do odratowania, ryzyko utraty danych, padnięty serwis zewnętrzny |
  | `WARN` | Problemy odwracalne, retry, uruchomione fallbacki |
  | `INFO` | Zdarzenia biznesowe (import zakończony, job wystartował) |
  | `DEBUG` | Szczegóły żądań i odpowiedzi, wejście/wyjście metod — tylko dev |
  | `TRACE` | Surowy SQL, pełne payloady — nigdy na produkcji |

- Zawsze parametryzuj, nigdy nie konkatenuj:
  ```java
  // ŹLE
  log.info("Enriching track " + spotifyId + " with fields " + fields);
  // DOBRZE
  log.info("Enriching track {} with fields {}", spotifyId, fields);
  ```
- Wstaw `traceId` do MDC na początku każdego żądania (filtr albo interceptor).
- Nigdy nie loguj sekretów, tokenów, kluczy API ani danych osobowych.
- Nie loguj w ciasnej pętli — zaloguj podsumowanie po niej.

## Retry i circuit breaker

- Używaj **Resilience4j**. Nie używaj Spring Retry w nowym kodzie.
- Retry konfiguruj wyłącznie dla operacji **idempotentnych** (GET, PUT, DELETE,
  odczyty z klientów zewnętrznych).
- Nigdy nie ponawiaj na `4xx` — tylko na `5xx` i timeoutach.
  Wyjątek: `429 Too Many Requests` → honoruj nagłówek `Retry-After` zamiast
  zwykłego backoffu.
- Załóż circuit breaker na każde zewnętrzne wywołanie HTTP.

---

## Walidacja wejścia

- Stawiaj `@Valid` / `@Validated` na każdym parametrze kontrolera przyjmującym
  ciało żądania.
- Ograniczenia definiuj na DTO, nie warunkami w serwisie.
- Za błąd walidacji zwracaj `400 Bad Request` — nigdy `500`.
- Używaj `@Pattern`, `@Size`, `@NotBlank`. Nie pisz własnych walidatorów do rzeczy,
  które pokrywa Bean Validation.
- Traktuj wejście z zewnątrz jako niezaufane. Sanityzuj przed użyciem w SQL-u,
  ścieżkach plików i wywołaniach zewnętrznych — dotyczy też zawartości plików
  wgrywanych przez użytkownika (CSV).

## Sekrety

- Żadnych sekretów w repo: ani w kodzie, ani w `application*.yml`, ani w testach,
  ani w commitach. Wyłącznie zmienne środowiskowe i lokalny `.env`
  (jest w `.gitignore`); w repo tylko `.env.example` z pustymi wartościami.
- W `application.yml` odwołuj się przez `${ZMIENNA:}`, nigdy nie wpisuj wartości.
- Klucz albo token, który pojawił się jawnie w czacie, logu czy commicie,
  jest spalony — zgłoś potrzebę rotacji zamiast usuwać go po cichu.
- Tokeny OAuth (access i refresh) trzymaj server-side, w bazie.
  Nigdy nie zwracaj refresh tokena w odpowiedzi API do frontu.

## Actuator

- Wystawiaj wąsko: `health,info` (lokalnie ewentualnie `metrics`).
  Nigdy całego `/actuator`, nigdy `include: "*"`.
  ```yaml
  management:
    endpoints:
      web:
        exposure:
          include: health,info
    endpoint:
      health:
        show-details: when-authorized
  ```

## Spring Security **[warunkowa]**

Nie dotyczy serwisów bez auth — nie dodawaj auth do serwisu, który go nie ma
w wymaganiach. Gdy serwis wystawia uwierzytelnianie:

- Konfiguruj beanem **`SecurityFilterChain`**. Nigdy nie dziedzicz
  po `WebSecurityConfigurerAdapter`.
- Definiuj jawnie, które endpointy są publiczne; domyślnie odmawiaj wszystkiego:
  ```java
  http.authorizeHttpRequests(auth -> auth
      .requestMatchers("/api/public/**").permitAll()
      .anyRequest().authenticated()
  );
  ```
- Włącz `@EnableMethodSecurity` i stawiaj `@PreAuthorize` na metodach serwisu,
  nie tylko na kontrolerach. Używaj `hasRole('ADMIN')`, nie `hasAuthority('ROLE_ADMIN')`.
- Nie wyłączaj CSRF, chyba że budujesz bezstanowe REST API chronione JWT/OAuth2.
- Włącz nagłówki bezpieczeństwa: CSP, `frameOptions().deny()`, HSTS.
- Hasła haszuj **BCryptem** (siła ≥ 12) przez bean `PasswordEncoder`.
  Nigdy MD5, SHA1 ani gołego SHA256; nigdy `new BCryptPasswordEncoder()` w miejscu użycia.

### JWT / OAuth2 **[warunkowa]**

- Access token maksymalnie **15 minut**, refresh token maksymalnie **7 dni**.
- Waliduj zawsze: podpis, `exp`, `iss`, `aud`.
- Refresh tokeny trzymaj server-side (baza lub Redis), żeby dało się je unieważnić.
- Używaj `spring-security-oauth2-resource-server`. Nie parsuj JWT ręcznie.
- Nigdy nie wkładaj wrażliwych claimów do payloadu tokena.

## Messaging **[warunkowa]**

Dotyczy serwisu, który faktycznie używa Kafki lub RabbitMQ:

- Ustaw `enable.auto.commit=false` — commituj offsety ręcznie po udanym przetworzeniu.
- Wiadomości, które padły po wyczerpaniu retry, kieruj na **dead-letter topic**.
  Nigdy nie porzucaj ich po cichu.
- ID grupy konsumenckiej musi być unikalne na serwis i środowisko.
- Producent idempotentny: `enable.idempotence=true`, `acks=all`.
- Kontrakty wiadomości między serwisami trzymaj w schema registry (Avro/Protobuf).
  Nigdy surowy JSON bez schematu.

## Reactive / WebFlux **[warunkowa]**

Dotyczy serwisu zbudowanego na WebFluksie. Nie mieszaj tego do serwisu na MVC:

- `.onErrorMap()` do zamiany wyjątków niskopoziomowych na domenowe.
- `.onErrorResume()` do logiki zapasowej.
- Nigdy nie blokuj wewnątrz łańcucha reaktywnego — od wywołań blokujących jest
  `Mono.fromCallable(...)` z `.subscribeOn(Schedulers.boundedElastic())`.

---

## Kontrakt API

- `contracts/openapi/` jest źródłem prawdy i jest pisany ręcznie.
  Kontrakt zmieniaj przed implementacją, nie po.
- Test kontraktowy w serwisie porównuje specyfikację wygenerowaną z kodu
  z plikiem w `contracts/openapi/` i wywala build przy rozjeździe.
  Nie „naprawiaj" go przez podmianę pliku kontraktu pod kod — zdecyduj,
  która strona jest błędna.

---

## Testy — narzędzia

| Rodzaj | Narzędzia |
|---|---|
| Jednostkowe | JUnit 5 + Mockito + AssertJ |
| Integracyjne | `@SpringBootTest` + Testcontainers |
| Warstwa web | `@WebMvcTest` (MVC) / `@WebFluxTest` (WebFlux) |
| Repozytoria | `@DataJpaTest` + Testcontainers — **nigdy H2** |
| Klienci zewnętrzni | WireMock na nagranych odpowiedziach |
| Kontrakty | test porównujący z `contracts/openapi/` |

Żaden test nie wychodzi do sieci.

## Testy — nazwy i struktura

Nazwa: `[metoda]_[warunek]_[oczekiwany wynik]`.

```
ingestFile_whenTrackAlreadyExists_reportsAlreadyExisted
findById_whenTrackExists_returnsTrackResponse
```

Każde ciało testu dziel na sekcje komentarzami `// given`, `// when`, `// then`:

```java
@Test
void resolveBpm_whenAllSourcesEmpty_leavesBpmForAi() {

    // given
    TrackCatalog track = TrackCatalogFixtures.skeletonTrack("sp-1");

    // when
    BpmResolution resolution = resolver.resolve(track);

    // then
    assertThat(resolution.source()).isNull();
}
```

## Testy jednostkowe

- Jeden `@Test` = jedno pojęcie asercji. Powiązane pola grupuj `assertAll`.
- Używaj `@ExtendWith(MockitoExtension.class)` — nigdy `@SpringBootTest`
  do czystego testu jednostkowego.
- Mockuj wyłącznie bezpośrednie zależności, nigdy przechodnie.
- Weryfikuj `ArgumentCaptor`-em, co poszło do mocka — nie tylko to, że został wywołany.
- Nigdy nie testuj metod prywatnych wprost — testuj zachowanie przez API publiczne.
- Nie używaj `Thread.sleep()` — od asercji asynchronicznych jest `Awaitility`.

## Testy integracyjne

- `@Testcontainers` + realny obraz bazy (`postgres:16-alpine` — ta sama wersja
  co w `deploy/compose`).
- Współdziel jeden kontener w całej suicie: `@ServiceConnection` we wspólnej
  `TestcontainersConfiguration` albo `@Container` + pole `static`.
- Większe fixtures ładuj `@Sql("/test-data/….sql")`, nie insertami wklejonymi
  do metody testowej.
- Resetuj stan między testami: `@Transactional` (rollback) albo
  `@Sql(executionPhase = AFTER_TEST_METHOD)`.
- Testuj pełny stos HTTP przez `MockMvc` / `WebTestClient` — nie przez
  bezpośrednie wołanie metod serwisu.

## Testy — fixtures

Buduj wzorcem Builder albo Object Mother. Nigdy nie powtarzaj
`new TrackCatalog(...)` z kilkunastoma argumentami po testach.

```java
public final class TrackCatalogFixtures {

    public static TrackCatalog enrichedTrack(String spotifyId) {
        // pełny rekord z sensownymi wartościami domenowymi
    }
}
```

## Testy — pokrycie

- Warstwa serwisów: **≥ 80%** pokrycia linii.
- Ścieżki krytyczne (import danych, zapisy wzbogacania, mutacje biblioteki,
  płatności, auth): **100%** pokrycia gałęzi.
- Nie goń za procentem — nieprzetestowany przypadek brzegowy waży więcej
  niż liczba w raporcie.
- Każda publiczna metoda serwisu ma mieć test jednostkowy.
- Nowa logika = nowe testy w tej samej zmianie.

## Czego NIE testować

- Wnętrza Springa (auto-konfiguracja, wiązanie beanów).
- Prostych getterów i setterów na encjach, o ile nie zawierają logiki.
- Trywialnych jednolinijkowców oczywistych z kodu.

---

## Zasady pracy

- Pytaj przed każdą operacją destrukcyjną: `DROP TABLE`, kasowanie danych,
  `git push --force`, czyszczenie wolumenów z danymi.
- Nie twórz README ani ADR-ów bez wyraźnej prośby.
- Komunikaty commitów po polsku, w trybie rozkazującym.

## Czego pilnują narzędzia

| Reguła | Narzędzie |
|---|---|
| wersje wtyczek, Java 21, Maven ≥ 3.9.9 | `maven-enforcer-plugin` w `verify` |
| brak `<version>` w modułach | krok CI skanujący POM-y modułów |
| prefiks `com.pgoogol`, brak zależności serwis→serwis | ArchUnit (`ArchitectureTest`) |
| formatowanie, wcięcia, klamry | Spotless (`./mvnw spotless:check`) |
| schemat zgodny z migracjami | `ddl-auto: validate` + Flyway na starcie |
| kontrakt zgodny z kodem | test kontraktowy w `verify` |
| testy jednostkowe / integracyjne | Surefire / Failsafe w `verify` |
| pokrycie warstwy serwisów | JaCoCo w `verify` |
| brak sekretów w commicie | `gitleaks` w CI |
