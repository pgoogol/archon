package com.pgoogol.finance.api;

import com.pgoogol.finance.currency.application.ExchangeRateService;
import com.pgoogol.finance.currency.domain.Currency;
import com.pgoogol.finance.currency.domain.ExchangeRate;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR,
    uses = DecimalMapper.class)
public interface CurrencyApiMapper {

    CurrencyResponse toResponse(Currency currency);

    List<CurrencyResponse> toCurrencyResponses(List<Currency> currencies);

    ExchangeRateResponse toResponse(ExchangeRate exchangeRate);

    List<ExchangeRateResponse> toRateResponses(List<ExchangeRate> rates);

    SyncExchangeRatesResponse toResponse(ExchangeRateService.SyncResult result);
}
