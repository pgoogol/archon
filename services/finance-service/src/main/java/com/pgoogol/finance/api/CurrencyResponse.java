package com.pgoogol.finance.api;

/** Waluta słownikowa; {@code minorUnit} mówi klientowi, jak sformatować kwoty. */
public record CurrencyResponse(String code, String name, int minorUnit) {

}
