package com.pgoogol.kitchen.ingredient.domain;

/**
 * Skąd wzięła się pozycja katalogu. {@code NEW} to składnik dopisany przez
 * import albo formularz i czekający na przejrzenie — ekran katalogu pokazuje
 * takie osobno, żeby dało się je scalić z tym, co już jest.
 */
public enum IngredientStatus {

    NEW,
    VERIFIED
}
