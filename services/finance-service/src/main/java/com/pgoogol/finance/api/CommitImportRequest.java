package com.pgoogol.finance.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Zatwierdzenie partii. Kategorie przychodzą listą, bo to człowiek decyduje,
 * co jest czym — sugestia z importu jest tylko podpowiedzią i nigdy nie zapisuje
 * się sama.
 */
public record CommitImportRequest(
    @NotNull @Valid List<ImportCategoryAssignment> categoryAssignments) {

}
