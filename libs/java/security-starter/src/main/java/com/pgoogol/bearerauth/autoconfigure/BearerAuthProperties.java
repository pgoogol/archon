package com.pgoogol.bearerauth.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Ustawienia uwierzytelniania. Dwie niezależne drogi wejścia: token Google
 * niesiony jako Bearer oraz konto lokalne z konfiguracji, niesione jako Basic.
 *
 * <p>Droga Google włącza się dopiero po podaniu {@code audience} — bez
 * identyfikatora klienta nie ma czego sprawdzać w tokenie, więc walidacja
 * w ogóle się nie rejestruje.</p>
 */
@ConfigurationProperties(prefix = "bearer-auth")
public class BearerAuthProperties {

    /** Hash BCrypt hasła {@code admin}. Wartość domyślna konta deweloperskiego. */
    public static final String DEFAULT_PASSWORD_HASH =
            "$2a$12$BvwzPFLCgfGMGbvLuk.S8eag3KABV0PHfzaXdFyvnI9g3pruZuw0q";

    private boolean enabled = true;

    /**
     * Klucze publiczne Google. Świadomie adres JWKS, a nie issuer-uri: ten drugi
     * pobiera dokument discovery przy starcie kontekstu, więc aplikacja nie
     * wstałaby bez sieci. Klucze spod tego adresu ściągają się leniwie, przy
     * pierwszym tokenie.
     */
    private String jwkSetUri = "https://www.googleapis.com/oauth2/v3/certs";

    /** Google wystawia tokeny w obu tych formach zapisu wystawcy. */
    private List<String> issuers = new ArrayList<>(List.of(
            "https://accounts.google.com",
            "accounts.google.com"
    ));

    /** Identyfikator klienta OAuth — token musi mieć go w polu {@code aud}. */
    private String audience = "";

    /** Adresy, które wolno wpuścić. Konto spoza listy dostaje 403, nie 401. */
    private List<String> allowedEmails = new ArrayList<>();

    /**
     * Ścieżki bez uwierzytelniania. Domyślnie tylko sonda zdrowia, bo używa jej
     * healthcheck kontenera — jej zamknięcie psuje kolejność startu w compose.
     */
    private List<String> publicPaths = new ArrayList<>(List.of("/actuator/health"));

    private LocalAccount localAccount = new LocalAccount();

    public boolean isEnabled() {

        return enabled;
    }

    public void setEnabled(boolean enabled) {

        this.enabled = enabled;
    }

    public String getJwkSetUri() {

        return jwkSetUri;
    }

    public void setJwkSetUri(String jwkSetUri) {

        this.jwkSetUri = jwkSetUri;
    }

    public List<String> getIssuers() {

        return issuers;
    }

    public void setIssuers(List<String> issuers) {

        this.issuers = issuers;
    }

    public String getAudience() {

        return audience;
    }

    public void setAudience(String audience) {

        this.audience = audience;
    }

    public List<String> getAllowedEmails() {

        return allowedEmails;
    }

    public void setAllowedEmails(List<String> allowedEmails) {

        this.allowedEmails = allowedEmails;
    }

    public List<String> getPublicPaths() {

        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {

        this.publicPaths = publicPaths;
    }

    public LocalAccount getLocalAccount() {

        return localAccount;
    }

    public void setLocalAccount(LocalAccount localAccount) {

        this.localAccount = localAccount;
    }

    /** Konto z konfiguracji — alternatywa dla Google, logowanie loginem i hasłem. */
    public static class LocalAccount {

        private boolean enabled = true;

        private String username = "admin";

        /** Wyłącznie hash BCrypt. Hasło jawnym tekstem nigdy nie trafia do konfiguracji. */
        private String passwordHash = DEFAULT_PASSWORD_HASH;

        public boolean isEnabled() {

            return enabled;
        }

        public void setEnabled(boolean enabled) {

            this.enabled = enabled;
        }

        public String getUsername() {

            return username;
        }

        public void setUsername(String username) {

            this.username = username;
        }

        public String getPasswordHash() {

            return passwordHash;
        }

        public void setPasswordHash(String passwordHash) {

            this.passwordHash = passwordHash;
        }
    }
}
