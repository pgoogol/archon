package com.pgoogol.finance.currency.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MinorUnitsTest {

    @Test
    @DisplayName("waluta z zerową skalą nie ma części ułamkowej ani przy zapisie, ani przy odczycie")
    void toMinor_whenCurrencyHasNoFraction_keepsWholeUnits() {

        // given
        MinorUnits yen = new MinorUnits(0);

        // when
        long minor = yen.toMinor(new BigDecimal("1234"));

        // then
        assertThat(minor).isEqualTo(1234L);
        assertThat(yen.toDecimal(1234L)).isEqualByComparingTo("1234");
    }

    @Test
    @DisplayName("waluta dwumiejscowa zamienia grosze na jednostki podrzędne bez mnożenia przez 100")
    void toMinor_whenCurrencyHasTwoDecimals_usesScaleFromCurrency() {

        // given
        MinorUnits zloty = new MinorUnits(2);

        // when
        long minor = zloty.toMinor(new BigDecimal("12.34"));

        // then
        assertThat(minor).isEqualTo(1234L);
        assertThat(zloty.toDecimal(1234L)).isEqualByComparingTo("12.34");
    }

    @Test
    @DisplayName("kwota z nadmiarem miejsc zaokrągla połówkę w górę")
    void toMinor_whenAmountHasMoreDecimals_roundsHalfUp() {

        // given
        MinorUnits zloty = new MinorUnits(2);

        // when / then
        assertThat(zloty.toMinor(new BigDecimal("12.345"))).isEqualTo(1235L);
        assertThat(zloty.toMinor(new BigDecimal("12.344"))).isEqualTo(1234L);
    }

    @Test
    @DisplayName("waluta trzymiejscowa zachowuje trzecią cyfrę po przecinku")
    void toMinor_whenCurrencyHasThreeDecimals_keepsThirdDigit() {

        // given
        MinorUnits dinar = new MinorUnits(3);

        // when / then
        assertThat(dinar.toMinor(new BigDecimal("1.234"))).isEqualTo(1234L);
    }

    @Test
    @DisplayName("skala spoza zakresu walut świata jest odrzucana")
    void constructor_whenScaleOutOfRange_fails() {

        assertThatThrownBy(() -> new MinorUnits(5))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
