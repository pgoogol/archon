package com.pgoogol.finance.api;

import com.pgoogol.finance.recurring.domain.OccurrenceStatus;

import java.time.LocalDate;

/**
 * Pozycja terminarza. {@code status} bywa {@code OVERDUE}, którego w bazie nie
 * ma — wylicza się go przy odczycie z terminu i dzisiejszej daty.
 */
public record OccurrenceResponse(
    long id,
    long ruleId,
    String ruleName,
    long accountId,
    long categoryId,
    LocalDate dueDate,
    long expectedAmountMinor,
    String currency,
    OccurrenceStatus status,
    LocalDate paidOn,
    Long paidAmountMinor,
    Long transactionId) {

}
