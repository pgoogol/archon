package com.pgoogol.kitchen.revision.diff;

/**
 * Rodzaj zmiany. Cofanie idzie w drugą stronę: {@code ADD} usuwa wiersz,
 * {@code REMOVE} wstawia go z zapamiętanej treści, {@code UPDATE} i {@code MOVE}
 * przywracają starą wartość pola.
 */
public enum ChangeOperation {

    ADD,
    UPDATE,
    REMOVE,
    MOVE
}
