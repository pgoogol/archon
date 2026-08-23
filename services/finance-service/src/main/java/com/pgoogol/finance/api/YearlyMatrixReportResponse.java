package com.pgoogol.finance.api;

import com.pgoogol.finance.report.YearlyMatrixRow;

import java.util.List;

/** @param monthlyTotalsMinor dwanaście sum, od stycznia do grudnia */
public record YearlyMatrixReportResponse(
    String baseCurrency,
    int year,
    List<YearlyMatrixRow> rows,
    List<Long> monthlyTotalsMinor,
    long totalMinor) {

}
