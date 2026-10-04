package com.pgoogol.testdiagnostics.core;

import java.util.Objects;

/**
 * Ustawienia przebiegu czytane z parametrów konfiguracji JUnit: właściwości systemowe
 * (np. {@code systemPropertyVariables} Surefire) albo {@code junit-platform.properties}.
 *
 * @param label    identyfikator przebiegu, np. {@code unit}; raport pokazuje jego tłumaczenie
 * @param language {@code pl} albo {@code en}; pusty oznacza język JVM
 * @param enabled  {@code false} wyłącza raport
 */
public record RunSettings(String label, String language, boolean enabled) {

    public static final String LABEL_KEY = "test.diagnostics.label";

    public static final String LOCALE_KEY = "test.diagnostics.locale";

    public static final String ENABLED_KEY = "test.diagnostics.enabled";

    /** Etykieta przebiegu bez ustawionej {@value #LABEL_KEY}. */
    public static final String DEFAULT_LABEL = "tests";

    public RunSettings {

        Objects.requireNonNull(label, "etykieta przebiegu jest wymagana");
        Objects.requireNonNull(language, "język jest wymagany, pusty oznacza język JVM");
    }
}
