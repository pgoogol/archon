package com.pgoogol.bearerauth.account;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lista kont wzięta wprost z konfiguracji serwisu. Porównanie bez rozróżniania
 * wielkości liter, bo Google zwraca adres w zapisie, jakim posłużył się
 * właściciel konta przy rejestracji.
 */
public class ConfiguredAllowedAccounts implements AllowedAccounts {

    private final Set<String> allowed;

    public ConfiguredAllowedAccounts(List<String> allowedEmails) {

        this.allowed = normalize(allowedEmails);
    }

    @Override
    public boolean isAllowed(String email) {

        if (Objects.isNull(email) || email.isBlank()) {

            return false;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return allowed.contains(normalized);
    }

    private static Set<String> normalize(List<String> allowedEmails) {

        if (Objects.isNull(allowedEmails)) {

            return Set.of();
        }
        return allowedEmails.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(email -> !email.isBlank())
                .map(email -> email.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
