---
paths:
  - "services/**"
  - "libs/java/**"
---

# Testing

## Tooling

| Kind | Tools |
|---|---|
| Unit | JUnit 5 + Mockito + AssertJ |
| Integration | `@SpringBootTest` + Testcontainers |
| Web layer | `@WebMvcTest` (MVC) / `@WebFluxTest` (WebFlux) |
| Repositories | `@DataJpaTest` + Testcontainers — **never H2** |
| External clients | WireMock against recorded responses |
| Contracts | a test comparing against `contracts/openapi/` |

No test reaches the network.

## Naming and structure

Name: `[methodUnderTest]_[condition]_[expectedResult]`.

```
ingestFile_whenTrackAlreadyExists_reportsAlreadyExisted
findById_whenTrackExists_returnsTrackResponse
```

Split every test body into sections with `// given`, `// when`, `// then`:

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

## Unit tests

- One `@Test` = one assertion concept. Group related fields with `assertAll`.
- Use `@ExtendWith(MockitoExtension.class)` — never `@SpringBootTest` for a pure
  unit test.
- Mock direct dependencies only, never transitive ones.
- Use `ArgumentCaptor` to verify what was passed to a mock, not just that it was
  called.
- Never test private methods directly — test behaviour through the public API.
- Never use `Thread.sleep()` — `Awaitility` is there for async assertions.

## Which phase a test runs in

Every test that starts a container carries `@Tag("integration")`. Surefire
excludes that tag, Failsafe runs only it — so `./mvnw test` needs no Docker and
`./mvnw verify` does.

The split is by tag, never by class name: `LibraryDistributionsRepositoryTest`
ends in `RepositoryTest` and is pure logic with no database. Tag what the test
actually needs, not what it happens to be called.

## Integration tests

- The container is the default. When the environment already has a Postgres —
  or cannot pull the image — set `TEST_POSTGRES_CONTAINER=false` and supply
  `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`. That is still a real Postgres, so
  the never-H2 rule holds; it is an escape hatch, not a second supported setup.
- `@Testcontainers` + a real database image (`postgres:16-alpine` — the same
  version as in `deploy/compose`).
- Share one container across the suite: `@ServiceConnection` in a shared
  `TestcontainersConfiguration`, or `@Container` + a `static` field.
- Load larger fixtures with `@Sql("/test-data/….sql")`, not with inserts pasted
  into the test method.
- Reset state between tests with `@Transactional` (rollback) or
  `@Sql(executionPhase = AFTER_TEST_METHOD)`.
- Test the full HTTP stack through `MockMvc` / `WebTestClient` — not by calling
  service methods directly.

## Test data builders

Use the Builder pattern or an Object Mother. Never repeat
`new TrackCatalog(...)` with a dozen arguments across tests.

```java
public final class TrackCatalogFixtures {

    public static TrackCatalog enrichedTrack(String spotifyId) {
        // full record with sensible domain values
    }
}
```

## Coverage

- Service layer: **≥ 80%** line coverage.
- Critical paths (data import, enrichment writes, library mutations, payments,
  auth): **100%** branch coverage.
- Do not chase the number — an untested edge case matters more than the percentage.
- Every public service method has a unit test.
- New logic means new tests in the same change.

## What NOT to test

- Spring internals (auto-configuration, bean wiring).
- Simple getters and setters on entities, unless they contain logic.
- Trivial one-liners that are obvious from the code.
