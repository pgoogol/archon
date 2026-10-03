package com.pgoogol.diagnostics.core.analysis;

import com.pgoogol.diagnostics.core.DataAccessEvent;
import com.pgoogol.diagnostics.core.DataStore;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;
import com.pgoogol.diagnostics.core.UnitOfWork;
import com.pgoogol.diagnostics.core.report.Finding;
import com.pgoogol.diagnostics.core.report.ShapeSummary;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Wolna operacja: wykonanie trwające co najmniej tyle, ile próg magazynu
 * ({@code diagnostics.analyzers.slow-operation.threshold.postgresql}). Jeden wniosek
 * na kształt: ile wykonań było wolnych, najdłuższe z nich i ile się nie powiodło, bo
 * wolny błąd to zwykle limit czasu albo blokada, a nie brak indeksu.
 *
 * <p>Pomiarem wniosku jest najdłuższe wykonanie w milisekundach, więc waga
 * {@code CRITICAL} oznacza wykonanie dłuższe niż pięciokrotność progu. Próg domyślny:
 * 100 ms w dev, 500 ms w prod.</p>
 */
public class SlowOperationAnalyzer implements DiagnosticAnalyzer {

    public static final String ID = "slow-operation";

    public static final String CODE = "SLOW_OPERATION";

    public static final Duration DEV_THRESHOLD = Duration.ofMillis(100);

    public static final Duration PROD_THRESHOLD = Duration.ofMillis(500);

    private static final String TITLE = "Wolna operacja: najdłużej %d ms przy progu %d ms, wolnych wykonań: %d";

    private static final String FAILURES_SUFFIX = ", w tym nieudanych: %d";

    private final Duration defaultThreshold;

    private final Map<String, Duration> storeThresholds;

    private final int criticalMultiplier;

    private final int shapeLimit;

    /**
     * @param defaultThreshold   próg magazynu bez własnego progu
     * @param storeThresholds    progi według nazwy magazynu, np. {@code postgresql}
     * @param criticalMultiplier ile razy przekroczony próg daje {@code CRITICAL}
     * @param shapeLimit         ile różnych kształtów liczyć osobno w jednej jednostce
     */
    public SlowOperationAnalyzer(Duration defaultThreshold, Map<String, Duration> storeThresholds,
                                 int criticalMultiplier, int shapeLimit) {

        this.defaultThreshold = requireAtLeastMillisecond(defaultThreshold);
        storeThresholds.values().forEach(SlowOperationAnalyzer::requireAtLeastMillisecond);
        this.storeThresholds = Map.copyOf(storeThresholds);
        this.criticalMultiplier = criticalMultiplier;
        this.shapeLimit = shapeLimit;
    }

    /** Próg domyślny trybu, bez progów magazynów, i limit kształtów z ustawień. */
    public static SlowOperationAnalyzer defaults(DiagnosticsSettings settings) {

        Duration threshold = switch (settings.mode()) {

            case DEV -> DEV_THRESHOLD;
            case PROD -> PROD_THRESHOLD;
        };
        return new SlowOperationAnalyzer(threshold, Map.of(), SeverityScale.DEFAULT_CRITICAL_MULTIPLIER,
            settings.unitShapeLimit());
    }

    @Override
    public String id() {

        return ID;
    }

    @Override
    public AnalysisSession start(UnitOfWork unit) {

        return new Session();
    }

    /** Próg magazynu: własny, a gdy go nie ustawiono, domyślny. */
    public Duration thresholdFor(DataStore store) {

        return storeThresholds.getOrDefault(store.name(), defaultThreshold);
    }

    private static Duration requireAtLeastMillisecond(Duration threshold) {

        Objects.requireNonNull(threshold, "próg wolnej operacji jest wymagany");
        if (threshold.toMillis() < 1) {

            throw new IllegalArgumentException("próg wolnej operacji musi mieć co najmniej 1 ms: " + threshold);
        }
        return threshold;
    }

    private final class Session implements AnalysisSession {

        private final ShapeStats stats = new ShapeStats(shapeLimit);

        @Override
        public void onEvent(DataAccessEvent event) {

            Duration threshold = thresholdFor(event.store());
            if (event.duration().compareTo(threshold) >= 0) {

                stats.add(event);
            }
        }

        /** Wnioski od najdłuższego wykonania; kubełek zbiorczy nie daje wniosku. */
        @Override
        public List<Finding> findings() {

            return stats.summaries().stream()
                .filter(summary -> !summary.overflow())
                .flatMap(summary -> finding(summary).stream())
                .sorted(Comparator.comparingLong(Finding::measured).reversed())
                .toList();
        }

        private Optional<Finding> finding(ShapeSummary summary) {

            Duration threshold = thresholdFor(summary.store());
            SeverityScale scale = new SeverityScale(threshold.toMillis(), criticalMultiplier);
            long maxMillis = summary.maxTime().toMillis();
            String title = title(summary, maxMillis, scale.threshold());
            List<ShapeSummary> shapes = List.of(summary);
            return scale.grade(maxMillis)
                .map(severity -> new Finding(CODE, severity, title, maxMillis, scale.threshold(), shapes));
        }

        private String title(ShapeSummary summary, long maxMillis, long thresholdMillis) {

            String title = TITLE.formatted(maxMillis, thresholdMillis, summary.count());
            if (summary.failures() == 0) {

                return title;
            }
            return title + FAILURES_SUFFIX.formatted(summary.failures());
        }
    }
}
