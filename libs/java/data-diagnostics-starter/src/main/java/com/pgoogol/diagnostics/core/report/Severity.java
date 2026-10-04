package com.pgoogol.diagnostics.core.report;

/** Waga wniosku. Analizy operacji liczą ją z progu, patrz {@code SeverityScale}. */
public enum Severity {

    /** Wskazówka: warto poprawić, ale nic nie zwalnia aplikacji. */
    INFO,

    /** Pomiar przekroczył próg analizy. */
    WARN,

    /** Pomiar przekroczył wielokrotność progu, domyślnie pięciokrotność. */
    CRITICAL
}
