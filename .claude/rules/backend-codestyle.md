---
paths:
  - "services/**"
  - "libs/java/**"
---

# Code style and naming

## Java 21 features

- Use **records** for DTOs and value objects — never plain POJOs with getters and
  setters as data carriers. JPA entities are the exception: Hibernate requires
  mutable classes.
- Use **sealed classes** for domain result types (`Success | Failure | NotFound`).
- Use **pattern matching** (`instanceof`, `switch`) instead of cascaded if-else.
- Use **virtual threads** (`Executors.newVirtualThreadPerTaskExecutor()`) for
  blocking I/O.
- **Do not use `var`** — always declare the explicit type of a local variable.
- Use text blocks for multi-line SQL, JSON and HTML.
- Jackson is **Jackson 3** (`tools.jackson.*`). Never import
  `com.fasterxml.jackson.databind` or `com.fasterxml.jackson.core` — annotations
  from `com.fasterxml.jackson.annotation` stay, because Jackson 3 kept that package.

## Naming

| Element | Convention | Example |
|---|---|---|
| Class | PascalCase, noun | `EnrichmentService`, `SpotifyClient` |
| Interface | PascalCase, noun or adjective | `Auditable`, `TrackCatalogRepository` |
| Method | camelCase, verb | `findById`, `resolveBpm` |
| Constant | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |
| Package | lowercase, singular | `com.pgoogol.music.catalog` |
| DTO | suffix `Request` / `Response` | `IngestFileRequest`, `TrackResponse` |
| Exception | suffix `Exception` | `TrackNotFoundException` |
| Config class | suffix `Config` | `EnrichmentJobConfig` |
| Entity | plain noun, no suffix | `TrackCatalog`, `Playlist` |

## Code structure

- Maximum method length: **30 lines** — extract if longer.
- Maximum class length: **300 lines** — split by responsibility.
- Never return `null` from a public method — use `Optional<T>` or throw a typed
  exception.
- No static utility classes — use Spring beans. Exception: test fixtures
  (Object Mother).
- Prefer `List.of()`, `Map.of()`, `Set.of()` for immutable collections.
- Annotate `@NonNull` / `@Nullable` (`org.springframework.lang`) on parameters and
  return types of public methods.

## Loops and chaining

- Avoid plain `for` loops, both indexed and `for (x : xs)`. Prefer `Stream` /
  `forEach` / declarative constructs. Exception: a plain loop is genuinely cheaper
  in time or complexity — then write in a comment why.
- Avoid chained calls like `a().b().c()`. Exception: Builder, `Optional`, `Stream`
  and the Mockito API.

## Comparisons and null checks

- `Objects.equals(a, b)` instead of `a.equals(b)` and `a == b`.
- `Objects.isNull(x)` / `Objects.nonNull(x)` instead of `x == null` / `x != null`.
- `Objects.requireNonNull(x, "message")` as a guard clause at the top of a method.
- `Objects.requireNonNullElse(x, default)` instead of ternary null checks.
- `Objects.toString(x, "fallback")` instead of `x != null ? x.toString() : "fallback"`.
- Exception: `== null` is fine inside `equals()` overrides and at the start of a
  null-check chain.

```java
// WRONG
if (track.getBpm() == null || track.getBpm().equals(other.getBpm())) { ... }
if (entry != null) return entry.getDjNotes();

// CORRECT
if (Objects.isNull(track.getBpm()) || Objects.equals(track.getBpm(), other.getBpm())) { ... }
return Objects.toString(entry, "unknown");
```

## Collections

Use Apache Commons `CollectionUtils` (`org.apache.commons.collections4`), not
Spring's limited variant.

- `CollectionUtils.isEmpty(col)` / `isNotEmpty(col)` instead of
  `col == null || col.isEmpty()`.
- `CollectionUtils.emptyIfNull(col)` instead of ternary null-to-empty-list guards.
- `CollectionUtils.containsAny(col, candidates)` instead of a manual
  `stream().anyMatch()` for simple membership checks.
- `CollectionUtils.intersection(a, b)` / `union(a, b)` / `subtract(a, b)` instead of
  manual set operations.

```java
// WRONG
if (tracks == null || tracks.isEmpty()) { ... }
List<String> tags = entry.getCustomTags() != null ? entry.getCustomTags() : Collections.emptyList();
boolean hasRole = roles.stream().anyMatch(allowed::contains);

// CORRECT
if (CollectionUtils.isEmpty(tracks)) { ... }
List<String> tags = CollectionUtils.emptyIfNull(entry.getCustomTags());
boolean hasRole = CollectionUtils.containsAny(roles, allowed);
```

## Formatting

- 4-space indentation, never tabs.
- Opening brace on the same line.
- Leave one blank line after the opening brace of a class or method body.
- Do not hand-format what a tool should format — Spotless (`./mvnw spotless:check`).

## Spring

- Inject through the constructor. Never `@Autowired` on a field. Never put
  `@Autowired` on the only constructor — Spring injects it anyway.
- Keep `@RestController` thin: input validation and delegation to a service,
  no business logic.
- Put `@Transactional` on the class only when ALL methods need it. Otherwise
  annotate individual methods.
- Read groups of related settings through `@ConfigurationProperties`; leave
  `@Value` for single scalars.

## Configuration

- Configure through `application.yml` + profiles. The `local` profile describes
  working against the environment from `deploy/compose`.
- Keep environment-dependent values (addresses, keys, models, limits) in
  configuration, never in code. This applies especially to the LLM provider,
  model and prompt version.
- Reference configuration as `${VARIABLE:default}` so the application starts
  without a full set of variables where that makes sense.

## External API clients

- Isolate every client in a dedicated class (`SpotifyClient`, `DeezerClient`).
  Never call a foreign API directly from a domain service.
- Every client has a rate limiter and retry with backoff — take them from the
  shared `common/ratelimit`, do not write your own each time.
- Enforce provider-imposed limits hard in the client code, do not rely on
  configuration. MusicBrainz: 1 req/s, no exceptions — retries are subject to the
  limit too.
- Set a `User-Agent` header with contact details where the provider requires it.

Retry and circuit breaker behaviour: see [backend-errors.md](backend-errors.md).
