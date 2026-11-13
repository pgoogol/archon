package com.pgoogol.diagnostics.core.analysis;

import com.pgoogol.diagnostics.core.ActivityEvent;
import com.pgoogol.diagnostics.core.DataAccessEvent;
import com.pgoogol.diagnostics.core.DiagnosticsMode;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;
import com.pgoogol.diagnostics.core.TransactionEvent;
import com.pgoogol.diagnostics.core.UnitOfWork;
import com.pgoogol.diagnostics.core.report.Finding;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Długa transakcja: trwała co najmniej tyle, ile próg. Długa transakcja trzyma połączenie
 * z puli i blokady w bazie; zwykle siedzi w niej wywołanie zewnętrzne albo praca, która
 * transakcji nie potrzebuje. Jeden wniosek na nazwę transakcji, z najdłuższym czasem.
 *
 * <p>Próg domyślny: 500 ms w dev, 2 s w prod.</p>
 */
public class LongTransactionAnalyzer implements DiagnosticAnalyzer {

    public static final String ID = "long-transaction";

    public static final String CODE = "LONG_TRANSACTION";

    public static final long DEV_THRESHOLD_MILLIS = 500;

    public static final long PROD_THRESHOLD_MILLIS = 2_000;

    private static final String TITLE = "Długa transakcja %s: najdłużej %d ms przy progu %d ms, wystąpień: %d";

    private final SeverityScale scale;

    private final int subjectLimit;

    /**
     * @param scale        próg w milisekundach i mnożnik wagi krytycznej
     * @param subjectLimit ile różnych transakcji liczyć osobno w jednej jednostce
     */
    public LongTransactionAnalyzer(SeverityScale scale, int subjectLimit) {

        this.scale = Objects.requireNonNull(scale, "skala progu jest wymagana");
        this.subjectLimit = subjectLimit;
    }

    public static LongTransactionAnalyzer defaults(DiagnosticsSettings settings) {

        long threshold = defaultThreshold(settings.mode());
        SeverityScale scale = SeverityScale.of(threshold);
        return new LongTransactionAnalyzer(scale, settings.unitShapeLimit());
    }

    /** Próg domyślny w milisekundach: 500 w dev, 2000 w prod. */
    public static long defaultThreshold(DiagnosticsMode mode) {

        return switch (mode) {

            case DEV -> DEV_THRESHOLD_MILLIS;
            case PROD -> PROD_THRESHOLD_MILLIS;
        };
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

        private final SubjectTally slow = new SubjectTally(subjectLimit);

        @Override
        public void onEvent(DataAccessEvent event) {
        }

        @Override
        public void onActivity(ActivityEvent event) {

            if (event instanceof TransactionEvent transaction) {

                long millis = transaction.duration().toMillis();
                if (millis >= scale.threshold()) {

                    slow.add(transaction.name(), millis);
                }
            }
        }

        @Override
        public List<Finding> findings() {

            return slow.entries().stream()
                .flatMap(entry -> finding(entry).stream())
                .sorted(Comparator.comparingLong(Finding::measured).reversed())
                .toList();
        }

        private Optional<Finding> finding(SubjectTally.Entry entry) {

            String title = TITLE.formatted(entry.subject(), entry.max(), scale.threshold(), entry.count());
            return scale.grade(entry.max())
                .map(severity -> Finding.about(entry.subject(), CODE, severity, title, entry.max(), scale.threshold()));
        }
    }
}
