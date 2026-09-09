package com.pgoogol.bearerauth.validation;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Objects;

/**
 * Sprawdza wystawcę tokenu.
 *
 * <p>Własny walidator zamiast {@code JwtIssuerValidator}, bo tamten przyjmuje
 * dokładnie jedną wartość, a Google wystawia tokeny identyfikacyjne raz jako
 * {@code https://accounts.google.com}, raz jako {@code accounts.google.com}.</p>
 */
public class GoogleIssuerValidator implements OAuth2TokenValidator<Jwt> {

    private final List<String> issuers;

    public GoogleIssuerValidator(List<String> issuers) {

        this.issuers = List.copyOf(issuers);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {

        String issuer = token.getClaimAsString("iss");
        if (Objects.nonNull(issuer) && issuers.contains(issuer)) {

            return OAuth2TokenValidatorResult.success();
        }
        OAuth2Error error = new OAuth2Error(
                OAuth2ErrorCodes.INVALID_TOKEN,
                "Token wystawiony przez nieznanego wystawcę",
                null);
        return OAuth2TokenValidatorResult.failure(error);
    }
}
