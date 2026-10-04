package com.pgoogol.testdiagnostics.junit.fixture;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Wyłączona klasa z dwoma testami: JUnit zgłasza pominięcie klasy, a testów osobno już nie. */
@FixtureCases
@Disabled("whole class off")
public class DisabledCases {

    @Test
    void first() {
    }

    @Test
    void second() {
    }
}
