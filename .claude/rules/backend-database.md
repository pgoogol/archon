---
paths:
  - "services/**"
  - "libs/java/**"
---

# Database and performance

## Flyway migrations

- Every schema change goes through a Flyway migration. Never by hand, never via
  `ddl-auto`.
- Set `spring.jpa.hibernate.ddl-auto=validate` — Flyway owns the schema, Hibernate
  only validates it.
- Naming: sequential `V{n}__description.sql`, e.g. `V1__schemat_bazowy.sql`.
- Write migrations idempotently where possible (`IF NOT EXISTS`, `IF EXISTS`).
- **Never modify a migration that has been applied** — add the next one.
- Migrations must pass in CI on Testcontainers, against a clean database, before
  the change is merged.

## JPA and Hibernate

- Set `fetch = FetchType.LAZY` on associations. `@ManyToOne` is EAGER by default —
  set it explicitly.
- Use `@EntityGraph` or `JOIN FETCH` against N+1.
- Enable SQL logging in the development profile to catch N+1:
  ```yaml
  spring.jpa.show-sql: true
  spring.jpa.properties.hibernate.format_sql: true
  ```
- Prefer projections (interfaces or records) over fetching full entities for
  read-only queries.
- Put `@Version` on entities updated concurrently — optimistic locking.

## Queries

- Use **Spring Data derived queries** for simple lookups (1–2 conditions).
- Use **JPQL through `@Query`** for joins and projections.
- Use **native SQL** (`@Query(nativeQuery = true)`) only for complex aggregations
  and database-specific features (`pg_trgm`, `tsvector`).
- Use `@Modifying` + `@Transactional` for bulk updates and deletes — never load
  entities just to delete them.
- Always add indexes for: foreign keys, columns in `WHERE` / `ORDER BY` / `JOIN`,
  unique constraints.

## Connection pool

Tune HikariCP for the service's load; the defaults are often wrong:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10        # local tool; a service under traffic: start at 20
      minimum-idle: 2
      connection-timeout: 3000     # 3s — fail fast
      max-lifetime: 1800000        # 30min, less than the database-side connection timeout
      leak-detection-threshold: 5000
```

## Pagination

- Never return an unbounded list from an API — paginate with `Pageable`.
- Default page size **20**, maximum **100**; enforce the cap in the controller.
- Offset pagination is enough up to a few thousand rows. For datasets an order of
  magnitude larger, switch to keyset (cursor) pagination.

## Caching

- Use the Spring Cache abstraction (`@Cacheable`, `@CacheEvict`) — do not hardcode
  cache calls inside services.
- Cache read-heavy, rarely-changing data.
- Always set a TTL — never cache indefinitely. Exception: deterministic facts
  cached permanently in the database.
- A cache key must include every parameter that affects the result.
- Test eviction explicitly — a missing eviction is a common bug.
