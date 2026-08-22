package com.pgoogol.finance.currency.domain;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Przeliczenie kwoty między walutami o różnych skalach. Jedyne miejsce w module,
 * w którym kwota zamienia się na liczbę dziesiętną — i natychmiast wraca
 * do jednostek podrzędnych.
 */
@Component
public class MoneyConverter {

    /**
     * @param amountMinor kwota w jednostkach podrzędnych waluty źródłowej
     * @param from        skala waluty źródłowej
     * @param rate        ile waluty docelowej za jednostkę źródłowej
     * @param to          skala waluty docelowej
     */
    public long convert(long amountMinor, MinorUnits from, BigDecimal rate, MinorUnits to) {

        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(rate, "rate");
        Objects.requireNonNull(to, "to");
        BigDecimal amount = from.toDecimal(amountMinor);
        BigDecimal converted = amount.multiply(rate);
        return to.toMinor(converted);
    }
}
