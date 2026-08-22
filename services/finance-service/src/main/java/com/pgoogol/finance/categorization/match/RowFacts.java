package com.pgoogol.finance.categorization.match;

import java.time.LocalDate;
import java.util.Objects;

/**
 * To, co o wierszu wyciągu wie dopasowywanie — bez encji i bez identyfikatorów
 * bazodanowych. Dzięki temu regułę „kwota o 5% wyższa i termin trzy dni
 * wcześniej" sprawdza się przez podanie liczb, a nie przez postawienie bazy.
 *
 * @param amountMinor kwota ze znakiem, dokładnie jak na wyciągu
 */
public record RowFacts(
    LocalDate bookedOn,
    long amountMinor,
    String currency,
    String description,
    String counterparty) {

    public RowFacts {

        Objects.requireNonNull(bookedOn, "bookedOn");
        Objects.requireNonNull(currency, "currency");
    }

    public long absoluteAmountMinor() {

        return Math.abs(amountMinor);
    }
}
