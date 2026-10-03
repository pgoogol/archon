package com.pgoogol.diagnostics.core;

import com.pgoogol.diagnostics.core.analysis.DiagnosticAnalyzer;
import com.pgoogol.diagnostics.core.report.FindingReporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

import static com.pgoogol.diagnostics.core.DataAccessEventFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertAll;

class DiagnosticsEngineTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC);

    private static final DiagnosticsSettings DEV = DiagnosticsSettings.defaults(DiagnosticsMode.DEV);

    private final CapturingReporter reporter = new CapturingReporter();

    @Test
    @DisplayName("zamknięcie granicy zamyka jednostkę i przekazuje reporterowi jej wnioski")
    void open_whenScopeClosed_reportsUnitWithFindings() {

        // given
        DiagnosticsEngine engine = engine(DEV, new CountingAnalyzer());

        // when
        try (UnitOfWorkScope ignored = engine.open("GET /orders", UnitOfWorkType.HTTP, "trace-1")) {

            engine.record(read("select 1"));
            engine.record(read("select 2"));
        }

        // then
        CapturingReporter.Report report = reporter.single();
        UnitOfWork unit = report.unit();
        assertAll(
            () -> assertThat(unit.name()).isEqualTo("GET /orders"),
            () -> assertThat(unit.type()).isEqualTo(UnitOfWorkType.HTTP),
            () -> assertThat(unit.traceId()).contains("trace-1"),
            () -> assertThat(unit.id()).matches("[0-9a-f]{8}"),
            () -> assertThat(unit.isOpen()).isFalse(),
            () -> assertThat(unit.operationCount()).isEqualTo(2),
            () -> assertThat(report.findings()).containsExactly(CountingAnalyzer.findingFor(2)));
    }

    @Test
    @DisplayName("granica otwarta wewnątrz innej dołącza do jej jednostki, więc raport jest jeden")
    void open_whenUnitAlreadyOpen_joinsItAndReportsOnce() {

        // given
        DiagnosticsEngine engine = engine(DEV, new CountingAnalyzer());
        UnitOfWork outerUnit;
        UnitOfWork innerUnit;

        // when
        try (UnitOfWorkScope outer = engine.open("GET /orders", UnitOfWorkType.HTTP)) {

            outerUnit = outer.unit();
            engine.record(read("select 1"));
            try (UnitOfWorkScope inner = engine.open("ReportJob.run", UnitOfWorkType.SCHEDULED)) {

                innerUnit = inner.unit();
                engine.record(read("select 2"));
            }
            engine.record(read("select 3"));
        }

        // then
        CapturingReporter.Report report = reporter.single();
        assertAll(
            () -> assertThat(innerUnit).isSameAs(outerUnit),
            () -> assertThat(report.unit().name()).isEqualTo("GET /orders"),
            () -> assertThat(report.unit().operationCount()).isEqualTo(3));
    }

    @Test
    @DisplayName("podwójne zamknięcie wewnętrznej granicy nie zamyka zewnętrznej przedwcześnie")
    void close_whenInnerScopeClosedTwice_keepsOuterUnitOpen() {

        // given
        DiagnosticsEngine engine = engine(DEV, new CountingAnalyzer());
        UnitOfWorkScope outer = engine.open("GET /orders", UnitOfWorkType.HTTP);
        UnitOfWorkScope inner = engine.open("ReportJob.run", UnitOfWorkType.SCHEDULED);

        // when
        inner.close();
        inner.close();

        // then
        assertAll(
            () -> assertThat(engine.current()).containsSame(outer.unit()),
            () -> assertThat(reporter.reports()).isEmpty());
    }

    @Test
    @DisplayName("po zamknięciu ostatniej granicy wątek nie ma bieżącej jednostki")
    void current_whenLastScopeClosed_isEmpty() {

        // given
        DiagnosticsEngine engine = engine(DEV, new CountingAnalyzer());
        UnitOfWorkScope scope = engine.open("GET /orders", UnitOfWorkType.HTTP);

        // when
        scope.close();

        // then
        assertThat(engine.current()).isEmpty();
    }

    @Test
    @DisplayName("zdarzenie bez otwartej jednostki przepada, gdy przechwytywanie poza jednostką jest wyłączone")
    void record_whenNoUnitOpen_dropsEvent() {

        // given
        DiagnosticsEngine engine = engine(DEV, new CountingAnalyzer());

        // when
        engine.record(read("select 1"));
        engine.flushOutsideUnit();

        // then
        assertThat(reporter.reports()).isEmpty();
    }

    @Test
    @DisplayName("zdarzenia spoza jednostki trafiają do wspólnej jednostki startup, raportowanej przy flush")
    void record_whenOutsideCaptureEnabled_collectsIntoStartupUnit() {

        // given
        DiagnosticsEngine engine = engine(DEV.withCaptureOutsideUnit(true), new CountingAnalyzer());

        // when
        engine.record(read("select 1"));
        engine.record(read("select 2"));
        engine.flushOutsideUnit();

        // then
        CapturingReporter.Report report = reporter.single();
        assertAll(
            () -> assertThat(report.unit().type()).isEqualTo(UnitOfWorkType.STARTUP),
            () -> assertThat(report.unit().operationCount()).isEqualTo(2),
            () -> assertThat(report.findings()).containsExactly(CountingAnalyzer.findingFor(2)));
    }

    @Test
    @DisplayName("po pierwszym flush zdarzenia spoza jednostki zbiera jednostka background")
    void flushOutsideUnit_afterStartup_switchesToBackgroundUnit() {

        // given
        DiagnosticsEngine engine = engine(DEV.withCaptureOutsideUnit(true), new CountingAnalyzer());
        engine.record(read("select 1"));
        engine.flushOutsideUnit();

        // when
        engine.record(read("select 2"));
        engine.flushOutsideUnit();

        // then
        List<UnitOfWorkType> types = reporter.reports().stream()
            .map(report -> report.unit().type())
            .toList();
        assertThat(types).containsExactly(UnitOfWorkType.STARTUP, UnitOfWorkType.BACKGROUND);
    }

    @Test
    @DisplayName("pusta wspólna jednostka nie daje raportu")
    void flushOutsideUnit_whenNothingRecorded_reportsNothing() {

        // given
        DiagnosticsEngine engine = engine(DEV.withCaptureOutsideUnit(true), new CountingAnalyzer());

        // when
        engine.flushOutsideUnit();

        // then
        assertThat(reporter.reports()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(FailingAnalyzer.FailurePoint.class)
    @DisplayName("awaria jednej analizy nie wychodzi do wywołującego i nie zabiera wniosków pozostałym")
    void close_whenAnalyzerFails_otherAnalyzersStillReport(FailingAnalyzer.FailurePoint point) {

        // given
        DiagnosticsEngine engine = engine(DEV, new FailingAnalyzer(point), new CountingAnalyzer());

        // when & then
        assertThatNoException().isThrownBy(() -> runUnit(engine, 2));
        assertThat(reporter.single().findings()).containsExactly(CountingAnalyzer.findingFor(2));
    }

    @Test
    @DisplayName("awaria jednego reportera nie zabiera raportu następnemu")
    void close_whenReporterFails_nextReporterStillGetsReport() {

        // given
        FindingReporter failing = (unit, findings) -> {

            throw new IllegalStateException("awaria reportera");
        };
        List<DiagnosticAnalyzer> analyzers = List.of(new CountingAnalyzer());
        List<FindingReporter> reporters = List.of(failing, reporter);
        DiagnosticsEngine engine = new DiagnosticsEngine(DEV, analyzers, reporters, CLOCK);

        // when & then
        assertThatNoException().isThrownBy(() -> runUnit(engine, 1));
        assertThat(reporter.single().findings()).containsExactly(CountingAnalyzer.findingFor(1));
    }

    @Test
    @DisplayName("granica zamknięta dwa razy daje jeden raport")
    void close_whenScopeClosedTwice_reportsOnce() {

        // given
        DiagnosticsEngine engine = engine(DEV, new CountingAnalyzer());
        UnitOfWorkScope scope = engine.open("GET /orders", UnitOfWorkType.HTTP);

        // when
        scope.close();
        scope.close();

        // then
        assertThat(reporter.reports()).hasSize(1);
    }

    @Test
    @DisplayName("w prod jednostka nie trzyma zdarzeń, a analizy i tak je liczą")
    void record_whenProd_unitKeepsOnlyCounters() {

        // given
        DiagnosticsEngine engine = engine(DiagnosticsSettings.defaults(DiagnosticsMode.PROD), new CountingAnalyzer());

        // when
        runUnit(engine, 3);

        // then
        CapturingReporter.Report report = reporter.single();
        assertAll(
            () -> assertThat(report.unit().events()).isEmpty(),
            () -> assertThat(report.unit().operationCount()).isEqualTo(3),
            () -> assertThat(report.findings()).containsExactly(CountingAnalyzer.findingFor(3)));
    }

    /** Jedna jednostka HTTP z {@code count} odczytami. */
    private static void runUnit(DiagnosticsEngine engine, int count) {

        try (UnitOfWorkScope ignored = engine.open("GET /orders", UnitOfWorkType.HTTP)) {

            Stream.generate(() -> read("select 1"))
                .limit(count)
                .forEach(engine::record);
        }
    }

    private DiagnosticsEngine engine(DiagnosticsSettings settings, DiagnosticAnalyzer... analyzers) {

        List<DiagnosticAnalyzer> analyzerList = List.of(analyzers);
        List<FindingReporter> reporters = List.of(reporter);
        return new DiagnosticsEngine(settings, analyzerList, reporters, CLOCK);
    }
}
