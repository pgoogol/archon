package com.pgoogol.diagnostics.core.analysis;

import com.pgoogol.diagnostics.core.ActivityEvent;
import com.pgoogol.diagnostics.core.DataAccessEvent;
import com.pgoogol.diagnostics.core.DataAccessEventBuilder;
import com.pgoogol.diagnostics.core.DataAccessEventFixtures;
import com.pgoogol.diagnostics.core.TransactionEvent;
import com.pgoogol.diagnostics.core.TransactionOutcome;
import com.pgoogol.diagnostics.core.UnitOfWork;
import com.pgoogol.diagnostics.core.UnitOfWorkType;
import com.pgoogol.diagnostics.core.report.Finding;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

/** Uruchamia analizę na zdarzeniach syntetycznych, bez silnika i bez bazy. */
final class AnalyzerRuns {

    private AnalyzerRuns() {
    }

    /** Wnioski jednej sesji analizy po przyjęciu wszystkich zdarzeń. */
    static List<Finding> findings(DiagnosticAnalyzer analyzer, List<DataAccessEvent> events) {

        UnitOfWork unit = new UnitOfWork("a1b2c3d4", "GET /orders", UnitOfWorkType.HTTP,
            DataAccessEventFixtures.TIMESTAMP, null, 0);
        AnalysisSession session = analyzer.start(unit);
        events.forEach(session::onEvent);
        return session.findings();
    }

    /** Wnioski jednej sesji analizy po przyjęciu zdarzeń innych niż operacje. */
    static List<Finding> activityFindings(DiagnosticAnalyzer analyzer, List<? extends ActivityEvent> events) {

        UnitOfWork unit = new UnitOfWork("a1b2c3d4", "GET /orders", UnitOfWorkType.HTTP,
            DataAccessEventFixtures.TIMESTAMP, null, 0);
        AnalysisSession session = analyzer.start(unit);
        events.forEach(session::onActivity);
        return session.findings();
    }

    /** Zatwierdzona transakcja bez flagi readOnly, z podaną liczbą odczytów i zapisów. */
    static TransactionEvent transaction(String name, long millis, long reads, long writes) {

        Duration duration = Duration.ofMillis(millis);
        return new TransactionEvent(name, DataAccessEventFixtures.TIMESTAMP, duration, TransactionOutcome.COMMITTED,
            false, 0, reads, writes, 0);
    }

    /** {@code count} takich samych zdarzeń. */
    static List<DataAccessEvent> repeat(long count, DataAccessEventBuilder builder) {

        return Stream.generate(builder::build)
            .limit(count)
            .toList();
    }
}
