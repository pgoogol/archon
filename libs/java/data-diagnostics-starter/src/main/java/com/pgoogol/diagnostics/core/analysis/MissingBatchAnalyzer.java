package com.pgoogol.diagnostics.core.analysis;

import com.pgoogol.diagnostics.core.DataAccessEvent;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;
import com.pgoogol.diagnostics.core.OperationKind;
import com.pgoogol.diagnostics.core.UnitOfWork;

import java.util.Objects;

/**
 * Brak batcha: ten sam kształt zapisu wykonany co najmniej N razy w jednej jednostce,
 * każde wykonanie osobnym poleceniem. Zwykle to {@code save} w pętli bez
 * {@code hibernate.jdbc.batch_size} albo z identyfikatorem {@code IDENTITY}, który
 * wyłącza batch w Hibernate. Wykonania wysłane w batchu się nie liczą.
 *
 * <p>Próg domyślny: 5 wykonań w obu trybach.</p>
 */
public class MissingBatchAnalyzer implements DiagnosticAnalyzer {

    public static final String ID = "missing-batch";

    public static final String CODE = "MISSING_BATCH";

    public static final long DEFAULT_THRESHOLD = 5;

    private static final String TITLE = "Ten sam zapis wykonany %d razy bez batcha w jednej jednostce pracy (próg %d)";

    private final SeverityScale scale;

    private final int shapeLimit;

    /**
     * @param scale      próg w liczbie wykonań jednego kształtu poza batchem i mnożnik wagi
     *                   krytycznej
     * @param shapeLimit ile różnych kształtów liczyć osobno w jednej jednostce
     */
    public MissingBatchAnalyzer(SeverityScale scale, int shapeLimit) {

        this.scale = Objects.requireNonNull(scale, "skala progu jest wymagana");
        this.shapeLimit = shapeLimit;
    }

    /** Próg domyślny i limit kształtów z ustawień. */
    public static MissingBatchAnalyzer defaults(DiagnosticsSettings settings) {

        SeverityScale scale = SeverityScale.of(DEFAULT_THRESHOLD);
        return new MissingBatchAnalyzer(scale, settings.unitShapeLimit());
    }

    @Override
    public String id() {

        return ID;
    }

    @Override
    public AnalysisSession start(UnitOfWork unit) {

        return new RepeatedShapeSession(this::counts, scale, CODE, TITLE, shapeLimit);
    }

    private boolean counts(DataAccessEvent event) {

        return Objects.equals(event.kind(), OperationKind.WRITE) && event.batchSize() == 0;
    }
}
