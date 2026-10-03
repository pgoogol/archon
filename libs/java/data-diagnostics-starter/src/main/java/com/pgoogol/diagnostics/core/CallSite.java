package com.pgoogol.diagnostics.core;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Miejsce w kodzie aplikacji, z którego wyszła operacja.
 *
 * <p>Osobne pola zamiast sklejonego {@code Klasa.metoda:linia}, żeby wnioski w JSON-ie
 * dało się filtrować po klasie albo metodzie bez parsowania tekstu.</p>
 *
 * @param className        pełna nazwa klasy aplikacji
 * @param method           nazwa metody
 * @param line             numer linii; ujemny, gdy klasa nie niesie informacji o liniach
 * @param repositoryMethod metoda repozytorium Spring Data, przez którą przeszło wywołanie
 *                         ({@code OrderRepository.findAllByStatus}); {@code null}, gdy
 *                         operacja nie szła przez repozytorium
 */
public record CallSite(String className, String method, int line, @Nullable String repositoryMethod) {

    public CallSite {

        requireNotBlank(className, "nazwa klasy miejsca wywołania jest wymagana");
        requireNotBlank(method, "nazwa metody miejsca wywołania jest wymagana");
    }

    private static void requireNotBlank(String value, String message) {

        Objects.requireNonNull(value, message);
        if (value.isBlank()) {

            throw new IllegalArgumentException(message);
        }
    }
}
