package com.pgoogol.finance.api;

import java.time.LocalDate;

/** Wynik uzupełnienia terminarza. Drugi przebieg tego samego dnia daje zero. */
public record GenerateOccurrencesResponse(
    int createdCount,
    LocalDate horizonTo) {

}
