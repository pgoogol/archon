package com.pgoogol.finance.api;

import com.pgoogol.finance.currency.domain.RateSource;

import java.time.LocalDate;

public record ExchangeRateResponse(String code, LocalDate rateDate, String rate,
                                   RateSource source) {

}
