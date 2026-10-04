package com.pgoogol.testdiagnostics.junit.fixture;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Klasa, której {@code @BeforeAll} rzuca: błąd całej klasy. */
@FixtureCases
public class FailedSetupCases {

    @BeforeAll
    static void breaks() {

        throw new IllegalStateException("setup broke");
    }

    @Test
    void neverRuns() {
    }
}
