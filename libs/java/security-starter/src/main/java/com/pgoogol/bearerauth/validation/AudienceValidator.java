package com.pgoogol.bearerauth.validation;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Objects;

/**
 * Sprawdza, czy token wystawiono dla tej aplikacji.
 *
 * <p>Bez tego sprawdzenia serwis przyjąłby dowolny poprawnie podpisany token
 * Google — także wystawiony dla zupełnie innej aplikacji, której właściciel
 * mógłby go tu podstawić.</p>
 */
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final String audience;

    public AudienceValidator(String audience) {

        this.audience = audience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {

        List<String> tokenAudience = token.getAudience();
        if (Objects.nonNull(tokenAudience) && tokenAudience.contains(audience)) {

            return OAuth2TokenValidatorResult.success();
        }
        OAuth2Error error = new OAuth2Error(
                OAuth2ErrorCodes.INVALID_TOKEN,
                "Token wystawiony dla innej aplikacji",
                null);
        return OAuth2TokenValidatorResult.failure(error);
    }
}
