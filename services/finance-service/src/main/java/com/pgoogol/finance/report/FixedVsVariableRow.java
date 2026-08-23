package com.pgoogol.finance.report;

import org.springframework.lang.Nullable;

import java.time.LocalDate;

/**
 * @param fixedMinor         wydatki powiązane z pozycją terminarza
 * @param fixedSharePercent  udział kosztów stałych jako tekst; {@code null},
 *                           gdy w okresie nie było żadnego wydatku
 */
public record FixedVsVariableRow(
    LocalDate period,
    long fixedMinor,
    long variableMinor,
    @Nullable String fixedSharePercent) {

}
