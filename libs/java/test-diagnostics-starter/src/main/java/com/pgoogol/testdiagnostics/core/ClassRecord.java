package com.pgoogol.testdiagnostics.core;

import java.util.Objects;

/**
 * Klasa testów w migawce przebiegu.
 *
 * @param className         pełna nazwa klasy najwyższego poziomu (klasy {@code @Nested} się do niej wliczają)
 * @param durationMillis    od startu do końca klasy, razem z przygotowaniem i sprzątaniem
 * @param testMillis        suma czasów samych testów
 * @param environmentMillis start i wznowienia środowisk Springa wywołane przez tę klasę
 * @param tests             liczba testów, także pominiętych
 * @param failed            testy zakończone błędem
 * @param skipped           testy pominięte albo przerwane przez założenie
 */
public record ClassRecord(
    String className,
    long durationMillis,
    long testMillis,
    long environmentMillis,
    int tests,
    int failed,
    int skipped) {

    public ClassRecord {

        Objects.requireNonNull(className, "nazwa klasy jest wymagana");
    }

    /** Czas klasy poza testami i środowiskiem: {@code @BeforeAll}, {@code @AfterAll}, rozszerzenia. */
    public long otherMillis() {

        return Math.max(0, durationMillis - testMillis - environmentMillis);
    }
}
