package com.pgoogol.diagnostics.jdbc;

import com.pgoogol.diagnostics.core.DataAccessEvent;
import com.pgoogol.diagnostics.core.UnitOfWork;
import com.pgoogol.diagnostics.core.analysis.AnalysisSession;
import com.pgoogol.diagnostics.core.analysis.DiagnosticAnalyzer;
import com.pgoogol.diagnostics.core.report.Finding;

import java.util.List;

/**
 * Analiza testowa, która tylko zbiera zdarzenia, także w prod, gdzie jednostka ich nie trzyma.
 *
 * @param sink lista, do której trafia każde zdarzenie każdej jednostki
 */
record CollectingAnalyzer(List<DataAccessEvent> sink) implements DiagnosticAnalyzer {

    @Override
    public String id() {

        return "collector";
    }

    @Override
    public AnalysisSession start(UnitOfWork unit) {

        return new AnalysisSession() {

            @Override
            public void onEvent(DataAccessEvent event) {

                sink.add(event);
            }

            @Override
            public List<Finding> findings() {

                return List.of();
            }
        };
    }
}
