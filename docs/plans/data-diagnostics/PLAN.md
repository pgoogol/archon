# data-diagnostics: plan i stan prac

Plan biblioteki diagnostyki dostępu do danych (`libs/java/data-diagnostics-starter`). Plik jest punktem wejścia dla każdej nowej sesji: najpierw stan, potem decyzje, potem szczegóły etapów. Pierwotny dokument przekazania pracy z czatu, razem ze szkicem kodu (normalizacja SQL, resolver miejsca wywołania), leży obok: [`handoff.md`](handoff.md). Tam, gdzie handoff mówi inaczej niż ten plan, obowiązuje plan.

## Jak wznowić pracę w nowej sesji

1. Przeczytaj ten plik w całości, potem `CLAUDE.md` i `.claude/rules/backend-*.md`. Reguły archona mają pierwszeństwo przed handoffem.
2. Sprawdź tabelę „Stan” poniżej: weź pierwszy podpunkt bez znacznika „zrobione”.
3. Pokaż Patrykowi krótki plan tego podpunktu i poczekaj na akceptację. Jeden podpunkt = jeden commit.
4. Po podpunkcie: testy na JDK 21 i JDK 25, wynik w odpowiedzi, aktualizacja tabeli „Stan” w tym pliku w tym samym commicie.
5. Starter **nie** trafia do żadnego serwisu (`music-service`, `finance-service`).
6. Błędy w testach `finance-service` ignorujemy, prace tam trwają.

### Branche

- Etap 0: `claude/spring-boot-4.1` → PR [pgoogol/archon#14](https://github.com/pgoogol/archon/pull/14).
- Etap 1: `claude/hopeful-darwin-j7r2ur`, zbudowany na `claude/spring-boot-4.1`, bo potrzebuje Boota 4.1. Po merge PR #14 wciągnij `main` merge'em (bez rebase'u).

### Środowisko (kontener bez Dockera)

```bash
# JDK 25 obok 21
apt-get install -y openjdk-25-jdk-headless
# moduł startera na obu JDK
for v in 21 25; do
  JAVA_HOME=/usr/lib/jvm/java-$v-openjdk-amd64 ./mvnw -B -pl libs/java/data-diagnostics-starter -am verify
done
```

Od 1.4 testy integracyjne potrzebują PostgreSQL. Bez Dockera: `service postgresql start`, hasło `postgres` dla użytkownika `postgres`, osobna pusta baza na przebieg, potem `TEST_POSTGRES_CONTAINER=false SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/<baza> SPRING_DATASOURCE_USERNAME=postgres SPRING_DATASOURCE_PASSWORD=postgres`.

## Stan

| Podpunkt | Stan | Commit / uwagi |
|---|---|---|
| 0.1 Boot 4.1.1 | zrobione | `Podbij Spring Boot do 4.1.1`; springdoc 3.1.0 już na Boot 4.1 |
| 0.2 JDK 21 i 25 w CI | zrobione | `Testuj build Javy na JDK 21 i 25` |
| 1.1.1 Moduł | zrobione | moduł w reaktorze, pakiety `core`, `core.store`, `core.analysis`, `core.report` z `package-info.java`; zielono na JDK 21 i 25 |
| 1.1.2 Test architektury | następny | |
| 1.1.3 – 1.9.4 | do zrobienia | |
| Etapy 2–5+ | do zrobienia | |

## Decyzje (2026-10-03)

| Decyzja | Ustalenie |
|---|---|
| Forma | jeden starter `libs/java/data-diagnostics-starter` w reaktorze, pakiet `com.pgoogol.diagnostics`; `core` bez Springa, pilnowany ArchUnit |
| Wzorzec | strategia wszędzie: każda analiza, baza, mechanizm przechwytywania, granica jednostki pracy i wyjście to osobna klasa za interfejsem. Rdzeń zna tylko interfejsy |
| Przechwytywanie | własne strategie (datasource-proxy, listenery Springa i Hibernate); adapter Micrometer Observation przy innych bazach. Jeden magazyn = jedna aktywna strategia |
| Wyniki | log strukturalny zawsze, plik JSONL w dev; endpoint i eksport do czatu w etapie 3 |
| Metryki | Micrometer w bibliotece, eksport wybiera aplikacja; rekomendacja: Prometheus przez actuator, OTLP jako alternatywa |
| Spring Boot | cały archon 4.0.6 → 4.1.1 w etapie 0, osobny branch |
| JDK | `release=21`, testy na 21 i 25 (`openjdk-25-jdk-headless` z apt) |
| R2DBC, inne bazy | etap 5+ |
| Serwisy | starter **nie** trafia do `music-service` ani `finance-service` |

Zasady pracy: każdy podetap kończy się commitem i wynikiem testów na JDK 21 i 25. Przed każdym podetapem pokazuję krótki plan i czekam na akceptację. Etapy 4 i 5+ to wstępny zarys do rozbudowy przy realizacji.

## Mapa etapów

| Etap | Zakres | Start |
|---|---|---|
| 0 | Spring Boot 4.1.1, JDK 25 w CI | zrobione, PR #14 |
| **1** | wszystkie strategie dla PostgreSQL w stosie blokującym (podetapy 1.1–1.9) | **w toku** |
| 2 | wystawienie metryk | po 1 |
| 3 | wnioski dla interpreterów: endpoint, eksport do czatu | po 2 |
| 4 | produkcja (zarys) | po 3 |
| 5+ | inne bazy (zarys) | później |

---

## Etap 0: Spring Boot 4.1.1 i JDK 25

Branch `claude/spring-boot-4.1` od `main`.

- **0.1** `spring-boot.version` 4.0.6 → 4.1.1 w root POM. Sprawdzić zgodność `springdoc` (3.1.0) i reszty zależności spoza BOM-u; naprawić to, co się rozjedzie w serwisach i `logging-starter`. Commit: `Podbij Spring Boot do 4.1.1`.
- **0.2** JDK 25 lokalnie, `./mvnw -T 1C verify` na 21 i 25; job `java` w `.github/workflows/ci.yml` dostaje macierz `[21, 25]`. Commit: `Testuj build Javy na JDK 21 i 25`.

Dockera w kontenerze nie ma: integracyjne przez `TEST_POSTGRES_CONTAINER=false` na lokalnym PostgreSQL; jeśli go nie uruchomię, raportuję `-DskipITs` jako wynik niepełny. Wypchnięte na `claude/spring-boot-4.1`, PR [pgoogol/archon#14](https://github.com/pgoogol/archon/pull/14). Nazwa joba CI zmieniona na „Java 21/25 — …” za zgodą Patryka.

---

## Etap 1: PostgreSQL (branch `claude/hopeful-darwin-j7r2ur`)

### 1.1 Moduł i rdzeń

- **1.1.1 Moduł.** `libs/java/data-diagnostics-starter/pom.xml` bez `<version>`; wpis w `<modules>` i `<dependencyManagement>` root POM-u. Main: `slf4j-api`. Test: `spring-boot-starter-test`, `archunit-junit5`. Pakiety: `core`, `core.store`, `core.analysis`, `core.report`; `jdbc` i `spring` dochodzą w 1.4.
- **1.1.2 Test architektury.** `core` nie importuje `org.springframework..`, `jakarta.persistence..`, `net.ttddyy..`, `org.hibernate..`; wszystko pod `com.pgoogol`.
- **1.1.3 Model zdarzenia.**
  - `DataStore`: record z nazwą (`postgresql`); otwarty zbiór, nie enum.
  - `OperationKind`: `READ`, `WRITE`, `OTHER`.
  - `CallSite`: record `className`, `method`, `line`, `repositoryMethod` (nullable). Osobne pola zamiast sklejonego tekstu, żeby JSON dał się filtrować.
  - `DataAccessEvent`: magazyn, rodzaj, tekst (null w prod), kształt, czas (`Duration`), sukces, rozmiar batcha, `CallSite` (nullable), znacznik czasu.
- **1.1.4 Strategia bazy.** Interfejs `DataStoreSupport`: `store()`, `shape(statement)`, `classify(statement)`. `PostgreSqlSupport`:
  - kształt: literały tekstowe (też `E'…'` i `$$…$$`) i liczbowe → `?`; placeholdery `$1` → `?`; listy `IN (…)` i wielowierszowe `VALUES (…), (…)` zwinięte do jednego elementu; `= ANY(?)` bez zmian; komentarze `/* */` i `--` usunięte; białe znaki zwinięte; rzutowania `::typ` zostają, bo są częścią kształtu;
  - rodzaj: `SELECT`, `WITH … SELECT`, `SHOW` → `READ`; `INSERT`, `UPDATE`, `DELETE`, `MERGE`, `WITH … INSERT/UPDATE/DELETE` → `WRITE`; reszta → `OTHER`;
  - testy: po jednym przypadku na każdą regułę, plus zapytania wygenerowane przez Hibernate (aliasy `o1_0`).
- **1.1.5 Jednostka pracy.** `UnitOfWork`: krótki id, nazwa, typ (`http`, `scheduled`, `async`, `message`, `batch-chunk`, `startup` — otwarty zbiór), start, traceId (nullable), licznik operacji; w dev lista zdarzeń z limitem (domyślnie 10 000, nadmiar liczony, nie trzymany). Bezpieczna dla wielu wątków, bo w 1.7 trafią do niej zdarzenia z `@Async`.
- **1.1.6 Silnik.** `DiagnosticsEngine`:
  - `open(name, type)` zwraca `AutoCloseable`; bieżąca jednostka w `ThreadLocal`;
  - gdy jednostka już jest otwarta, kolejna granica do niej dołącza (licznik wejść), więc HTTP wołające `@Scheduled`-owy kod nie daje dwóch raportów;
  - `record(event)` bez otwartej jednostki: zdarzenie pomijane, chyba że `diagnostics.capture-outside-unit=true` (wtedy trafia do jednostki `startup`/`background`);
  - zamknięcie: analizy → wnioski → reportery; każde wywołanie analizy i reportera w osobnym `try`, błąd na DEBUG, nigdy do wywołującego.
- **1.1.7 Ustawienia.** `DiagnosticsSettings` (czysty record, bez Springa) z `DiagnosticsMode.DEV`/`PROD`:

  | Ustawienie | dev | prod |
  |---|---|---|
  | tekst zapytania | pełny | null, sam kształt |
  | parametry | wyłączone, można włączyć | zawsze wyłączone |
  | zdarzenia w jednostce | lista, limit 10 000 | brak, tylko liczniki w analizach |
  | limit kształtów na jednostkę | 500 | 200, nadmiar jako `other` |
  | miejsce wywołania | każda operacja | tylko operacje wolne i N-te powtórzenie kształtu |

### 1.2 Analizy operacji (każda osobną klasą)

- **1.2.1 Kontrakt.** `DiagnosticAnalyzer`: `id()`, `start(UnitOfWork)` → `AnalysisSession` z `onEvent(event)` i `findings()`. Silnik dostaje listę analiz; włączanie, wyłączanie i progi po `id()` (`diagnostics.analyzers.<id>.*`).
- **1.2.2 Wspólny licznik kształtów.** `ShapeStats`: liczba, łączny i maksymalny czas, pierwszy przykład tekstu, do 3 różnych miejsc wywołania, z limitem kształtów z 1.1.7. Używają go wszystkie analizy operacji, żeby pamięć była ograniczona tak samo.
- **1.2.3 Waga.** `Severity`: `INFO`, `WARN`, `CRITICAL`. Domyślnie `WARN` od progu, `CRITICAL` od 5× progu; mnożnik w ustawieniach analizy.
- **1.2.4 `NPlusOneAnalyzer`** (`n-plus-one`): ten sam kształt `READ` ≥ N razy w jednostce (dev 5, prod 10). Wniosek: kształt, liczba, łączny czas, miejsca wywołania.
- **1.2.5 `MissingBatchAnalyzer`** (`missing-batch`): ten sam kształt `WRITE` ≥ N razy z rozmiarem batcha 0 (domyślnie 5). Wykonania w batchu się nie liczą.
- **1.2.6 `SlowOperationAnalyzer`** (`slow-operation`): czas ≥ próg; próg per magazyn (`diagnostics.analyzers.slow-operation.threshold.postgresql`), domyślnie dev 100 ms, prod 500 ms. Jeden wniosek na kształt z liczbą wolnych wykonań i maksimum; nieudane operacje oznaczone osobno.
- **1.2.7 `OperationCountAnalyzer`** (`operation-count`): liczba operacji w jednostce > limit (dev 50, prod 100). Wniosek zawiera 5 najczęstszych kształtów.
- **1.2.8 Testy.** Na każdą analizę: poniżej progu brak wniosku, na progu `WARN`, przy 5× `CRITICAL`, wyłączona przez ustawienia, limit kształtów. Zdarzenia syntetyczne, bez bazy.

### 1.3 Format wniosków i wyjścia

Cel: wniosek da się przeczytać od razu w logu, znaleźć w narzędziu do logów i podać bez obróbki narzędziu albo AI. Każdy wniosek dostaje:

- **kod**: stała nazwa typu problemu, np. `N_PLUS_ONE`, `SLOW_OPERATION`;
- **fingerprint**: krótki skrót z kodu, bazy, kształtu zapytania i miejsca wywołania. Ten sam problem w tym samym miejscu ma zawsze ten sam fingerprint, więc wyszukanie go w logach pokazuje każde wystąpienie;
- **traceId** żądania, w którym wystąpił, żeby przejść do pełnego śladu;
- **wersję formatu** (`v1`), żeby narzędzia czytające wnioski wiedziały, czego się spodziewać po zmianach.

Przykład w logu (dev):

```
WARN  N+1: 37× "select ... from order_item where order_id=?" (412 ms) w GET /orders
      z OrderService.list:42 -> OrderRepository.findAllByStatus  [fp=a3f9c1e07b2d trace=6e1b…]
```

Ten sam wniosek jako linia w pliku `findings.jsonl`:

```json
{"schema":"data-diagnostics.finding/v1","code":"N_PLUS_ONE","fingerprint":"a3f9c1e07b2d","severity":"WARN","unit":{"name":"GET /orders","type":"http","traceId":"6e1b…"},"store":"postgresql","shape":"select ... from order_item where order_id=?","count":37,"totalMs":412,"callers":[{"class":"OrderService","method":"list","line":42,"repositoryMethod":"OrderRepository.findAllByStatus"}]}
```

- **1.3.1 Model `Finding`.** Pola z przykładu plus `title` (zdanie dla człowieka), `detectedAt`, `kind`, `sample` (dev), `maxMs`, `threshold`. `UnitOfWorkSummary`: nazwa, typ, traceId, liczba operacji, łączny czas w bazie, czas jednostki.
- **1.3.2 Fingerprint.** SHA-256 z `code|store|shape|klasa.metoda` pierwszego miejsca wywołania, pierwsze 12 znaków hex. Bez numeru linii, żeby dopisanie linii w pliku nie zmieniało odcisku.
- **1.3.3 Zapis JSON.** `FindingJsonWriter` w `core`, własny i mały, bez Jacksona. Test parsuje wynik Jacksonem (tylko w testach) i sprawdza każde pole oraz escaping znaków specjalnych w SQL.
- **1.3.4 `LogFindingReporter`.**
  - stała nazwa loggera `com.pgoogol.diagnostics.findings`, żeby jednym filtrem wyłowić wszystkie wnioski;
  - linia podsumowania jednostki + po jednej linii na wniosek jak w przykładzie;
  - te same dane jako pola SLF4J (`addKeyValue`: `dd.code`, `dd.fingerprint`, `dd.severity`, `dd.unit`, `dd.store`, `dd.count`, `dd.totalMs`). Gdy aplikacja włączy structured logging Boota (`logging.structured.format.console=ecs` albo `logstash`), pola stają się osobnymi kluczami JSON;
  - dev: podsumowanie jednostki bez wniosków na DEBUG; prod: tylko wnioski.
- **1.3.5 `JsonLinesFindingReporter`.** Jedna linia na wniosek, domyślnie `target/data-diagnostics/findings.jsonl` (`diagnostics.output.jsonl.path`), włączony domyślnie tylko w dev. Dopisywanie pod blokadą, limit rozmiaru 10 MB, potem plik przechodzi na `.1` i zaczyna się od nowa.
- **1.3.6 Testy.** Przechwycony log (`ListAppender` Logbacka) z polami strukturalnymi; plik JSONL w katalogu tymczasowym; rotacja; błąd zapisu pliku nie przerywa pozostałych reporterów.

### 1.4 Przechwytywanie JDBC

- **1.4.1 Zależności.** `net.ttddyy:datasource-proxy` 1.11.0 w root POM; w module opcjonalne: `spring-boot-autoconfigure`, `spring-jdbc`, `spring-webmvc`, `jakarta.servlet-api`, `spring-boot-configuration-processor`.
- **1.4.2 `JdbcCaptureStrategy`.** `QueryExecutionListener.afterQuery` → `DataAccessEvent`: tekst, kształt i rodzaj z `PostgreSqlSupport`, czas, sukces, rozmiar batcha; parametry tylko w dev za flagą, przycięte do 100 znaków. Czas obejmuje wykonanie bez pobierania wierszy; zapisuję to w javadocu.
- **1.4.3 Opakowanie `DataSource`.** `BeanPostProcessor` owija każdy `DataSource`, pomija już owinięte. Zamiast `ProxyDataSource` (zmienia typ beana i psuje wstrzykiwanie `HikariDataSource`) próbuję `ProxyFactory` Springa z `proxyTargetClass=true`, który przechwytuje `getConnection` i zwraca połączenie z datasource-proxy. Test sprawdza, że `HikariDataSource` dalej wstrzykuje się po typie.
- **1.4.4 Miejsce wywołania.** `CallSiteResolver` na `StackWalker`:
  - pakiet aplikacji z `AutoConfigurationPackages` (pakiet klasy `@SpringBootApplication`), nadpisywalny `diagnostics.application-packages`;
  - pomija ramki biblioteki, proxy JDK, klasy CGLIB (`$$`), proxy Hibernate, lambdy;
  - wywołanie przez proxy repozytorium Spring Data zapisuje jako `repositoryMethod`;
  - w prod wywoływany tylko zgodnie z 1.1.7.
- **1.4.5 Granica HTTP.** `HttpRequestBoundary` (filtr servletowy, kolejność `HIGHEST_PRECEDENCE + 10`, przed Spring Security). Nazwa jednostki z wzorca trasy (`GET /orders/{id}`), nie z surowego URI, żeby nie mnożyć nazw. traceId z Micrometer Tracing, gdy jest, inaczej z MDC.
- **1.4.6 Autokonfiguracja.** `DataDiagnosticsAutoConfiguration` + `DataDiagnosticsProperties` (`diagnostics.*`), wpis w `AutoConfiguration.imports`, każdy bean z `@ConditionalOnMissingBean`. Dodanie zależności włącza całość; `diagnostics.enabled=false` wyłącza bez proxy. Domyślny tryb `prod` (bezpieczny), `dev` ustawia się w profilu lokalnym. Każda analiza i reporter to osobny bean, więc serwis może dodać własną analizę albo podmienić istniejącą.
- **1.4.7 Testy integracyjne na PostgreSQL** (`@Tag("integration")`, Testcontainers `postgres:16-alpine`, obejście `TEST_POSTGRES_CONTAINER=false` jak w serwisach). Testowa aplikacja w `src/test`: encje `Order` i `OrderItem`, repozytorium, serwis. Dla każdego z sześciu sposobów (metoda repozytorium, JPQL, Criteria, natywne SQL, JdbcTemplate, lazy loading) test woła endpoint i sprawdza wniosek `N_PLUS_ONE` z miejscem wywołania wskazującym metodę serwisu testowego. Do tego: `enabled=false` nie owija `DataSource`; zapytania Flyway przy starcie nie dają wniosków.

### 1.5 Transakcje

- **1.5.1 `TransactionCaptureStrategy`** na `TransactionExecutionListener`; sprawdzić, czy Boot 4.1 sam podpina beany tego typu do menedżera transakcji, inaczej `BeanPostProcessor` na `ConfigurableTransactionManager`.
- **1.5.2 Zdarzenie transakcji** w rdzeniu: nazwa (metoda), początek i koniec, wynik (commit/rollback), `readOnly`, propagacja; jednostka pracy trzyma stos otwartych transakcji i liczy operacje w każdej.
- **1.5.3 `LongTransactionAnalyzer`** (`long-transaction`): czas > próg (dev 500 ms, prod 2 s).
- **1.5.4 `TransactionOperationCountAnalyzer`** (`transaction-operation-count`): operacje w jednej transakcji > limit (domyślnie 50).
- **1.5.5 `ReadOnlyCandidateAnalyzer`** (`read-only-candidate`): transakcja bez `readOnly` z samymi odczytami; waga `INFO`.
- **1.5.6 Testy** na PostgreSQL: `@Transactional`, `TransactionTemplate`, rollback, transakcja zagnieżdżona `REQUIRES_NEW`.

### 1.6 Pula połączeń

- **1.6.1 `ConnectionCaptureStrategy`:** na tym samym proxy co 1.4.3 mierzy czas `getConnection` (czekanie na pulę) i czas od pobrania do `close` (trzymanie).
- **1.6.2 `ConnectionWaitAnalyzer`** (`connection-wait`): czekanie > próg (dev 20 ms, prod 100 ms).
- **1.6.3 `ConnectionHoldAnalyzer`** (`connection-hold`): trzymanie > próg albo stosunek czasu w SQL do czasu trzymania < 10% (połączenie wisi np. podczas wywołania HTTP w transakcji).
- **1.6.4 `OpenInViewCheck`:** przy starcie, gdy `spring.jpa.open-in-view` jest włączone (domyślne w Boot), jeden wniosek `OPEN_IN_VIEW` w jednostce `startup`.
- **1.6.5 Testy:** pula HikariCP z `maximumPoolSize=1` i dwa równoległe żądania dają `CONNECTION_WAIT`; wywołanie zewnętrzne wewnątrz transakcji daje `CONNECTION_HOLD`.

### 1.7 Granice poza HTTP (każda osobną `UnitOfWorkBoundary`, aktywna tylko z biblioteką na classpath)

- **1.7.1 Kontrakt `UnitOfWorkBoundary`** i przeniesienie granicy HTTP z 1.4.5 na ten kontrakt.
- **1.7.2 `@Scheduled`:** dekorator zadań harmonogramu (sprawdzić, czy Boot 4.1 przyjmuje `TaskDecorator` dla schedulera); nazwa jednostki = klasa.metoda.
- **1.7.3 `@Async`:** `TaskDecorator` przenosi bieżącą jednostkę do wątku wykonawczego; zdarzenia z tego wątku trafiają do jednostki rodzica. Gdy rodzic zdążył się zamknąć, wątek otwiera własną jednostkę `async`.
- **1.7.4 Komunikaty:** Kafka (`RecordInterceptor`), RabbitMQ (`MessagePostProcessor` po stronie odbiorcy); jednostka na komunikat.
- **1.7.5 Spring Batch:** jednostka na chunk (`ChunkListener`), bo N+1 ma sens w obrębie chunka, nie całego kroku.
- **1.7.6 Testy:** każda granica daje osobny raport; zdarzenia z `@Async` liczone w rodzicu; test współbieżności jednostki.

### 1.8 Wnętrze Hibernate

- **1.8.1 Rejestracja:** sprawdzić w Hibernate 7 drogę dla `SessionEventListener` (`hibernate.session.events.auto` albo `HibernatePropertiesCustomizer`); `HibernateCaptureStrategy` przekłada zdarzenia sesji na zdarzenia rdzenia.
- **1.8.2 Zdarzenia:** flush (czas, liczba encji i kolekcji), dirty checking (czas), automatyczny flush przed zapytaniem, ładowanie encji (liczba), L2 cache (trafienia, chybienia, zapisy).
- **1.8.3 `FlushAnalyzer`** (`flush`): liczba flushy w jednostce > N, czas flushu > próg, flush z dużą liczbą encji.
- **1.8.4 `DirtyCheckingAnalyzer`** (`dirty-checking`): długi dirty checking przy wielu zarządzanych encjach; podpowiedź: `readOnly`, projekcje DTO.
- **1.8.5 `EntityLoadAnalyzer`** (`entity-load`): więcej załadowanych encji niż limit w jednej jednostce (brak paginacji, za szerokie fetch).
- **1.8.6 `SecondLevelCacheAnalyzer`** (`second-level-cache`): niski współczynnik trafień w regionach oznaczonych jako cache.
- **1.8.7 Testy** na PostgreSQL dla każdej analizy.

### 1.9 Repozytoria Spring Data

- **1.9.1 Rejestracja** `RepositoryMethodInvocationListener` przez `RepositoryFactoryCustomizer` (do sprawdzenia publiczna droga w Spring Data z Boota 4.1).
- **1.9.2 `RepositoryCaptureStrategy`:** zdarzenie wywołania metody repozytorium: interfejs, metoda, czas, wynik.
- **1.9.3 `RepositoryHotspotAnalyzer`** (`repository-hotspot`): ta sama metoda repozytorium ≥ N razy w jednostce (np. `findById` w pętli) oraz najwolniejsze metody.
- **1.9.4 Testy** dla Spring Data JPA i Spring Data JDBC.

---

## Etap 2: metryki

- **2.1 Mierniki** w `MicrometerFindingReporter` i strategiach:
  - `data.diagnostics.findings` (licznik): tagi `code`, `store`, `severity`, `unit.type`;
  - `data.diagnostics.operations` (timer): `store`, `kind`, `outcome`;
  - `data.diagnostics.unit.operations` (rozkład): liczba operacji na jednostkę, tag `unit.type`;
  - `data.diagnostics.transactions` i `data.diagnostics.connection.wait` (timery) z etapów 1.5–1.6.
- **2.2 Kardynalność:** kształt zapytania nigdy w tagach; nazwa jednostki (wzorzec trasy) tylko za flagą i z limitem przez `MeterFilter`.
- **2.3 Eksport:** biblioteka nie wybiera rejestru. Test z `micrometer-registry-prometheus` sprawdza, że mierniki są pod `/actuator/prometheus`; konfiguracja OTLP jako alternatywa w javadocu właściwości.
- **2.4 Exemplary:** przy włączonym tracingu timery niosą traceId, żeby z wykresu przejść do śladu.
- **2.5 Opcjonalnie:** przykładowe reguły alertów Prometheusa i dashboard Grafany jako pliki w module.

## Etap 3: wnioski dla interpreterów

Interpretację robisz poza biblioteką: narzędzie czyta wnioski z endpointu albo wklejasz je do czatu. Biblioteka nie woła AI.

- **3.1** endpoint actuatora z ostatnimi wnioskami (bufor w pamięci z limitem, filtr po kodzie, fingerprint, jednostce), JSON w formacie z 1.3
- **3.2** eksport „do czatu”: podsumowanie wniosków z jednostki pracy razem z kontekstem (kształt, miejsca wywołania, liczby) jako gotowy tekst do wklejenia
- **3.3** do decyzji przy realizacji: podpowiedzi regułowe per kod (np. N+1 → `JOIN FETCH`, `@EntityGraph`, `@BatchSize`)

## Etap 4: produkcja (wstępny zarys)

Limit częstości logów wniosków, limity pamięci jednostki pracy, pomiar narzutu testem obciążeniowym. Rozbudujemy przy realizacji.

## Etap 5+: inne bazy (wstępny zarys)

R2DBC z kontekstem Reactora i WebFluxem, MongoDB, Elasticsearch, Redis, Cassandra, adapter Micrometer Observation. Każda baza = nowa `DataStoreSupport` + strategia przechwytywania.

## Weryfikacja

- Etap 0: `./mvnw -T 1C verify` na JDK 21 i 25; integracyjne na lokalnym PostgreSQL albo jawnie zgłoszone `-DskipITs`.
- Etap 1+: `./mvnw -pl libs/java/data-diagnostics-starter -am verify` na obu JDK i `./mvnw spotless:check`; od 1.4 testy integracyjne na prawdziwym PostgreSQL, bez H2. Bez klas `*Service` w starterze, bo łapie je bramka JaCoCo.
