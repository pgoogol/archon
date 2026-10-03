package com.pgoogol.diagnostics.core.analysis;

import com.pgoogol.diagnostics.core.DataAccessEvent;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;
import com.pgoogol.diagnostics.core.UnitOfWork;
import com.pgoogol.diagnostics.core.report.Finding;
import com.pgoogol.diagnostics.core.report.ShapeSummary;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Za dużo operacji w jednej jednostce pracy, niezależnie od tego, czy któryś kształt się
 * powtarza. Wniosek podaje pięć najczęstszych kształtów, żeby było od czego zacząć.
 *
 * <p>Próg domyślny: 50 operacji w dev, 100 w prod. Jak w pozostałych analizach liczy się
 * włącznie, więc jednostka z dokładnie 50 operacjami w dev daje {@code WARN}.</p>
 */
public class OperationCountAnalyzer implements DiagnosticAnalyzer {

    public static final String ID = "operation-count";

    public static final String CODE = "OPERATION_COUNT";

    public static final long DEV_THRESHOLD = 50;

    public static final long PROD_THRESHOLD = 100;

    public static final int TOP_SHAPES = 5;

    private static final String TITLE = "Operacji w jednej jednostce pracy: %d (próg %d)";

    private final SeverityScale scale;

    private final int shapeLimit;

    /**
     * @param scale      próg w liczbie operacji jednostki i mnożnik wagi krytycznej
     * @param shapeLimit ile różnych kształtów liczyć osobno w jednej jednostce
     */
    public OperationCountAnalyzer(SeverityScale scale, int shapeLimit) {

        this.scale = Objects.requireNonNull(scale, "skala progu jest wymagana");
        this.shapeLimit = shapeLimit;
    }

    /** Próg domyślny trybu i limit kształtów z ustawień. */
    public static OperationCountAnalyzer defaults(DiagnosticsSettings settings) {

        long threshold = switch (settings.mode()) {

            case DEV -> DEV_THRESHOLD;
            case PROD -> PROD_THRESHOLD;
        };
        SeverityScale scale = SeverityScale.of(threshold);
        return new OperationCountAnalyzer(scale, settings.unitShapeLimit());
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

        private final ShapeStats stats = new ShapeStats(shapeLimit);

        private long count;

        @Override
        public void onEvent(DataAccessEvent event) {

            count++;
            stats.add(event);
        }

        @Override
        public List<Finding> findings() {

            String title = TITLE.formatted(count, scale.threshold());
            List<ShapeSummary> topShapes = topShapes();
            return scale.grade(count)
                .map(severity -> new Finding(CODE, severity, title, count, scale.threshold(), topShapes))
                .stream()
                .toList();
        }

        /** Najczęstsze kształty; kubełek zbiorczy pomijamy, bo nie wskazuje żadnego zapytania. */
        private List<ShapeSummary> topShapes() {

            return stats.summaries().stream()
                .filter(summary -> !summary.overflow())
                .sorted(Comparator.comparingLong(ShapeSummary::count).reversed())
                .limit(TOP_SHAPES)
                .toList();
        }
    }
}
