package com.pgoogol.bearerauth.support;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Wystawia tokeny podpisane kluczem wygenerowanym na potrzeby testu. Dzięki
 * temu testy nie sięgają do sieci ani do prawdziwego konta Google, a mimo to
 * przechodzą przez ten sam dekoder i te same walidatory co produkcja.
 */
public class TestTokens {

    public static final String ISSUER = "https://accounts.google.com";

    public static final String AUDIENCE = "klient-testowy.apps.googleusercontent.com";

    public static final String ALLOWED_EMAIL = "wlasciciel@example.com";

    private final KeyPair keyPair;

    public TestTokens() {

        this.keyPair = generateKeyPair();
    }

    public RSAPublicKey publicKey() {

        return (RSAPublicKey) keyPair.getPublic();
    }

    /** Token, który powinien przejść komplet sprawdzeń. */
    public String valid() {

        return sign(claims().build());
    }

    public String withEmail(String email, boolean emailVerified) {

        JWTClaimsSet claims = claims()
                .claim("email", email)
                .claim("email_verified", emailVerified)
                .build();
        return sign(claims);
    }

    public String withIssuer(String issuer) {

        return sign(claims().issuer(issuer).build());
    }

    public String withAudience(String audience) {

        return sign(claims().audience(audience).build());
    }

    public String expired() {

        Instant past = Instant.now().minusSeconds(3600);
        JWTClaimsSet claims = claims()
                .issueTime(Date.from(past.minusSeconds(60)))
                .expirationTime(Date.from(past))
                .build();
        return sign(claims);
    }

    /** Token o poprawnej treści, ale podpisany obcym kluczem. */
    public String signedByStranger() {

        KeyPair stranger = generateKeyPair();
        return sign(claims().build(), (RSAPrivateKey) stranger.getPrivate());
    }

    private JWTClaimsSet.Builder claims() {

        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .subject("123456789")
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .claim("email", ALLOWED_EMAIL)
                .claim("email_verified", true)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(3600)));
    }

    private String sign(JWTClaimsSet claims) {

        return sign(claims, (RSAPrivateKey) keyPair.getPrivate());
    }

    private String sign(JWTClaimsSet claims, RSAPrivateKey privateKey) {

        try {

            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner(privateKey));
            return jwt.serialize();
        } catch (Exception exception) {

            throw new IllegalStateException("Nie udało się podpisać tokenu testowego", exception);
        }
    }

    private static KeyPair generateKeyPair() {

        try {

            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {

            throw new IllegalStateException("Nie udało się wygenerować klucza testowego", exception);
        }
    }
}
