package com.pgoogol.finance.imports.statement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParsedStatementTest {

    private static final LocalDate JANUARY = LocalDate.of(2026, 1, 1);
    private static final LocalDate FEBRUARY = LocalDate.of(2026, 2, 1);

    @Test
    @DisplayName("konstruktor gdy okres jest odwrócony, wywala się")
    void constructor_whenPeriodIsReversed_throws() {

        // when & then
        assertThatThrownBy(() -> new ParsedStatement(FEBRUARY, JANUARY, null, null, List.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("konstruktor gdy okres ma tylko jedną datę, przechodzi")
    void constructor_whenPeriodHasOnlyOneDate_passes() {

        // when & then: nie każdy format podaje oba końce okresu
        assertThat(new ParsedStatement(JANUARY, null, null, null, List.of()).periodFrom())
            .isEqualTo(JANUARY);
        assertThat(new ParsedStatement(null, FEBRUARY, null, null, List.of()).periodTo())
            .isEqualTo(FEBRUARY);
    }

    @Test
    @DisplayName("konstruktor kopiuje wiersze, więc zmiana listy źródłowej nic nie psuje")
    void constructor_copiesRows_soChangingSourceListChangesNothing() {

        // given
        List<RawRow> mutable = new ArrayList<>();
        mutable.add(new RawRow(0, JANUARY, -100L, null, null, null, "opis", null, null));
        ParsedStatement statement = new ParsedStatement(JANUARY, FEBRUARY, null, null, mutable);

        // when
        mutable.clear();

        // then
        assertThat(statement.rows()).hasSize(1);
    }

    @Test
    @DisplayName("isEmpty gdy nie ma wierszy, zwraca prawdę")
    void isEmpty_whenThereAreNoRows_returnsTrue() {

        // when & then
        assertThat(new ParsedStatement(JANUARY, FEBRUARY, null, null, List.of()).isEmpty()).isTrue();
    }
}
