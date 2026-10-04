package com.pgoogol.testdiagnostics.core;

/** Wynik testu albo klasy testów, niezależny od silnika testów. */
public enum TestOutcome {

    PASSED,

    FAILED,

    /** Przerwany przez niespełnione założenie; w raporcie liczy się jako pominięty. */
    ABORTED
}
