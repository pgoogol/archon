package com.pgoogol.finance.recurring.domain;

/** Los pojedynczej pozycji terminarza. */
public enum OccurrenceStatus {

    /** Czeka na termin. */
    PENDING,

    /** Zapłacona — ma swoją transakcję. */
    PAID,

    /** Świadomie pominięta; żadna transakcja nie powstała. */
    SKIPPED,

    /**
     * Termin minął, a pozycja wciąż czeka.
     *
     * <p><b>Nigdy nie jest zapisywany w bazie</b> — to {@link #PENDING} z terminem
     * wcześniejszym niż dziś, wyliczany przy odczycie. Przechowywany wymagałby
     * joba przepisującego statusy o północy i rozjeżdżałby się za każdym razem,
     * gdy ten job nie wstanie.</p>
     */
    OVERDUE
}
