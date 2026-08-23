package com.pgoogol.finance.api;

import com.pgoogol.finance.recurring.domain.RecurringFrequency;
import com.pgoogol.finance.transaction.domain.TransactionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RecurringRuleRequest(
    @NotBlank @Size(max = 100)
    String name,

    @NotNull
    Long accountId,

    @NotNull
    Long categoryId,

    @NotNull
    TransactionType type,

    /** Kwota oczekiwana; faktyczna podawana jest dopiero przy płatności. */
    @NotNull @Min(1)
    Long amountMinor,

    @NotNull
    RecurringFrequency frequency,

    /** Dzień większy niż długość miesiąca przesuwa się na jego ostatni dzień. */
    @NotNull @Min(1) @Max(31)
    Integer dayOfMonth,

    @NotNull
    LocalDate startsOn,

    LocalDate endsOn,

    @Size(max = 255)
    String matchPattern) {

}
