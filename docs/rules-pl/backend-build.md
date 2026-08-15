---
paths:
  - "services/**"
  - "libs/java/**"
---

# Build, wersje i granice modułów

## Build i wersje

- Buduj Mavenem przez `./mvnw`. Nigdy Gradle, nigdy `mvn` z systemu.
- **Nigdy nie wpisuj `<version>` do POM-u modułu** — ani przy zależności, ani przy
  wtyczce. Wersje należą do root `pom.xml`: `<properties>` +
  `<dependencyManagement>` + `<pluginManagement>`.
- Nigdy nie dodawaj `<parent>` wskazującego na `spring-boot-starter-parent`.
  Spring wchodzi wyłącznie jako `spring-boot-dependencies` ze `<scope>import</scope>`.
  Konsekwencja: `spring-boot-maven-plugin` nie dostaje celu `repackage` sam
  z siebie — deklaruj `<execution>` jawnie w module, który produkuje wykonywalny jar.
- Nową zależność dopisz najpierw do root POM-u, potem użyj w module.
- Konflikt wersji między modułami rozstrzygaj na wersję wyższą i zapisz w root POM.
  Nigdy nie nadpisuj wersji lokalnie w module.
- Uruchom `./mvnw -T 1C verify` przed uznaniem zadania za skończone. Czerwony build
  to zadanie niezrobione, niezależnie od tego, ile kodu powstało.

## Pakiety i granice

- Każdy pakiet zaczyna się od `com.pgoogol`. Bez wyjątków.
- Serwis w `services/<nazwa>-service` ma pakiet bazowy `com.pgoogol.<nazwa>`;
  biblioteka w `libs/java/<lib>` ma własną, jedną przestrzeń nazw pod
  `com.pgoogol`. Nigdy nie umieszczaj pakietów domenowych serwisu bezpośrednio
  pod `com.pgoogol` — kolidowałyby z kolejnym serwisem.
- **Dziel pakiety po funkcji, nigdy po warstwie.** Nie twórz `controller/`,
  `service/`, `repository/` na szczycie serwisu:

  ```
  com.pgoogol.<nazwa>/
    <domena>/            ← np. catalog, library, playlist
      <Domena>Service.java
      <Domena>Repository.java
      dto/
      domain/
    api/                 ← kontrolery REST, DTO żądań i odpowiedzi, mappery
    config/              ← konfiguracja Springa przecinająca domeny (np. OpenAPI)
    common/              ← dzielone wewnątrz tego serwisu (np. rate limiting)
    shared/exception/    ← hierarchia wyjątków serwisu
  ```

  `config/` nie jest pakietem warstwowym w przebraniu: trzyma wyłącznie
  konfigurację, która nie należy do żadnej domeny. Konfiguracja należąca do
  domeny zostaje przy niej — `EnrichmentJobConfig` mieszka w `enrichment/`.

- Trzymaj kod domenowy w module, do którego należy. Nie rozsypuj go po `common/`.
- **Serwis nigdy nie zależy od innego serwisu.** Jedyne zależności dozwolone
  wewnątrz reaktora to moduły z `libs/java/`. Potrzeba innej domeny oznacza
  wywołanie HTTP albo przeniesienie kodu do `libs/`.

## Kod dzielony to starter

Kod dzielony między serwisami pisz jako starter Spring Boota, nie zwykły jar:

- klasa oznaczona `@AutoConfiguration`,
- wpis w `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`,
- konfiguracja przez `@ConfigurationProperties` z prefiksem, nie `@Value`,
- każdy bean pod `@ConditionalOnMissingBean`, żeby serwis mógł go nadpisać,
- funkcjonalność włącza się przez samo dodanie zależności — zero kroków ręcznych
  w serwisie, zero `@Import` po stronie konsumenta.

Zależności startera na Springa oznaczaj `<optional>true</optional>`.
Nigdy nie publikuj starterów na zewnątrz — zależność zostaje wewnątrz reaktora.

## Zasady pracy

- Pytaj przed każdą operacją destrukcyjną: `DROP TABLE`, kasowanie danych,
  `git push --force`, czyszczenie wolumenów z danymi.
- Nigdy nie twórz README ani ADR-a bez wyraźnej prośby.
- Komunikaty commitów pisz po polsku, w trybie rozkazującym.
