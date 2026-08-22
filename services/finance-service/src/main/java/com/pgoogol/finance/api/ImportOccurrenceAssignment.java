package com.pgoogol.finance.api;

import jakarta.validation.constraints.NotNull;

/** Potwierdzenie, że ten wiersz wyciągu rozlicza tę pozycję terminarza. */
public record ImportOccurrenceAssignment(@NotNull Long rowId, @NotNull Long occurrenceId) {

}
