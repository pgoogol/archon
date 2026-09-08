package com.pgoogol.kitchen.dictionary.domain;

import java.math.BigDecimal;

/**
 * Jednostka miary ze słownika.
 *
 * @param toBase przelicznik na jednostkę bazową rodzaju (gram albo mililitr);
 *               {@code null} dla {@link UnitKind#COUNT} i {@link UnitKind#OTHER},
 *               bo sztuki i szczypty nie mają wspólnej miary
 */
public record Unit(Long id, String code, String name, UnitKind kind, BigDecimal toBase) {

}
