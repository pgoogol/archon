package com.pgoogol.finance.transaction.domain;

/**
 * Kierunek operacji. {@code TRANSFER} jest osobnym typem, bo przelew na własne
 * konto oszczędnościowe nie jest wydatkiem — bez tego każda taka operacja
 * zawyżałaby naraz wydatki i przychody.
 */
public enum TransactionType {

    EXPENSE,
    INCOME,
    TRANSFER
}
