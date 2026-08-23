package com.pgoogol.finance.api;

import com.pgoogol.finance.report.CurrencyExposureRow;

import java.util.List;

/** Jedyny raport obok prognozy, który używa kursu bieżącego. */
public record CurrencyExposureReportResponse(
    String baseCurrency,
    List<CurrencyExposureRow> rows,
    long totalBaseMinor) {

}
