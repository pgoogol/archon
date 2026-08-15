---
paths:
  - "services/**"
  - "libs/java/**"
---

# Obsługa błędów i logowanie

## Hierarchia wyjątków

Trzymaj typowaną hierarchię wyjątków na domenę:

```
AppException (abstract, RuntimeException)
  ├── NotFoundException          → 404
  ├── ValidationException        → 400
  ├── ConflictException          → 409
  ├── UnauthorizedException      → 401
  ├── ForbiddenException         → 403
  └── ExternalServiceException   → 502
```

- Każdy wyjątek niesie pole `errorCode` czytelne maszynowo (`TRACK_NOT_FOUND`),
  nie sam komunikat tekstowy.
- Nigdy nie rzucaj gołego `RuntimeException` ani `Exception` z kodu biznesowego.

## Globalny handler wyjątków

- Zbierz obsługę w jednym `@RestControllerAdvice`. Nie rozsypuj `@ExceptionHandler`
  po kontrolerach.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(NotFoundException ex) {
        log.warn("Resource not found: {}", ex.getErrorCode());
        return ErrorResponse.of(ex.getErrorCode(), ex.getMessage());
    }
}
```

## Format odpowiedzi błędu

Zawsze zwracaj ten sam kształt:

```json
{
  "errorCode": "TRACK_NOT_FOUND",
  "message": "Track with spotify id 4uLU6hMCjMI75M1A2tKUQC does not exist",
  "timestamp": "2026-07-03T12:00:00Z",
  "traceId": "abc-123"
}
```

- Bierz `traceId` z MDC / Micrometer tracing.
- Nigdy nie wypuszczaj stack trace'a, SQL-a ani nazw klas wewnętrznych do body.

## Obsługa błędów w transakcjach

- Oznacz metodę `@Transactional(rollbackFor = Exception.class)`, gdy ma się wycofać
  także przy wyjątkach kontrolowanych.
- Spring domyślnie wycofuje tylko na `RuntimeException` — bądź jawny, gdy
  potrzebujesz inaczej.
- Nigdy nie połykaj wyjątków wewnątrz metod `@Transactional` — przerzuć dalej albo
  opakuj.

## Logowanie

- Loguj przez **SLF4J** z Logbackiem. Nigdy `System.out.println`.

  | Poziom | Do czego |
  |---|---|
  | `ERROR` | Awarie nie do odratowania, ryzyko utraty danych, padnięty serwis zewnętrzny |
  | `WARN` | Problemy odwracalne, retry, uruchomione fallbacki |
  | `INFO` | Zdarzenia biznesowe (import zakończony, job wystartował) |
  | `DEBUG` | Szczegóły żądań i odpowiedzi, wejście i wyjście metod — tylko dev |
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
- Nigdy nie loguj w ciasnej pętli — zaloguj podsumowanie po niej.

## Retry i circuit breakery

- Używaj **Resilience4j**. Nie używaj Spring Retry w nowym kodzie.
- Konfiguruj retry wyłącznie dla operacji **idempotentnych** (GET, PUT, DELETE,
  odczyty z klientów zewnętrznych).
- Nigdy nie ponawiaj na `4xx` — tylko na `5xx` i timeoutach. Wyjątek:
  `429 Too Many Requests` → honoruj nagłówek `Retry-After` zamiast zwykłego
  backoffu.
- Załóż circuit breaker na każde zewnętrzne wywołanie HTTP.

Struktura klientów i limitowanie: patrz [backend-codestyle.md](backend-codestyle.md).
