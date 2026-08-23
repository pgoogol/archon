package com.pgoogol.finance.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record PayOccurrenceRequest(
    @NotNull
    LocalDate paidOn,

    /** Kwota faktyczna; bywa inna niż oczekiwana. */
    @NotNull @Min(1)
    Long paidAmountMinor,

    /** Konto obciążone, gdy inne niż z reguły. */
    Long accountId) {

}
