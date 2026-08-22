package com.pgoogol.finance.api;

import com.pgoogol.finance.report.TopCounterpartyRow;
import com.pgoogol.finance.report.TopExpenseRow;

import java.util.List;

public record TopSpendReportResponse(
    String baseCurrency,
    List<TopExpenseRow> transactions,
    List<TopCounterpartyRow> counterparties) {

}
