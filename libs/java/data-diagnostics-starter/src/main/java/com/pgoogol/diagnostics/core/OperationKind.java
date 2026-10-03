package com.pgoogol.diagnostics.core;

/**
 * Rodzaj operacji na magazynie. Analizy dzielą po nim zdarzenia: N+1 dotyczy odczytów,
 * brak batcha — zapisów.
 */
public enum OperationKind {

    /** Operacja, która tylko czyta dane. */
    READ,

    /** Operacja, która zmienia dane. */
    WRITE,

    /** Wszystko inne: DDL, ustawienia sesji, wywołania procedur. */
    OTHER
}
