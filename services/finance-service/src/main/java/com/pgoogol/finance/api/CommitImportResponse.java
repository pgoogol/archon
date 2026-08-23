package com.pgoogol.finance.api;

/** Co faktycznie weszło do bazy i czy saldo się zgadza. */
public record CommitImportResponse(
    long batchId,
    int committedCount,
    int skippedDuplicateCount,
    ReconciliationResponse reconciliation) {

}
