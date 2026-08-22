package com.pgoogol.finance.api;

import org.springframework.lang.Nullable;

/**
 * Uzgodnienie salda po imporcie. Rozjazd jest pokazywany, nigdy poprawiany —
 * ciche wyrównanie zamieniłoby błąd widoczny w błąd niewidoczny.
 */
public record ReconciliationResponse(
    @Nullable Long statementClosingBalanceMinor,
    @Nullable Long computedBalanceMinor,
    @Nullable Long differenceMinor,
    boolean matched) {

}
