package com.pgoogol.finance.api;

import com.pgoogol.finance.report.FixedVsVariableRow;

import java.util.List;

/**
 * Za koszt stały uznajemy wyłącznie wydatek powiązany z pozycją terminarza.
 * Zgadywanie po kategorii dawałoby liczbę wyglądającą wiarygodnie i nieprawdziwą.
 */
public record FixedVsVariableReportResponse(String baseCurrency, List<FixedVsVariableRow> rows) {

}
