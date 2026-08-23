package com.pgoogol.finance.api;

import com.pgoogol.finance.report.ByCategoryRow;
import com.pgoogol.finance.report.PeriodTotal;

import java.util.List;

/**
 * Kategoria nadrzędna niesie sumę swoich podkategorii, a same podkategorie są
 * w odpowiedzi obok niej — klient rozwija gałąź bez drugiego zapytania.
 * Sumy okresów liczone są z surowych przepływów, więc nie zawierają gałęzi
 * policzonych dwa razy.
 */
public record ByCategoryReportResponse(
    String baseCurrency,
    List<ByCategoryRow> rows,
    List<PeriodTotal> totals) {

}
