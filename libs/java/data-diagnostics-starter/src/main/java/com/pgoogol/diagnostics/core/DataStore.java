package com.pgoogol.diagnostics.core;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Magazyn danych, którego dotyczy operacja ({@code postgresql}, później {@code mongodb},
 * {@code redis}…).
 *
 * <p>Otwarty zbiór zamiast enuma: kolejna baza to nowa implementacja strategii z własną
 * nazwą, bez zmiany w rdzeniu. Nazwa trafia do kluczy ustawień
 * ({@code threshold.postgresql}) i do tagów metryk, dlatego tylko małe litery, cyfry
 * i myślniki.</p>
 *
 * @param name nazwa magazynu
 */
public record DataStore(String name) {

    private static final Pattern NAME = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    public DataStore {

        Objects.requireNonNull(name, "nazwa magazynu jest wymagana");
        if (!NAME.matcher(name).matches()) {

            String message = "nazwa magazynu może mieć tylko małe litery, cyfry i myślniki: '%s'".formatted(name);
            throw new IllegalArgumentException(message);
        }
    }
}
