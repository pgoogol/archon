package com.pgoogol.kitchen.dictionary.units;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Przeliczanie jednostek jest robotą kodu, nie modelu językowego — więc każda
 * reguła z tabeli ma tu swój przypadek. Rozjazd tutaj znaczy przepis z ilością
 * wziętą z sufitu.
 */
class UnitConverterTest {

    @ParameterizedTest
    @DisplayName("convert_whenUnitIsKnownAlias_mapsToDictionaryCodeWithoutRescaling")
    @CsvSource({
        "tbsp, lyzka",
        "Tablespoon, lyzka",
        "łyżka stołowa, lyzka",
        "tsp, lyzeczka",
        "cup, szklanka",
        "cups, szklanka",
        "gram, g",
        "kg, kg",
        "clove, zabek",
        "pinch, szczypta",
        "to taste, do_smaku"
    })
    void convert_whenUnitIsKnownAlias_mapsToDictionaryCodeWithoutRescaling(String rawUnit,
                                                                           String expectedCode) {

        // given
        BigDecimal amount = new BigDecimal("2");

        // when
        UnitConversion conversion = UnitConverter.convert(amount, amount, rawUnit);

        // then
        assertThat(conversion.unitCode()).isEqualTo(expectedCode);
        assertThat(conversion.min()).isEqualByComparingTo("2");
        assertThat(conversion.hasWarning()).isFalse();
    }

    @Test
    @DisplayName("convert_whenUnitIsOunces_rescalesToGramsAndWarns")
    void convert_whenUnitIsOunces_rescalesToGramsAndWarns() {

        // given
        BigDecimal amount = new BigDecimal("8");

        // when
        UnitConversion conversion = UnitConverter.convert(amount, amount, "oz");

        // then: 8 × 28,35 = 226,8 → 227; gramy zostają dokładne
        assertThat(conversion.unitCode()).isEqualTo("g");
        assertThat(conversion.min()).isEqualByComparingTo("227");
        assertThat(conversion.hasWarning()).isTrue();
    }

    @Test
    @DisplayName("convert_whenUnitIsPound_rescalesToGrams")
    void convert_whenUnitIsPound_rescalesToGrams() {

        // when
        UnitConversion conversion = UnitConverter.convert(BigDecimal.ONE, BigDecimal.ONE, "lb");

        // then: 453,6 → 454
        assertThat(conversion.unitCode()).isEqualTo("g");
        assertThat(conversion.min()).isEqualByComparingTo("454");
    }

    @Test
    @DisplayName("convert_whenAmountIsSmall_keepsSingleGramPrecision")
    void convert_whenAmountIsSmall_keepsSingleGramPrecision() {

        // when
        UnitConversion conversion = UnitConverter.convert(BigDecimal.ONE, BigDecimal.ONE, "oz");

        // then: 28,35 → 28
        assertThat(conversion.min()).isEqualByComparingTo("28");
    }

    @Test
    @DisplayName("convert_whenRangeGiven_rescalesBothEnds")
    void convert_whenRangeGiven_rescalesBothEnds() {

        // when
        UnitConversion conversion =
            UnitConverter.convert(new BigDecimal("1"), new BigDecimal("2"), "stick");

        // then
        assertThat(conversion.min()).isEqualByComparingTo("113");
        assertThat(conversion.max()).isEqualByComparingTo("226");
    }

    @Test
    @DisplayName("convert_whenUnitIsUnknown_keepsAmountAndReportsWarning")
    void convert_whenUnitIsUnknown_keepsAmountAndReportsWarning() {

        // when
        UnitConversion conversion =
            UnitConverter.convert(BigDecimal.TEN, BigDecimal.TEN, "dzbanek");

        // then: nie zgadujemy — ilość zostaje, a jednostka wraca pusta z ostrzeżeniem
        assertThat(conversion.unitCode()).isNull();
        assertThat(conversion.min()).isEqualByComparingTo("10");
        assertThat(conversion.warning()).contains("dzbanek");
    }

    @Test
    @DisplayName("convert_whenUnitIsBlank_returnsAmountUntouched")
    void convert_whenUnitIsBlank_returnsAmountUntouched() {

        // when
        UnitConversion conversion = UnitConverter.convert(BigDecimal.ONE, null, "  ");

        // then
        assertThat(conversion.unitCode()).isNull();
        assertThat(conversion.hasWarning()).isFalse();
    }

    @ParameterizedTest
    @DisplayName("fahrenheitToCelsius_whenOvenTemperature_roundsToNearestFive")
    @CsvSource({
        "350, 175",
        "375, 190",
        "400, 205",
        "425, 220"
    })
    void fahrenheitToCelsius_whenOvenTemperature_roundsToNearestFive(int fahrenheit,
                                                                     int expectedCelsius) {

        // when
        int celsius = UnitConverter.fahrenheitToCelsius(fahrenheit);

        // then
        assertThat(celsius).isEqualTo(expectedCelsius);
    }
}
