package com.pgoogol.diagnostics.core.analysis;

import com.pgoogol.diagnostics.core.ActivityEvent;
import com.pgoogol.diagnostics.core.DataAccessEvent;
import com.pgoogol.diagnostics.core.DiagnosticsMode;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;
import com.pgoogol.diagnostics.core.RepositoryCallEvent;
import com.pgoogol.diagnostics.core.UnitOfWork;
import com.pgoogol.diagnostics.core.report.Finding;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Gorące miejsce w repozytoriach: ta sama metoda repozytorium wywołana co najmniej N razy
 * w jednej jednostce pracy, np. {@code findById} w pętli. W odróżnieniu od N+1 liczy
 * wywołania, a nie zapytania, więc łapie też metody, które same trafiają w cache.
 * Wnioski idą od metody, która zajęła łącznie najwięcej czasu.
 *
 * <p>Próg domyślny: 10 wywołań w dev, 20 w prod.</p>
 */
public class RepositoryHotspotAnalyzer implements DiagnosticAnalyzer {

    public static final String ID = "repository-hotspot";

    public static final String CODE = "REPOSITORY_HOTSPOT";

    public static final long DEV_THRESHOLD = 10;

    public static final long PROD_THRESHOLD = 20;

    private static final String TITLE = "Metoda %s wywołana %d razy w jednej jednostce pracy (próg %d), łącznie %d ms";

    private final SeverityScale scale;

    private final int subjectLimit;

    /**
     * @param scale        próg liczby wywołań jednej metody i mnożnik wagi krytycznej
     * @param subjectLimit ile różnych metod liczyć osobno w jednej jednostce
     */
    public RepositoryHotspotAnalyzer(SeverityScale scale, int subjectLimit) {

        this.scale = Objects.requireNonNull(scale, "skala progu jest wymagana");
        this.subjectLimit = subjectLimit;
    }

    public static RepositoryHotspotAnalyzer defaults(DiagnosticsSettings settings) {

        long threshold = defaultThreshold(settings.mode());
        return new RepositoryHotspotAnalyzer(SeverityScale.of(threshold), settings.unitShapeLimit());
    }

    /** Próg domyślny: 10 wywołań w dev, 20 w prod. */
    public static long defaultThreshold(DiagnosticsMode mode) {

        return switch (mode) {

            case DEV -> DEV_THRESHOLD;
            case PROD -> PROD_THRESHOLD;
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

        private final SubjectTally calls = new SubjectTally(subjectLimit);

        @Override
        public void onEvent(DataAccessEvent event) {
        }

        @Override
        public void onActivity(ActivityEvent event) {

            if (event instanceof RepositoryCallEvent call) {

                calls.add(call.qualifiedMethod(), call.duration().toNanos());
            }
        }

        @Override
        public List<Finding> findings() {

            return calls.entries().stream()
                .sorted(Comparator.comparingLong(SubjectTally.Entry::total).reversed())
                .flatMap(this::finding)
                .toList();
        }

        private Stream<Finding> finding(SubjectTally.Entry entry) {

            long totalMillis = entry.total() / 1_000_000;
            String title = TITLE.formatted(entry.subject(), entry.count(), scale.threshold(), totalMillis);
            Optional<Finding> finding = scale.grade(entry.count())
                .map(severity -> Finding.about(entry.subject(), CODE, severity, title, entry.count(), scale.threshold()));
            return finding.stream();
        }
    }
}
