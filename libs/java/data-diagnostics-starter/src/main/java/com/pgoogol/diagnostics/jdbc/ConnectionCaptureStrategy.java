package com.pgoogol.diagnostics.jdbc;

import com.pgoogol.diagnostics.core.ConnectionEvent;
import com.pgoogol.diagnostics.core.DiagnosticsEngine;
import net.ttddyy.dsproxy.ConnectionInfo;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.MethodExecutionContext;
import net.ttddyy.dsproxy.listener.MethodExecutionListener;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * Strategia przechwytywania puli połączeń dla jednego owiniętego {@code DataSource}:
 * mierzy czekanie w {@code getConnection}, czas od pobrania do {@code close} i łączny czas
 * zapytań na połączeniu. Przy oddaniu połączenia silnik dostaje {@link ConnectionEvent}.
 *
 * <p>Jedna instancja na {@code DataSource}, bo datasource-proxy numeruje połączenia osobno
 * w każdym. Połączenie, którego aplikacja nigdy nie oddała, zostaje w mapie; ponad
 * {@value #MAX_TRACKED} takich kolejne nie są śledzone, żeby wyciek połączeń w aplikacji
 * nie stał się wyciekiem pamięci w diagnostyce.</p>
 */
final class ConnectionCaptureStrategy implements MethodExecutionListener, QueryExecutionListener {

    static final int MAX_TRACKED = 10_000;

    private static final Logger log = LoggerFactory.getLogger(ConnectionCaptureStrategy.class);

    private static final String STARTED_AT = ConnectionCaptureStrategy.class.getName() + ".startedAt";

    private final Supplier<@Nullable DiagnosticsEngine> engine;

    private final Map<String, Borrowed> borrowed = new ConcurrentHashMap<>();

    ConnectionCaptureStrategy(Supplier<@Nullable DiagnosticsEngine> engine) {

        this.engine = engine;
    }

    @Override
    public void beforeMethod(MethodExecutionContext context) {

        if (context.getTarget() instanceof DataSource && isNamed(context, "getConnection")) {

            context.addCustomValue(STARTED_AT, System.nanoTime());
        }
    }

    @Override
    public void afterMethod(MethodExecutionContext context) {

        try {

            if (context.getTarget() instanceof DataSource && isNamed(context, "getConnection")) {

                acquired(context);
            } else if (context.getTarget() instanceof Connection && isNamed(context, "close")) {

                released(context.getConnectionInfo());
            }
        } catch (RuntimeException | LinkageError failure) {

            log.debug("Nie udało się zmierzyć połączenia z puli", failure);
        }
    }

    @Override
    public void beforeQuery(ExecutionInfo execution, List<QueryInfo> queries) {

        execution.addCustomValue(STARTED_AT, System.nanoTime());
    }

    @Override
    public void afterQuery(ExecutionInfo execution, List<QueryInfo> queries) {

        Long startedAt = execution.getCustomValue(STARTED_AT, Long.class);
        Borrowed connection = borrowed.get(Objects.toString(execution.getConnectionId(), ""));
        if (Objects.nonNull(startedAt) && Objects.nonNull(connection)) {

            connection.sqlNanos.add(System.nanoTime() - startedAt);
            connection.operations.add(queries.size());
        }
    }

    private void acquired(MethodExecutionContext context) {

        Long startedAt = context.getCustomValue(STARTED_AT, Long.class);
        ConnectionInfo info = context.getConnectionInfo();
        boolean failed = Objects.nonNull(context.getThrown());
        if (failed || Objects.isNull(startedAt) || Objects.isNull(info) || borrowed.size() >= MAX_TRACKED) {

            return;
        }
        long now = System.nanoTime();
        borrowed.put(info.getConnectionId(), new Borrowed(now - startedAt, now));
    }

    private void released(@Nullable ConnectionInfo info) {

        if (Objects.isNull(info)) {

            return;
        }
        Borrowed connection = borrowed.remove(info.getConnectionId());
        DiagnosticsEngine current = engine.get();
        if (Objects.isNull(connection) || Objects.isNull(current)) {

            return;
        }
        Duration wait = Duration.ofNanos(connection.waitNanos);
        Duration hold = Duration.ofNanos(System.nanoTime() - connection.acquiredAt);
        Duration sqlTime = Duration.ofNanos(connection.sqlNanos.sum());
        current.recordActivity(new ConnectionEvent(wait, hold, sqlTime, connection.operations.sum()));
    }

    private static boolean isNamed(MethodExecutionContext context, String method) {

        return Objects.equals(context.getMethod().getName(), method);
    }

    /** Połączenie między pobraniem a oddaniem; liczniki są sumatorami, bo zapytania mogą iść z innego wątku. */
    private static final class Borrowed {

        private final long waitNanos;

        private final long acquiredAt;

        private final LongAdder sqlNanos = new LongAdder();

        private final LongAdder operations = new LongAdder();

        private Borrowed(long waitNanos, long acquiredAt) {

            this.waitNanos = waitNanos;
            this.acquiredAt = acquiredAt;
        }
    }
}
