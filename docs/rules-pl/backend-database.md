---
paths:
  - "services/**"
  - "libs/java/**"
---

# Baza danych i wydajność

## Migracje Flyway

- Każda zmiana schematu idzie przez migrację Flyway. Nigdy ręcznie, nigdy przez
  `ddl-auto`.
- Ustaw `spring.jpa.hibernate.ddl-auto=validate` — Flyway jest właścicielem
  schematu, Hibernate go tylko weryfikuje.
- Nazewnictwo: sekwencyjne `V{n}__opis.sql`, np. `V1__schemat_bazowy.sql`.
- Pisz migracje idempotentnie tam, gdzie się da (`IF NOT EXISTS`, `IF EXISTS`).
- **Nigdy nie modyfikuj zaaplikowanej migracji** — dopisz następną.
- Migracje muszą przechodzić w CI na Testcontainers, przeciw czystej bazie, zanim
  zmiana zostanie scalona.

## JPA i Hibernate

- Ustawiaj `fetch = FetchType.LAZY` na asocjacjach. `@ManyToOne` jest domyślnie
  EAGER — ustaw jawnie.
- Używaj `@EntityGraph` albo `JOIN FETCH` przeciw N+1.
- Włącz logowanie SQL w profilu deweloperskim, żeby wyłapywać N+1:
  ```yaml
  spring.jpa.show-sql: true
  spring.jpa.properties.hibernate.format_sql: true
  ```
- Preferuj projekcje (interfejsy lub rekordy) zamiast pobierania pełnych encji
  w zapytaniach tylko do odczytu.
- Stawiaj `@Version` na encjach modyfikowanych współbieżnie — blokowanie
  optymistyczne.

## Zapytania

- Używaj **derived queries** Spring Data dla prostych wyszukiwań (1–2 warunki).
- Używaj **JPQL przez `@Query`** dla złączeń i projekcji.
- Używaj **natywnego SQL-a** (`@Query(nativeQuery = true)`) tylko dla złożonych
  agregacji i cech specyficznych dla bazy (`pg_trgm`, `tsvector`).
- Używaj `@Modifying` + `@Transactional` do masowych update'ów i delete'ów —
  nigdy nie ładuj encji tylko po to, żeby je skasować.
- Zawsze zakładaj indeksy na: klucze obce, kolumny w `WHERE` / `ORDER BY` / `JOIN`,
  ograniczenia unikalności.

## Pula połączeń

Stroj HikariCP pod obciążenie serwisu; wartości domyślne bywają złe:

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

## Paginacja

- Nigdy nie zwracaj nieograniczonej listy z API — paginuj przez `Pageable`.
- Domyślny rozmiar strony **20**, maksymalny **100**; egzekwuj limit w kontrolerze.
- Paginacja offsetowa wystarcza do kilku tysięcy wierszy. Dla zbiorów większych
  o rząd wielkości przejdź na keyset (kursor).

## Cache

- Używaj abstrakcji Spring Cache (`@Cacheable`, `@CacheEvict`) — nie wpisuj wywołań
  cache'a wprost do serwisów.
- Cache'uj dane często czytane i rzadko zmieniane.
- Zawsze ustawiaj TTL — nigdy nie cache'uj bezterminowo. Wyjątek: fakty
  deterministyczne cache'owane trwale w bazie.
- Klucz cache'a musi zawierać każdy parametr wpływający na wynik.
- Testuj eviction jawnie — brakujące unieważnienie to częsty błąd.
