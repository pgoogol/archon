package com.pgoogol.testdiagnostics.core;

import java.util.Objects;

/** Czas jednego testu, do listy najwolniejszych. */
public record TestTiming(String className, String testName, long millis) {

    public TestTiming {

        Objects.requireNonNull(className, "nazwa klasy jest wymagana");
        Objects.requireNonNull(testName, "nazwa testu jest wymagana");
    }
}
