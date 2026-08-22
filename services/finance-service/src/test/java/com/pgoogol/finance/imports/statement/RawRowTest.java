package com.pgoogol.finance.imports.statement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RawRowTest {

    private static final LocalDate DZIEN = LocalDate.of(2026, 1, 5);

    @Test
    @DisplayName("konstruktor gdy kwota jest zerowa, wywala się")
    void constructor_whenAmountIsZero_throws() {

        // when & then: wiersz na zero nie jest operacją, tylko śmieciem w pliku
        assertThatThrownBy(() -> row(0L, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("konstruktor gdy pozycja jest ujemna, wywala się")
    void constructor_whenOrdinalIsNegative_throws() {

        // when & then
        assertThatThrownBy(() -> new RawRow(-1, DZIEN, -100L, null, null, null, null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("konstruktor gdy kwota oryginalna jest bez waluty, wywala się")
    void constructor_whenOriginalAmountHasNoCurrency_throws() {

        // when & then
        assertThatThrownBy(() -> row(-100L, 50L, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("konstruktor gdy waluta oryginalna jest bez kwoty, wywala się")
    void constructor_whenOriginalCurrencyHasNoAmount_throws() {

        // when & then
        assertThatThrownBy(() -> row(-100L, null, "EUR"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("konstruktor gdy kwota oryginalna ma walutę, przechodzi")
    void constructor_whenOriginalAmountHasCurrency_passes() {

        // when
        RawRow created = row(-4500L, -1050L, "EUR");

        // then
        assertThat(created.originalCurrency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("hasBankReference gdy referencji brak albo jest pusta, zwraca fałsz")
    void hasBankReference_whenReferenceIsMissingOrBlank_returnsFalse() {

        // given
        RawRow bezReferencji = new RawRow(0, DZIEN, -100L, null, null, null, null, null, null);
        RawRow zPusta = new RawRow(0, DZIEN, -100L, null, null, null, null, null, "  ");

        // when & then
        assertThat(bezReferencji.hasBankReference()).isFalse();
        assertThat(zPusta.hasBankReference()).isFalse();
    }

    @Test
    @DisplayName("hasBankReference gdy referencja jest, zwraca prawdę")
    void hasBankReference_whenReferenceIsPresent_returnsTrue() {

        // given
        RawRow row = new RawRow(0, DZIEN, -100L, null, null, null, null, null, "REF-1");

        // when & then
        assertThat(row.hasBankReference()).isTrue();
    }

    private RawRow row(long amountMinor, Long originalAmountMinor, String originalCurrency) {

        return new RawRow(0, DZIEN, amountMinor, "PLN", originalAmountMinor, originalCurrency,
            "opis", null, null);
    }
}
