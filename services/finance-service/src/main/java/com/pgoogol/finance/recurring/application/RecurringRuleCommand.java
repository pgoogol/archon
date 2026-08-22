package com.pgoogol.finance.recurring.application;

import com.pgoogol.finance.recurring.domain.RecurringFrequency;
import com.pgoogol.finance.transaction.domain.TransactionType;
import org.springframework.lang.Nullable;

import java.time.LocalDate;

/**
 * Komplet danych reguły cyklicznej. Waluty tu nie ma — bierze się z konta,
 * inaczej dałoby się opisać rachunek w walucie, w której to konto nie działa.
 */
public record RecurringRuleCommand(
    String name,
    long accountId,
    long categoryId,
    TransactionType type,
    long amountMinor,
    RecurringFrequency frequency,
    int dayOfMonth,
    LocalDate startsOn,
    @Nullable LocalDate endsOn,
    @Nullable String matchPattern) {

}
