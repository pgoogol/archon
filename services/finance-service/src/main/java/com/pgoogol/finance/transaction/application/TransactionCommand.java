package com.pgoogol.finance.transaction.application;

import com.pgoogol.finance.transaction.domain.TransactionType;
import org.springframework.lang.Nullable;

import java.time.LocalDate;

/**
 * Komplet danych potrzebnych do zapisania transakcji. Kurs i kwota bazowa
 * NIE są tu podawane — wylicza je serwis, żeby nie dało się wpisać kwoty
 * bazowej niezgodnej z kursem.
 */
public record TransactionCommand(
    TransactionType type,
    LocalDate bookedOn,
    long amountMinor,
    String currency,
    @Nullable Long originalAmountMinor,
    @Nullable String originalCurrency,
    long accountId,
    @Nullable Long toAccountId,
    @Nullable Long toAmountMinor,
    @Nullable Long categoryId,
    @Nullable String description,
    @Nullable String counterparty) {

}
