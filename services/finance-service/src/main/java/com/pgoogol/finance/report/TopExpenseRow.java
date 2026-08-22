package com.pgoogol.finance.report;

import org.springframework.lang.Nullable;

import java.time.LocalDate;

/** Pojedynczy wydatek w zestawieniu największych; kwota w walucie bazowej. */
public record TopExpenseRow(
    long transactionId,
    LocalDate bookedOn,
    long amountMinor,
    @Nullable String description,
    @Nullable String counterparty,
    @Nullable String categoryName,
    String accountName) {

}
