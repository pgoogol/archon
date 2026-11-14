package com.pgoogol.diagnostics.core.analysis;

import com.pgoogol.diagnostics.core.ActivityEvent;
import com.pgoogol.diagnostics.core.DataAccessEvent;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;
import com.pgoogol.diagnostics.core.TransactionEvent;
import com.pgoogol.diagnostics.core.TransactionOutcome;
import com.pgoogol.diagnostics.core.UnitOfWork;
import com.pgoogol.diagnostics.core.report.Finding;
import com.pgoogol.diagnostics.core.report.Severity;

import java.util.List;
import java.util.Objects;

/**
 * Kandydat na {@code readOnly}: zatwierdzona transakcja, która nie była oznaczona jako
 * tylko do odczytu, a wykonała same odczyty. Z {@code @Transactional(readOnly = true)}
 * Hibernate pomija dirty checking, a baza może skierować zapytania na replikę.
 * To wskazówka, więc waga {@code INFO}; pomiarem jest liczba takich transakcji.
 */
public class ReadOnlyCandidateAnalyzer implements DiagnosticAnalyzer {

    public static final String ID = "read-only-candidate";

    public static final String CODE = "READ_ONLY_CANDIDATE";

    private static final String TITLE = "Transakcja %s wykonała same odczyty (%d) i może być readOnly, wystąpień: %d";

    private final int subjectLimit;

    /** @param subjectLimit ile różnych transakcji liczyć osobno w jednej jednostce */
    public ReadOnlyCandidateAnalyzer(int subjectLimit) {

        this.subjectLimit = subjectLimit;
    }

    public static ReadOnlyCandidateAnalyzer defaults(DiagnosticsSettings settings) {

        return new ReadOnlyCandidateAnalyzer(settings.unitShapeLimit());
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

        private final SubjectTally candidates = new SubjectTally(subjectLimit);

        @Override
        public void onEvent(DataAccessEvent event) {
        }

        @Override
        public void onActivity(ActivityEvent event) {

            if (event instanceof TransactionEvent transaction && isCandidate(transaction)) {

                candidates.add(transaction.name(), transaction.reads());
            }
        }

        @Override
        public List<Finding> findings() {

            return candidates.entries().stream()
                .map(this::finding)
                .toList();
        }

        private boolean isCandidate(TransactionEvent transaction) {

            return !transaction.readOnly()
                && Objects.equals(transaction.outcome(), TransactionOutcome.COMMITTED)
                && transaction.reads() > 0
                && transaction.writes() == 0
                && transaction.others() == 0;
        }

        private Finding finding(SubjectTally.Entry entry) {

            String title = TITLE.formatted(entry.subject(), entry.max(), entry.count());
            return Finding.about(entry.subject(), CODE, Severity.INFO, title, entry.count(), 0);
        }
    }
}
