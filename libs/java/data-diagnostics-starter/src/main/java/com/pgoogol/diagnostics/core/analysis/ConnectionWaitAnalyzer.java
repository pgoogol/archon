package com.pgoogol.diagnostics.core.analysis;

import com.pgoogol.diagnostics.core.ActivityEvent;
import com.pgoogol.diagnostics.core.ConnectionEvent;
import com.pgoogol.diagnostics.core.DataAccessEvent;
import com.pgoogol.diagnostics.core.DiagnosticsMode;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;
import com.pgoogol.diagnostics.core.UnitOfWork;
import com.pgoogol.diagnostics.core.report.Finding;

import java.util.List;
import java.util.Objects;

/**
 * Czekanie na połączenie z puli: {@code getConnection} trwało co najmniej tyle, ile próg.
 * To znak, że pula jest za mała albo ktoś trzyma połączenia za długo. Jeden wniosek na
 * jednostkę, z najdłuższym czekaniem.
 *
 * <p>Próg domyślny: 20 ms w dev, 100 ms w prod.</p>
 */
public class ConnectionWaitAnalyzer implements DiagnosticAnalyzer {

    public static final String ID = "connection-wait";

    public static final String CODE = "CONNECTION_WAIT";

    public static final long DEV_THRESHOLD_MILLIS = 20;

    public static final long PROD_THRESHOLD_MILLIS = 100;

    private static final String TITLE = "Czekanie na połączenie z puli: najdłużej %d ms przy progu %d ms, wystąpień: %d";

    private final SeverityScale scale;

    /** @param scale próg w milisekundach i mnożnik wagi krytycznej */
    public ConnectionWaitAnalyzer(SeverityScale scale) {

        this.scale = Objects.requireNonNull(scale, "skala progu jest wymagana");
    }

    public static ConnectionWaitAnalyzer defaults(DiagnosticsSettings settings) {

        long threshold = defaultThreshold(settings.mode());
        return new ConnectionWaitAnalyzer(SeverityScale.of(threshold));
    }

    /** Próg domyślny w milisekundach: 20 w dev, 100 w prod. */
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

        private long count;

        private long max;

        @Override
        public void onEvent(DataAccessEvent event) {
        }

        @Override
        public void onActivity(ActivityEvent event) {

            if (event instanceof ConnectionEvent connection) {

                long millis = connection.waitTime().toMillis();
                if (millis >= scale.threshold()) {

                    count++;
                    max = Math.max(max, millis);
                }
            }
        }

        @Override
        public List<Finding> findings() {

            String title = TITLE.formatted(max, scale.threshold(), count);
            return scale.grade(max)
                .map(severity -> new Finding(CODE, severity, title, max, scale.threshold(), List.of()))
                .stream()
                .toList();
        }
    }
}
