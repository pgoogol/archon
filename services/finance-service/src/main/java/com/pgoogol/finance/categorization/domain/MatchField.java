package com.pgoogol.finance.categorization.domain;

/** Które pole wiersza wyciągu porównujemy ze wzorcem reguły. */
public enum MatchField {

    DESCRIPTION,
    COUNTERPARTY,

    /** Opis albo kontrahent — wystarczy, że wzorzec trafi w jedno z nich. */
    ANY
}
