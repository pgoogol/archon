package com.pgoogol.diagnostics.core.report;

import com.pgoogol.diagnostics.core.UnitOfWorkSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.pgoogol.diagnostics.core.report.FindingFixtures.caller;
import static com.pgoogol.diagnostics.core.report.FindingFixtures.shape;
import static com.pgoogol.diagnostics.core.report.FindingFixtures.shapeFinding;
import static com.pgoogol.diagnostics.core.report.FindingFixtures.unit;
import static org.assertj.core.api.Assertions.assertThat;

class FindingFingerprintTest {

    private static final String ITEMS = "select * from order_item where order_id = ?";

    private final FindingFingerprint fingerprint = new FindingFingerprint();

    private final UnitOfWorkSummary unit = unit("GET /orders");

    @Test
    @DisplayName("odcisk to 12 małych znaków szesnastkowych")
    void of_whenShapeFinding_returnsTwelveHexCharacters() {

        // given
        Finding finding = shapeFinding("N_PLUS_ONE", shape(ITEMS, caller("list", 42)));

        // when
        String value = fingerprint.of(finding, unit);

        // then
        assertThat(value).matches("[0-9a-f]{12}");
    }

    @Test
    @DisplayName("dopisanie linii w pliku nie zmienia odcisku, bo numer linii do niego nie wchodzi")
    void of_whenOnlyLineDiffers_returnsSameFingerprint() {

        // given
        Finding before = shapeFinding("N_PLUS_ONE", shape(ITEMS, caller("list", 42)));
        Finding after = shapeFinding("N_PLUS_ONE", shape(ITEMS, caller("list", 57)));

        // when
        String first = fingerprint.of(before, unit);
        String second = fingerprint.of(after, unit);

        // then
        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("ten sam kształt wołany z innej metody to inny problem i inny odcisk")
    void of_whenMethodDiffers_returnsDifferentFingerprint() {

        // given
        Finding fromList = shapeFinding("N_PLUS_ONE", shape(ITEMS, caller("list", 42)));
        Finding fromExport = shapeFinding("N_PLUS_ONE", shape(ITEMS, caller("export", 42)));

        // when
        String first = fingerprint.of(fromList, unit);
        String second = fingerprint.of(fromExport, unit);

        // then
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("inny kod przy tym samym kształcie i miejscu daje inny odcisk")
    void of_whenCodeDiffers_returnsDifferentFingerprint() {

        // given
        Finding repeated = shapeFinding("N_PLUS_ONE", shape(ITEMS, caller("list", 42)));
        Finding slow = shapeFinding("SLOW_OPERATION", shape(ITEMS, caller("list", 42)));

        // when
        String first = fingerprint.of(repeated, unit);
        String second = fingerprint.of(slow, unit);

        // then
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("wniosek o całej jednostce ma odcisk jednostki, niezależny od jej najczęstszych kształtów")
    void of_whenUnitLevelFinding_dependsOnUnitNotShapes() {

        // given
        List<ShapeSummary> someShapes = List.of(shape("select 1"), shape("select 2"));
        List<ShapeSummary> otherShapes = List.of(shape("select 3"), shape("select 4"));
        Finding first = new Finding("OPERATION_COUNT", Severity.WARN, "Operacji: 60", 60, 50, someShapes);
        Finding second = new Finding("OPERATION_COUNT", Severity.WARN, "Operacji: 70", 70, 50, otherShapes);
        UnitOfWorkSummary otherUnit = unit("GET /customers");

        // when
        String sameUnitFirst = fingerprint.of(first, unit);
        String sameUnitSecond = fingerprint.of(second, unit);
        String differentUnit = fingerprint.of(first, otherUnit);

        // then
        assertThat(sameUnitFirst)
            .isEqualTo(sameUnitSecond)
            .isNotEqualTo(differentUnit);
    }
}
