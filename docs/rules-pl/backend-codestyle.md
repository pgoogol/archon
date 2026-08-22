---
paths:
  - "services/**"
  - "libs/java/**"
---

# Styl kodu i nazewnictwo

## Możliwości Javy 21

- Używaj **rekordów** dla DTO i obiektów wartości — nigdy gołych POJO z getterami
  i setterami jako nośników danych. Encje JPA są wyjątkiem: Hibernate wymaga klas
  mutowalnych.
- Używaj **klas sealed** dla typów wynikowych domeny (`Success | Failure | NotFound`).
- Używaj **pattern matchingu** (`instanceof`, `switch`) zamiast kaskad if-else.
- Używaj **wątków wirtualnych** (`Executors.newVirtualThreadPerTaskExecutor()`)
  dla blokującego I/O.
- **Nie używaj `var`** — zawsze deklaruj jawny typ zmiennej lokalnej.
- Używaj text blocków dla wielolinijkowego SQL-a, JSON-a i HTML-a.
- **Nigdy nie sklejaj długiego łańcucha z literałów przez `+`.** Łańcuch, który
  nie mieści się w linii, idzie do text blocka. Gdy tekst ma zostać jedną linią
  (komunikaty logów, opisy `@Operation`, fragmenty SQL-a), kończ każdą linię
  znakiem `\` — kontynuacja zjada łamanie, więc treść zostaje co do bajta ta sama
  co przy konkatenacji. Spację na końcu linii chroń przez `\s`, inaczej zniknie
  jako biały znak nieistotny.
  ```java
  // ŹLE
  String sql = "select … from track_catalog t "
      + "where t.bpm is null";
  // DOBRZE
  String sql = """
      select … from track_catalog t \
      where t.bpm is null""";
  ```
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

- Maksymalna długość metody: **30 linii** — dłuższą wydziel.
- Maksymalna długość klasy: **300 linii** — dłuższą rozbij po odpowiedzialnościach.
- Nigdy nie zwracaj `null` z metody publicznej — użyj `Optional<T>` albo rzuć
  typowany wyjątek.
- **Nigdy nie odwołuj się do decyzji projektowej z kodu, komentarza ani
  konfiguracji.** Czytelnik bez `docs/` pod ręką nie rozwiąże `(D19)`, więc pisz
  to, co decyzja mówi: nie „kryterium (D19)", tylko „kryterium: pomiar czy
  estymata". Jedyny wyjątek to zastosowana migracja Flyway — jej suma kontrolna
  obejmuje także komentarze, więc edycja wywala walidację na istniejących bazach.
- Żadnych statycznych klas narzędziowych — używaj beanów Springa. Wyjątki:
  fixtures testowe (Object Mother) oraz klasa ze stałymi bez żadnego zachowania,
  jak `ExceptionMessageConstants`.
- **Nigdy nie używaj operatora warunkowego (ternary).** Zamiast tego `if`
  z wczesnym wyjściem — warunek schowany w wyrażeniu czyta się dwa razy,
  a zagnieżdżony trzy.
  ```java
  // ŹLE
  return Objects.isNull(parentId) ? null : get(parentId);
  // DOBRZE
  if (Objects.isNull(parentId)) {
      return null;
  }
  return get(parentId);
  ```
- **Nigdy nie przekazuj wyniku wywołania jako argumentu innego wywołania.**
  Najpierw nazwana zmienna lokalna — nazwa mówi, czym jest wartość, a stack trace
  wskazuje jedną linię zamiast gniazda wywołań.
  ```java
  // ŹLE
  return mapper.toResponses(accountService.list(includeArchived));
  // DOBRZE
  List<Account> accounts = accountService.list(includeArchived);
  return mapper.toResponses(accounts);
  ```
- Preferuj `List.of()`, `Map.of()`, `Set.of()` dla kolekcji niemodyfikowalnych.
- Oznaczaj `@NonNull` / `@Nullable` (`org.springframework.lang`) na parametrach
  i typach zwracanych metod publicznych.

## Pętle i łańcuchy wywołań

- Unikaj gołych pętli `for`, zarówno indeksowanych, jak i `for (x : xs)`. Preferuj
  `Stream` / `forEach` / konstrukcje deklaratywne. Wyjątek: pętla jest realnie
  tańsza czasowo lub prostsza — wtedy napisz w komentarzu dlaczego.
- Unikaj łańcuchów `a().b().c()`. Wyjątek: Builder, `Optional`, `Stream` i API
  Mockito.
- **Ciało lambdy dłuższe niż 3 linie wydziel do osobnej metody.** `forEach`
  i `map` z blokiem w środku przestają się czytać jak potok, a zaczynają ukrywać
  logikę, której nic nie przetestuje osobno.
  ```java
  // ŹLE
  flat.forEach(category -> {
      CategoryNode node = node(category, childrenByParent);
      Long parentId = category.getParentId();
      if (Objects.isNull(parentId)) { roots.add(node); }
      else { childrenByParent.get(parentId).add(node); }
  });
  // DOBRZE
  flat.forEach(category -> attach(category, loaded, childrenByParent, roots));
  ```

## Porównania i sprawdzanie null

- `Objects.equals(a, b)` zamiast `a.equals(b)` i `a == b`.
- `Objects.isNull(x)` / `Objects.nonNull(x)` zamiast `x == null` / `x != null`.
- `Objects.requireNonNull(x, "komunikat")` jako strażnik na początku metody.
- `Objects.requireNonNullElse(x, domyślna)` zamiast ternarnych sprawdzeń null.
- `Objects.toString(x, "fallback")` zamiast `x != null ? x.toString() : "fallback"`.
- Wyjątek: `== null` jest w porządku w nadpisaniach `equals()` i na początku
  łańcucha sprawdzeń null.

```java
// ŹLE
if (track.getBpm() == null || track.getBpm().equals(other.getBpm())) { ... }
if (entry != null) return entry.getDjNotes();

// DOBRZE
if (Objects.isNull(track.getBpm()) || Objects.equals(track.getBpm(), other.getBpm())) { ... }
return Objects.toString(entry, "unknown");
```

## Kolekcje

Używaj Apache Commons `CollectionUtils` (`org.apache.commons.collections4`), nie
okrojonego wariantu ze Springa.

- `CollectionUtils.isEmpty(col)` / `isNotEmpty(col)` zamiast
  `col == null || col.isEmpty()`.
- `CollectionUtils.emptyIfNull(col)` zamiast ternarnych strażników
  null→pusta lista.
- `CollectionUtils.containsAny(col, kandydaci)` zamiast ręcznego
  `stream().anyMatch()` przy prostym sprawdzeniu przynależności.
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
- Nie formatuj ręcznie tego, co ma formatować narzędzie — Spotless
  (`./mvnw spotless:check`).

## Spring

- Wstrzykuj przez konstruktor. Nigdy `@Autowired` na polu. Nigdy nie stawiaj
  `@Autowired` na jedynym konstruktorze — Spring i tak wstrzyknie.
- Trzymaj `@RestController` cienki: walidacja wejścia i delegacja do serwisu,
  zero logiki biznesowej.
- Stawiaj `@Transactional` na klasie tylko wtedy, gdy WSZYSTKIE metody go
  potrzebują. W przeciwnym razie oznaczaj pojedyncze metody.
- Czytaj grupy powiązanych ustawień przez `@ConfigurationProperties`; `@Value`
  zostaw dla pojedynczych skalarów.

## Konfiguracja

- Konfiguruj przez `application.yml` + profile. Profil `local` opisuje pracę
  ze środowiskiem z `deploy/compose`.
- Trzymaj wartości zależne od środowiska (adresy, klucze, modele, limity)
  w konfiguracji, nigdy w kodzie. Dotyczy to zwłaszcza providera LLM, modelu
  i wersji promptu.
- Odwołuj się do konfiguracji przez `${ZMIENNA:domyślna}`, żeby aplikacja wstawała
  bez kompletu zmiennych tam, gdzie to ma sens.

## Klienci zewnętrznych API

- Izoluj każdego klienta w dedykowanej klasie (`SpotifyClient`, `DeezerClient`).
  Nigdy nie wołaj obcego API bezpośrednio z serwisu domenowego.
- Każdy klient ma limiter i retry z backoffem — bierz je ze wspólnego
  `common/ratelimit`, nie pisz własnych za każdym razem.
- Egzekwuj limity narzucone przez dostawcę twardo w kodzie klienta, nie licz
  na konfigurację. MusicBrainz: 1 req/s, bez wyjątków — retry też podlega limitowi.
- Ustawiaj nagłówek `User-Agent` z kontaktem tam, gdzie dostawca tego wymaga.

Zachowanie retry i circuit breakera: patrz [backend-errors.md](backend-errors.md).
