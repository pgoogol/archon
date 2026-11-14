package com.pgoogol.diagnostics.core.analysis;

import com.pgoogol.diagnostics.core.DataAccessEvent;
import com.pgoogol.diagnostics.core.DiagnosticsMode;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;
import com.pgoogol.diagnostics.core.report.Finding;
import com.pgoogol.diagnostics.core.report.Severity;
import com.pgoogol.diagnostics.core.report.ShapeSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static com.pgoogol.diagnostics.core.DataAccessEventBuilder.read;
import static com.pgoogol.diagnostics.core.DataAccessEventBuilder.write;
import static com.pgoogol.diagnostics.core.analysis.AnalyzerRuns.findings;
import static com.pgoogol.diagnostics.core.analysis.AnalyzerRuns.repeat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class NPlusOneAnalyzerTest {

    private static final String ITEMS = "select * from order_item where order_id = ?";

    private final NPlusOneAnalyzer analyzer = new NPlusOneAnalyzer(SeverityScale.of(5), 100);

    @Test
    @DisplayName("odczyt powtórzony mniej razy niż próg nie daje wniosku")
    void findings_whenReadRepeatedBelowThreshold_returnsNothing() {

        // given
        List<DataAccessEvent> events = repeat(4, read(ITEMS));

        // when
        List<Finding> findings = findings(analyzer, events);

        // then
        assertThat(findings).isEmpty();
    }

    @Test
    @DisplayName("odczyt powtórzony tyle razy, ile wynosi próg, daje WARN z kształtem, czasem i miejscem wywołania")
    void findings_whenReadRepeatedAtThreshold_reportsWarnWithShapeDetails() {

        // given
        List<DataAccessEvent> events = repeat(5, read(ITEMS).millis(3).calledFrom("com.example.OrderService", "list", 42));

        // when
        List<Finding> findings = findings(analyzer, events);

        // then
        Finding finding = findings.getFirst();
        ShapeSummary shape = finding.shapes().getFirst();
        assertAll(
            () -> assertThat(findings).hasSize(1),
            () -> assertThat(finding.code()).isEqualTo("N_PLUS_ONE"),
            () -> assertThat(finding.severity()).isEqualTo(Severity.WARN),
            () -> assertThat(finding.measured()).isEqualTo(5),
            () -> assertThat(finding.threshold()).isEqualTo(5),
            () -> assertThat(finding.title()).contains("5 razy"),
            () -> assertThat(shape.shape()).isEqualTo(ITEMS),
            () -> assertThat(shape.totalTime()).isEqualTo(Duration.ofMillis(15)),
            () -> assertThat(shape.callers()).extracting(caller -> caller.method()).containsExactly("list"));
    }

    @Test
    @DisplayName("pięciokrotność progu daje CRITICAL")
    void findings_whenReadRepeatedFiveTimesThreshold_reportsCritical() {

        // given
        List<DataAccessEvent> events = repeat(25, read(ITEMS));

        // when
        List<Finding> findings = findings(analyzer, events);

        // then
        assertThat(findings).extracting(Finding::severity).containsExactly(Severity.CRITICAL);
    }

    @Test
    @DisplayName("powtórzone zapisy to nie N+1, tylko sprawa analizy batchy")
    void findings_whenWritesRepeated_ignoresThem() {

        // given
        List<DataAccessEvent> events = repeat(10, write("insert into order_item values (?, ?)"));

        // when
        List<Finding> findings = findings(analyzer, events);

        // then
        assertThat(findings).isEmpty();
    }

    @Test
    @DisplayName("kilka powtarzanych kształtów daje osobne wnioski, od najczęstszego")
    void findings_whenSeveralShapesRepeat_ordersByCountDescending() {

        // given
        List<DataAccessEvent> items = repeat(6, read(ITEMS));
        List<DataAccessEvent> customers = repeat(9, read("select * from customer where id = ?"));
        List<DataAccessEvent> events = Stream.concat(items.stream(), customers.stream()).toList();

        // when
        List<Finding> findings = findings(analyzer, events);

        // then
        assertThat(findings).extracting(Finding::measured).containsExactly(9L, 6L);
    }

    @ParameterizedTest(name = "{0}: próg {1}")
    @CsvSource({"DEV, 5", "PROD, 10"})
    @DisplayName("próg domyślny zależy od trybu: 5 w dev, 10 w prod")
    void defaults_whenModeGiven_usesModeThreshold(DiagnosticsMode mode, long threshold) {

        // given
        DiagnosticsSettings settings = DiagnosticsSettings.defaults(mode);
        NPlusOneAnalyzer defaults = NPlusOneAnalyzer.defaults(settings);

        List<DataAccessEvent> fewer = repeat(threshold - 1, read(ITEMS));
        List<DataAccessEvent> enough = repeat(threshold, read(ITEMS));

        // when
        List<Finding> belowThreshold = findings(defaults, fewer);
        List<Finding> atThreshold = findings(defaults, enough);

        // then
        assertAll(
            () -> assertThat(belowThreshold).isEmpty(),
            () -> assertThat(atThreshold).hasSize(1));
    }
}
