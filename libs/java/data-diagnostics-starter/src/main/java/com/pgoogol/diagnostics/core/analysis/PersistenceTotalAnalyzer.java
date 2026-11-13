package com.pgoogol.diagnostics.core.analysis;

import com.pgoogol.diagnostics.core.ActivityEvent;
import com.pgoogol.diagnostics.core.DataAccessEvent;
import com.pgoogol.diagnostics.core.PersistenceSessionEvent;
import com.pgoogol.diagnostics.core.UnitOfWork;
import com.pgoogol.diagnostics.core.report.Finding;

import java.util.List;
import java.util.function.ToLongFunction;

/**
 * Wspólna podstawa analiz, które sumują jedną wielkość ze wszystkich sesji utrwalania
 * w jednostce i porównują sumę z progiem. Analizy różnią się wielkością, kodem i tytułem.
 */
abstract class PersistenceTotalAnalyzer implements DiagnosticAnalyzer {

    private final String code;

    private final SeverityScale scale;

    private final String titleTemplate;

    private final ToLongFunction<PersistenceSessionEvent> measure;

    /**
     * @param titleTemplate tytuł z dwoma miejscami na liczby: sumę i próg
     * @param measure       wielkość brana z każdej sesji
     */
    PersistenceTotalAnalyzer(String code, SeverityScale scale, String titleTemplate,
                             ToLongFunction<PersistenceSessionEvent> measure) {

        this.code = code;
        this.scale = scale;
        this.titleTemplate = titleTemplate;
        this.measure = measure;
    }

    /** Suma, od której powstaje wniosek. */
    public long threshold() {

        return scale.threshold();
    }

    @Override
    public AnalysisSession start(UnitOfWork unit) {

        return new Session();
    }

    private final class Session implements AnalysisSession {

        private long total;

        @Override
        public void onEvent(DataAccessEvent event) {
        }

        @Override
        public void onActivity(ActivityEvent event) {

            if (event instanceof PersistenceSessionEvent session) {

                total += measure.applyAsLong(session);
            }
        }

        @Override
        public List<Finding> findings() {

            String title = titleTemplate.formatted(total, scale.threshold());
            return scale.grade(total)
                .map(severity -> new Finding(code, severity, title, total, scale.threshold(), List.of()))
                .stream()
                .toList();
        }
    }
}
