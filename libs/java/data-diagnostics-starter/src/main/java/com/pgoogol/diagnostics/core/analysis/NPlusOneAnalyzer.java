package com.pgoogol.diagnostics.core.analysis;

import com.pgoogol.diagnostics.core.DataAccessEvent;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;
import com.pgoogol.diagnostics.core.OperationKind;
import com.pgoogol.diagnostics.core.UnitOfWork;

import java.util.Objects;

/**
 * N+1: ten sam kształt odczytu wykonany co najmniej N razy w jednej jednostce pracy.
 * Zwykle to pętla, która dociąga powiązania po jednym wierszu. Wniosek podaje kształt,
 * liczbę wykonań, łączny czas i miejsca wywołania.
 *
 * <p>Próg domyślny: 5 wykonań w dev, 10 w prod.</p>
 */
public class NPlusOneAnalyzer implements DiagnosticAnalyzer {

    public static final String ID = "n-plus-one";

    public static final String CODE = "N_PLUS_ONE";

    public static final long DEV_THRESHOLD = 5;

    public static final long PROD_THRESHOLD = 10;

    private static final String TITLE = "Ten sam odczyt wykonany %d razy w jednej jednostce pracy (próg %d)";

    private final SeverityScale scale;

    private final int shapeLimit;

    /**
     * @param scale      próg w liczbie wykonań jednego kształtu i mnożnik wagi krytycznej
     * @param shapeLimit ile różnych kształtów liczyć osobno w jednej jednostce
     */
    public NPlusOneAnalyzer(SeverityScale scale, int shapeLimit) {

        this.scale = Objects.requireNonNull(scale, "skala progu jest wymagana");
        this.shapeLimit = shapeLimit;
    }

    /** Próg domyślny trybu i limit kształtów z ustawień. */
    public static NPlusOneAnalyzer defaults(DiagnosticsSettings settings) {

        long threshold = switch (settings.mode()) {

            case DEV -> DEV_THRESHOLD;
            case PROD -> PROD_THRESHOLD;
        };
        SeverityScale scale = SeverityScale.of(threshold);
        return new NPlusOneAnalyzer(scale, settings.unitShapeLimit());
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

        return Objects.equals(event.kind(), OperationKind.READ);
    }
}
