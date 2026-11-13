package com.pgoogol.diagnostics.core.analysis;

import com.pgoogol.diagnostics.core.ActivityEvent;
import com.pgoogol.diagnostics.core.DataAccessEvent;
import com.pgoogol.diagnostics.core.DiagnosticsMode;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;
import com.pgoogol.diagnostics.core.PersistenceSessionEvent;
import com.pgoogol.diagnostics.core.UnitOfWork;
import com.pgoogol.diagnostics.core.report.Finding;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Flushe sesji w jednostce pracy. Trzy osobne wnioski:
 * {@code FLUSH_COUNT}, gdy flushy (pełnych i automatycznych) jest za dużo, zwykle przez
 * {@code saveAndFlush} w pętli albo zapytania przeplatane ze zmianami;
 * {@code SLOW_FLUSH}, gdy pojedynczy flush trwa za długo;
 * {@code LARGE_FLUSH}, gdy jeden flush obejmuje bardzo dużo encji.
 *
 * <p>Progi domyślne: 10 flushy, flush od 50 ms w dev i 200 ms w prod, 1000 encji.</p>
 */
public class FlushAnalyzer implements DiagnosticAnalyzer {

    public static final String ID = "flush";

    public static final String COUNT_CODE = "FLUSH_COUNT";

    public static final String SLOW_CODE = "SLOW_FLUSH";

    public static final String LARGE_CODE = "LARGE_FLUSH";

    public static final long DEFAULT_COUNT_THRESHOLD = 10;

    public static final long DEV_SLOW_MILLIS = 50;

    public static final long PROD_SLOW_MILLIS = 200;

    public static final long DEFAULT_LARGE_ENTITIES = 1_000;

    private final SeverityScale count;

    private final SeverityScale slowMillis;

    private final SeverityScale largeEntities;

    /**
     * @param count         próg liczby flushy w jednostce
     * @param slowMillis    próg czasu jednego flushu w milisekundach
     * @param largeEntities próg liczby encji w jednym flushu
     */
    public FlushAnalyzer(SeverityScale count, SeverityScale slowMillis, SeverityScale largeEntities) {

        this.count = Objects.requireNonNull(count, "próg liczby flushy jest wymagany");
        this.slowMillis = Objects.requireNonNull(slowMillis, "próg czasu flushu jest wymagany");
        this.largeEntities = Objects.requireNonNull(largeEntities, "próg wielkości flushu jest wymagany");
    }

    public static FlushAnalyzer defaults(DiagnosticsSettings settings) {

        return withCountScale(SeverityScale.of(DEFAULT_COUNT_THRESHOLD), settings.mode());
    }

    /** Analiza z podanym progiem liczby flushy i domyślnymi progami czasu oraz wielkości dla trybu. */
    public static FlushAnalyzer withCountScale(SeverityScale count, DiagnosticsMode mode) {

        long slow = switch (mode) {

            case DEV -> DEV_SLOW_MILLIS;
            case PROD -> PROD_SLOW_MILLIS;
        };
        return new FlushAnalyzer(count, SeverityScale.of(slow), SeverityScale.of(DEFAULT_LARGE_ENTITIES));
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

        private long flushes;

        private long longestMillis;

        private long largest;

        @Override
        public void onEvent(DataAccessEvent event) {
        }

        @Override
        public void onActivity(ActivityEvent event) {

            if (event instanceof PersistenceSessionEvent session) {

                flushes += session.flushes() + session.autoFlushes();
                longestMillis = Math.max(longestMillis, session.longestFlush().toMillis());
                largest = Math.max(largest, session.largestFlush());
            }
        }

        @Override
        public List<Finding> findings() {

            return Stream.of(
                    finding(COUNT_CODE, count, flushes, "Flushy sesji w jednej jednostce pracy: %d (próg %d)"),
                    finding(SLOW_CODE, slowMillis, longestMillis, "Najdłuższy flush trwał %d ms (próg %d ms)"),
                    finding(LARGE_CODE, largeEntities, largest, "Jeden flush objął %d encji (próg %d)"))
                .flatMap(Optional::stream)
                .toList();
        }

        private Optional<Finding> finding(String code, SeverityScale scale, long measured, String titleTemplate) {

            String title = titleTemplate.formatted(measured, scale.threshold());
            return scale.grade(measured)
                .map(severity -> new Finding(code, severity, title, measured, scale.threshold(), List.of()));
        }
    }
}
