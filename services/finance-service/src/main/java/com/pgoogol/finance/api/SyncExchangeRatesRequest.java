package com.pgoogol.finance.api;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/** Pusta lista kodów znaczy: wszystkie waluty ze słownika poza bazową. */
public record SyncExchangeRatesRequest(
    @NotNull LocalDate from,
    @NotNull LocalDate to,
    List<String> codes) {

}
