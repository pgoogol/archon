package com.pgoogol.finance.imports.statement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParsedStatementTest {

    private static final LocalDate STYCZEN = LocalDate.of(2026, 1, 1);
    private static final LocalDate LUTY = LocalDate.of(2026, 2, 1);

    @Test
    @DisplayName("konstruktor gdy okres jest odwrócony, wywala się")
    void constructor_whenPeriodIsReversed_throws() {

        // when & then
        assertThatThrownBy(() -> new ParsedStatement(LUTY, STYCZEN, null, null, List.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("konstruktor gdy okres ma tylko jedną datę, przechodzi")
    void constructor_whenPeriodHasOnlyOneDate_passes() {

        // when & then: nie każdy format podaje oba końce okresu
        assertThat(new ParsedStatement(STYCZEN, null, null, null, List.of()).periodFrom())
            .isEqualTo(STYCZEN);
        assertThat(new ParsedStatement(null, LUTY, null, null, List.of()).periodTo())
            .isEqualTo(LUTY);
    }

    @Test
    @DisplayName("konstruktor kopiuje wiersze, więc zmiana listy źródłowej nic nie psuje")
    void constructor_copiesRows_soChangingSourceListChangesNothing() {

        // given
        List<RawRow> mutowalna = new ArrayList<>();
        mutowalna.add(new RawRow(0, STYCZEN, -100L, null, null, null, "opis", null, null));
        ParsedStatement statement = new ParsedStatement(STYCZEN, LUTY, null, null, mutowalna);

        // when
        mutowalna.clear();

        // then
        assertThat(statement.rows()).hasSize(1);
    }

    @Test
    @DisplayName("isEmpty gdy nie ma wierszy, zwraca prawdę")
    void isEmpty_whenThereAreNoRows_returnsTrue() {

        // when & then
        assertThat(new ParsedStatement(STYCZEN, LUTY, null, null, List.of()).isEmpty()).isTrue();
    }
}
