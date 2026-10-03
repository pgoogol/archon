package com.pgoogol.diagnostics.core;

/**
 * Kiedy ustalać miejsce wywołania operacji. Przejście po stosie kosztuje, więc w prod
 * robimy je tylko tam, gdzie wniosek i tak go potrzebuje.
 */
public enum CallSiteCapture {

    /** Przy każdej operacji. */
    EVERY_OPERATION,

    /** Tylko przy operacji wolnej i przy N-tym powtórzeniu tego samego kształtu. */
    SLOW_OR_REPEATED
}
