package com.pgoogol.testdiagnostics.core;

/**
 * Udział części w całości, np. czasu GC w czasie przebiegu.
 *
 * @param part  część
 * @param whole całość; zero albo mniej daje udział 0%
 */
public record Share(long part, long whole) {

    /** Procent zaokrąglony do całości. */
    public int percent() {

        if (whole <= 0) {

            return 0;
        }
        return (int) Math.round(100.0 * part / whole);
    }
}
