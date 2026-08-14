---
paths:
  - "services/**"
  - "libs/java/**"
---

# Bezpieczeństwo

## Sekrety

- Żadnych sekretów w repo: ani w kodzie, ani w `application*.yml`, ani w testach,
  ani w commitach. Wyłącznie zmienne środowiskowe i lokalny `.env` (jest
  w `.gitignore`); repo trzyma `.env.example` z pustymi wartościami.
- W `application.yml` odwołuj się do sekretów przez `${ZMIENNA:}` — nigdy nie
  wpisuj wartości.
- Klucz albo token, który pojawił się jawnie w czacie, logu czy commicie, jest
  spalony — zgłoś potrzebę rotacji zamiast usuwać go po cichu.
- Trzymaj tokeny OAuth (access i refresh) server-side, w bazie. Nigdy nie zwracaj
  refresh tokena w odpowiedzi API do frontu.
- Nigdy nie loguj danych wrażliwych: haseł, tokenów, kluczy API, danych osobowych.

## Walidacja wejścia

- Stawiaj `@Valid` / `@Validated` na każdym parametrze kontrolera przyjmującym
  ciało żądania.
- Definiuj ograniczenia na DTO, nie jako warunki w kodzie serwisu.
- Za błąd walidacji zwracaj `400 Bad Request` — nigdy `500`.
- Używaj `@Pattern`, `@Size`, `@NotBlank`. Nie pisz własnych walidatorów do tego,
  co pokrywa już Bean Validation.
- Traktuj wejście z zewnątrz jako niezaufane. Sanityzuj przed użyciem w SQL-u,
  ścieżkach plików i wywołaniach zewnętrznych — dotyczy to także zawartości plików
  wgrywanych przez użytkownika (CSV).

## Actuator

- Wystawiaj wąsko: `health,info` (lokalnie także `metrics`). Nigdy całego
  `/actuator`, nigdy `include: "*"`.

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: when-authorized
```

## Spring Security

Dotyczy serwisu, który wystawia uwierzytelnianie.

- Konfiguruj beanem **`SecurityFilterChain`**. Nigdy nie dziedzicz po
  `WebSecurityConfigurerAdapter`.
- Definiuj jawnie, które endpointy są publiczne; domyślnie odmawiaj wszystkiego:
  ```java
  http.authorizeHttpRequests(auth -> auth
      .requestMatchers("/api/public/**").permitAll()
      .anyRequest().authenticated()
  );
  ```
- Włącz `@EnableMethodSecurity` i stawiaj `@PreAuthorize` na metodach serwisu,
  nie tylko na kontrolerach. Używaj `hasRole('ADMIN')`, nie
  `hasAuthority('ROLE_ADMIN')`.
- Nigdy nie wyłączaj CSRF, chyba że budujesz bezstanowe REST API chronione
  JWT/OAuth2.
- Włącz nagłówki bezpieczeństwa:
  ```java
  http.headers(headers -> headers
      .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
      .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
      .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
  );
  ```

## Hasła

- Haszuj **BCryptem** (siła ≥ 12) przez bean `PasswordEncoder`.
- Nigdy MD5, SHA1 ani gołego SHA256. Nigdy `new BCryptPasswordEncoder()` w miejscu
  użycia.

## JWT i OAuth2

- Access token maksymalnie **15 minut**, refresh token maksymalnie **7 dni**.
- Waliduj zawsze: podpis, `exp`, `iss`, `aud`.
- Trzymaj refresh tokeny server-side (baza albo Redis), żeby dało się je unieważnić.
- Używaj `spring-security-oauth2-resource-server` — nigdy nie parsuj JWT ręcznie.
- Nigdy nie wkładaj wrażliwych claimów do payloadu tokena.
