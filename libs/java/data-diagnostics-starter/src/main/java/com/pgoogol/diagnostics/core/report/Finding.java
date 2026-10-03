package com.pgoogol.diagnostics.core.report;

import java.util.Objects;

/**
 * Wniosek analizy: nazwany problem znaleziony w jednostce pracy.
 *
 * <p>Na razie kod i zdanie dla człowieka. Waga, odcisk, kształt zapytania i miejsca
 * wywołania dochodzą razem z formatem wyjścia wniosków.</p>
 *
 * @param code  stała nazwa typu problemu, np. {@code N_PLUS_ONE}
 * @param title zdanie, które człowiek przeczyta w logu bez kontekstu
 */
public record Finding(String code, String title) {

    public Finding {

        requireNotBlank(code, "kod wniosku jest wymagany");
        requireNotBlank(title, "tytuł wniosku jest wymagany");
    }

    private static void requireNotBlank(String value, String message) {

        Objects.requireNonNull(value, message);
        if (value.isBlank()) {

            throw new IllegalArgumentException(message);
        }
    }
}
