package com.pgoogol.finance.api;

import com.pgoogol.finance.account.domain.AccountType;

import java.time.LocalDate;

public record AccountResponse(
    long id,
    String name,
    AccountType type,
    String currency,
    String iban,
    long openingBalanceMinor,
    LocalDate openingBalanceOn,
    boolean archived) {

}
