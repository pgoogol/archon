package com.pgoogol.diagnostics.jdbc;

import com.pgoogol.diagnostics.core.CallSite;
import com.pgoogol.diagnostics.core.CallSiteResolver;
import com.pgoogol.diagnostics.core.DataAccessEvent;
import com.pgoogol.diagnostics.core.DataStore;
import com.pgoogol.diagnostics.core.DiagnosticsEngine;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;
import com.pgoogol.diagnostics.core.OperationKind;
import com.pgoogol.diagnostics.core.store.DataStoreSupport;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import net.ttddyy.dsproxy.proxy.ParameterSetOperation;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Strategia przechwytywania JDBC: słuchacz datasource-proxy, który zamienia każde wykonane
 * zapytanie na {@link DataAccessEvent} i oddaje je silnikowi.
 *
 * <p>Czas zdarzenia obejmuje samo wykonanie polecenia ({@code execute…}), bez pobierania
 * wierszy z {@code ResultSet}: sterownik dociąga je później, przy {@code next()}, poza
 * pomiarem. Mierzymy go sami przez {@link System#nanoTime()}, bo czas z datasource-proxy
 * ma rozdzielczość milisekundy i szybkie zapytania wychodziłyby w nim zerowe.</p>
 *
 * <p>Jedno wykonanie z kilkoma zapytaniami ({@code Statement.addBatch} z różnym SQL) daje
 * zdarzenie na zapytanie, a czas dzieli się po równo, bo sterownik nie podaje go osobno.
 * Parametry zapisujemy tylko za flagą w dev, każdy przycięty do {@value #MAX_PARAMETER_LENGTH}
 * znaków, z pierwszego zestawu wykonania.</p>
 *
 * <p>Błąd w przechwytywaniu ląduje w logu na DEBUG i nie wychodzi do aplikacji: zapytanie
 * już się wykonało i diagnostyka nie może zmienić jego wyniku.</p>
 */
public class JdbcCaptureStrategy implements QueryExecutionListener {

    static final int MAX_PARAMETER_LENGTH = 100;

    private static final Logger log = LoggerFactory.getLogger(JdbcCaptureStrategy.class);

    private static final String STARTED_AT = JdbcCaptureStrategy.class.getName() + ".startedAt";

    private static final String NULL_PARAMETER = "null";

    private final DiagnosticsEngine engine;

    private final DataStoreSupport storeSupport;

    private final DiagnosticsSettings settings;

    private final CallSiteResolver callSites;

    private final Clock clock;

    public JdbcCaptureStrategy(DiagnosticsEngine engine, DataStoreSupport storeSupport, DiagnosticsSettings settings,
                               CallSiteResolver callSites, Clock clock) {

        this.engine = Objects.requireNonNull(engine, "silnik jest wymagany");
        this.storeSupport = Objects.requireNonNull(storeSupport, "strategia bazy jest wymagana");
        this.settings = Objects.requireNonNull(settings, "ustawienia są wymagane");
        this.callSites = Objects.requireNonNull(callSites, "ustalanie miejsca wywołania jest wymagane");
        this.clock = Objects.requireNonNull(clock, "zegar jest wymagany");
    }

    @Override
    public void beforeQuery(ExecutionInfo execution, List<QueryInfo> queries) {

        execution.addCustomValue(STARTED_AT, System.nanoTime());
    }

    @Override
    public void afterQuery(ExecutionInfo execution, List<QueryInfo> queries) {

        try {

            capture(execution, queries);
        } catch (RuntimeException | LinkageError failure) {

            log.debug("Nie udało się przechwycić zapytania JDBC", failure);
        }
    }

    private void capture(ExecutionInfo execution, List<QueryInfo> queries) {

        if (queries.isEmpty() || !worthCapturing()) {

            return;
        }
        Duration elapsed = elapsed(execution);
        Duration perQuery = elapsed.dividedBy(queries.size());
        queries.forEach(query -> record(execution, query, perQuery));
    }

    /** Bez otwartej jednostki silnik i tak odrzuci zdarzenie, więc nie liczymy kształtu na darmo. */
    private boolean worthCapturing() {

        return settings.captureOutsideUnit() || engine.current().isPresent();
    }

    private void record(ExecutionInfo execution, QueryInfo query, Duration duration) {

        String sql = query.getQuery();
        DataStore store = storeSupport.store();
        String shape = storeSupport.shape(sql);
        OperationKind kind = storeSupport.classify(sql);
        String text = textOf(sql);
        List<String> parameters = parametersOf(query);
        int batchSize = batchSizeOf(execution);
        CallSite callSite = callSites.resolve(store, shape, duration).orElse(null);
        Instant timestamp = clock.instant();
        DataAccessEvent event = new DataAccessEvent(store, kind, text, parameters, shape, duration,
            execution.isSuccess(), batchSize, callSite, timestamp);
        engine.record(event);
    }

    private Duration elapsed(ExecutionInfo execution) {

        Long startedAt = execution.getCustomValue(STARTED_AT, Long.class);
        if (Objects.isNull(startedAt)) {

            return Duration.ofMillis(execution.getElapsedTime());
        }
        long nanos = System.nanoTime() - startedAt;
        return Duration.ofNanos(nanos);
    }

    private @Nullable String textOf(String sql) {

        if (settings.keepStatementText()) {

            return sql;
        }
        return null;
    }

    private int batchSizeOf(ExecutionInfo execution) {

        if (execution.isBatch()) {

            return execution.getBatchSize();
        }
        return 0;
    }

    private List<String> parametersOf(QueryInfo query) {

        if (!settings.captureParameters()) {

            return List.of();
        }
        return query.getParametersList().stream()
            .findFirst()
            .orElse(List.of())
            .stream()
            .map(this::render)
            .toList();
    }

    private String render(ParameterSetOperation operation) {

        Object[] args = operation.getArgs();
        if (ParameterSetOperation.isSetNullParameterOperation(operation) || Objects.isNull(args) || args.length < 2) {

            return NULL_PARAMETER;
        }
        String value = String.valueOf(args[1]);
        if (value.length() <= MAX_PARAMETER_LENGTH) {

            return value;
        }
        return value.substring(0, MAX_PARAMETER_LENGTH) + "…";
    }
}
