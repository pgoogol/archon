---
paths:
  - "services/**"
  - "libs/java/**"
---

# Reactive and WebFlux

Applies to a service built on WebFlux. Never mix reactive chains into an MVC
service.

- Use `.onErrorMap()` to convert low-level exceptions into domain exceptions.
- Use `.onErrorResume()` for fallback logic.
- Never block inside a reactive chain — wrap blocking calls:
  ```java
  Mono.fromCallable(() -> blockingRepository.findById(id))
      .subscribeOn(Schedulers.boundedElastic());
  ```
- Test the web layer with `@WebFluxTest` and `WebTestClient`.
