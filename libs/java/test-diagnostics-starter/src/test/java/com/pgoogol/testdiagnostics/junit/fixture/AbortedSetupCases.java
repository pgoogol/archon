package com.pgoogol.testdiagnostics.junit.fixture;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Klasa przerwana założeniem w {@code @BeforeAll}: jej dwa testy nie startują. */
@FixtureCases
public class AbortedSetupCases {

    @BeforeAll
    static void needsDatabase() {

        Assumptions.abort("no database");
    }

    @Test
    void first() {
    }

    @Test
    void second() {
    }
}
