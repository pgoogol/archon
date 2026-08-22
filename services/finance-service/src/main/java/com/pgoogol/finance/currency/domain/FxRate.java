package com.pgoogol.finance.currency.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Kurs użyty do przeliczenia: ile waluty bazowej za jednostkę waluty obcej,
 * wraz z datą, z której faktycznie pochodzi.
 *
 * <p>Data bywa wcześniejsza niż data księgowania — NBP nie publikuje tabel
 * w weekendy i święta. Bez zapisania jej nie da się później odtworzyć, skąd
 * wzięła się kwota w walucie bazowej.</p>
 */
public record FxRate(BigDecimal rate, LocalDate rateDate) {

    public FxRate {

        Objects.requireNonNull(rate, "rate");
        Objects.requireNonNull(rateDate, "rateDate");
        if (rate.signum() <= 0) {
            throw new IllegalArgumentException("Kurs musi być dodatni: " + rate);
        }
    }

    /** Kurs waluty bazowej na samą siebie — zawsze 1, bez sięgania do tabeli kursów. */
    public static FxRate identity(LocalDate onDate) {

        return new FxRate(BigDecimal.ONE, onDate);
    }
}
