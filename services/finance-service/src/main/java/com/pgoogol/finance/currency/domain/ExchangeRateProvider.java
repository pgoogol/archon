package com.pgoogol.finance.currency.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * Port wyjściowy do zewnętrznego źródła kursów. Domena wie, że kursy skądś
 * przychodzą; nie wie, że tym czymś jest NBP.
 */
public interface ExchangeRateProvider {

    /**
     * Kursy waluty w zakresie dat, włącznie. Dni bez publikacji po prostu nie mają
     * wpisu — pusta lista jest poprawnym wynikiem, nie błędem.
     */
    List<FxRate> fetchRates(String code, LocalDate from, LocalDate to);
}
