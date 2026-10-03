package com.pgoogol.diagnostics.core;

/**
 * Tryb pracy diagnostyki. Dev zbiera wszystko, czego człowiek potrzebuje do poprawki;
 * prod zbiera tylko to, co jest tanie i nie ujawnia danych.
 */
public enum DiagnosticsMode {

    /** Pełny tekst zapytań, lista zdarzeń w jednostce, miejsce wywołania przy każdej operacji. */
    DEV,

    /** Sam kształt zapytań, same liczniki, miejsce wywołania tylko tam, gdzie jest problem. */
    PROD
}
