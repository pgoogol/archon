---
paths:
  - "services/**"
  - "libs/java/**"
---

# Reactive i WebFlux

Dotyczy serwisu zbudowanego na WebFluksie. Nigdy nie mieszaj łańcuchów reaktywnych
do serwisu na MVC.

- Używaj `.onErrorMap()` do zamiany wyjątków niskopoziomowych na domenowe.
- Używaj `.onErrorResume()` do logiki zapasowej.
- Nigdy nie blokuj wewnątrz łańcucha reaktywnego — opakuj wywołania blokujące:
  ```java
  Mono.fromCallable(() -> blockingRepository.findById(id))
      .subscribeOn(Schedulers.boundedElastic());
  ```
- Testuj warstwę web przez `@WebFluxTest` i `WebTestClient`.
