package com.pgoogol.finance.categorization.match;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Pozycja terminarza rozważana jako pokrycie wiersza wyciągu.
 *
 * @param matchPattern fragment opisu z reguły; {@code null} znaczy „reguła nie
 *                     stawia warunku na tekst", a nie „pasuje wszystko"
 */
public record ScheduleCandidate(
    long occurrenceId,
    LocalDate dueDate,
    long expectedAmountMinor,
    String currency,
    String matchPattern) {

    public ScheduleCandidate {

        Objects.requireNonNull(dueDate, "dueDate");
        Objects.requireNonNull(currency, "currency");
    }
}
