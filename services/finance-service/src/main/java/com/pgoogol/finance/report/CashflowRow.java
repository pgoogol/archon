package com.pgoogol.finance.report;

import java.time.LocalDate;

/** @param netMinor przychody minus wydatki; bywa ujemny i to jest w porządku */
public record CashflowRow(
    LocalDate period,
    long incomeMinor,
    long expenseMinor,
    long netMinor) {

}
