---
paths:
  - "services/**"
  - "libs/java/**"
---

# Reguły backendu

Java 21, Spring Boot 4, Maven. Każdy serwis stoi osobno; wszystko, co dzielone,
mieszka w `libs/java/`.

## Build i wersje

- Buduj wyłącznie Mavenem przez `./mvnw`. Nigdy Gradle, nigdy `mvn` z systemu.
- **Nie wpisuj `<version>` do POM-u modułu** — ani przy zależności, ani przy
  wtyczce. Wersja idzie do root `pom.xml`: `<properties>` + `<dependencyManagement>`
  + `<pluginManagement>`. Wymusza to `maven-enforcer-plugin`
  (`requirePluginVersions`) i osobny krok CI, który szuka `<version>` w POM-ach
  modułów i wywala build.
- Nie dodawaj `<parent>` na `spring-boot-starter-parent`. Spring wchodzi
  wyłącznie jako `spring-boot-dependencies` z `<scope>import</scope>`.
  Konsekwencja: `spring-boot-maven-plugin` nie dostaje celu `repackage` sam —
  deklaruj `<execution>` jawnie w module, który produkuje wykonywalny jar.
- Nowa zależność = wpis w root POM najpierw, potem użycie w module.
- Konflikt wersji między modułami rozstrzygaj na wersję wyższą i zapisz to
  w root POM, nigdy lokalnym nadpisaniem w module.

## Pakiety i granice

- Każdy pakiet zaczyna się od `com.pgoogol`. Bez wyjątków.
- Serwis `services/<nazwa>-service` ma pakiet bazowy `com.pgoogol.<nazwa>`,
  a jego moduły są podpakietami: `com.pgoogol.<nazwa>.catalog` itd.
  Nigdy nie zakładaj pakietu bezpośrednio pod `com.pgoogol` — kolidowałby
  z kolejnym serwisem.
- **Serwis nigdy nie zależy od innego serwisu.** Jedyne dozwolone zależności
  wewnątrz reaktora to moduły z `libs/java/`. Potrzeba sięgnięcia do cudzej
  domeny oznacza wywołanie po HTTP albo przeniesienie kodu do `libs/`.
- Pilnuje tego test ArchUnit w każdym serwisie (`ArchitectureTest`) — prefiks
  pakietu i zakaz importów z pakietów innych serwisów.

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

## Java 21

- Używaj **rekordów** dla DTO i obiektów wartości. Encje JPA są wyjątkiem —
  Hibernate wymaga klas mutowalnych.
- Używaj klas **sealed** dla typów wynikowych domeny.
- Używaj **pattern matchingu** (`instanceof`, `switch`) zamiast kaskad if-else.
- Używaj **wątków wirtualnych** dla blokującego I/O.
- **Nie używaj `var`** — zawsze deklaruj jawny typ zmiennej lokalnej.
- Używaj text blocków dla wielolinijkowego SQL-a i JSON-a.
- Jackson to **Jackson 3** (`tools.jackson.*`). Nigdy nie importuj
  `com.fasterxml.jackson.databind` — adnotacje z `com.fasterxml.jackson.annotation`
  zostają, bo Jackson 3 zachował ten pakiet.

## Nazewnictwo

| Element | Konwencja | Przykład |
|---|---|---|
| Klasa | PascalCase, rzeczownik | `EnrichmentService` |
| Metoda | camelCase, czasownik | `findById`, `resolveBpm` |
| Stała | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |
| Pakiet | lowercase, liczba pojedyncza | `com.pgoogol.music.catalog` |
| DTO | sufiks `Request` / `Response` | `TrackResponse` |
| Wyjątek | sufiks `Exception` | `TrackNotFoundException` |
| Klasa konfiguracji | sufiks `Config` | `EnrichmentJobConfig` |
| Encja | goły rzeczownik, bez sufiksu | `TrackCatalog` |

## Struktura kodu

- Metoda maksymalnie **30 linii**, klasa maksymalnie **300 linii**.
- Nie zwracaj `null` z metody publicznej — `Optional<T>` albo typowany wyjątek.
- Nie pisz statycznych klas narzędziowych — używaj beanów.
  Wyjątek: fixtures testowe (Object Mother).
- Preferuj `List.of()`, `Map.of()`, `Set.of()`.
- Wstrzykuj przez konstruktor. Nigdy `@Autowired` na polu; nie stawiaj
  `@Autowired` na jedynym konstruktorze — Spring wstrzyknie sam.
- `@RestController` trzymaj cienki: walidacja wejścia i delegacja do serwisu,
  zero logiki biznesowej.
- Grupy powiązanych ustawień czytaj przez `@ConfigurationProperties`;
  `@Value` zostaw dla pojedynczych skalarów.
- Unikaj gołych pętli `for` — preferuj `Stream`/`forEach`, chyba że pętla jest
  realnie tańsza (wtedy napisz w komentarzu dlaczego).

## Null i kolekcje

- `Objects.equals(a, b)` zamiast `a.equals(b)`; `Objects.isNull/nonNull`
  zamiast `== null` / `!= null`; `Objects.requireNonNull` w strażnikach;
  `Objects.requireNonNullElse` zamiast ternarnych domyślek.
  Wyjątek: `== null` wolno w `equals()` i na początku łańcucha null-checków.
- `CollectionUtils.isEmpty/isNotEmpty/emptyIfNull` (Apache Commons
  `org.apache.commons.collections4`) zamiast ręcznych sprawdzeń `null || isEmpty`.

## Baza

- Zmiana schematu wyłącznie przez migrację Flyway `V<nr>__opis.sql`.
  Nigdy `ddl-auto=update` — w konfiguracji ma być `validate`.
- Nie modyfikuj zaaplikowanej migracji. Dopisz następną.
- `fetch = FetchType.LAZY` na `@OneToMany` i `@ManyToMany`. Nigdy EAGER.
- Przeciw N+1 używaj `@EntityGraph` albo `JOIN FETCH`.
- Do odczytu preferuj projekcje (interfejsy lub rekordy) zamiast pełnych encji.
- Zawsze zakładaj indeksy na klucze obce i kolumny w `WHERE`/`ORDER BY`/`JOIN`.
- Nigdy nie zwracaj nieograniczonej listy z API — paginuj przez `Pageable`,
  domyślnie 20, maksymalnie 100, limit egzekwuj w kontrolerze.

## Błędy i logowanie

- Trzymaj typowaną hierarchię wyjątków na domenę, z polem `errorCode`
  czytelnym maszynowo. Nigdy nie rzucaj gołego `RuntimeException`.
- Obsługę zbieraj w jednym `@RestControllerAdvice`, nie rozsypuj
  `@ExceptionHandler` po kontrolerach.
- Odpowiedź błędu ma stały kształt: `errorCode`, `message`, `timestamp`, `traceId`.
  Nigdy nie wypuszczaj stack trace'a, SQL-a ani nazw klas do body.
- Loguj przez SLF4J, zawsze parametryzowanie (`log.info("... {}", id)`),
  nigdy konkatenacją i nigdy `System.out`.
- Nigdy nie loguj sekretów, tokenów ani danych osobowych.
- Nie loguj w ciasnej pętli — zaloguj podsumowanie po niej.
- Retry tylko dla operacji idempotentnych i tylko na `5xx`/timeout, nigdy na `4xx`.

## Kontrakt API

- `contracts/openapi/` jest źródłem prawdy i jest pisany ręcznie.
  Kontrakt zmieniaj przed implementacją, nie po.
- Test kontraktowy w serwisie porównuje specyfikację wygenerowaną z kodu
  z plikiem w `contracts/openapi/` i wywala build przy rozjeździe.
  Nie „naprawiaj" go przez podmianę pliku kontraktu pod kod — zdecyduj,
  która strona jest błędna.

## Testy

- JUnit 5 + Mockito + AssertJ. Czyste testy jednostkowe pod
  `@ExtendWith(MockitoExtension.class)` — nigdy `@SpringBootTest`.
- Nazwa testu: `[metoda]_[warunek]_[oczekiwany wynik]`, np.
  `findById_whenTrackExists_returnsTrackResponse`.
- Każdy test dziel komentarzami `// given`, `// when`, `// then`.
- Testy repozytoriów i integracyjne na realnym Postgresie przez **Testcontainers**.
  Nigdy H2 pod testy JPA.
- Kontener współdziel w suicie — `static` pole z `@Container`.
- Klientów zewnętrznych API testuj na WireMocku, na nagranych odpowiedziach.
  Żaden test nie wychodzi do sieci.
- Fixtures buduj wzorcem Object Mother, nie powtarzaj konstruktorów po testach.
- Nie używaj `Thread.sleep()` — od asynchronicznych asercji jest `Awaitility`.
- Nie testuj wnętrza Springa ani trywialnych getterów.
- Nowa logika = nowe testy w tej samej zmianie.

## Sekrety

- Żadnych sekretów w repo: ani w kodzie, ani w `application*.yml`, ani w testach.
  Wyłącznie zmienne środowiskowe; w repo tylko `.env.example` z pustymi wartościami.
- W `application.yml` odwołuj się przez `${ZMIENNA:}`, nigdy nie wpisuj wartości.
- Klucz, który pojawił się jawnie w czacie, logu albo commicie, jest spalony —
  zgłoś potrzebę rotacji zamiast go usuwać po cichu.
- Actuator wystawiaj wąsko (`health,info`), nigdy całego `*`.

## Czego pilnują narzędzia

| Reguła | Narzędzie |
|---|---|
| wersje wtyczek, Java 21, Maven ≥ 3.9.9 | `maven-enforcer-plugin` w `verify` |
| brak `<version>` w modułach | krok CI skanujący POM-y modułów |
| prefiks `com.pgoogol`, brak zależności serwis→serwis | ArchUnit (`ArchitectureTest`) |
| schemat zgodny z migracjami | `ddl-auto: validate` + Flyway na starcie |
| kontrakt zgodny z kodem | test kontraktowy w `verify` |
| testy jednostkowe / integracyjne | Surefire / Failsafe w `verify` |
| brak sekretów w commicie | `gitleaks` w CI |
