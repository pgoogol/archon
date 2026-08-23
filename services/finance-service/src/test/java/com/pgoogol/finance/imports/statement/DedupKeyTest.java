package com.pgoogol.finance.imports.statement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DedupKeyTest {

    private static final LocalDate DAY = LocalDate.of(2026, 1, 5);

    private final DedupKey dedupKey = new DedupKey();

    @Test
    @DisplayName("fromContent dla tych samych danych daje ten sam klucz")
    void fromContent_forSameData_returnsSameKey() {

        // when
        String first = dedupKey.fromContent(1L, DAY, -1200L, "Kawa", 0);
        String second = dedupKey.fromContent(1L, DAY, -1200L, "Kawa", 0);

        // then
        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("fromContent gdy zmienia się numer kolejny, daje inny klucz")
    void fromContent_whenOrdinalDiffers_returnsDifferentKey() {

        // given: dwie identyczne kawy tego samego dnia to dwie operacje,
        // nie jedna — numer kolejny jest jedynym, co je odróżnia
        String first = dedupKey.fromContent(1L, DAY, -1200L, "Kawa", 0);

        // when
        String second = dedupKey.fromContent(1L, DAY, -1200L, "Kawa", 1);

        // then
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    @DisplayName("fromContent gdy zmienia się konto, daje inny klucz")
    void fromContent_whenAccountDiffers_returnsDifferentKey() {

        // given
        String onFirstAccount = dedupKey.fromContent(1L, DAY, -1200L, "Kawa", 0);

        // when
        String onSecondAccount = dedupKey.fromContent(2L, DAY, -1200L, "Kawa", 0);

        // then
        assertThat(onSecondAccount).isNotEqualTo(onFirstAccount);
    }

    @Test
    @DisplayName("fromContent gdy opis różni się tylko spacjami i wielkością liter, daje ten sam klucz")
    void fromContent_whenDescriptionDiffersOnlyInSpacingAndCase_returnsSameKey() {

        // given: bank potrafi wyeksportować ten sam opis raz z podwójną spacją
        String canonical = dedupKey.fromContent(1L, DAY, -1200L, "Kawa u Zbycha", 0);

        // when
        String messy = dedupKey.fromContent(1L, DAY, -1200L, "  kawa   U ZBYCHA ", 0);

        // then
        assertThat(messy).isEqualTo(canonical);
    }

    @Test
    @DisplayName("fromContent gdy opisu brak, nadal daje klucz")
    void fromContent_whenDescriptionIsMissing_stillReturnsKey() {

        // when
        String key = dedupKey.fromContent(1L, DAY, -1200L, null, 0);

        // then
        assertThat(key).hasSize(64);
    }

    @Test
    @DisplayName("fromBankReference dla tej samej referencji daje ten sam klucz")
    void fromBankReference_forSameReference_returnsSameKey() {

        // when
        String first = dedupKey.fromBankReference(1L, "REF-001");
        String second = dedupKey.fromBankReference(1L, " REF-001 ");

        // then
        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("fromBankReference i fromContent nie mogą dać tego samego klucza")
    void fromBankReference_neverCollidesWithContentKey() {

        // given
        String referenceKey = dedupKey.fromBankReference(1L, "REF-001");

        // when
        String contentKey = dedupKey.fromContent(1L, DAY, -1200L, "REF-001", 0);

        // then
        assertThat(referenceKey).isNotEqualTo(contentKey);
    }

    @Test
    @DisplayName("normalize redukuje białe znaki i podnosi do wersalików")
    void normalize_collapsesWhitespaceAndUppercases() {

        // when
        String normalized = dedupKey.normalize("  Płatność   kartą\tw sklepie ");

        // then
        assertThat(normalized).isEqualTo("PŁATNOŚĆ KARTĄ W SKLEPIE");
    }

    @Test
    @DisplayName("normalize gdy opisu brak, daje pusty tekst")
    void normalize_whenDescriptionIsMissing_returnsEmptyText() {

        // when & then
        assertThat(dedupKey.normalize(null)).isEmpty();
    }
}
