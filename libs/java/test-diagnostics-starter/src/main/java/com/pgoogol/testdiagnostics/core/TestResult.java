package com.pgoogol.testdiagnostics.core;

import java.util.Objects;

/**
 * Zakończony test w postaci, jakiej potrzebuje rejestrator.
 *
 * @param className      klasa najwyższego poziomu, do której raport przypisuje test
 * @param testName       nazwa wyświetlana testu
 * @param outcome        wynik
 * @param failureMessage pierwsza linia błędu albo pusty tekst, gdy test przeszedł
 */
public record TestResult(String className, String testName, TestOutcome outcome, String failureMessage) {

    public TestResult {

        Objects.requireNonNull(className, "nazwa klasy jest wymagana");
        Objects.requireNonNull(testName, "nazwa testu jest wymagana");
        Objects.requireNonNull(outcome, "wynik testu jest wymagany");
        Objects.requireNonNull(failureMessage, "opis błędu jest wymagany, pusty, gdy go nie ma");
    }

    public static TestResult passed(String className, String testName) {

        return new TestResult(className, testName, TestOutcome.PASSED, "");
    }
}
