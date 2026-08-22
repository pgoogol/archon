package com.pgoogol.finance.imports.domain;

/** Los pojedynczego wiersza wyciągu. */
public enum ImportRowStatus {

    /** Wejdzie do bazy przy zatwierdzeniu partii. */
    NEW,

    /** Ta sama operacja jest już zaimportowana — wiersz zostanie pominięty. */
    DUPLICATE,

    /** Wiersz ma już swoją transakcję. */
    COMMITTED
}
