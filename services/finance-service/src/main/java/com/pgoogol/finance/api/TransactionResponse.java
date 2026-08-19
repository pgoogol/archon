package com.pgoogol.finance.api;

import com.pgoogol.finance.transaction.domain.TransactionType;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Transakcja wraz z kursem, którym została przeliczona. {@code fxRateDate}
 * bywa wcześniejsza niż {@code bookedOn} — NBP nie publikuje tabel w weekendy
 * i święta, a bez tej daty nie da się odtworzyć, skąd wzięła się kwota bazowa.
 */
public record TransactionResponse(
    long id,
    TransactionType type,
    LocalDate bookedOn,
    long amountMinor,
    String currency,
    long baseAmountMinor,
    String baseCurrency,
    String fxRate,
    LocalDate fxRateDate,
    Long originalAmountMinor,
    String originalCurrency,
    long accountId,
    String accountName,
    Long toAccountId,
    String toAccountName,
    Long toAmountMinor,
    Long categoryId,
    String categoryName,
    String description,
    String counterparty,
    Instant createdAt) {

}
