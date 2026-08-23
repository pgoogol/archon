package com.pgoogol.finance.report;

import java.time.LocalDate;

/** @param changeMinor ruch tego dnia; zero, gdy nic nie wypada */
public record ForecastPoint(LocalDate date, long balanceMinor, long changeMinor) {

}
