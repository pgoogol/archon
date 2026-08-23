package com.pgoogol.finance.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * Zatwierdzenie partii. Kategorie przychodzą listą, bo to człowiek decyduje,
 * co jest czym — sugestia z importu jest tylko podpowiedzią i nigdy nie zapisuje
 * się sama.
 *
 * @param occurrenceAssignments potwierdzone rozliczenia rachunków cyklicznych.
 *                              Bez wpisu na tej liście sugestia z podglądu
 *                              niczego nie rozlicza
 */
public record CommitImportRequest(
    @NotNull @Valid List<ImportCategoryAssignment> categoryAssignments,
    @Nullable @Valid List<ImportOccurrenceAssignment> occurrenceAssignments) {

}
