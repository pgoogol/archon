package com.pgoogol.kitchen.revision.diff;

/** Czego dotyczy pojedyncza zmiana w dzienniku. */
public enum TargetType {

    /** Nagłówek przepisu: tytuł, opis, porcje, czasy, kuchnia, kategoria, trudność. */
    RECIPE,

    /** Wiersz składnika. */
    INGREDIENT,

    /** Zamiennik składnika. */
    ALTERNATIVE,

    /** Krok przygotowania. */
    STEP,

    /** Przypięcie albo odpięcie tagu. */
    TAG,

    /** Przypięcie albo odpięcie diety. */
    DIET,

    /** Powiązanie kroku ze składnikiem. */
    STEP_INGREDIENT,

    /** Sprzęt użyty w kroku. */
    STEP_EQUIPMENT
}
