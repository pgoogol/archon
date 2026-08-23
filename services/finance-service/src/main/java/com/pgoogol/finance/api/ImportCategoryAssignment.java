package com.pgoogol.finance.api;

import jakarta.validation.constraints.NotNull;
import org.springframework.lang.Nullable;

/**
 * Kategoria wskazana przez człowieka dla konkretnego wiersza wyciągu.
 *
 * @param rememberPattern fragment opisu, z którego ma powstać reguła
 *                        kategoryzacji; puste znaczy „nie zapamiętuj". Reguła
 *                        powstaje z poprawki użytkownika, nigdy sama z siebie
 */
public record ImportCategoryAssignment(
    @NotNull Long rowId,
    @NotNull Long categoryId,
    @Nullable String rememberPattern) {

}
