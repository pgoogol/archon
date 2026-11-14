package com.pgoogol.diagnostics.core.report;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Filtr ostatnich wniosków. Każde pole jest opcjonalne; {@code null} nie zawęża.
 *
 * @param code        kod wniosku, np. {@code N_PLUS_ONE}
 * @param fingerprint odcisk wniosku
 * @param unit        nazwa jednostki, np. {@code GET /orders/{id}}
 */
public record FindingQuery(@Nullable String code, @Nullable String fingerprint, @Nullable String unit) {

    /** Bez zawężania. */
    public static final FindingQuery ALL = new FindingQuery(null, null, null);

    boolean matches(ReportedFinding reported) {

        return accepts(code, reported.finding().code())
            && accepts(fingerprint, reported.fingerprint())
            && accepts(unit, reported.unit().name());
    }

    private static boolean accepts(@Nullable String wanted, String actual) {

        return Objects.isNull(wanted) || Objects.equals(wanted, actual);
    }
}
