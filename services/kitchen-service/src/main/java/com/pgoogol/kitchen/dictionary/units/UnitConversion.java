package com.pgoogol.kitchen.dictionary.units;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Wynik sprowadzenia ilości do jednostek używanych w polskich przepisach.
 *
 * @param unitCode kod jednostki ze słownika albo {@code null}, gdy nie dało się
 *                 jej rozpoznać — wtedy wywołujący zostawia oryginalny zapis
 *                 w polu tekstowym ilości
 * @param warning  informacja dla użytkownika, gdy coś zostało przeliczone albo
 *                 nierozpoznane; {@code null}, gdy nie ma o czym mówić
 */
public record UnitConversion(BigDecimal min, BigDecimal max, String unitCode, String warning) {

    public boolean hasWarning() {

        return Objects.nonNull(warning);
    }
}
