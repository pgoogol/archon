package com.pgoogol.diagnostics.core.report;

import java.util.List;
import java.util.Objects;

/**
 * Wniosek analizy: nazwany problem znaleziony w jednostce pracy.
 *
 * <p>To, co ustaliła analiza. Odcisk, wersję formatu i podsumowanie jednostki dokłada
 * dopiero wyjście wniosków.</p>
 *
 * @param code      stała nazwa typu problemu, np. {@code N_PLUS_ONE}
 * @param severity  waga
 * @param title     zdanie, które człowiek przeczyta w logu bez kontekstu
 * @param measured  pomiar porównany z progiem, w jednostkach analizy: liczba wykonań
 *                  albo milisekundy, zależnie od kodu
 * @param threshold próg w tych samych jednostkach; 0, gdy wniosek nie wynika z progu
 * @param shapes    kształty, których dotyczy wniosek: jeden przy wniosku o kształcie,
 *                  kilka najczęstszych przy wniosku o całej jednostce
 */
public record Finding(
    String code,
    Severity severity,
    String title,
    long measured,
    long threshold,
    List<ShapeSummary> shapes) {

    public Finding {

        requireNotBlank(code, "kod wniosku jest wymagany");
        Objects.requireNonNull(severity, "waga wniosku jest wymagana");
        requireNotBlank(title, "tytuł wniosku jest wymagany");
        if (measured < 0 || threshold < 0) {

            String message = "pomiar i próg wniosku nie mogą być ujemne: %d, %d".formatted(measured, threshold);
            throw new IllegalArgumentException(message);
        }
        shapes = List.copyOf(shapes);
    }

    private static void requireNotBlank(String value, String message) {

        Objects.requireNonNull(value, message);
        if (value.isBlank()) {

            throw new IllegalArgumentException(message);
        }
    }
}
