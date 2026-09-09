package com.pgoogol.bearerauth.validation;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

import java.util.ArrayList;
import java.util.List;

/**
 * Komplet sprawdzeń tokenu identyfikacyjnego Google: termin ważności, wystawca
 * i odbiorca. Podpis sprawdza sam dekoder, kluczem pobranym z JWKS.
 *
 * <p>Jeden typ zamiast trzech osobno wpinanych walidatorów, żeby test wpinał
 * dokładnie ten łańcuch, który działa na produkcji — inaczej komplet sprawdzeń
 * byłby ustalany w dwóch miejscach i mógłby się rozjechać.</p>
 */
public class GoogleTokenValidator implements OAuth2TokenValidator<Jwt> {

    private final OAuth2TokenValidator<Jwt> delegate;

    public GoogleTokenValidator(List<String> issuers, String audience) {

        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        validators.add(new GoogleIssuerValidator(issuers));
        validators.add(new AudienceValidator(audience));
        this.delegate = new DelegatingOAuth2TokenValidator<>(validators);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {

        return delegate.validate(token);
    }
}
