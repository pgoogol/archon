package com.pgoogol.testdiagnostics.core;

import java.util.Objects;

/**
 * Błąd testu albo całej klasy.
 *
 * @param className klasa najwyższego poziomu
 * @param testName  nazwa testu; pusta, gdy padła cała klasa (np. {@code @BeforeAll} albo start środowiska)
 * @param message   pierwsza linia błędu
 */
public record FailureRecord(String className, String testName, String message) {

    public FailureRecord {

        Objects.requireNonNull(className, "nazwa klasy jest wymagana");
        Objects.requireNonNull(testName, "nazwa testu jest wymagana, pusta dla całej klasy");
        Objects.requireNonNull(message, "opis błędu jest wymagany");
    }

    public static FailureRecord ofWholeClass(String className, String message) {

        return new FailureRecord(className, "", message);
    }

    public boolean wholeClass() {

        return testName.isEmpty();
    }
}
