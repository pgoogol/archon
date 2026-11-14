package com.pgoogol.diagnostics.core;

/** Wynik transakcji. Nieudany commit liczy się jako wycofanie. */
public enum TransactionOutcome {

    COMMITTED,

    ROLLED_BACK
}
