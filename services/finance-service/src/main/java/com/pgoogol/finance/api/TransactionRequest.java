package com.pgoogol.finance.api;

import com.pgoogol.finance.transaction.domain.TransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Kwota jest zawsze dodatnia — kierunek wynika z {@code type}. Kurs i kwota
 * w walucie bazowej nie są tu podawane: wylicza je serwis przy zapisie.
 */
public record TransactionRequest(
    @NotNull
    TransactionType type,

    @NotNull
    LocalDate bookedOn,

    @NotNull @Positive
    Long amountMinor,

    @NotNull @Pattern(regexp = "^[A-Za-z]{3}$", message = "kod waluty to trzy litery")
    String currency,

    @Positive
    Long originalAmountMinor,

    @Pattern(regexp = "^[A-Za-z]{3}$", message = "kod waluty to trzy litery")
    String originalCurrency,

    @NotNull
    Long accountId,

    Long toAccountId,

    @Positive
    Long toAmountMinor,

    Long categoryId,

    @Size(max = 255)
    String description,

    @Size(max = 255)
    String counterparty) {

}
