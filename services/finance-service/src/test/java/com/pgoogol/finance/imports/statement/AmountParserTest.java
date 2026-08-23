package com.pgoogol.finance.imports.statement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Formaty kwot z polskich wyciągów. Każdy przypadek tutaj wziął się z wariantu,
 * na którym naiwny parser się wykłada.
 */
class AmountParserTest {

    private final AmountParser parser = new AmountParser();

    @ParameterizedTest(name = "{0} przy {1} miejscach po przecinku daje {2}")
    @CsvSource({
        // separator tysięcy: spacja zwykła
        "'1 234,56', 2, 123456",
        "'-45,00',   2, -4500",
        "'0,05',     2, 5",
        // separator tysięcy: kropka, przecinek dziesiętny
        "'1.234,56', 2, 123456",
        // plus na początku i kod waluty na końcu
        "'+12,00',      2, 1200",
        "'-45,00 PLN',  2, -4500",
        "'1 234,56 EUR', 2, 123456",
        // waluta bez części ułamkowej
        "'1200',  0, 1200",
        "'1 200', 0, 1200"
    })
    @DisplayName("parse dla wariantu zapisu daje kwotę w jednostkach podrzędnych")
    void parse_forWriteVariant_returnsMinorUnits(String raw, int minorUnit, long expected) {

        // when
        long parsed = parser.parse(raw, minorUnit);

        // then
        assertThat(parsed).isEqualTo(expected);
    }

    @Test
    @DisplayName("parse gdy separatorem tysięcy jest spacja niełamliwa, czyta kwotę")
    void parse_whenThousandsSeparatorIsNonBreakingSpace_readsAmount() {

        // given: U+00A0 — dokładnie to, co wychodzi z eksportu bankowego,
        // a czego zwykły trim() nie rusza
        String raw = "1\u00A0234,56";

        // when
        long parsed = parser.parse(raw, 2);

        // then
        assertThat(parsed).isEqualTo(123456L);
    }

    @Test
    @DisplayName("parse gdy separatorem jest wąska spacja niełamliwa, czyta kwotę")
    void parse_whenThousandsSeparatorIsNarrowNoBreakSpace_readsAmount() {

        // given: U+202F
        String raw = "1\u202F234,56";

        // when
        long parsed = parser.parse(raw, 2);

        // then
        assertThat(parsed).isEqualTo(123456L);
    }

    @Test
    @DisplayName("parse gdy kwota ma więcej miejsc niż waluta, zaokrągla połówki w górę")
    void parse_whenAmountHasMoreDecimalsThanCurrency_roundsHalfUp() {

        // given
        String raw = "12,345";

        // when
        long parsed = parser.parse(raw, 2);

        // then
        assertThat(parsed).isEqualTo(1235L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "brak", "12,34,56"})
    @DisplayName("parse gdy tekst nie jest kwotą, wywala się zamiast zgadywać")
    void parse_whenTextIsNotAnAmount_throws(String raw) {

        // when & then
        assertThatThrownBy(() -> parser.parse(raw, 2))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("parseOptional gdy kolumna jest pusta, zwraca brak wartości")
    void parseOptional_whenColumnIsEmpty_returnsNull() {

        // when & then
        assertThat(parser.parseOptional(null, 2)).isNull();
        assertThat(parser.parseOptional("   ", 2)).isNull();
    }

    @Test
    @DisplayName("parseOptional gdy kolumna ma kwotę, czyta ją tak samo jak parse")
    void parseOptional_whenColumnHasAmount_readsIt() {

        // when
        Long parsed = parser.parseOptional("1 000,00", 2);

        // then
        assertThat(parsed).isEqualTo(100000L);
    }
}
