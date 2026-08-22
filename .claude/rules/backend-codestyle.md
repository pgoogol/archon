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
- **Never build a long string by concatenating literals with `+`.** A string too
  long for one line goes into a text block. When the text must stay a single line
  (log messages, `@Operation` descriptions, SQL fragments), end each line with `\`
  — the continuation eats the line break, so the content stays byte for byte what
  concatenation produced. Use `\s` to protect a trailing space that would
  otherwise be stripped as incidental white space.
  ```java
  // WRONG
  String sql = "select … from track_catalog t "
      + "where t.bpm is null";
  // CORRECT
  String sql = """
      select … from track_catalog t \
      where t.bpm is null""";
  ```
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
- **Never reference a decision record from code, comments or configuration.**
  A reader without `docs/` in front of them cannot resolve `(D19)`, so write what
  the decision says: not "criterion (D19)" but "criterion: measured or estimated".
  The one exception is an applied Flyway migration — its checksum covers comments
  too, so editing one breaks validation on every existing database.
- No static utility classes — use Spring beans. Exceptions: test fixtures
  (Object Mother) and a constants holder with no behaviour, such as
  `ExceptionMessageConstants`.
- **Never use the ternary operator.** Write `if` with an early return instead —
  a conditional buried inside an expression is read twice, and a nested one is
  read three times.
  ```java
  // WRONG
  return Objects.isNull(parentId) ? null : get(parentId);
  // CORRECT
  if (Objects.isNull(parentId)) {
      return null;
  }
  return get(parentId);
  ```
- **Never pass the result of a call as an argument to another call.** Give it a
  named local first — the name says what the value is, and a stack trace points
  at one line instead of a nest.
  ```java
  // WRONG
  return mapper.toResponses(accountService.list(includeArchived));
  // CORRECT
  List<Account> accounts = accountService.list(includeArchived);
  return mapper.toResponses(accounts);
  ```
- Prefer `List.of()`, `Map.of()`, `Set.of()` for immutable collections.
- Annotate `@NonNull` / `@Nullable` (`org.springframework.lang`) on parameters and
  return types of public methods.

## Loops and chaining

- Avoid plain `for` loops, both indexed and `for (x : xs)`. Prefer `Stream` /
  `forEach` / declarative constructs. Exception: a plain loop is genuinely cheaper
  in time or complexity — then write in a comment why.
- Avoid chained calls like `a().b().c()`. Exception: Builder, `Optional`, `Stream`
  and the Mockito API.
- **A lambda body longer than 3 lines goes into its own method.** `forEach` and
  `map` with a block inside stop reading as a pipeline and start hiding logic that
  nothing can test on its own.
  ```java
  // WRONG
  flat.forEach(category -> {
      CategoryNode node = node(category, childrenByParent);
      Long parentId = category.getParentId();
      if (Objects.isNull(parentId)) { roots.add(node); }
      else { childrenByParent.get(parentId).add(node); }
  });
  // CORRECT
  flat.forEach(category -> attach(category, loaded, childrenByParent, roots));
  ```

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
- Generate that constructor with Lombok's **`@RequiredArgsConstructor`** — a
  hand-written constructor that only assigns `final` fields is a place to forget
  a field, and adding a dependency means editing three lines instead of one.
  Write the constructor by hand only when it does something beyond assignment
  (building a `RestClient`, deriving a value from properties). Lombok stops at
  that annotation: no `@Data`, no `@Builder`, no `@Getter`/`@Setter` on entities —
  a JPA entity's accessors stay visible in the source, because that is where
  lazy loading and `equals` go wrong.
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
