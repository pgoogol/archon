package com.pgoogol.finance.account.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Saldo konta w jego walucie i w walucie bazowej.
 *
 * <p>Wartość bazowa liczy się kursem <b>bieżącym</b> — to stan majątku na dziś,
 * w odróżnieniu od transakcji, które na zawsze trzymają kurs z dnia księgowania.</p>
 */
public record AccountBalance(
    long accountId,
    String currency,
    int minorUnit,
    long balanceMinor,
    String baseCurrency,
    long baseBalanceMinor,
    BigDecimal fxRate,
    LocalDate fxRateDate) {

}
