package com.pgoogol.finance.api;

import com.pgoogol.finance.report.CategoryComparisonRow;

import java.time.LocalDate;
import java.util.List;

/**
 * Poprzedni okres jest w odpowiedzi wprost, a nie tylko w kwotach — bez dat
 * czytelnik nie wie, z czym właściwie porównuje.
 */
public record ComparisonReportResponse(
    String baseCurrency,
    LocalDate previousFrom,
    LocalDate previousTo,
    List<CategoryComparisonRow> rows) {

}
