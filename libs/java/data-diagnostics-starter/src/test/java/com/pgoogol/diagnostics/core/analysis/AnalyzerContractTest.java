package com.pgoogol.diagnostics.core.analysis;

import com.pgoogol.diagnostics.core.DataAccessEvent;
import com.pgoogol.diagnostics.core.DiagnosticsEngine;
import com.pgoogol.diagnostics.core.DiagnosticsMode;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;
import com.pgoogol.diagnostics.core.UnitOfWorkScope;
import com.pgoogol.diagnostics.core.UnitOfWorkType;
import com.pgoogol.diagnostics.core.report.Finding;
import com.pgoogol.diagnostics.core.report.FindingReporter;
import com.pgoogol.diagnostics.core.report.Severity;
import com.pgoogol.diagnostics.core.report.ShapeSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.IntFunction;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static com.pgoogol.diagnostics.core.DataAccessEventBuilder.read;
import static com.pgoogol.diagnostics.core.DataAccessEventBuilder.write;
import static com.pgoogol.diagnostics.core.analysis.AnalyzerRuns.findings;
import static com.pgoogol.diagnostics.core.analysis.AnalyzerRuns.repeat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Kontrakt wspólny dla każdej analizy operacji: te same scenariusze progów, wyłączenia
 * i limitu kształtów, a różni się tylko to, jak dla danej analizy zbudować pomiar.
 */
class AnalyzerContractTest {

    private static final int SHAPE_LIMIT = 100;

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    @DisplayName("pusta jednostka nie daje wniosku")
    void findings_whenUnitEmpty_returnsNothing(Scenario scenario) {

        // given
        DiagnosticAnalyzer analyzer = scenario.analyzer(SHAPE_LIMIT);

        // when
        List<Finding> findings = findings(analyzer, List.of());

        // then
        assertThat(findings).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    @DisplayName("pomiar poniżej progu nie daje wniosku")
    void findings_whenBelowThreshold_returnsNothing(Scenario scenario) {

        // given
        List<DataAccessEvent> events = scenario.events("s0", scenario.threshold() - 1);

        // when
        List<Finding> findings = findings(scenario.analyzer(SHAPE_LIMIT), events);

        // then
        assertThat(findings).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    @DisplayName("pomiar równy progowi daje jeden wniosek WARN")
    void findings_whenAtThreshold_reportsWarn(Scenario scenario) {

        // given
        List<DataAccessEvent> events = scenario.events("s0", scenario.threshold());

        // when
        List<Finding> findings = findings(scenario.analyzer(SHAPE_LIMIT), events);

        // then
        assertAll(
            () -> assertThat(findings).extracting(Finding::severity).containsExactly(Severity.WARN),
            () -> assertThat(findings.getFirst().measured()).isEqualTo(scenario.threshold()),
            () -> assertThat(findings.getFirst().threshold()).isEqualTo(scenario.threshold()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    @DisplayName("pięciokrotność progu daje CRITICAL")
    void findings_whenFiveTimesThreshold_reportsCritical(Scenario scenario) {

        // given
        List<DataAccessEvent> events = scenario.events("s0", scenario.threshold() * 5);

        // when
        List<Finding> findings = findings(scenario.analyzer(SHAPE_LIMIT), events);

        // then
        assertThat(findings).extracting(Finding::severity).containsExactly(Severity.CRITICAL);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    @DisplayName("analiza wyłączona w ustawieniach nie daje wniosku nawet przy pięciokrotności progu")
    void engine_whenAnalyzerDisabled_reportsNothing(Scenario scenario) {

        // given
        DiagnosticAnalyzer analyzer = scenario.analyzer(SHAPE_LIMIT);
        DiagnosticsSettings settings = DiagnosticsSettings.defaults(DiagnosticsMode.DEV)
            .withAnalyzerEnabled(analyzer.id(), false);
        List<Finding> reported = new ArrayList<>();
        FindingReporter reporter = (unit, findings) -> reported.addAll(findings);
        DiagnosticsEngine engine = new DiagnosticsEngine(settings, List.of(analyzer), List.of(reporter));
        List<DataAccessEvent> events = scenario.events("s0", scenario.threshold() * 5);

        // when
        try (UnitOfWorkScope ignored = engine.open("GET /orders", UnitOfWorkType.HTTP)) {

            events.forEach(engine::record);
        }

        // then
        assertThat(reported).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    @DisplayName("kształty ponad limit nie dają wniosku o kubełku zbiorczym, a liczony kształt zostaje")
    void findings_whenShapesOverflowLimit_reportOnlyCountedShape(Scenario scenario) {

        // given: limit jednego kształtu, a każdy z sześciu kształtów osiąga próg
        List<DataAccessEvent> events = LongStream.range(0, 6)
            .boxed()
            .flatMap(index -> scenario.events("s" + index, scenario.threshold()).stream())
            .toList();

        // when
        List<Finding> findings = findings(scenario.analyzer(1), events);

        // then
        List<ShapeSummary> shapes = findings.stream()
            .flatMap(finding -> finding.shapes().stream())
            .toList();
        assertAll(
            () -> assertThat(findings).isNotEmpty(),
            () -> assertThat(shapes).extracting(ShapeSummary::overflow).containsOnly(false),
            () -> assertThat(shapes).extracting(ShapeSummary::shape).containsOnly("s0"));
    }

    static Stream<Scenario> scenarios() {

        return Stream.of(
            new Scenario("n-plus-one", limit -> new NPlusOneAnalyzer(SeverityScale.of(10), limit), 10,
                (shape, count) -> repeat(count, read(shape))),
            new Scenario("missing-batch", limit -> new MissingBatchAnalyzer(SeverityScale.of(10), limit), 10,
                (shape, count) -> repeat(count, write(shape))),
            new Scenario("slow-operation",
                limit -> new SlowOperationAnalyzer(Duration.ofMillis(100), Map.of(), 5, limit), 100,
                (shape, millis) -> List.of(read(shape).millis(millis).build())),
            new Scenario("operation-count", limit -> new OperationCountAnalyzer(SeverityScale.of(10), limit), 10,
                (shape, count) -> repeat(count, read(shape))));
    }

    /**
     * @param name      nazwa scenariusza w raporcie testów
     * @param factory   analiza dla podanego limitu kształtów
     * @param threshold próg analizy w jej jednostkach pomiaru
     * @param generator zdarzenia, które dają dla kształtu podany pomiar
     */
    record Scenario(String name, IntFunction<DiagnosticAnalyzer> factory, long threshold,
                    BiFunction<String, Long, List<DataAccessEvent>> generator) {

        DiagnosticAnalyzer analyzer(int shapeLimit) {

            return factory.apply(shapeLimit);
        }

        List<DataAccessEvent> events(String shape, long measure) {

            return generator.apply(shape, measure);
        }

        @Override
        public String toString() {

            return name;
        }
    }
}
