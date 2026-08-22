package com.pgoogol.finance.report;

import org.springframework.lang.Nullable;

import java.time.LocalDate;

/**
 * @param baseValueMinor wartość bieżąca w walucie bazowej; {@code null}, gdy
 *                       waluta nie ma jeszcze żadnego kursu
 */
public record CurrencyExposureRow(
    String currency,
    int minorUnit,
    long balanceMinor,
    @Nullable Long baseValueMinor,
    @Nullable String rate,
    @Nullable LocalDate rateDate) {

}
