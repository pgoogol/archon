package com.pgoogol.testdiagnostics.spring;

import java.util.Objects;
import java.util.Optional;

/**
 * Wynik opisu konfiguracji: opis albo opis błędu, który nie pozwolił jej odczytać.
 *
 * @param description opis; pusty, gdy się nie udał
 * @param failure     opis błędu; pusty, gdy opis się udał
 */
record Described(Optional<ContextDescription> description, String failure) {

    Described {

        Objects.requireNonNull(description, "opis jest wymagany, pusty, gdy się nie udał");
        Objects.requireNonNull(failure, "opis błędu jest wymagany, pusty, gdy go nie ma");
    }

    static Described of(ContextDescription description) {

        return new Described(Optional.of(description), "");
    }

    static Described failed(String failure) {

        return new Described(Optional.empty(), failure);
    }
}
