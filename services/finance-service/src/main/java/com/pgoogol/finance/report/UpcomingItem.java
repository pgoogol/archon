package com.pgoogol.finance.report;

import com.pgoogol.finance.transaction.domain.TransactionType;

import java.time.LocalDate;

/**
 * Zobowiązanie z terminarza. {@code overdue} liczy się przy odczycie — status
 * o tej nazwie nie istnieje w bazie.
 */
public record UpcomingItem(
    long occurrenceId,
    long ruleId,
    String ruleName,
    long accountId,
    String accountName,
    LocalDate dueDate,
    long expectedAmountMinor,
    String currency,
    TransactionType type,
    boolean overdue) {

}
