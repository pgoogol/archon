package com.pgoogol.finance.api;

import jakarta.validation.constraints.NotNull;

/** Kategoria wskazana przez człowieka dla konkretnego wiersza wyciągu. */
public record ImportCategoryAssignment(@NotNull Long rowId, @NotNull Long categoryId) {

}
