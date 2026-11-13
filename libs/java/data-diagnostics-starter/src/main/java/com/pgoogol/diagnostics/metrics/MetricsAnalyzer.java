package com.pgoogol.diagnostics.metrics;

import com.pgoogol.diagnostics.core.ActivityEvent;
import com.pgoogol.diagnostics.core.ConnectionEvent;
import com.pgoogol.diagnostics.core.DataAccessEvent;
import com.pgoogol.diagnostics.core.TransactionEvent;
import com.pgoogol.diagnostics.core.UnitOfWork;
import com.pgoogol.diagnostics.core.analysis.AnalysisSession;
import com.pgoogol.diagnostics.core.analysis.DiagnosticAnalyzer;
import com.pgoogol.diagnostics.core.report.Finding;

import java.util.List;
import java.util.Objects;

/**
 * „Analiza”, która nie daje wniosków, tylko zapisuje timery: każdą operację, każdą
 * transakcję i każde czekanie na połączenie. Jest analizą, bo to jedyne miejsce, które
 * widzi każde zdarzenie jednostki; wyłącza się jak inne ({@code diagnostics.analyzers.metrics.enabled}).
 * Sesja nie trzyma stanu, więc jest jedna dla wszystkich jednostek.
 */
public class MetricsAnalyzer implements DiagnosticAnalyzer {

    public static final String ID = "metrics";

    private final AnalysisSession session;

    public MetricsAnalyzer(DiagnosticsMeters meters) {

        Objects.requireNonNull(meters, "mierniki są wymagane");
        this.session = new Session(meters);
    }

    @Override
    public String id() {

        return ID;
    }

    @Override
    public AnalysisSession start(UnitOfWork unit) {

        return session;
    }

    private record Session(DiagnosticsMeters meters) implements AnalysisSession {

        @Override
        public void onEvent(DataAccessEvent event) {

            meters.operation(event.store().name(), event.kind().name(), event.success(), event.duration());
        }

        @Override
        public void onActivity(ActivityEvent event) {

            if (event instanceof TransactionEvent transaction) {

                meters.transaction(transaction.outcome().name(), transaction.readOnly(), transaction.duration());
            }
            if (event instanceof ConnectionEvent connection) {

                meters.connectionWait(connection.waitTime());
            }
        }

        @Override
        public List<Finding> findings() {

            return List.of();
        }
    }
}
