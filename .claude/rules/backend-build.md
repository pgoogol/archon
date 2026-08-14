---
paths:
  - "services/**"
  - "libs/java/**"
---

# Build, versions and module boundaries

## Build and versions

- Build with Maven through `./mvnw` only. Never Gradle, never a system `mvn`.
- **Never put `<version>` in a module POM** — neither on a dependency nor on a
  plugin. Versions belong in the root `pom.xml`: `<properties>` +
  `<dependencyManagement>` + `<pluginManagement>`.
- Never add `<parent>` pointing at `spring-boot-starter-parent`. Spring enters
  only as `spring-boot-dependencies` with `<scope>import</scope>`.
  Consequence: `spring-boot-maven-plugin` does not get the `repackage` goal on
  its own — declare the `<execution>` explicitly in the module that produces an
  executable jar.
- Add a new dependency to the root POM first, then use it in the module.
- Resolve a version conflict between modules to the higher version and record it
  in the root POM. Never override a version locally in a module.
- Run `./mvnw -T 1C verify` before calling a task done. A red build is an
  unfinished task, no matter how much code exists.

## Packages and boundaries

- Every package starts with `com.pgoogol`. No exceptions.
- A service in `services/<name>-service` has base package `com.pgoogol.<name>`.
  Never create a package directly under `com.pgoogol` — it would collide with
  the next service.
- **Split packages by feature, never by layer.** Do not create `controller/`,
  `service/`, `repository/` at the top of a service:

  ```
  com.pgoogol.<name>/
    <domain>/            ← e.g. catalog, library, playlist
      <Domain>Service.java
      <Domain>Repository.java
      dto/
      domain/
    api/                 ← REST controllers, request/response DTOs, mappers
    common/              ← shared within this service (e.g. rate limiting)
    shared/exception/    ← the service's exception hierarchy
  ```

- Keep domain code in the module it belongs to. Do not scatter it into `common/`.
- **A service never depends on another service.** The only dependencies allowed
  inside the reactor are modules from `libs/java/`. Needing another domain means
  an HTTP call or moving the code into `libs/`.

## Shared code is a starter

Write code shared between services as a Spring Boot starter, not a plain jar:

- a class annotated `@AutoConfiguration`,
- an entry in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`,
- configuration through `@ConfigurationProperties` with a prefix, not `@Value`,
- every bean guarded by `@ConditionalOnMissingBean` so a service can override it,
- the feature switches on by adding the dependency alone — no manual steps in the
  service, no `@Import` on the consumer side.

Mark the starter's Spring dependencies `<optional>true</optional>`.
Never publish starters externally — the dependency stays inside the reactor.

## Working rules

- Ask before any destructive operation: `DROP TABLE`, deleting data,
  `git push --force`, wiping volumes that hold data.
- Never create a README or an ADR unless explicitly asked.
- Write commit messages in Polish, in the imperative mood.
