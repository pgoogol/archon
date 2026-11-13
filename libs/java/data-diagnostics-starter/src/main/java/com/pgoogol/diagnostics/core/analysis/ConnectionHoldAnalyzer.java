package com.pgoogol.diagnostics.core.analysis;

import com.pgoogol.diagnostics.core.ActivityEvent;
import com.pgoogol.diagnostics.core.ConnectionEvent;
import com.pgoogol.diagnostics.core.DataAccessEvent;
import com.pgoogol.diagnostics.core.DiagnosticsMode;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;
import com.pgoogol.diagnostics.core.UnitOfWork;
import com.pgoogol.diagnostics.core.report.Finding;
import com.pgoogol.diagnostics.core.report.Severity;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Trzymanie połączenia: od pobrania do oddania minęło co najmniej tyle, ile próg, albo
 * połączenie przez większość czasu wisiało bezczynnie (zapytania zajęły mniej niż
 * {@value #IDLE_SHARE_PERCENT}% czasu trzymania). To drugie to zwykle wywołanie HTTP albo
 * ciężkie obliczenia wewnątrz transakcji. Jeden wniosek na jednostkę, z najdłuższym
 * trzymaniem; pomiarem są milisekundy.
 *
 * <p>Próg domyślny: 1 s w dev, 5 s w prod. Bezczynność liczy się dopiero od
 * {@code idleFloor} (domyślnie 100 ms), żeby krótkie wypożyczenia nie dawały szumu.</p>
 */
public class ConnectionHoldAnalyzer implements DiagnosticAnalyzer {

    public static final String ID = "connection-hold";

    public static final String CODE = "CONNECTION_HOLD";

    public static final long DEV_THRESHOLD_MILLIS = 1_000;

    public static final long PROD_THRESHOLD_MILLIS = 5_000;

    public static final int IDLE_SHARE_PERCENT = 10;

    public static final Duration DEFAULT_IDLE_FLOOR = Duration.ofMillis(100);

    private static final String TITLE = "Połączenie trzymane najdłużej %d ms (próg %d ms), z czego w SQL %d%%, wystąpień: %d";

    private final SeverityScale scale;

    private final Duration idleFloor;

    /**
     * @param scale     próg trzymania w milisekundach i mnożnik wagi krytycznej
     * @param idleFloor najkrótsze trzymanie, przy którym liczy się bezczynność
     */
    public ConnectionHoldAnalyzer(SeverityScale scale, Duration idleFloor) {

        this.scale = Objects.requireNonNull(scale, "skala progu jest wymagana");
        this.idleFloor = Objects.requireNonNull(idleFloor, "próg bezczynności jest wymagany");
    }

    public static ConnectionHoldAnalyzer defaults(DiagnosticsSettings settings) {

        long threshold = defaultThreshold(settings.mode());
        return new ConnectionHoldAnalyzer(SeverityScale.of(threshold), DEFAULT_IDLE_FLOOR);
    }

    /** Próg domyślny w milisekundach: 1000 w dev, 5000 w prod. */
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

        private long maxMillis;

        private long shareAtMax;

        @Override
        public void onEvent(DataAccessEvent event) {
        }

        @Override
        public void onActivity(ActivityEvent event) {

            if (event instanceof ConnectionEvent connection && isProblem(connection)) {

                count++;
                long millis = connection.holdTime().toMillis();
                if (millis >= maxMillis) {

                    maxMillis = millis;
                    shareAtMax = connection.sqlSharePercent();
                }
            }
        }

        @Override
        public List<Finding> findings() {

            if (count == 0) {

                return List.of();
            }
            String title = TITLE.formatted(maxMillis, scale.threshold(), shareAtMax, count);
            Severity severity = scale.grade(maxMillis).orElse(Severity.WARN);
            Finding finding = new Finding(CODE, severity, title, maxMillis, scale.threshold(), List.of());
            return Optional.of(finding).stream().toList();
        }

        private boolean isProblem(ConnectionEvent connection) {

            boolean longHold = connection.holdTime().toMillis() >= scale.threshold();
            boolean idle = connection.holdTime().compareTo(idleFloor) >= 0
                && connection.sqlSharePercent() < IDLE_SHARE_PERCENT;
            return longHold || idle;
        }
    }
}
