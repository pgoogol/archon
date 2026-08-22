package com.pgoogol.finance.api;

import com.pgoogol.finance.report.AccountBalanceRow;

import java.util.List;

/**
 * Salda w walucie konta. Nie ma tu waluty bazowej: dla okresu zamkniętego
 * w przeszłości nie istnieje jeden uczciwy kurs — bieżący zmieniałby historię,
 * a historyczny nie opisuje dzisiejszego stanu majątku.
 */
public record BalancesReportResponse(List<AccountBalanceRow> rows) {

}
