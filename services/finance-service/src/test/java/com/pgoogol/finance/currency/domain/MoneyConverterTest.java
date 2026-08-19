package com.pgoogol.finance.currency.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyConverterTest {

    private final MoneyConverter converter = new MoneyConverter();

    @Test
    @DisplayName("przeliczenie na walutę bazową idzie przez skalę obu walut, nie przez stałą")
    void convert_whenBothCurrenciesHaveTwoDecimals_usesRate() {

        // given: 100,00 EUR po kursie 4,3215
        MinorUnits euro = new MinorUnits(2);
        MinorUnits zloty = new MinorUnits(2);

        // when
        long baseMinor = converter.convert(10_000L, euro, new BigDecimal("4.32150000"), zloty);

        // then
        assertThat(baseMinor).isEqualTo(43_215L);
    }

    @Test
    @DisplayName("waluta bez części ułamkowej przelicza się na dwumiejscową bez gubienia groszy")
    void convert_whenSourceHasNoFraction_scalesToTargetCurrency() {

        // given: 1000 JPY po kursie 0,0271
        MinorUnits yen = new MinorUnits(0);
        MinorUnits zloty = new MinorUnits(2);

        // when
        long baseMinor = converter.convert(1_000L, yen, new BigDecimal("0.02710000"), zloty);

        // then: 1000 × 0,0271 = 27,10 zł
        assertThat(baseMinor).isEqualTo(2_710L);
    }

    @Test
    @DisplayName("kurs równy jeden zostawia kwotę nietkniętą")
    void convert_whenRateIsOne_returnsSameAmount() {

        // given
        MinorUnits zloty = new MinorUnits(2);

        // when
        long baseMinor = converter.convert(12_345L, zloty, BigDecimal.ONE, zloty);

        // then
        assertThat(baseMinor).isEqualTo(12_345L);
    }
}
