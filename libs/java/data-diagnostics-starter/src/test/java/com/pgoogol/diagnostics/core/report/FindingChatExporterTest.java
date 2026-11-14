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

class FindingChatExporterTest {

    private static final String ITEMS = "select * from order_item where order_id = ?";

    private final FindingChatExporter exporter = new FindingChatExporter(new FindingHints());

    private final UnitOfWorkSummary unit = unit("GET /orders");

    @Test
    @DisplayName("eksport niesie wszystko, czego trzeba do poprawki: kod, pomiar, kształt, miejsce i podpowiedź")
    void export_whenShapeFinding_writesContextAndHint() {

        // given
        Finding finding = shapeFinding("N_PLUS_ONE", shape(ITEMS, caller("list", 42)));
        ReportedFinding reported = new ReportedFinding(finding, unit, "a3f9c1e07b2d");

        // when
        String text = exporter.export(List.of(reported));

        // then
        assertThat(text).contains(
            "## N_PLUS_ONE (WARN)",
            "- jednostka: `GET /orders` (http), operacji 52, w bazie 612 ms z 890 ms",
            "- pomiar: 37, próg: 5",
            "- odcisk: `a3f9c1e07b2d`",
            "```sql\n" + ITEMS + "\n```",
            "- wywołane z `com.example.OrderService.list:42` przez `OrderRepository.findAllByStatus`",
            "- typowa poprawka: Dociągnij powiązania jednym zapytaniem");
    }

    @Test
    @DisplayName("wniosek o transakcji podaje jej nazwę, a nieznany kod nie dostaje podpowiedzi")
    void export_whenSubjectFindingWithUnknownCode_writesSubjectWithoutHint() {

        // given
        Finding finding = Finding.about("OrderService.place", "CUSTOM_CHECK", Severity.INFO, "Własna analiza", 3, 1);
        ReportedFinding reported = new ReportedFinding(finding, unit, "0123456789ab");

        // when
        String text = exporter.export(List.of(reported));

        // then
        assertThat(text)
            .contains("- dotyczy: `OrderService.place`")
            .doesNotContain("typowa poprawka");
    }

    @Test
    @DisplayName("pusta lista daje jedno zdanie zamiast pustego nagłówka")
    void export_whenNoFindings_saysSo() {

        // when
        String text = exporter.export(List.of());

        // then
        assertThat(text).isEqualTo("Brak wniosków pasujących do filtra.\n");
    }
}
