package com.pgoogol.diagnostics.core;

import com.pgoogol.diagnostics.core.analysis.AnalysisSession;
import com.pgoogol.diagnostics.core.analysis.DiagnosticAnalyzer;
import com.pgoogol.diagnostics.core.report.Finding;

import java.util.List;
import java.util.Objects;

/** Analiza testowa, która psuje się w wybranym miejscu cyklu jednostki. */
final class FailingAnalyzer implements DiagnosticAnalyzer {

    enum FailurePoint {

        START,
        EVENT,
        EVENT_MISSING_CLASS,
        FINDINGS
    }

    private final FailurePoint point;

    FailingAnalyzer(FailurePoint point) {

        this.point = point;
    }

    @Override
    public String id() {

        return "failing";
    }

    @Override
    public AnalysisSession start(UnitOfWork unit) {

        if (Objects.equals(point, FailurePoint.START)) {

            throw new IllegalStateException("awaria analizy przy starcie");
        }
        return new FailingSession();
    }

    private final class FailingSession implements AnalysisSession {

        @Override
        public void onEvent(DataAccessEvent event) {

            if (Objects.equals(point, FailurePoint.EVENT)) {

                throw new IllegalStateException("awaria analizy przy zdarzeniu");
            }
            if (Objects.equals(point, FailurePoint.EVENT_MISSING_CLASS)) {

                throw new NoClassDefFoundError("org/example/MissingOptionalDependency");
            }
        }

        @Override
        public List<Finding> findings() {

            if (Objects.equals(point, FailurePoint.FINDINGS)) {

                throw new IllegalStateException("awaria analizy przy wnioskach");
            }
            return List.of();
        }
    }
}
