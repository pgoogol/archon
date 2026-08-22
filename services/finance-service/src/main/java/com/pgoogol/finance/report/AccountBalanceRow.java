package com.pgoogol.finance.report;

import java.time.LocalDate;

/** @param balanceMinor saldo na ostatni dzień okresu, w walucie konta */
public record AccountBalanceRow(
    LocalDate period,
    long accountId,
    String accountName,
    String currency,
    long balanceMinor) {

}
