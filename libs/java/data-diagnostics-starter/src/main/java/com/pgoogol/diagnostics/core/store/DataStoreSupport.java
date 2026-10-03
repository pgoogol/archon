package com.pgoogol.diagnostics.core.store;

import com.pgoogol.diagnostics.core.DataStore;
import com.pgoogol.diagnostics.core.OperationKind;

/**
 * Strategia bazy danych: to, co rdzeń musi wiedzieć o dialekcie, żeby zgrupować
 * i podzielić operacje. Kolejna baza to nowa implementacja, bez zmian w rdzeniu.
 *
 * <p>Implementacje są bezstanowe i bezpieczne dla wielu wątków — strategia
 * przechwytywania woła je przy każdej operacji. Żadna metoda nie rzuca wyjątku
 * na nietypowym albo uciętym tekście: wynik ma być zawsze, nawet przybliżony.</p>
 */
public interface DataStoreSupport {

    /** Magazyn, którego dotyczy strategia. */
    DataStore store();

    /**
     * Kształt operacji: tekst bez wartości, po którym analizy grupują wykonania.
     * Dwa wywołania tego samego kodu z innymi danymi mają ten sam kształt.
     */
    String shape(String statement);

    /** Rodzaj operacji: odczyt, zapis albo inna. */
    OperationKind classify(String statement);
}
