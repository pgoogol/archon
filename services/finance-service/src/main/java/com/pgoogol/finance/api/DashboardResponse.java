package com.pgoogol.finance.api;

import com.pgoogol.finance.report.AccountValuation;
import com.pgoogol.finance.report.UpcomingItem;

import java.time.LocalDate;
import java.util.List;

/** @param month pierwszy dzień miesiąca, którego dotyczy bilans */
public record DashboardResponse(
    String baseCurrency,
    LocalDate month,
    List<AccountValuation> accountBalances,
    long monthIncomeMinor,
    long monthExpenseMinor,
    long monthNetMinor,
    List<UpcomingItem> overdue,
    List<UpcomingItem> upcoming) {

}
