package com.pgoogol.finance.categorization.match;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextMatcherTest {

    private final TextMatcher matcher = new TextMatcher();

    @Test
    @DisplayName("contains gdy tekst różni się wielkością liter i spacjami, dopasowuje")
    void contains_whenTextDiffersInCaseAndSpacing_matches() {

        // given: bank potrafi wydrukować ten sam opis raz z podwójną spacją
        String text = "  ZAKUP   BIEDRONKA  1234 ";

        // when
        boolean matched = matcher.contains(text, "biedronka 1234");

        // then
        assertThat(matched).isTrue();
    }

    @Test
    @DisplayName("contains gdy wzorzec jest pusty, nie dopasowuje niczego")
    void contains_whenPatternIsBlank_matchesNothing() {

        // given & when & then: pusty wzorzec pasujący do wszystkiego zamieniłby
        // regułę w przypisanie wszystkiego do jednej kategorii
        assertThat(matcher.contains("cokolwiek", "   ")).isFalse();
    }

    @Test
    @DisplayName("contains gdy tekst jest pusty, nie dopasowuje")
    void contains_whenTextIsNull_doesNotMatch() {

        // given & when & then
        assertThat(matcher.contains(null, "biedronka")).isFalse();
    }

    @Test
    @DisplayName("contains gdy wzorzec nie występuje w tekście, nie dopasowuje")
    void contains_whenPatternIsAbsent_doesNotMatch() {

        // given & when & then
        assertThat(matcher.contains("ZAKUP LIDL", "biedronka")).isFalse();
    }

    @Test
    @DisplayName("normalize zwija białe znaki i podnosi do wielkich liter")
    void normalize_collapsesWhitespaceAndUpperCases() {

        // given & when
        String normalized = matcher.normalize("  Opłata\tza   prąd\n");

        // then
        assertThat(normalized).isEqualTo("OPŁATA ZA PRĄD");
    }
}
