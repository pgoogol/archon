package com.pgoogol.finance.report;

/**
 * Podział osi czasu raportu. Wartość idzie wprost do {@code date_trunc}, więc
 * nazwa części daty siedzi przy stałej, a nie w mapowaniu rozsypanym po
 * zapytaniach.
 */
public enum Granularity {

    DAY("day"),
    MONTH("month"),
    YEAR("year");

    private final String datePart;

    Granularity(String datePart) {

        this.datePart = datePart;
    }

    /** Nazwa części daty dla {@code date_trunc} i dla długości okresu. */
    public String datePart() {

        return datePart;
    }
}
