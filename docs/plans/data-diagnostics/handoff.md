# data-diagnostics: przekazanie pracy do Claude Code

Stan na 2026-10-03. Ten plik przenosi cały dorobek z czatu: projekt rozwiązania, decyzje, plan etapów, stan weryfikacji i szkic kodu. Autor: Patryk. Pakiet bazowy: `com.pgoogol.diagnostics`.

## Instrukcje dla Claude Code

1. Przeczytaj cały plik przed pierwszą zmianą w repozytorium.
2. Zacznij od etapu 1a. Pracuj nad jednym etapem naraz i nie pisz kodu ani dokumentów dla etapów późniejszych.
3. Decyzje z listy „Otwarte” należą do Patryka. Zanim zaczniesz część, którą taka decyzja blokuje, zatrzymaj się i zapytaj.
4. Patryk woli mały, działający zakres od pełnego opisu całości. Plan etapu pokaż krótko i poczekaj na akceptację przed implementacją.
5. Obowiązuje globalny ruleset Java/Spring z `~/.claude/`.
6. Szkic z załącznika traktuj jako materiał do przepisania na kontrakty z sekcji „Architektura”. Nie kopiuj go bez zmian.
7. Po każdym etapie uruchom testy na JDK 21 i JDK 25 i podaj wynik.

## Cel i granice

Biblioteka pokazuje, co aplikacja Spring robi ze swoimi magazynami danych: każdą operację (zapytanie SQL, komendę Mongo, żądanie do Elasticsearch), jej czas i miejsce w kodzie, które ją wywołało. Z tych zdarzeń wyprowadza wnioski: N+1, wolne operacje, długie transakcje, oczekiwanie na połączenie, kosztowne flushe Hibernate.

Działa w dwóch trybach. Lokalnie zbiera komplet szczegółów i raportuje po każdym żądaniu. Na produkcji działa stale, z ograniczonym narzutem i bez wartości parametrów.

Przechwytywanie, analiza i wyjście to strategie. Nową bazę albo nową analizę dodajesz jako kolejną implementację, rdzeń zostaje bez zmian.

Poza zakresem: biblioteka nie zastępuje APM ani tracingu (wysyła do nich dane), nie naprawia zapytań i nie monitoruje samej bazy. Po stronie PostgreSQL robią to `pg_stat_statements` i `auto_explain`.

Docelowo biblioteka obejmuje wszystkie magazyny i sposoby zapytań, których używa aplikacja: JPA, EntityManager, Criteria, QueryDSL, JdbcTemplate, natywne SQL, R2DBC, MongoDB, Elasticsearch i kolejne.

## Decyzje

### Potwierdzone

| Decyzja | Ustalenie |
| --- | --- |
| Platforma | Spring Boot 4.1 |
| Java | 21 i 25: kompilacja z `maven.compiler.release=21`, testy na JDK 21 i JDK 25 |
| Narzędzie budowania | Maven |
| Nazwa i pakiet | `data-diagnostics`, `com.pgoogol.diagnostics` (nazwa robocza) |

Zapis „21 i 25” Patryk podał bez rozwinięcia. Wiersz wyżej to interpretacja z czatu: jeśli ma być inaczej, zapytaj przed konfiguracją `pom.xml`.

### Otwarte

Patryk odłożył trzy decyzje. Etap 1a ich nie potrzebuje.

| Decyzja | Warianty | Rekomendacja z czatu | Co blokuje | Jak etap 1 zostawia ją otwartą |
| --- | --- | --- | --- | --- |
| Forma | osobne repozytorium ze starterem i modułami albo moduł w istniejącym projekcie | starter z modułami | podział na moduły Maven, publikację artefaktów | jeden moduł Maven, pakiety odpowiadają przyszłym modułom, `core` bez zależności od Springa |
| Przechwytywanie | własne strategie na proxy i listenerach albo odczyt z Micrometer Observation | własne strategie | etap 1b i każdą kolejną strategię przechwytywania | kontrakt `DataAccessCaptureStrategy` ukrywa mechanizm; etap 1a testuje rdzeń na zdarzeniach syntetycznych |
| Wyjście produkcyjne | logi i metryki Micrometer albo same logi | logi i metryki | etap 2 | etap 1 ma jedno wyjście: log; kontrakt `FindingReporter` przyjmie kolejne |

Druga droga przechwytywania (Micrometer Observation) to mniej kodu, ale zależy od instrumentacji każdej biblioteki i gubi część szczegółów. Dla JDBC oba warianty stoją na datasource-proxy, bo Datasource Micrometer też go używa.

## Architektura

Rdzeń zna cztery kontrakty, a każdy magazyn, analiza i wyjście to osobna implementacja jednego z nich.

```
Przechwytywanie  -->  Rdzeń  -->  Analiza  -->  Wyjście
(strategia na         DataAccessEvent   (strategia na      (log, metryki,
 magazyn)             UnitOfWork         rodzaj wniosku)    spany)
                      Finding

Tryb dev albo prod wybiera zestaw strategii i ich progi.
```

Strategia przechwytywania zamienia natywne zdarzenie sterownika na `DataAccessEvent`: magazyn, rodzaj operacji, tekst i kształt, czas, wynik, miejsce wywołania. Rdzeń dopisuje je do bieżącej jednostki pracy i przekazuje analizom. Przy zamknięciu jednostki analizy zwracają wnioski, a wyjścia je publikują.

| Kontrakt | Odpowiedzialność |
| --- | --- |
| `DataAccessCaptureStrategy` | podpina się pod sterownik lub framework i publikuje `DataAccessEvent` |
| `UnitOfWorkBoundary` | otwiera i zamyka jednostkę pracy: żądanie HTTP, transakcja, zadanie z harmonogramu, komunikat |
| `DiagnosticAnalyzer` | zbiera zdarzenia jednostki pracy i zwraca `Finding` |
| `FindingReporter` | publikuje wnioski: log, metryki, spany |

Kształt zapytania to jego tekst bez literałów i z listą `IN` zwiniętą do jednego znaku zapytania. Analizy grupują po kształcie, więc wykonania różniące się tylko wartościami trafiają do jednej grupy. Działająca implementacja: `SqlDiagnosticsReporter.normalize` w załączniku.

## Strategie przechwytywania

Każdy sposób dostępu do danych dostaje jedną strategię. Wszystko, co idzie przez JDBC, obsługuje jedna strategia, więc etap 1 pokrywa większość typowej aplikacji. Strategia aktywuje się sama, gdy jej biblioteka jest na classpath, i daje się wyłączyć właściwością.

| Sposób dostępu | Strategia | Mechanizm | Etap |
| --- | --- | --- | --- |
| Spring Data JPA, EntityManager (JPQL, Criteria, natywne SQL), QueryDSL, JdbcTemplate, Spring Data JDBC | `JdbcCaptureStrategy` | proxy na `DataSource` (datasource-proxy) | 1b |
| Transakcje Springa | `TransactionCaptureStrategy` | `TransactionExecutionListener` na menedżerze transakcji | 3 |
| Pula połączeń | `ConnectionCaptureStrategy` | pomiar `getConnection` i `close` na tym samym proxy | 3 |
| Wnętrze Hibernate | `HibernateCaptureStrategy` | `SessionEventListener` i `Statistics` | 4 |
| Repozytoria Spring Data, wszystkie moduły | `RepositoryCaptureStrategy` | `RepositoryMethodInvocationListener` ze spring-data-commons | 5 |
| R2DBC | `R2dbcCaptureStrategy` | r2dbc-proxy na `ConnectionFactory` | 6 |
| MongoDB, blokująco i reaktywnie | `MongoCaptureStrategy` | `CommandListener` sterownika | 7 |
| Elasticsearch | `ElasticsearchCaptureStrategy` | hak w transporcie klienta, do sprawdzenia w etapie 8 | 8 |
| Redis, Cassandra i kolejne | po jednej strategii | listener sterownika (Lettuce `CommandListener`, Cassandra `RequestTracker`) | 9 |

## Strategie analizy

Analiza dostaje zdarzenia jednej jednostki pracy i przy jej zamknięciu zwraca wnioski (`Finding`): typ, wagę, przykładowe zapytanie i miejsce wywołania. Każda ma własne progi w konfiguracji i daje się wyłączyć osobno.

| Analiza | Co wykrywa | Źródło zdarzeń | Etap |
| --- | --- | --- | --- |
| Powtórzenia | ten sam kształt zapytania co najmniej N razy: N+1 przy odczycie, brak batchowania przy zapisie | operacje magazynu | 1a |
| Wolne operacje | czas powyżej progu, osobny próg na magazyn | operacje magazynu | 1a |
| Liczba operacji | więcej operacji w jednostce pracy niż limit | operacje magazynu | 1a |
| Transakcje | transakcja dłuższa niż próg, liczba operacji w transakcji | zdarzenia transakcji | 3 |
| Połączenia | oczekiwanie na połączenie i czas jego trzymania powyżej progu, włączone `open-in-view` | zdarzenia puli | 3 |
| Hibernate | liczba i czas flushy, flush z dużą liczbą encji, czas dirty checkingu, trafienia cache drugiego poziomu | zdarzenia sesji | 4 |
| Repozytoria | najwolniejsze i najczęściej wołane metody repozytoriów | wywołania repozytoriów | 5 |

## Tryby pracy

Właściwość `diagnostics.mode` wybiera domyślny zestaw ustawień: `dev` albo `prod`. Każde ustawienie da się nadpisać osobno.

| Ustawienie | dev | prod |
| --- | --- | --- |
| Miejsce wywołania | dla każdej operacji | tylko gdy operacja przekracza próg albo licznik powtórzeń osiąga próg |
| Tekst zapytania | pełny, parametry na życzenie | sam kształt, bez literałów i parametrów |
| Pamięć | wszystkie zdarzenia jednostki pracy | liczniki na kształt, z limitem liczby kształtów |
| Wyjście | raport w logu po każdej jednostce pracy | zależy od otwartej decyzji „Wyjście produkcyjne”; do tego czasu log samych wniosków z limitem częstości |

W obu trybach błąd diagnostyki nie przerywa operacji na bazie, a `diagnostics.enabled=false` wyłącza całość bez proxy na `DataSource`. Narzut trybu `prod` mierzymy testem obciążeniowym w etapie 2, przed włączeniem na produkcji.

## Technologie

| Obszar | Wybór | Dlaczego i co odrzucono |
| --- | --- | --- |
| Platforma | Java 21 i 25, Spring Boot 4.1 | potwierdzone przez Patryka; 4.1.1 to wersja stabilna na 2026-10-03 |
| Budowanie | Maven | potwierdzone przez Patryka |
| JDBC | datasource-proxy 1.11.0 (`net.ttddyy:datasource-proxy`) | listener w kodzie, działa pod każdym API nad JDBC; odrzucono p6spy (konfiguracja przez podmianę sterownika) i `StatementInspector` (tylko Hibernate, bez czasu) |
| R2DBC | r2dbc-proxy | Spring Boot buduje na nim własną obserwowalność R2DBC |
| Hibernate | `SessionEventListener`, `Statistics` | API samego Hibernate; odrzucono parsowanie logów |
| Transakcje | `TransactionExecutionListener` | obejmuje `@Transactional` i `TransactionTemplate`, blokująco i reaktywnie; odrzucono aspekt na `@Transactional` |
| Kontekst jednostki pracy | `ThreadLocal`, dla Reactora Micrometer Context Propagation | jeden model dla stosu blokującego i reaktywnego |
| Wyjścia | SLF4J z logami strukturalnymi; Micrometer zależnie od otwartej decyzji | trafiają do narzędzi, które aplikacja już zasila; odrzucono własny endpoint z historią |
| Testy | JUnit 5, Testcontainers | prawdziwa baza w testach każdej strategii; odrzucono H2 |

Źródła sprawdzone w czacie:

- Spring Boot, Observability: https://docs.spring.io/spring-boot/3.5/reference/actuator/observability.html (JDBC przez Datasource Micrometer, R2DBC przez r2dbc-proxy)
- Datasource Micrometer: https://jdbc-observations.github.io/datasource-micrometer/docs/current/docs/html/
- datasource-proxy: https://github.com/jdbc-observations/datasource-proxy

## Etapy

### Etap 1a: rdzeń, bez otwartych decyzji

Zakres:

- projekt Maven, jeden moduł `data-diagnostics`, `maven.compiler.release=21`
- pakiety: `com.pgoogol.diagnostics.core` (bez zależności od Springa), `.jdbc`, `.spring`
- rdzeń: `DataAccessEvent`, `UnitOfWork`, `DiagnosticAnalyzer`, `Finding`, `FindingReporter`, funkcja kształtu zapytania i silnik, który rozsyła zdarzenia
- analizy: powtórzenia, wolne operacje, liczba operacji
- wyjście: `LogFindingReporter`
- ustawienia trybów `dev` i `prod` w części, która dotyczy rdzenia: tekst albo kształt, komplet zdarzeń albo liczniki z limitem
- testy jednostkowe analiz na zdarzeniach syntetycznych, bez bazy

Etap 1a jest gotowy, gdy testy pokazują wniosek dla każdej z trzech analiz, a test architektury potwierdza, że `core` nie importuje Springa.

### Etap 1b: JDBC w aplikacji Spring MVC

Wymaga decyzji „Przechwytywanie”. Zapytaj Patryka przed startem.

Zakres:

- `JdbcCaptureStrategy`
- ustalanie miejsca wywołania w kodzie aplikacji (działająca implementacja: `ApplicationCallerResolver` w załączniku)
- granica jednostki pracy: żądanie HTTP (filtr servletowy)
- autokonfiguracja z właściwościami `diagnostics.*`
- testy na PostgreSQL w Testcontainers: metoda repozytorium, JPQL, Criteria, natywne SQL, JdbcTemplate, lazy loading z N+1

Etap 1b jest gotowy, gdy test z N+1 zwraca wniosek z właściwym miejscem wywołania dla każdego z tych sześciu sposobów.

### Kolejne etapy

2. Produkcja: wyjście według decyzji „Wyjście produkcyjne”, limity pamięci, pomiar narzutu.
3. Transakcje i połączenia oraz granice poza HTTP: scheduler, listenery komunikatów.
4. Wnętrze Hibernate.
5. Poziom repozytoriów Spring Data.
6. Stos reaktywny: kontekst Reactora, WebFlux, R2DBC.
7. MongoDB.
8. Elasticsearch.
9. Kolejne magazyny według tego samego kontraktu.

## Stan weryfikacji szkicu

Szkic z załącznika powstał w czacie przed projektem i obejmuje jedną ścieżkę: JDBC przez datasource-proxy, raport po żądaniu HTTP.

Sprawdzone:

- sygnatury datasource-proxy odczytane ze źródeł repozytorium: `QueryExecutionListener`, `ExecutionInfo`, `QueryInfo`, `ParameterSetOperation`, `ProxyDataSourceBuilder`
- kompilacja z `-Xlint:all` na prawdziwych klasach datasource-proxy i atrapach sygnatur Springa, API servletów i SLF4J
- symulacja listenera, zakresu i raportu na sztucznych zapytaniach: wykryła N+1 w mapperze, zgrupowała natywne zapytania różniące się literałami i długością listy `IN`, wskazała wywołanie przez proxy repozytorium (`OrderService.list:32 -> OrderRepository.findAllByStatus`)

Niesprawdzone:

- start w prawdziwym kontekście Springa
- zgodność ze Spring Boot 4.1: szkic używa pakietów z linii 3.x
- zachowanie z prawdziwym sterownikiem JDBC i pulą HikariCP

Znane ograniczenia szkicu:

- czas obejmuje wykonanie instrukcji bez pobierania wierszy z `ResultSet`
- zakres opiera się na `ThreadLocal`, więc pomija zapytania z innych wątków (`@Async`, strumienie równoległe)
- bean `DataSource` zmienia typ na `ProxyDataSource`; wstrzyknięcie `HikariDataSource` po typie przestaje działać, `unwrap` działa
- test miejsca wywołania wymaga atrap sterownika poza pakietem aplikacji, inaczej resolver wskaże atrapę

## Do sprawdzenia w trakcie implementacji

- Spring Boot 4.1: pakiety i moduły autokonfiguracji, `FilterRegistrationBean`, rejestracja `BeanPostProcessor` dla `DataSource`
- Hibernate 7: dostępność i sposób rejestracji `SessionEventListener` (etap 4)
- Spring Data: publiczna droga rejestracji `RepositoryMethodInvocationListener` (etap 5)
- Elasticsearch: punkt zaczepienia w transporcie klienta (etap 8)
- JDK 25: zachowanie `StackWalker` i proxy bez zmian względem 21

## Załącznik: szkic kodu

Dziewięć klas w pakiecie `com.pgoogol.diagnostics.sql`. Tabela pokazuje, dokąd trafia każda z nich w docelowej architekturze.

| Klasa szkicu | Cel w architekturze |
| --- | --- |
| `QueryRecord` | `DataAccessEvent`; dojdą magazyn, rodzaj operacji i kształt |
| `SqlDiagnosticsScope` | `UnitOfWork` |
| `SqlDiagnosticsFilter` | `UnitOfWorkBoundary` dla żądania HTTP |
| `SqlDiagnosticsListener`, `DataSourceProxyBeanPostProcessor` | `JdbcCaptureStrategy` |
| `ApplicationCallerResolver` | ustalanie miejsca wywołania, wspólne dla strategii przechwytywania |
| `SqlDiagnosticsReporter` | analizy powtórzeń i wolnych operacji, `LogFindingReporter`, funkcja kształtu (`normalize`) |
| `SqlDiagnosticsProperties`, `SqlDiagnosticsConfiguration` | właściwości `diagnostics.*` i autokonfiguracja |

### QueryRecord.java

```java
package com.pgoogol.diagnostics.sql;

import java.util.List;

/**
 * A single JDBC statement execution captured by {@link SqlDiagnosticsListener}.
 *
 * @param sql        statement text as sent to the driver
 * @param elapsedMs  execution time reported by datasource-proxy (without result set fetching)
 * @param success    false when the driver threw an exception
 * @param batchSize  number of batched parameter sets, 0 for a non-batch execution
 * @param caller     application code that triggered the statement
 * @param parameters bound values, empty unless parameter logging is enabled
 */
public record QueryRecord(
        String sql,
        long elapsedMs,
        boolean success,
        int batchSize,
        String caller,
        List<String> parameters) {
}
```

### SqlDiagnosticsScope.java

```java
package com.pgoogol.diagnostics.sql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Collects statements executed by the current thread between {@link #open(String)} and {@link #close()}.
 * Statements executed on other threads (@Async, parallel streams) are not part of the scope.
 */
public final class SqlDiagnosticsScope implements AutoCloseable {

    private static final ThreadLocal<SqlDiagnosticsScope> CURRENT = new ThreadLocal<>();

    private final String name;
    private final SqlDiagnosticsScope parent;
    private final List<QueryRecord> queries = new ArrayList<>();

    private SqlDiagnosticsScope(String name, SqlDiagnosticsScope parent) {
        this.name = name;
        this.parent = parent;
    }

    public static SqlDiagnosticsScope open(String name) {
        SqlDiagnosticsScope scope = new SqlDiagnosticsScope(name, CURRENT.get());
        CURRENT.set(scope);
        return scope;
    }

    public static Optional<SqlDiagnosticsScope> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    void record(QueryRecord query) {
        queries.add(query);
        if (parent != null) {
            parent.record(query);
        }
    }

    public String name() {
        return name;
    }

    public List<QueryRecord> queries() {
        return Collections.unmodifiableList(queries);
    }

    @Override
    public void close() {
        if (parent == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(parent);
        }
    }
}
```

### ApplicationCallerResolver.java

```java
package com.pgoogol.diagnostics.sql;

import java.lang.StackWalker.StackFrame;
import java.lang.reflect.Proxy;
import java.util.Iterator;

/**
 * Finds the application code that triggered a statement by walking the current stack.
 * Result format: {@code OrderService.list:42 -> OrderRepository.findAllByStatus}.
 * The part after the arrow appears only when the call went through a Spring Data repository proxy.
 */
final class ApplicationCallerResolver {

    static final String UNKNOWN = "unknown";

    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final String OWN_PACKAGE = ApplicationCallerResolver.class.getPackageName() + ".";

    private final String applicationPackage;

    ApplicationCallerResolver(String applicationPackage) {
        this.applicationPackage = applicationPackage.endsWith(".") ? applicationPackage : applicationPackage + ".";
    }

    String resolve() {
        return WALKER.walk(frames -> {
            String proxyCall = null;
            for (Iterator<StackFrame> iterator = frames.iterator(); iterator.hasNext(); ) {
                StackFrame frame = iterator.next();
                Class<?> type = frame.getDeclaringClass();
                if (Proxy.isProxyClass(type)) {
                    if (proxyCall == null) {
                        proxyCall = describeProxyCall(type, frame.getMethodName());
                    }
                } else if (isApplicationClass(type.getName())) {
                    String caller = simpleName(type.getName()) + "." + frame.getMethodName() + ":" + frame.getLineNumber();
                    return proxyCall == null ? caller : caller + " -> " + proxyCall;
                }
            }
            return proxyCall == null ? UNKNOWN : proxyCall;
        });
    }

    private String describeProxyCall(Class<?> proxyType, String methodName) {
        for (Class<?> proxiedInterface : proxyType.getInterfaces()) {
            if (proxiedInterface.getName().startsWith(applicationPackage)) {
                return proxiedInterface.getSimpleName() + "." + methodName;
            }
        }
        return null;
    }

    private boolean isApplicationClass(String className) {
        return className.startsWith(applicationPackage)
                && !className.startsWith(OWN_PACKAGE)
                // CGLIB subclasses, Hibernate proxies and lambda classes only delegate, skip to the real caller
                && !className.contains("$$")
                && !className.contains("$HibernateProxy");
    }

    private static String simpleName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }
}
```

### SqlDiagnosticsListener.java

```java
package com.pgoogol.diagnostics.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import net.ttddyy.dsproxy.proxy.ParameterSetOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receives every statement that goes through the proxied DataSource: Spring Data repositories,
 * EntityManager (JPQL, Criteria, native), lazy loading, JdbcTemplate, Flyway.
 */
public class SqlDiagnosticsListener implements QueryExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(SqlDiagnosticsListener.class);
    private static final int MAX_PARAMETER_LENGTH = 100;

    private final SqlDiagnosticsProperties properties;
    private final ApplicationCallerResolver callerResolver;

    public SqlDiagnosticsListener(SqlDiagnosticsProperties properties) {
        this.properties = properties;
        this.callerResolver = new ApplicationCallerResolver(properties.applicationPackage());
    }

    @Override
    public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        // nothing to capture before execution
    }

    @Override
    public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        try {
            capture(execInfo, queryInfoList);
        } catch (RuntimeException e) {
            // diagnostics must never break the statement they observe
            log.debug("SQL diagnostics failed to capture a statement", e);
        }
    }

    private void capture(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        Optional<SqlDiagnosticsScope> scope = SqlDiagnosticsScope.current();
        boolean slow = execInfo.getElapsedTime() >= properties.slowQueryThreshold().toMillis();
        if (scope.isEmpty() && !slow && !log.isDebugEnabled()) {
            return;
        }

        QueryRecord query = new QueryRecord(
                queryInfoList.stream().map(QueryInfo::getQuery).collect(Collectors.joining("; ")),
                execInfo.getElapsedTime(),
                execInfo.isSuccess(),
                execInfo.isBatch() ? execInfo.getBatchSize() : 0,
                callerResolver.resolve(),
                properties.logParameters() ? extractParameters(queryInfoList) : List.of());

        scope.ifPresent(current -> current.record(query));

        if (slow) {
            log.warn("Slow SQL {} ms | {} | {}{}", query.elapsedMs(), query.caller(), query.sql(), describeParameters(query));
        } else {
            log.debug("SQL {} ms | {} | {}{}", query.elapsedMs(), query.caller(), query.sql(), describeParameters(query));
        }
    }

    private static List<String> extractParameters(List<QueryInfo> queryInfoList) {
        List<String> parameterSets = new ArrayList<>();
        for (QueryInfo queryInfo : queryInfoList) {
            for (List<ParameterSetOperation> operations : queryInfo.getParametersList()) {
                if (operations.isEmpty()) {
                    continue;
                }
                parameterSets.add(operations.stream()
                        .map(SqlDiagnosticsListener::describeValue)
                        .collect(Collectors.joining(", ", "(", ")")));
            }
        }
        return parameterSets;
    }

    private static String describeValue(ParameterSetOperation operation) {
        Object[] args = operation.getArgs();
        if (ParameterSetOperation.isSetNullParameterOperation(operation) || args.length < 2) {
            return "null";
        }
        String value = String.valueOf(args[1]);
        return value.length() > MAX_PARAMETER_LENGTH ? value.substring(0, MAX_PARAMETER_LENGTH) + "..." : value;
    }

    private static String describeParameters(QueryRecord query) {
        return query.parameters().isEmpty() ? "" : " | params " + String.join(" ", query.parameters());
    }
}
```

### SqlDiagnosticsReporter.java

```java
package com.pgoogol.diagnostics.sql;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Summarises a closed {@link SqlDiagnosticsScope}: statement count, time spent in JDBC,
 * statements repeated many times (typical N+1 or missing batching) and slow statements.
 */
public class SqlDiagnosticsReporter {

    private static final Logger log = LoggerFactory.getLogger(SqlDiagnosticsReporter.class);

    private static final Pattern STRING_LITERAL = Pattern.compile("'(?:[^']|'')*'");
    private static final Pattern NUMBER_LITERAL = Pattern.compile("\\b\\d+(?:\\.\\d+)?\\b");
    private static final Pattern PLACEHOLDER_LIST = Pattern.compile("\\(\\s*\\?(?:\\s*,\\s*\\?)+\\s*\\)");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final int MAX_SQL_LENGTH = 300;
    private static final int MAX_CALLERS = 3;

    private final SqlDiagnosticsProperties properties;

    public SqlDiagnosticsReporter(SqlDiagnosticsProperties properties) {
        this.properties = properties;
    }

    public void report(SqlDiagnosticsScope scope) {
        List<QueryRecord> queries = scope.queries();
        if (queries.isEmpty()) {
            return;
        }

        List<RepeatedStatement> repeated = findRepeated(queries);
        List<QueryRecord> slow = queries.stream()
                .filter(query -> query.elapsedMs() >= properties.slowQueryThreshold().toMillis())
                .sorted(Comparator.comparingLong(QueryRecord::elapsedMs).reversed())
                .toList();

        boolean suspicious = !repeated.isEmpty() || !slow.isEmpty();
        if (!suspicious && !log.isDebugEnabled()) {
            return;
        }

        long totalMs = queries.stream().mapToLong(QueryRecord::elapsedMs).sum();
        StringBuilder message = new StringBuilder()
                .append("SQL diagnostics: ").append(scope.name())
                .append(" -> ").append(queries.size()).append(" statements, ")
                .append(totalMs).append(" ms in JDBC");
        for (RepeatedStatement statement : repeated) {
            message.append("\n  repeated x").append(statement.count)
                    .append(", ").append(statement.totalMs).append(" ms: ").append(shorten(statement.sampleSql))
                    .append("\n    from ").append(String.join(", ", statement.callers));
        }
        for (QueryRecord query : slow) {
            message.append("\n  slow ").append(query.elapsedMs()).append(" ms: ").append(shorten(query.sql()))
                    .append("\n    from ").append(query.caller());
        }

        if (suspicious) {
            log.warn(message.toString());
        } else {
            log.debug(message.toString());
        }
    }

    private List<RepeatedStatement> findRepeated(List<QueryRecord> queries) {
        Map<String, RepeatedStatement> byShape = new LinkedHashMap<>();
        for (QueryRecord query : queries) {
            byShape.computeIfAbsent(normalize(query.sql()), shape -> new RepeatedStatement(query.sql())).add(query);
        }
        List<RepeatedStatement> repeated = new ArrayList<>();
        for (RepeatedStatement statement : byShape.values()) {
            if (statement.count >= properties.repeatedQueryThreshold()) {
                repeated.add(statement);
            }
        }
        repeated.sort(Comparator.comparingInt((RepeatedStatement statement) -> statement.count).reversed());
        return repeated;
    }

    /**
     * Reduces a statement to its shape, so executions that differ only by literals
     * or by the length of an IN list land in the same group.
     */
    static String normalize(String sql) {
        String shape = STRING_LITERAL.matcher(sql).replaceAll("?");
        shape = NUMBER_LITERAL.matcher(shape).replaceAll("?");
        shape = PLACEHOLDER_LIST.matcher(shape).replaceAll("(?)");
        return WHITESPACE.matcher(shape).replaceAll(" ").trim();
    }

    private static String shorten(String sql) {
        String singleLine = WHITESPACE.matcher(sql).replaceAll(" ").trim();
        return singleLine.length() > MAX_SQL_LENGTH ? singleLine.substring(0, MAX_SQL_LENGTH) + "..." : singleLine;
    }

    private static final class RepeatedStatement {

        private final String sampleSql;
        private final Set<String> callers = new LinkedHashSet<>();
        private int count;
        private long totalMs;

        private RepeatedStatement(String sampleSql) {
            this.sampleSql = sampleSql;
        }

        private void add(QueryRecord query) {
            count++;
            totalMs += query.elapsedMs();
            if (callers.size() < MAX_CALLERS) {
                callers.add(query.caller());
            }
        }
    }
}
```

### SqlDiagnosticsFilter.java

```java
package com.pgoogol.diagnostics.sql;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Opens one {@link SqlDiagnosticsScope} per HTTP request and reports it when the request ends.
 */
public class SqlDiagnosticsFilter extends OncePerRequestFilter {

    private final SqlDiagnosticsReporter reporter;

    public SqlDiagnosticsFilter(SqlDiagnosticsReporter reporter) {
        this.reporter = reporter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        SqlDiagnosticsScope scope = SqlDiagnosticsScope.open(request.getMethod() + " " + request.getRequestURI());
        try {
            filterChain.doFilter(request, response);
        } finally {
            scope.close();
            reporter.report(scope);
        }
    }
}
```

### DataSourceProxyBeanPostProcessor.java

```java
package com.pgoogol.diagnostics.sql;

import javax.sql.DataSource;

import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Wraps every DataSource bean in a datasource-proxy, so statements are observed at JDBC level
 * no matter which API issued them.
 */
public class DataSourceProxyBeanPostProcessor implements BeanPostProcessor {

    private final ObjectProvider<SqlDiagnosticsListener> listener;

    public DataSourceProxyBeanPostProcessor(ObjectProvider<SqlDiagnosticsListener> listener) {
        this.listener = listener;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource dataSource && !(bean instanceof ProxyDataSource)) {
            return ProxyDataSourceBuilder.create(dataSource)
                    .name(beanName)
                    .listener(listener.getObject())
                    .build();
        }
        return bean;
    }
}
```

### SqlDiagnosticsProperties.java

```java
package com.pgoogol.diagnostics.sql;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param enabled                turns the whole module on
 * @param slowQueryThreshold     statements at or above this time are reported as slow
 * @param repeatedQueryThreshold how many executions of the same statement in one scope count as suspicious
 * @param logParameters          include bound values in logs (may expose personal data)
 * @param applicationPackage     base package used to find the application code that triggered a statement
 */
@ConfigurationProperties(prefix = "diagnostics.sql")
public record SqlDiagnosticsProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("100ms") Duration slowQueryThreshold,
        @DefaultValue("5") int repeatedQueryThreshold,
        @DefaultValue("false") boolean logParameters,
        @DefaultValue("com.pgoogol") String applicationPackage) {
}
```

### SqlDiagnosticsConfiguration.java

```java
package com.pgoogol.diagnostics.sql;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Active only with {@code diagnostics.sql.enabled=true}. Import it explicitly
 * when this package lies outside the application's component scan.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "diagnostics.sql", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(SqlDiagnosticsProperties.class)
public class SqlDiagnosticsConfiguration {

    // static, so the post-processor is registered without instantiating this configuration early
    @Bean
    static DataSourceProxyBeanPostProcessor dataSourceProxyBeanPostProcessor(
            ObjectProvider<SqlDiagnosticsListener> listener) {
        return new DataSourceProxyBeanPostProcessor(listener);
    }

    @Bean
    SqlDiagnosticsListener sqlDiagnosticsListener(SqlDiagnosticsProperties properties) {
        return new SqlDiagnosticsListener(properties);
    }

    @Bean
    SqlDiagnosticsReporter sqlDiagnosticsReporter(SqlDiagnosticsProperties properties) {
        return new SqlDiagnosticsReporter(properties);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class ServletConfiguration {

        @Bean
        FilterRegistrationBean<SqlDiagnosticsFilter> sqlDiagnosticsFilter(SqlDiagnosticsReporter reporter) {
            FilterRegistrationBean<SqlDiagnosticsFilter> registration =
                    new FilterRegistrationBean<>(new SqlDiagnosticsFilter(reporter));
            // ahead of Spring Security, so statements issued by security filters are counted too
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
            return registration;
        }
    }
}
```
