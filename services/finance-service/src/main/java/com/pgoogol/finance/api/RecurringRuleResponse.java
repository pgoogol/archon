package com.pgoogol.finance.api;

import com.pgoogol.finance.recurring.domain.RecurringFrequency;
import com.pgoogol.finance.transaction.domain.TransactionType;

import java.time.LocalDate;

public record RecurringRuleResponse(
    long id,
    String name,
    long accountId,
    String accountName,
    long categoryId,
    String categoryName,
    TransactionType type,
    long amountMinor,
    String currency,
    RecurringFrequency frequency,
    int dayOfMonth,
    LocalDate startsOn,
    LocalDate endsOn,
    String matchPattern,
    boolean active) {

}
