package com.pgoogol.diagnostics.core.analysis;

import com.pgoogol.diagnostics.core.DiagnosticsMode;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;

/**
 * Za dużo encji załadowanych w jednej jednostce pracy. Zwykle brakuje paginacji albo
 * zapytanie dociąga za szeroki graf; każda encja to pamięć i czas sprawdzania zmian.
 *
 * <p>Próg domyślny: 1000 encji w dev, 5000 w prod.</p>
 */
public class EntityLoadAnalyzer extends PersistenceTotalAnalyzer {

    public static final String ID = "entity-load";

    public static final String CODE = "ENTITY_LOAD";

    public static final long DEV_THRESHOLD = 1_000;

    public static final long PROD_THRESHOLD = 5_000;

    private static final String TITLE = "Encji załadowanych w jednej jednostce pracy: %d (próg %d)";

    /** @param scale próg liczby encji i mnożnik wagi krytycznej */
    public EntityLoadAnalyzer(SeverityScale scale) {

        super(CODE, scale, TITLE, session -> session.entitiesLoaded());
    }

    public static EntityLoadAnalyzer defaults(DiagnosticsSettings settings) {

        long threshold = defaultThreshold(settings.mode());
        return new EntityLoadAnalyzer(SeverityScale.of(threshold));
    }

    /** Próg domyślny: 1000 encji w dev, 5000 w prod. */
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
}
