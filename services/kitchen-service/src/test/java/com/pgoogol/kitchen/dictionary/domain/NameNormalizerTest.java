package com.pgoogol.kitchen.dictionary.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Postać porównawcza decyduje o dopasowaniu do słownika i do katalogu — a wpisy
 * słowników startowych w migracji są nią zapisane, więc rozjazd tutaj oznaczałby
 * słownik, którego nie da się trafić nazwą.
 */
class NameNormalizerTest {

    @ParameterizedTest
    @DisplayName("normalize_whenNameHasCaseOrDiacritics_stripsThemToComparableForm")
    @CsvSource({
        "Cebula, cebula",
        "CEBULA, cebula",
        "  cebula  , cebula",
        "Włoska, wloska",
        "łyżka, lyzka",
        "danie główne, danie glowne",
        "wysokobiałkowa, wysokobialkowa",
        "Bałkańska, balkanska",
        "cebula   czerwona, cebula czerwona",
        "Żółć, zolc"
    })
    void normalize_whenNameHasCaseOrDiacritics_stripsThemToComparableForm(String input,
                                                                          String expected) {

        // when
        String normalized = NameNormalizer.normalize(input);

        // then
        assertThat(normalized).isEqualTo(expected);
    }

    @Test
    @DisplayName("normalize_whenValueIsNull_returnsNull")
    void normalize_whenValueIsNull_returnsNull() {

        // when
        String normalized = NameNormalizer.normalize(null);

        // then
        assertThat(normalized).isNull();
    }
}
