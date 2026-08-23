package com.pgoogol.finance.api;

import java.util.List;

/** Partia wraz z wierszami — jeden odczyt zamiast dwóch okrążeń. */
public record ImportBatchDetailResponse(ImportBatchResponse batch, List<ImportRowResponse> rows) {

}
