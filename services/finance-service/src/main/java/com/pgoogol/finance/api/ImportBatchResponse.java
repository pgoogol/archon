package com.pgoogol.finance.api;

import com.pgoogol.finance.imports.domain.ImportBatchStatus;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.time.LocalDate;

/** Wgrany wyciąg: skąd, kiedy, ile wierszy i co z nimi będzie. */
public record ImportBatchResponse(
    long id,
    long accountId,
    String accountName,
    String fileName,
    ImportBatchStatus status,
    @Nullable LocalDate periodFrom,
    @Nullable LocalDate periodTo,
    @Nullable Long openingBalanceMinor,
    @Nullable Long closingBalanceMinor,
    int rowCount,
    int newCount,
    int duplicateCount,
    Instant uploadedAt,
    @Nullable Instant committedAt) {

}
