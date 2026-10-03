package com.pgoogol.diagnostics.core;

import com.pgoogol.diagnostics.core.analysis.AnalysisSession;
import com.pgoogol.diagnostics.core.analysis.DiagnosticAnalyzer;
import com.pgoogol.diagnostics.core.report.Finding;
import com.pgoogol.diagnostics.core.report.Severity;

import java.util.List;

/** Analiza testowa: liczy zdarzenia jednostki i oddaje jeden wniosek z ich liczbą. */
final class CountingAnalyzer implements DiagnosticAnalyzer {

    static final String CODE = "COUNT";

    /** Wniosek, który ta analiza oddaje po {@code count} zdarzeniach. */
    static Finding findingFor(int count) {

        return new Finding(CODE, Severity.INFO, "zdarzenia: " + count, count, 0, List.of());
    }

    @Override
    public String id() {

        return "counting";
    }

    @Override
    public AnalysisSession start(UnitOfWork unit) {

        return new CountingSession();
    }

    private static final class CountingSession implements AnalysisSession {

        private int count;

        @Override
        public void onEvent(DataAccessEvent event) {

            count++;
        }

        @Override
        public List<Finding> findings() {

            Finding finding = findingFor(count);
            return List.of(finding);
        }
    }
}
