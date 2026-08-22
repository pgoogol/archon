package com.pgoogol.finance.api;

import com.pgoogol.finance.imports.domain.ImportRowStatus;
import org.springframework.lang.Nullable;

import java.time.LocalDate;

/**
 * Wiersz wyciągu w podglądzie. Kwota jest ZE ZNAKIEM, tak jak na wyciągu —
 * klient pokazuje ją tak, jak przyszła z banku, a nie po naszej interpretacji.
 */
public record ImportRowResponse(
    long id,
    int ordinal,
    LocalDate bookedOn,
    long amountMinor,
    String currency,
    @Nullable Long originalAmountMinor,
    @Nullable String originalCurrency,
    @Nullable String description,
    @Nullable String counterparty,
    @Nullable String bankReference,
    ImportRowStatus status,
    @Nullable Long suggestedCategoryId,
    @Nullable String suggestedCategoryName,
    @Nullable Long transactionId) {

}
