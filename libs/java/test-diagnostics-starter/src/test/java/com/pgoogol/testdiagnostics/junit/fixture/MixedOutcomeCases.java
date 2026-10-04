package com.pgoogol.testdiagnostics.junit.fixture;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Siedem testów: udany, z błędem, przerwany, wyłączony, dwa parametryzowane i jeden w klasie {@code @Nested}. */
@FixtureCases
public class MixedOutcomeCases {

    @Test
    void passes() {
    }

    @Test
    void fails() {

        throw new AssertionError("""
            expected 400
            but was 500""");
    }

    @Test
    void aborts() {

        Assumptions.abort("needs docker");
    }

    @Test
    @Disabled("not ready")
    void disabled() {
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void parameterized(int value) {
    }

    @Nested
    class Inner {

        @Test
        void nestedPasses() {
        }
    }
}
