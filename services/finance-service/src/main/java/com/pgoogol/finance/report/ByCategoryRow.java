package com.pgoogol.finance.report;

import org.springframework.lang.Nullable;

import java.time.LocalDate;

/**
 * Kwota jednej kategorii w jednym okresie.
 *
 * @param amountMinor    suma wraz z podkategoriami — to ona idzie na wykres
 * @param ownAmountMinor kwota zapisana wprost na tej kategorii; różnica względem
 *                       {@code amountMinor} pokazuje, ile przyszło z gałęzi
 */
public record ByCategoryRow(
    LocalDate period,
    long categoryId,
    String categoryName,
    @Nullable Long parentCategoryId,
    long amountMinor,
    long ownAmountMinor,
    int transactionCount) {

}
