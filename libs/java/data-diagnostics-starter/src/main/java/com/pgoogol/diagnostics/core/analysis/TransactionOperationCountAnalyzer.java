package com.pgoogol.diagnostics.core.analysis;

import com.pgoogol.diagnostics.core.ActivityEvent;
import com.pgoogol.diagnostics.core.DataAccessEvent;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;
import com.pgoogol.diagnostics.core.TransactionEvent;
import com.pgoogol.diagnostics.core.UnitOfWork;
import com.pgoogol.diagnostics.core.report.Finding;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Za dużo operacji w jednej transakcji. Każda operacja wydłuża czas trzymania blokad
 * i połączenia, a dziesiątki zapytań w jednej transakcji to zwykle pętla, którą da się
 * zastąpić jednym zapytaniem albo batchem. Jeden wniosek na nazwę transakcji.
 *
 * <p>Próg domyślny: 50 operacji w obu trybach.</p>
 */
public class TransactionOperationCountAnalyzer implements DiagnosticAnalyzer {

    public static final String ID = "transaction-operation-count";

    public static final String CODE = "TRANSACTION_OPERATION_COUNT";

    public static final long DEFAULT_THRESHOLD = 50;

    private static final String TITLE = "Operacji w transakcji %s: %d (próg %d)";

    private final SeverityScale scale;

    private final int subjectLimit;

    /**
     * @param scale        próg w liczbie operacji jednej transakcji i mnożnik wagi krytycznej
     * @param subjectLimit ile różnych transakcji liczyć osobno w jednej jednostce
     */
    public TransactionOperationCountAnalyzer(SeverityScale scale, int subjectLimit) {

        this.scale = Objects.requireNonNull(scale, "skala progu jest wymagana");
        this.subjectLimit = subjectLimit;
    }

    public static TransactionOperationCountAnalyzer defaults(DiagnosticsSettings settings) {

        SeverityScale scale = SeverityScale.of(DEFAULT_THRESHOLD);
        return new TransactionOperationCountAnalyzer(scale, settings.unitShapeLimit());
    }

    @Override
    public String id() {

        return ID;
    }

    @Override
    public AnalysisSession start(UnitOfWork unit) {

        return new Session();
    }

    private final class Session implements AnalysisSession {

        private final SubjectTally busy = new SubjectTally(subjectLimit);

        @Override
        public void onEvent(DataAccessEvent event) {
        }

        @Override
        public void onActivity(ActivityEvent event) {

            if (event instanceof TransactionEvent transaction && transaction.operations() >= scale.threshold()) {

                busy.add(transaction.name(), transaction.operations());
            }
        }

        @Override
        public List<Finding> findings() {

            return busy.entries().stream()
                .flatMap(entry -> finding(entry).stream())
                .sorted(Comparator.comparingLong(Finding::measured).reversed())
                .toList();
        }

        private Optional<Finding> finding(SubjectTally.Entry entry) {

            String title = TITLE.formatted(entry.subject(), entry.max(), scale.threshold());
            return scale.grade(entry.max())
                .map(severity -> Finding.about(entry.subject(), CODE, severity, title, entry.max(), scale.threshold()));
        }
    }
}
