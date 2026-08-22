package com.pgoogol.finance.imports.domain;

/** Etap, na którym stoi wgrany wyciąg. */
public enum ImportBatchStatus {

    /** Plik sparsowany i zdeduplikowany; żadna transakcja jeszcze nie powstała. */
    PARSED,

    /** Wiersze zamienione na transakcje — partia jest zamknięta. */
    COMMITTED
}
