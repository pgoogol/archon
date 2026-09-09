package com.pgoogol.kitchen.common;

import lombok.experimental.UtilityClass;

/**
 * Treści komunikatów w jednym miejscu. Rozsypane po serwisie rozjeżdżają się
 * w tonie i szczegółowości, a zmiana jednego zdania znaczy polowanie po całym
 * module. Kod błędu zostaje przy rzuceniu wyjątku — to kontrakt dla klienta;
 * tekst jest wyłącznie dla człowieka.
 */
@UtilityClass
public class ExceptionMessageConstants {

    public static final String RECIPE_NOT_FOUND = "Przepis o identyfikatorze %d nie istnieje";
    public static final String RECIPE_MODIFIED =
        "Przepis zmienił się w międzyczasie — odśwież i nanieś zmiany jeszcze raz";
    public static final String RECIPE_EMPTY =
        "Przepis bez składników i bez kroków to pusta kartka — dodaj chociaż jedno";
    public static final String RECIPE_ARCHIVED = "Przepis %d jest zarchiwizowany i nie da się go zmienić";

    public static final String NOTE_NOT_FOUND = "Uwaga o identyfikatorze %d nie istnieje";

    public static final String INGREDIENT_NOT_FOUND = "Składnik o identyfikatorze %d nie istnieje";
    public static final String INGREDIENT_EXISTS = "Składnik „%s” już jest w katalogu";
    public static final String ALIAS_EXISTS = "Synonim „%s” prowadzi już do innego składnika";
    public static final String ALIAS_NOT_FOUND = "Synonim o identyfikatorze %d nie istnieje";

    public static final String VALIDATION_FAILED = "Żądanie nie przeszło walidacji";
    public static final String INVALID_PARAMETER = "Niepoprawna wartość parametru %s";
    public static final String DATA_INTEGRITY_VIOLATION = "Operacja naruszyłaby spójność danych";
    public static final String INTERNAL_ERROR = "Coś poszło nie tak po naszej stronie";
}
