package com.pgoogol.diagnostics.core;

import com.pgoogol.diagnostics.core.analysis.DiagnosticAnalyzer;
import com.pgoogol.diagnostics.core.report.FindingReporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.pgoogol.diagnostics.core.DataAccessEventFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;

/** Silnik a wątki: transakcje związane z wątkiem i jednostka przekazana innym wątkom. */
class DiagnosticsEngineThreadsTest {

    private static final DiagnosticsSettings DEV = DiagnosticsSettings.defaults(DiagnosticsMode.DEV);

    private final CapturingReporter reporter = new CapturingReporter();

    @Test
    @DisplayName("operacje liczą się do transakcji z wierzchu stosu, a jej koniec trafia do analiz")
    void endTransaction_whenNestedTransactions_countsOperationsInInnermost() {

        // given
        List<ActivityEvent> activities = new ArrayList<>();
        DiagnosticsEngine engine = engine(new ActivityCollector(activities));

        // when
        try (UnitOfWorkScope ignored = engine.open("GET /orders", UnitOfWorkType.HTTP)) {

            engine.beginTransaction("outer", false);
            engine.record(read("select 1"));
            engine.beginTransaction("inner", true);
            engine.record(read("select 2"));
            engine.record(read("select 3"));
            engine.endTransaction(TransactionOutcome.ROLLED_BACK);
            engine.endTransaction(TransactionOutcome.COMMITTED);
        }

        // then
        assertThat(activities)
            .extracting(event -> ((TransactionEvent) event).name() + ":" + ((TransactionEvent) event).depth()
                + ":" + ((TransactionEvent) event).reads() + ":" + ((TransactionEvent) event).outcome())
            .containsExactly("inner:1:2:ROLLED_BACK", "outer:0:1:COMMITTED");
    }

    @Test
    @DisplayName("koniec transakcji bez początku nie daje zdarzenia ani wyjątku")
    void endTransaction_whenNothingBegan_isIgnored() {

        // given
        List<ActivityEvent> activities = new ArrayList<>();
        DiagnosticsEngine engine = engine(new ActivityCollector(activities));

        // when
        try (UnitOfWorkScope ignored = engine.open("GET /orders", UnitOfWorkType.HTTP)) {

            engine.endTransaction(TransactionOutcome.COMMITTED);
        }

        // then
        assertThat(activities).isEmpty();
    }

    @Test
    @DisplayName("wiele wątków dołączonych do jednej jednostki daje jeden raport z każdym zdarzeniem")
    void join_whenManyThreadsJoinAndRecord_reportsOnceWithEveryEvent() {

        // given
        DiagnosticsEngine engine = engine(new CountingAnalyzer());

        // when
        // granica rodzica stoi pierwsza, więc zamyka się ostatnia, po dokończeniu zadań
        try (UnitOfWorkScope ignored = engine.open("GET /orders", UnitOfWorkType.HTTP);
             ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            UnitOfWorkHandoff handoff = engine.handoff().orElseThrow();
            IntStream.range(0, 16).forEach(index -> executor.submit(() -> joinAndRecord(engine, handoff, 250)));
        }

        // then
        CapturingReporter.Report report = reporter.single();
        assertThat(report.unit().operationCount()).isEqualTo(4_000);
        assertThat(report.findings()).containsExactly(CountingAnalyzer.findingFor(4_000));
    }

    @Test
    @DisplayName("do zamkniętej jednostki nie da się już dołączyć")
    void join_whenUnitClosed_returnsEmpty() {

        // given
        DiagnosticsEngine engine = engine(new CountingAnalyzer());
        UnitOfWorkScope scope = engine.open("GET /orders", UnitOfWorkType.HTTP);
        UnitOfWorkHandoff handoff = engine.handoff().orElseThrow();
        scope.close();

        // when
        Optional<UnitOfWorkScope> joined = engine.join(handoff);

        // then
        assertThat(joined).isEmpty();
        assertThat(reporter.reports()).hasSize(1);
    }

    private static void joinAndRecord(DiagnosticsEngine engine, UnitOfWorkHandoff handoff, int count) {

        try (UnitOfWorkScope ignored = engine.join(handoff).orElseThrow()) {

            Stream.generate(() -> read("select 1"))
                .limit(count)
                .forEach(engine::record);
        }
    }

    private DiagnosticsEngine engine(DiagnosticAnalyzer analyzer) {

        List<DiagnosticAnalyzer> analyzers = List.of(analyzer);
        List<FindingReporter> reporters = List.of(reporter);
        return new DiagnosticsEngine(DEV, analyzers, reporters);
    }
}
