---
paths:
  - "services/**"
  - "libs/java/**"
---

# Error handling and logging

## Exception hierarchy

Keep a typed exception hierarchy per domain:

```
AppException (abstract, RuntimeException)
  ├── NotFoundException          → 404
  ├── ValidationException        → 400
  ├── ConflictException          → 409
  ├── UnauthorizedException      → 401
  ├── ForbiddenException         → 403
  └── ExternalServiceException   → 502
```

- Every exception carries a machine-readable `errorCode` field (`TRACK_NOT_FOUND`),
  not just a message string.
- **Keep the codes themselves in one `ErrorCodes` class per service**, never as a
  literal at the throw site. The code is the contract the frontend switches on, so
  a literal typed from memory is a contract written from memory — and one list is
  the only answer to "which codes can this service return" that isn't `grep`.
- Never throw a raw `RuntimeException` or `Exception` from business code.
- **Keep the message texts in one `ExceptionMessageConstants` class per service**,
  not inline at the throw site. Scattered texts drift apart in tone and detail,
  and rewording one means hunting through the whole module. The `errorCode` stays
  at the throw site — it is the contract for the client; the text is only for
  a human.
- Do not add an exception type beyond the hierarchy above. A new type is worth it
  only when the handler must map it to a different status code — otherwise the
  `errorCode` already tells the client what happened.

## Global exception handler

- Collect handling in a single `@RestControllerAdvice`. Do not scatter
  `@ExceptionHandler` across controllers.

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

## Error response format

Always return the same shape:

```json
{
  "errorCode": "TRACK_NOT_FOUND",
  "message": "Track with spotify id 4uLU6hMCjMI75M1A2tKUQC does not exist",
  "timestamp": "2026-07-03T12:00:00Z",
  "traceId": "abc-123"
}
```

- Take `traceId` from MDC / Micrometer tracing.
- Never expose stack traces, SQL or internal class names in the response body.

## Transactional error handling

- Mark a method `@Transactional(rollbackFor = Exception.class)` when it must roll
  back on checked exceptions too.
- Spring rolls back on `RuntimeException` only by default — be explicit when you
  need otherwise.
- Never swallow exceptions inside `@Transactional` methods — rethrow or wrap them.

## Logging

- Log through **SLF4J** with Logback. Never `System.out.println`.

  | Level | Use for |
  |---|---|
  | `ERROR` | Unrecoverable failures, data loss risk, external service down |
  | `WARN` | Recoverable issues, retries, fallbacks triggered |
  | `INFO` | Business events (import finished, job started) |
  | `DEBUG` | Request and response details, method entry and exit — dev only |
  | `TRACE` | Raw SQL, full payloads — never in production |

- Always use parameterized logging, never concatenation:
  ```java
  // WRONG
  log.info("Enriching track " + spotifyId + " with fields " + fields);
  // CORRECT
  log.info("Enriching track {} with fields {}", spotifyId, fields);
  ```
- Put `traceId` into MDC at the start of every request (filter or interceptor).
- Never log secrets, tokens, API keys or personal data.
- Never log inside a tight loop — log a summary after it.

## Retries and circuit breakers

- Use **Resilience4j**. Do not use Spring Retry in new code.
- Configure retry only for **idempotent** operations (GET, PUT, DELETE, reads from
  external clients).
- Never retry on `4xx` — only on `5xx` and timeouts. Exception:
  `429 Too Many Requests` → honour the `Retry-After` header instead of the usual
  backoff.
- Put a circuit breaker on every external HTTP call.

Client structure and rate limiting: see [backend-codestyle.md](backend-codestyle.md).
