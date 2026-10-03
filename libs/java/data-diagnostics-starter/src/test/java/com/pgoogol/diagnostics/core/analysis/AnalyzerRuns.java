package com.pgoogol.diagnostics.core.analysis;

import com.pgoogol.diagnostics.core.DataAccessEvent;
import com.pgoogol.diagnostics.core.DataAccessEventBuilder;
import com.pgoogol.diagnostics.core.DataAccessEventFixtures;
import com.pgoogol.diagnostics.core.UnitOfWork;
import com.pgoogol.diagnostics.core.UnitOfWorkType;
import com.pgoogol.diagnostics.core.report.Finding;

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

    /** {@code count} takich samych zdarzeń. */
    static List<DataAccessEvent> repeat(long count, DataAccessEventBuilder builder) {

        return Stream.generate(builder::build)
            .limit(count)
            .toList();
    }
}
