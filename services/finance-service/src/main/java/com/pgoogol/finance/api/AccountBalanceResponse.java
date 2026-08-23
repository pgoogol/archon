package com.pgoogol.finance.api;

import java.time.LocalDate;

/**
 * Saldo konta. {@code baseBalanceMinor} liczy się kursem bieżącym — to wycena
 * majątku na dziś, nie przeliczenie historii.
 */
public record AccountBalanceResponse(
    long accountId,
    String currency,
    int minorUnit,
    long balanceMinor,
    String baseCurrency,
    long baseBalanceMinor,
    String fxRate,
    LocalDate fxRateDate) {

}
