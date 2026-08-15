---
paths:
  - "services/**"
  - "libs/java/**"
---

# Security

## Secrets

- No secrets in the repository: not in code, not in `application*.yml`, not in
  tests, not in commits. Environment variables and a local `.env` only (it is in
  `.gitignore`); the repository holds `.env.example` with empty values.
- In `application.yml` reference secrets as `${VARIABLE:}` — never write the value.
- A key or token that appeared in plain text in a chat, a log or a commit is
  burned — report that it needs rotation instead of quietly removing it.
- Store OAuth tokens (access and refresh) server-side, in the database. Never
  return a refresh token in an API response to the frontend.
- Never log sensitive data: passwords, tokens, API keys, personal identifiers.

## Input validation

- Put `@Valid` / `@Validated` on every controller parameter that accepts a request
  body.
- Define constraints on the DTO, not as conditions in service code.
- Return `400 Bad Request` for validation failures — never `500`.
- Use `@Pattern`, `@Size`, `@NotBlank`. Do not write custom validators for what
  Bean Validation already covers.
- Treat external input as untrusted. Sanitize before using it in SQL, file paths
  or external commands — this includes the contents of user-uploaded files (CSV).

## Actuator

- Expose narrowly: `health,info` (locally `metrics` as well). Never the whole
  `/actuator`, never `include: "*"`.

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

Applies to a service that exposes authentication.

- Configure with a **`SecurityFilterChain`** bean. Never extend
  `WebSecurityConfigurerAdapter`.
- Define explicitly which endpoints are public; deny everything by default:
  ```java
  http.authorizeHttpRequests(auth -> auth
      .requestMatchers("/api/public/**").permitAll()
      .anyRequest().authenticated()
  );
  ```
- Enable `@EnableMethodSecurity` and put `@PreAuthorize` on service methods, not
  only on controllers. Use `hasRole('ADMIN')`, not `hasAuthority('ROLE_ADMIN')`.
- Never disable CSRF unless you are building a stateless REST API protected by
  JWT/OAuth2.
- Enable security headers:
  ```java
  http.headers(headers -> headers
      .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
      .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
      .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
  );
  ```

## Passwords

- Hash with **BCrypt** (strength ≥ 12) through a `PasswordEncoder` bean.
- Never MD5, SHA1 or plain SHA256. Never `new BCryptPasswordEncoder()` inline.

## JWT and OAuth2

- Access token max **15 minutes**, refresh token max **7 days**.
- Always validate: signature, `exp`, `iss`, `aud`.
- Store refresh tokens server-side (database or Redis) so they can be revoked.
- Use `spring-security-oauth2-resource-server` — never hand-roll JWT parsing.
- Never put sensitive claims in the token payload.
