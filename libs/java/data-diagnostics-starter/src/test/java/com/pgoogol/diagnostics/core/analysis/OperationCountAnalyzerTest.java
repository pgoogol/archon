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

import java.util.List;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static com.pgoogol.diagnostics.core.DataAccessEventBuilder.read;
import static com.pgoogol.diagnostics.core.DataAccessEventBuilder.write;
import static com.pgoogol.diagnostics.core.analysis.AnalyzerRuns.findings;
import static com.pgoogol.diagnostics.core.analysis.AnalyzerRuns.repeat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class OperationCountAnalyzerTest {

    private static final String BY_ID = "select * from orders where id = ?";

    private final OperationCountAnalyzer analyzer = new OperationCountAnalyzer(SeverityScale.of(10), 100);

    @Test
    @DisplayName("jednostka z mniejszą liczbą operacji niż próg nie daje wniosku")
    void findings_whenBelowThreshold_returnsNothing() {

        // given
        List<DataAccessEvent> events = repeat(9, read(BY_ID));

        // when
        List<Finding> findings = findings(analyzer, events);

        // then
        assertThat(findings).isEmpty();
    }

    @Test
    @DisplayName("jednostka z liczbą operacji równą progowi daje WARN")
    void findings_whenAtThreshold_reportsWarn() {

        // given
        List<DataAccessEvent> events = repeat(10, read(BY_ID));

        // when
        List<Finding> findings = findings(analyzer, events);

        // then
        Finding finding = findings.getFirst();
        assertAll(
            () -> assertThat(findings).hasSize(1),
            () -> assertThat(finding.code()).isEqualTo("OPERATION_COUNT"),
            () -> assertThat(finding.severity()).isEqualTo(Severity.WARN),
            () -> assertThat(finding.measured()).isEqualTo(10),
            () -> assertThat(finding.threshold()).isEqualTo(10));
    }

    @Test
    @DisplayName("pięciokrotność progu daje CRITICAL")
    void findings_whenFiveTimesThreshold_reportsCritical() {

        // given
        List<DataAccessEvent> events = repeat(50, read(BY_ID));

        // when
        List<Finding> findings = findings(analyzer, events);

        // then
        assertThat(findings).extracting(Finding::severity).containsExactly(Severity.CRITICAL);
    }

    @Test
    @DisplayName("liczą się operacje każdego rodzaju, nie tylko odczyty")
    void findings_whenReadsAndWritesMixed_countsAll() {

        // given
        List<DataAccessEvent> reads = repeat(6, read(BY_ID));
        List<DataAccessEvent> writes = repeat(4, write("update orders set status = ? where id = ?"));
        List<DataAccessEvent> events = Stream.concat(reads.stream(), writes.stream()).toList();

        // when
        List<Finding> findings = findings(analyzer, events);

        // then
        assertThat(findings).extracting(Finding::measured).containsExactly(10L);
    }

    @Test
    @DisplayName("wniosek podaje pięć najczęstszych kształtów, od najczęstszego")
    void findings_whenManyShapes_listsFiveMostFrequent() {

        // given: kształt "select N" pada N razy, od 1 do 7
        List<DataAccessEvent> events = LongStream.rangeClosed(1, 7)
            .boxed()
            .flatMap(count -> repeat(count, read("select " + count)).stream())
            .toList();

        // when
        List<Finding> findings = findings(analyzer, events);

        // then
        assertThat(findings.getFirst().shapes())
            .extracting(ShapeSummary::count)
            .containsExactly(7L, 6L, 5L, 4L, 3L);
    }

    @ParameterizedTest(name = "{0}: próg {1}")
    @CsvSource({"DEV, 50", "PROD, 100"})
    @DisplayName("próg domyślny zależy od trybu: 50 operacji w dev, 100 w prod")
    void defaults_whenModeGiven_usesModeThreshold(DiagnosticsMode mode, long threshold) {

        // given
        DiagnosticsSettings settings = DiagnosticsSettings.defaults(mode);
        OperationCountAnalyzer defaults = OperationCountAnalyzer.defaults(settings);
        List<DataAccessEvent> fewer = repeat(threshold - 1, read(BY_ID));
        List<DataAccessEvent> enough = repeat(threshold, read(BY_ID));

        // when
        List<Finding> belowThreshold = findings(defaults, fewer);
        List<Finding> atThreshold = findings(defaults, enough);

        // then
        assertAll(
            () -> assertThat(belowThreshold).isEmpty(),
            () -> assertThat(atThreshold).hasSize(1));
    }
}
