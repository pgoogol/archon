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
import java.util.Map;
import java.util.stream.Stream;

import static com.pgoogol.diagnostics.core.DataAccessEventBuilder.read;
import static com.pgoogol.diagnostics.core.analysis.AnalyzerRuns.findings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertAll;

class SlowOperationAnalyzerTest {

    private static final String REPORT = "select sum(total) from orders where created > ?";

    private final SlowOperationAnalyzer analyzer =
        new SlowOperationAnalyzer(Duration.ofMillis(100), Map.of(), 5, 100);

    @Test
    @DisplayName("operacja szybsza niż próg nie daje wniosku")
    void findings_whenBelowThreshold_returnsNothing() {

        // given
        List<DataAccessEvent> events = List.of(read(REPORT).millis(99).build());

        // when
        List<Finding> findings = findings(analyzer, events);

        // then
        assertThat(findings).isEmpty();
    }

    @Test
    @DisplayName("operacja trwająca dokładnie tyle, ile próg, daje WARN z pomiarem w milisekundach")
    void findings_whenAtThreshold_reportsWarn() {

        // given
        List<DataAccessEvent> events = List.of(read(REPORT).millis(100).build());

        // when
        List<Finding> findings = findings(analyzer, events);

        // then
        Finding finding = findings.getFirst();
        assertAll(
            () -> assertThat(findings).hasSize(1),
            () -> assertThat(finding.code()).isEqualTo("SLOW_OPERATION"),
            () -> assertThat(finding.severity()).isEqualTo(Severity.WARN),
            () -> assertThat(finding.measured()).isEqualTo(100),
            () -> assertThat(finding.threshold()).isEqualTo(100));
    }

    @Test
    @DisplayName("wykonanie dłuższe niż pięciokrotność progu daje CRITICAL")
    void findings_whenFiveTimesThreshold_reportsCritical() {

        // given
        List<DataAccessEvent> events = List.of(read(REPORT).millis(500).build());

        // when
        List<Finding> findings = findings(analyzer, events);

        // then
        assertThat(findings).extracting(Finding::severity).containsExactly(Severity.CRITICAL);
    }

    @Test
    @DisplayName("jeden wniosek na kształt liczy tylko wolne wykonania i podaje najdłuższe")
    void findings_whenShapeHasSlowAndFastExecutions_countsOnlySlowOnes() {

        // given
        List<DataAccessEvent> events = Stream.of(120L, 5L, 300L, 4L, 150L)
            .map(millis -> read(REPORT).millis(millis).build())
            .toList();

        // when
        List<Finding> findings = findings(analyzer, events);

        // then
        Finding finding = findings.getFirst();
        ShapeSummary shape = finding.shapes().getFirst();
        assertAll(
            () -> assertThat(findings).hasSize(1),
            () -> assertThat(finding.measured()).isEqualTo(300),
            () -> assertThat(shape.count()).isEqualTo(3),
            () -> assertThat(shape.totalTime()).isEqualTo(Duration.ofMillis(570)),
            () -> assertThat(finding.title()).contains("wolnych wykonań: 3"));
    }

    @Test
    @DisplayName("nieudane wolne wykonania są we wniosku policzone osobno")
    void findings_whenSlowExecutionFailed_marksFailuresSeparately() {

        // given
        List<DataAccessEvent> events = List.of(
            read(REPORT).millis(200).build(),
            read(REPORT).millis(3000).failed().build());

        // when
        List<Finding> findings = findings(analyzer, events);

        // then
        Finding finding = findings.getFirst();
        assertAll(
            () -> assertThat(finding.shapes().getFirst().failures()).isEqualTo(1),
            () -> assertThat(finding.title()).contains("w tym nieudanych: 1"));
    }

    @Test
    @DisplayName("magazyn z własnym progiem jest mierzony swoim progiem, reszta domyślnym")
    void findings_whenStoreHasOwnThreshold_usesIt() {

        // given
        SlowOperationAnalyzer perStore =
            new SlowOperationAnalyzer(Duration.ofMillis(100), Map.of("redis", Duration.ofMillis(10)), 5, 100);
        List<DataAccessEvent> events = List.of(
            read("get ?").store("redis").millis(20).build(),
            read(REPORT).millis(20).build());

        // when
        List<Finding> findings = findings(perStore, events);

        // then
        assertAll(
            () -> assertThat(findings).hasSize(1),
            () -> assertThat(findings.getFirst().threshold()).isEqualTo(10),
            () -> assertThat(findings.getFirst().shapes().getFirst().store().name()).isEqualTo("redis"));
    }

    @ParameterizedTest(name = "{0}: próg {1} ms")
    @CsvSource({"DEV, 100", "PROD, 500"})
    @DisplayName("próg domyślny zależy od trybu: 100 ms w dev, 500 ms w prod")
    void defaults_whenModeGiven_usesModeThreshold(DiagnosticsMode mode, long thresholdMillis) {

        // given
        DiagnosticsSettings settings = DiagnosticsSettings.defaults(mode);
        SlowOperationAnalyzer defaults = SlowOperationAnalyzer.defaults(settings);
        List<DataAccessEvent> faster = List.of(read(REPORT).millis(thresholdMillis - 1).build());
        List<DataAccessEvent> slow = List.of(read(REPORT).millis(thresholdMillis).build());

        // when
        List<Finding> belowThreshold = findings(defaults, faster);
        List<Finding> atThreshold = findings(defaults, slow);

        // then
        assertAll(
            () -> assertThat(belowThreshold).isEmpty(),
            () -> assertThat(atThreshold).hasSize(1));
    }

    @Test
    @DisplayName("próg krótszy niż milisekunda jest odrzucany, bo pomiar wniosku liczy się w milisekundach")
    void constructor_whenThresholdBelowMillisecond_fails() {

        // given
        Duration tooShort = Duration.ofNanos(500_000);

        // when & then
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new SlowOperationAnalyzer(tooShort, Map.of(), 5, 100))
            .withMessageContaining("1 ms");
    }
}
