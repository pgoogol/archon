package com.pgoogol.kitchen.dictionary.domain;

/**
 * Rodzaj jednostki. Przeliczać wolno wyłącznie w obrębie jednego rodzaju —
 * zamiana gramów na mililitry zależy od produktu i nie należy do słownika.
 */
public enum UnitKind {

    /** Masa: gram, kilogram. */
    MASS,

    /** Objętość: mililitr, litr, łyżka, szklanka. */
    VOLUME,

    /** Sztuki: sztuka, ząbek, plaster, opakowanie. */
    COUNT,

    /** Miary bez wartości liczbowej: szczypta, garść, „do smaku". */
    OTHER
}
