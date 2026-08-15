---
paths:
  - "services/**"
  - "libs/java/**"
---

# Backend rules

Java 21, Spring Boot 4, Maven. Every service stands on its own; everything
shared between services lives in `libs/java/`.

| File | Scope |
|---|---|
| [backend-build.md](backend-build.md) | Maven reactor, versions, packages, shared starters |
| [backend-codestyle.md](backend-codestyle.md) | Java 21 features, naming, structure, Spring, config, API clients |
| [backend-database.md](backend-database.md) | Flyway, JPA, queries, connection pool, pagination, cache |
| [backend-errors.md](backend-errors.md) | Exception hierarchy, error format, transactions, logging, retry |
| [backend-security.md](backend-security.md) | Secrets, input validation, actuator, authentication |
| [backend-testing.md](backend-testing.md) | Tooling, naming, unit and integration tests, coverage |
| [backend-api.md](backend-api.md) | OpenAPI contract as the source of truth |
| [backend-messaging.md](backend-messaging.md) | Kafka and RabbitMQ |
| [backend-reactive.md](backend-reactive.md) | WebFlux and reactive chains |

Do not pull a technology into a service that does not need it. Rules for Kafka,
WebFlux and authentication describe how to do those things correctly — they are
not an instruction to add them.

## What enforces what

| Rule | Tool |
|---|---|
| plugin versions, Java 21, Maven ≥ 3.9.9 | `maven-enforcer-plugin` in `verify` |
| no `<version>` in module POMs | CI step scanning module POMs |
| `com.pgoogol` prefix, no service→service dependency | ArchUnit (`ArchitectureTest`) |
| formatting, indentation, braces | Spotless (`./mvnw spotless:check`) |
| schema matches migrations | `ddl-auto: validate` + Flyway on startup |
| contract matches code | contract test in `verify` |
| unit and integration tests | Surefire / Failsafe in `verify` |
| service layer coverage | JaCoCo in `verify` |
| no secrets in a commit | `gitleaks` in CI |

A faithful Polish copy of every rule file lives in `docs/rules-pl/`. When you
change a rule, change it in both places.
