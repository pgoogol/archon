package com.pgoogol.llm.provider;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Wycina klucz API z tekstu, zanim ten trafi do logu albo do komunikatu wyjątku.
 * Klucz, który raz pokazał się w logu, jest spalony i wymaga rotacji — a
 * providerzy potrafią odesłać go w treści błędu.
 */
public class SecretMasker {

    private static final String MASK = "***";
    private static final int MIN_SECRET_LENGTH = 8;

    private final List<String> secrets;

    public SecretMasker(String... secrets) {

        this.secrets = Arrays.stream(secrets)
                .filter(Objects::nonNull)
                .filter(secret -> secret.length() >= MIN_SECRET_LENGTH)
                .toList();
    }

    public String mask(String text) {

        if (Objects.isNull(text)) {

            return "";
        }
        return secrets.stream().reduce(text, (masked, secret) -> masked.replace(secret, MASK));
    }

    /** Wartość nagłówka niosącego sekret — treści nie pokazujemy nigdy. */
    public static String maskedHeaderValue() {

        return MASK;
    }
}
