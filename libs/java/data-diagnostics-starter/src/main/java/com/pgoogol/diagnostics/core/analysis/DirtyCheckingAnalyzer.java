package com.pgoogol.diagnostics.core.analysis;

import com.pgoogol.diagnostics.core.DiagnosticsMode;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;

/**
 * Długie sprawdzanie zmian: przy każdym flushu Hibernate porównuje każdą zarządzaną encję
 * z jej stanem z chwili załadowania. Gdy jednostka trzyma tysiące encji, których nie
 * zmienia, ten czas idzie na marne; pomaga {@code readOnly = true} albo projekcja DTO
 * zamiast encji. Pomiarem jest łączny czas w milisekundach.
 *
 * <p>Próg domyślny: 50 ms w dev, 200 ms w prod.</p>
 */
public class DirtyCheckingAnalyzer extends PersistenceTotalAnalyzer {

    public static final String ID = "dirty-checking";

    public static final String CODE = "DIRTY_CHECKING";

    public static final long DEV_THRESHOLD_MILLIS = 50;

    public static final long PROD_THRESHOLD_MILLIS = 200;

    private static final String TITLE = """
        Sprawdzanie zmian w encjach trwało %d ms (próg %d ms); \
        dla odczytów użyj readOnly albo projekcji DTO""";

    /** @param scale próg łącznego czasu w milisekundach i mnożnik wagi krytycznej */
    public DirtyCheckingAnalyzer(SeverityScale scale) {

        super(CODE, scale, TITLE, session -> session.dirtyCheckTime().toMillis());
    }

    public static DirtyCheckingAnalyzer defaults(DiagnosticsSettings settings) {

        long threshold = defaultThreshold(settings.mode());
        return new DirtyCheckingAnalyzer(SeverityScale.of(threshold));
    }

    /** Próg domyślny w milisekundach: 50 w dev, 200 w prod. */
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
}
