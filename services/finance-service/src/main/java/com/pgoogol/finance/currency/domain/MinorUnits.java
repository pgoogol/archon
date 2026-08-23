package com.pgoogol.finance.currency.domain;

import com.pgoogol.finance.common.ExceptionMessageConstants;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Skala waluty, czyli liczba miejsc po przecinku. Istnieje, bo mnożenie przez 100
 * jest błędem: PLN i EUR mają 2 miejsca, JPY 0, TND 3. Wartość pochodzi
 * z {@code currency.minor_unit}, nigdy ze stałej w kodzie.
 *
 * <p>Kwota w jednostkach podrzędnych jest liczbą całkowitą — nigdzie w tym module
 * kwota nie przechodzi przez {@code double} ani {@code float}.</p>
 */
public record MinorUnits(int scale) {

    public MinorUnits {

        if (scale < 0 || scale > 4) {

            throw new IllegalArgumentException(
                ExceptionMessageConstants.MINOR_UNIT_OUT_OF_RANGE.formatted(scale));
        }
    }

    /** Kwota podrzędna → dziesiętna: {@code (1234, scale=2)} daje {@code 12.34}. */
    public BigDecimal toDecimal(long amountMinor) {

        return BigDecimal.valueOf(amountMinor, scale);
    }

    /**
     * Kwota dziesiętna → podrzędna, z zaokrągleniem połówek w górę.
     * Zaokrąglenie jest tu nieuniknione: przeliczenie kursem prawie nigdy nie
     * wychodzi równo na groszach.
     */
    public long toMinor(BigDecimal amount) {

        BigDecimal scaled = amount.setScale(scale, RoundingMode.HALF_UP);
        BigInteger unscaled = scaled.unscaledValue();
        return unscaled.longValueExact();
    }
}
