---
paths:
  - "services/**"
  - "libs/java/**"
---

# Reguły backendu

Java 21, Spring Boot 4, Maven. Każdy serwis stoi osobno; wszystko dzielone między
serwisami mieszka w `libs/java/`.

| Plik | Zakres |
|---|---|
| [backend-build.md](backend-build.md) | Reaktor Mavena, wersje, pakiety, startery dzielone |
| [backend-codestyle.md](backend-codestyle.md) | Java 21, nazewnictwo, struktura, Spring, konfiguracja, klienci API |
| [backend-database.md](backend-database.md) | Flyway, JPA, zapytania, pula połączeń, paginacja, cache |
| [backend-errors.md](backend-errors.md) | Hierarchia wyjątków, format błędów, transakcje, logowanie, retry |
| [backend-security.md](backend-security.md) | Sekrety, walidacja wejścia, actuator, uwierzytelnianie |
| [backend-testing.md](backend-testing.md) | Narzędzia, nazewnictwo, testy jednostkowe i integracyjne, pokrycie |
| [backend-api.md](backend-api.md) | Kontrakt OpenAPI jako źródło prawdy |
| [backend-messaging.md](backend-messaging.md) | Kafka i RabbitMQ |
| [backend-reactive.md](backend-reactive.md) | WebFlux i łańcuchy reaktywne |

Nie wciągaj do serwisu technologii, której nie potrzebuje. Reguły dla Kafki,
WebFluksa i uwierzytelniania opisują, jak zrobić te rzeczy poprawnie — nie są
poleceniem, żeby je dodać.

## Co wymusza którą regułę

| Reguła | Narzędzie |
|---|---|
| wersje wtyczek, Java 21, Maven ≥ 3.9.9 | `maven-enforcer-plugin` w `verify` |
| brak `<version>` w POM-ach modułów | krok CI skanujący POM-y modułów |
| prefiks `com.pgoogol`, brak zależności serwis→serwis | ArchUnit (`ArchitectureTest`) |
| formatowanie, wcięcia, klamry | Spotless (`./mvnw spotless:check`) |
| schemat zgodny z migracjami | `ddl-auto: validate` + Flyway na starcie |
| kontrakt zgodny z kodem | test kontraktowy w `verify` |
| testy jednostkowe i integracyjne | Surefire / Failsafe w `verify` |
| pokrycie warstwy serwisów | JaCoCo w `verify` |
| brak sekretów w commicie | `gitleaks` w CI |

To jest polska kopia reguł. Wersja obowiązująca, ładowana automatycznie, leży
w `.claude/rules/`. Zmieniasz regułę — zmieniasz w obu miejscach.
