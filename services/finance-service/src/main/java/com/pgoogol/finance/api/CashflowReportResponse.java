package com.pgoogol.finance.api;

import com.pgoogol.finance.report.CashflowRow;

import java.util.List;

public record CashflowReportResponse(String baseCurrency, List<CashflowRow> rows) {

}
