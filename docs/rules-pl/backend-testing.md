---
paths:
  - "services/**"
  - "libs/java/**"
---

# Testy

## Narzędzia

| Rodzaj | Narzędzia |
|---|---|
| Jednostkowe | JUnit 5 + Mockito + AssertJ |
| Integracyjne | `@SpringBootTest` + Testcontainers |
| Warstwa web | `@WebMvcTest` (MVC) / `@WebFluxTest` (WebFlux) |
| Repozytoria | `@DataJpaTest` + Testcontainers — **nigdy H2** |
| Klienci zewnętrzni | WireMock na nagranych odpowiedziach |
| Kontrakty | test porównujący z `contracts/openapi/` |

Żaden test nie wychodzi do sieci.

## Nazewnictwo i struktura

Nazwa: `[metoda]_[warunek]_[oczekiwany wynik]`.

```
ingestFile_whenTrackAlreadyExists_reportsAlreadyExisted
findById_whenTrackExists_returnsTrackResponse
```

Dziel każde ciało testu na sekcje komentarzami `// given`, `// when`, `// then`:

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
- Używaj `@ExtendWith(MockitoExtension.class)` — nigdy `@SpringBootTest` do czystego
  testu jednostkowego.
- Mockuj wyłącznie bezpośrednie zależności, nigdy przechodnie.
- Używaj `ArgumentCaptor`, żeby zweryfikować, co poszło do mocka — nie tylko to,
  że został wywołany.
- Nigdy nie testuj metod prywatnych wprost — testuj zachowanie przez API publiczne.
- Nigdy nie używaj `Thread.sleep()` — od asercji asynchronicznych jest `Awaitility`.

## Testy integracyjne

- `@Testcontainers` + realny obraz bazy (`postgres:16-alpine` — ta sama wersja co
  w `deploy/compose`).
- Współdziel jeden kontener w całej suicie: `@ServiceConnection` we wspólnej
  `TestcontainersConfiguration` albo `@Container` + pole `static`.
- Ładuj większe fixtures przez `@Sql("/test-data/….sql")`, nie insertami wklejonymi
  do metody testowej.
- Resetuj stan między testami przez `@Transactional` (rollback) albo
  `@Sql(executionPhase = AFTER_TEST_METHOD)`.
- Testuj pełny stos HTTP przez `MockMvc` / `WebTestClient` — nie przez bezpośrednie
  wołanie metod serwisu.

## Budowniczowie danych testowych

Używaj wzorca Builder albo Object Mother. Nigdy nie powtarzaj
`new TrackCatalog(...)` z kilkunastoma argumentami po testach.

```java
public final class TrackCatalogFixtures {

    public static TrackCatalog enrichedTrack(String spotifyId) {
        // pełny rekord z sensownymi wartościami domenowymi
    }
}
```

## Pokrycie

- Warstwa serwisów: **≥ 80%** pokrycia linii.
- Ścieżki krytyczne (import danych, zapisy wzbogacania, mutacje biblioteki,
  płatności, auth): **100%** pokrycia gałęzi.
- Nie goń za liczbą — nieprzetestowany przypadek brzegowy waży więcej niż procent.
- Każda publiczna metoda serwisu ma test jednostkowy.
- Nowa logika oznacza nowe testy w tej samej zmianie.

## Czego NIE testować

- Wnętrza Springa (auto-konfiguracja, wiązanie beanów).
- Prostych getterów i setterów na encjach, o ile nie zawierają logiki.
- Trywialnych jednolinijkowców oczywistych z kodu.
