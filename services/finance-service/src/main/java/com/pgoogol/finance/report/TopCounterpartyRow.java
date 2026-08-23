package com.pgoogol.finance.report;

public record TopCounterpartyRow(
    String counterparty,
    long amountMinor,
    int transactionCount) {

}
