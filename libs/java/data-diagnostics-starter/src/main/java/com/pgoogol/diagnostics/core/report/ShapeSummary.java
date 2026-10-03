package com.pgoogol.diagnostics.core.report;

import com.pgoogol.diagnostics.core.CallSite;
import com.pgoogol.diagnostics.core.DataStore;
import com.pgoogol.diagnostics.core.OperationKind;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Podsumowanie jednego kształtu operacji w jednostce pracy: ile razy padł, ile trwał
 * łącznie i najdłużej, skąd go wołano.
 *
 * @param store     magazyn
 * @param kind      rodzaj operacji; {@code OTHER} dla kubełka zbiorczego
 * @param shape     kształt albo {@code other} dla kubełka zbiorczego
 * @param overflow  czy to kubełek zbiorczy dla kształtów ponad limit; taki kubełek miesza
 *                  różne zapytania, więc żadna analiza nie robi z niego wniosku o kształcie
 * @param sample    pierwszy pełny tekst operacji; {@code null} w trybie prod i w kubełku
 *                  zbiorczym
 * @param count     liczba wykonań
 * @param failures  ile z nich zakończyło się błędem
 * @param totalTime łączny czas wykonań
 * @param maxTime   najdłuższe wykonanie
 * @param callers   do trzech różnych miejsc wywołania, w kolejności pojawienia się
 */
public record ShapeSummary(
    DataStore store,
    OperationKind kind,
    String shape,
    boolean overflow,
    @Nullable String sample,
    long count,
    long failures,
    Duration totalTime,
    Duration maxTime,
    List<CallSite> callers) {

    public ShapeSummary {

        Objects.requireNonNull(store, "magazyn kształtu jest wymagany");
        Objects.requireNonNull(kind, "rodzaj operacji kształtu jest wymagany");
        Objects.requireNonNull(shape, "kształt jest wymagany");
        Objects.requireNonNull(totalTime, "łączny czas kształtu jest wymagany");
        Objects.requireNonNull(maxTime, "najdłuższy czas kształtu jest wymagany");
        if (count < 0 || failures < 0 || failures > count) {

            String message = "niespójne liczniki kształtu: wykonania %d, błędy %d".formatted(count, failures);
            throw new IllegalArgumentException(message);
        }
        callers = List.copyOf(callers);
    }
}
