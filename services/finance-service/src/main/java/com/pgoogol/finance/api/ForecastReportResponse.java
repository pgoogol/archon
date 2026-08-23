package com.pgoogol.finance.api;

import com.pgoogol.finance.report.ForecastPoint;

import java.util.List;

/** Prognoza w walucie bazowej, po jednym punkcie na każdy dzień horyzontu. */
public record ForecastReportResponse(
    String baseCurrency,
    long startingBalanceMinor,
    List<ForecastPoint> points) {

}
