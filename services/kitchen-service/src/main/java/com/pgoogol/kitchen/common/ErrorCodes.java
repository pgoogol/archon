package com.pgoogol.kitchen.common;

import lombok.experimental.UtilityClass;

/**
 * Kody błędów w jednym miejscu. To one są kontraktem dla klienta — front
 * rozpoznaje przypadek po kodzie, nigdy po treści komunikatu — więc literał
 * wpisany w miejscu rzucenia wyjątku jest kontraktem pisanym z pamięci.
 *
 * <p>Jedna lista daje też odpowiedź na pytanie „jakie kody może zwrócić ten
 * serwis", której inaczej udziela wyłącznie grep. Treści komunikatów siedzą
 * obok, w {@link ExceptionMessageConstants}.</p>
 */
@UtilityClass
public class ErrorCodes {

    public static final String RECIPE_NOT_FOUND = "RECIPE_NOT_FOUND";
    public static final String RECIPE_MODIFIED = "RECIPE_MODIFIED";
    public static final String RECIPE_EMPTY = "RECIPE_EMPTY";
    public static final String RECIPE_ARCHIVED = "RECIPE_ARCHIVED";

    public static final String NOTE_NOT_FOUND = "NOTE_NOT_FOUND";

    public static final String INGREDIENT_NOT_FOUND = "INGREDIENT_NOT_FOUND";
    public static final String INGREDIENT_EXISTS = "INGREDIENT_EXISTS";
    public static final String ALIAS_EXISTS = "ALIAS_EXISTS";
    public static final String ALIAS_NOT_FOUND = "ALIAS_NOT_FOUND";

    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String INVALID_PARAMETER = "INVALID_PARAMETER";
    public static final String DATA_INTEGRITY_VIOLATION = "DATA_INTEGRITY_VIOLATION";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
}
