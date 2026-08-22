package com.pgoogol.finance.imports.statement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DedupKeyTest {

    private static final LocalDate DZIEN = LocalDate.of(2026, 1, 5);

    private final DedupKey dedupKey = new DedupKey();

    @Test
    @DisplayName("fromContent dla tych samych danych daje ten sam klucz")
    void fromContent_forSameData_returnsSameKey() {

        // when
        String first = dedupKey.fromContent(1L, DZIEN, -1200L, "Kawa", 0);
        String second = dedupKey.fromContent(1L, DZIEN, -1200L, "Kawa", 0);

        // then
        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("fromContent gdy zmienia się numer kolejny, daje inny klucz")
    void fromContent_whenOrdinalDiffers_returnsDifferentKey() {

        // given: dwie identyczne kawy tego samego dnia to dwie operacje,
        // nie jedna — numer kolejny jest jedynym, co je odróżnia
        String pierwsza = dedupKey.fromContent(1L, DZIEN, -1200L, "Kawa", 0);

        // when
        String druga = dedupKey.fromContent(1L, DZIEN, -1200L, "Kawa", 1);

        // then
        assertThat(druga).isNotEqualTo(pierwsza);
    }

    @Test
    @DisplayName("fromContent gdy zmienia się konto, daje inny klucz")
    void fromContent_whenAccountDiffers_returnsDifferentKey() {

        // given
        String naPierwszym = dedupKey.fromContent(1L, DZIEN, -1200L, "Kawa", 0);

        // when
        String naDrugim = dedupKey.fromContent(2L, DZIEN, -1200L, "Kawa", 0);

        // then
        assertThat(naDrugim).isNotEqualTo(naPierwszym);
    }

    @Test
    @DisplayName("fromContent gdy opis różni się tylko spacjami i wielkością liter, daje ten sam klucz")
    void fromContent_whenDescriptionDiffersOnlyInSpacingAndCase_returnsSameKey() {

        // given: bank potrafi wyeksportować ten sam opis raz z podwójną spacją
        String kanoniczny = dedupKey.fromContent(1L, DZIEN, -1200L, "Kawa u Zbycha", 0);

        // when
        String rozjechany = dedupKey.fromContent(1L, DZIEN, -1200L, "  kawa   U ZBYCHA ", 0);

        // then
        assertThat(rozjechany).isEqualTo(kanoniczny);
    }

    @Test
    @DisplayName("fromContent gdy opisu brak, nadal daje klucz")
    void fromContent_whenDescriptionIsMissing_stillReturnsKey() {

        // when
        String key = dedupKey.fromContent(1L, DZIEN, -1200L, null, 0);

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
        String zReferencji = dedupKey.fromBankReference(1L, "REF-001");

        // when
        String zTresci = dedupKey.fromContent(1L, DZIEN, -1200L, "REF-001", 0);

        // then
        assertThat(zReferencji).isNotEqualTo(zTresci);
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
