package com.pgoogol.finance.api;

import jakarta.validation.constraints.NotNull;

/** Potwierdzenie, że te dwie transakcje to dwie strony jednego przelewu. */
public record MergeTransferRequest(
    @NotNull Long expenseTransactionId,
    @NotNull Long incomeTransactionId) {

}
