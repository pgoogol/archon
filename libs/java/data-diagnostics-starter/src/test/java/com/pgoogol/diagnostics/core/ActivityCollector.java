package com.pgoogol.diagnostics.core;

import com.pgoogol.diagnostics.core.analysis.AnalysisSession;
import com.pgoogol.diagnostics.core.analysis.DiagnosticAnalyzer;
import com.pgoogol.diagnostics.core.report.Finding;

import java.util.List;

/**
 * Analiza testowa, która zbiera zdarzenia inne niż operacje.
 *
 * @param sink lista, do której trafia każde takie zdarzenie
 */
public record ActivityCollector(List<ActivityEvent> sink) implements DiagnosticAnalyzer {

    @Override
    public String id() {

        return "activity-collector";
    }

    @Override
    public AnalysisSession start(UnitOfWork unit) {

        return new AnalysisSession() {

            @Override
            public void onEvent(DataAccessEvent event) {
            }

            @Override
            public void onActivity(ActivityEvent event) {

                sink.add(event);
            }

            @Override
            public List<Finding> findings() {

                return List.of();
            }
        };
    }
}
