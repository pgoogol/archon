package com.pgoogol.testdiagnostics.junit.fixture;

import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Warunek klas-wzorców: działają tylko w zagnieżdżonym uruchomieniu JUnit z testów
 * słuchaczy, które ustawia parametr {@value #KEY}. Zwykły przebieg Surefire ich nie
 * wybiera (nazwy {@code *Cases}), a uruchomione z IDE kończą jako pominięte.
 */
public final class FixtureRun {

    public static final String KEY = "test.diagnostics.fixtures.enabled";

    private FixtureRun() {
    }

    public static boolean active(ExtensionContext context) {

        return context.getConfigurationParameter(KEY).isPresent();
    }
}
