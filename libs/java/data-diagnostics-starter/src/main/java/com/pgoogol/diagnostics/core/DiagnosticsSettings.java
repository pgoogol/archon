package com.pgoogol.diagnostics.core;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Ustawienia diagnostyki, bez Springa. Tryb wyznacza to, czego nie da się nadpisać,
 * i wartości domyślne reszty:
 *
 * <table>
 *     <caption>Różnice między trybami</caption>
 *     <tr><th>Ustawienie</th><th>dev</th><th>prod</th></tr>
 *     <tr><td>tekst zapytania</td><td>pełny</td><td>brak, sam kształt</td></tr>
 *     <tr><td>parametry</td><td>wyłączone, można włączyć</td><td>zawsze wyłączone</td></tr>
 *     <tr><td>zdarzenia w jednostce</td><td>lista, limit 10 000</td><td>brak, tylko liczniki w analizach</td></tr>
 *     <tr><td>limit kształtów na jednostkę</td><td>500</td><td>200, nadmiar jako {@code other}</td></tr>
 *     <tr><td>miejsce wywołania</td><td>każda operacja</td><td>tylko operacje wolne i N-te powtórzenie kształtu</td></tr>
 * </table>
 *
 * <p>Ustawienie sprzeczne z trybem prod (parametry, lista zdarzeń) jest odrzucane, a nie
 * po cichu poprawiane: błędna konfiguracja ma wyjść przy starcie, nie w audycie danych.</p>
 *
 * @param mode               tryb pracy
 * @param captureParameters  czy zapisywać parametry zapytań
 * @param unitEventLimit     ile zdarzeń trzyma jedna jednostka; 0 wyłącza listę
 * @param unitShapeLimit     ile różnych kształtów analiza liczy osobno w jednej jednostce
 * @param captureOutsideUnit czy zdarzenie bez otwartej jednostki trafia do wspólnej
 *                           jednostki {@code startup}/{@code background}, zamiast przepaść
 * @param disabledAnalyzers  identyfikatory analiz wyłączonych w tej aplikacji
 *                           ({@code diagnostics.analyzers.<id>.enabled=false}); progi
 *                           każdej analizy przychodzą w jej konstruktorze
 */
public record DiagnosticsSettings(
    DiagnosticsMode mode,
    boolean captureParameters,
    int unitEventLimit,
    int unitShapeLimit,
    boolean captureOutsideUnit,
    Set<String> disabledAnalyzers) {

    public static final int DEV_UNIT_EVENT_LIMIT = 10_000;

    public static final int DEV_UNIT_SHAPE_LIMIT = 500;

    public static final int PROD_UNIT_SHAPE_LIMIT = 200;

    public DiagnosticsSettings {

        Objects.requireNonNull(mode, "tryb diagnostyki jest wymagany");
        if (unitEventLimit < 0) {

            throw new IllegalArgumentException("limit zdarzeń jednostki nie może być ujemny: " + unitEventLimit);
        }
        if (unitShapeLimit < 1) {

            throw new IllegalArgumentException("limit kształtów jednostki musi być dodatni: " + unitShapeLimit);
        }
        boolean prod = Objects.equals(mode, DiagnosticsMode.PROD);
        if (prod && captureParameters) {

            throw new IllegalArgumentException("w trybie prod parametry zapytań są zawsze wyłączone");
        }
        if (prod && unitEventLimit > 0) {

            throw new IllegalArgumentException("w trybie prod jednostka nie trzyma zdarzeń, tylko liczniki");
        }
        disabledAnalyzers = Set.copyOf(disabledAnalyzers);
    }

    /** Wartości domyślne trybu; pojedyncze ustawienia zmienia się metodami {@code with…}. */
    public static DiagnosticsSettings defaults(DiagnosticsMode mode) {

        Objects.requireNonNull(mode, "tryb diagnostyki jest wymagany");
        return switch (mode) {

            case DEV -> new DiagnosticsSettings(mode, false, DEV_UNIT_EVENT_LIMIT, DEV_UNIT_SHAPE_LIMIT, false, Set.of());
            case PROD -> new DiagnosticsSettings(mode, false, 0, PROD_UNIT_SHAPE_LIMIT, false, Set.of());
        };
    }

    /** Czy zdarzenie niesie pełny tekst zapytania; w prod zostaje sam kształt. */
    public boolean keepStatementText() {

        return Objects.equals(mode, DiagnosticsMode.DEV);
    }

    /** Czy analiza o tym identyfikatorze ma pracować; silnik pomija wyłączone. */
    public boolean analyzerEnabled(String analyzerId) {

        return !disabledAnalyzers.contains(analyzerId);
    }

    /** Kiedy ustalać miejsce wywołania. */
    public CallSiteCapture callSiteCapture() {

        return switch (mode) {

            case DEV -> CallSiteCapture.EVERY_OPERATION;
            case PROD -> CallSiteCapture.SLOW_OR_REPEATED;
        };
    }

    public DiagnosticsSettings withCaptureParameters(boolean value) {

        return new DiagnosticsSettings(mode, value, unitEventLimit, unitShapeLimit, captureOutsideUnit, disabledAnalyzers);
    }

    public DiagnosticsSettings withUnitEventLimit(int value) {

        return new DiagnosticsSettings(mode, captureParameters, value, unitShapeLimit, captureOutsideUnit, disabledAnalyzers);
    }

    public DiagnosticsSettings withUnitShapeLimit(int value) {

        return new DiagnosticsSettings(mode, captureParameters, unitEventLimit, value, captureOutsideUnit, disabledAnalyzers);
    }

    public DiagnosticsSettings withCaptureOutsideUnit(boolean value) {

        return new DiagnosticsSettings(mode, captureParameters, unitEventLimit, unitShapeLimit, value, disabledAnalyzers);
    }

    public DiagnosticsSettings withAnalyzerEnabled(String analyzerId, boolean enabled) {

        Objects.requireNonNull(analyzerId, "identyfikator analizy jest wymagany");
        Set<String> disabled = new HashSet<>(disabledAnalyzers);
        if (enabled) {

            disabled.remove(analyzerId);
        } else {

            disabled.add(analyzerId);
        }
        return new DiagnosticsSettings(mode, captureParameters, unitEventLimit, unitShapeLimit, captureOutsideUnit, disabled);
    }
}
