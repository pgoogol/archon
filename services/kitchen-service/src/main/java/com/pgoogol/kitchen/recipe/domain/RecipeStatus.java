package com.pgoogol.kitchen.recipe.domain;

/**
 * Przepis znika z książki przez archiwizację, nie przez skasowanie — dziennik
 * zmian i zlecenia importu zachowują odniesienia.
 */
public enum RecipeStatus {

    ACTIVE,
    ARCHIVED
}
