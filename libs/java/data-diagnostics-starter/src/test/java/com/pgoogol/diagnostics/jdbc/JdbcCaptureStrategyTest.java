package com.pgoogol.diagnostics.jdbc;

import com.pgoogol.diagnostics.core.CallSite;
import com.pgoogol.diagnostics.core.CallSiteResolver;
import com.pgoogol.diagnostics.core.DataAccessEvent;
import com.pgoogol.diagnostics.core.DiagnosticsEngine;
import com.pgoogol.diagnostics.core.DiagnosticsMode;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;
import com.pgoogol.diagnostics.core.OperationKind;
import com.pgoogol.diagnostics.core.UnitOfWorkScope;
import com.pgoogol.diagnostics.core.UnitOfWorkType;
import com.pgoogol.diagnostics.core.analysis.DiagnosticAnalyzer;
import com.pgoogol.diagnostics.core.store.DataStoreSupport;
import com.pgoogol.diagnostics.core.store.PostgreSqlSupport;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.proxy.ParameterSetOperation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertAll;

class JdbcCaptureStrategyTest {

    private static final String ITEMS = "select * from order_item where order_id = 7";

    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");

    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final DiagnosticsSettings DEV = DiagnosticsSettings.defaults(DiagnosticsMode.DEV);

    private final List<DataAccessEvent> captured = new ArrayList<>();

    @Test
    @DisplayName("zapytanie w otwartej jednostce daje zdarzenie z kształtem, rodzajem, tekstem i czasem")
    void afterQuery_whenUnitOpenInDev_recordsEvent() {

        // given
        Capture capture = capture(DEV, CallSiteResolver.NONE);

        // when
        capture.inUnit(() -> capture.execute(execution(), query(ITEMS)));

        // then
        DataAccessEvent event = captured.getFirst();
        assertAll(
            () -> assertThat(captured).hasSize(1),
            () -> assertThat(event.store().name()).isEqualTo("postgresql"),
            () -> assertThat(event.kind()).isEqualTo(OperationKind.READ),
            () -> assertThat(event.text()).isEqualTo(ITEMS),
            () -> assertThat(event.shape()).isEqualTo("select * from order_item where order_id = ?"),
            () -> assertThat(event.parameters()).isEmpty(),
            () -> assertThat(event.success()).isTrue(),
            () -> assertThat(event.batchSize()).isZero(),
            () -> assertThat(event.timestamp()).isEqualTo(NOW));
    }

    @Test
    @DisplayName("w prod zdarzenie niesie sam kształt, bez pełnego tekstu")
    void afterQuery_whenProd_keepsShapeWithoutText() {

        // given
        Capture capture = capture(DiagnosticsSettings.defaults(DiagnosticsMode.PROD), CallSiteResolver.NONE);

        // when
        capture.inUnit(() -> capture.execute(execution(), query(ITEMS)));

        // then
        DataAccessEvent event = captured.getFirst();
        assertAll(
            () -> assertThat(event.text()).isNull(),
            () -> assertThat(event.shape()).isEqualTo("select * from order_item where order_id = ?"));
    }

    @Test
    @DisplayName("parametry za flagą w dev: każdy jako tekst, długi przycięty, setNull jako null")
    void afterQuery_whenParametersEnabled_recordsTruncatedValues() throws NoSuchMethodException {

        // given
        Capture capture = capture(DEV.withCaptureParameters(true), CallSiteResolver.NONE);
        String longValue = "x".repeat(150);
        QueryInfo query = query("select * from note where id = ? and body = ? and tag = ?");
        query.setParametersList(List.of(List.of(setObject(1, 7L), setObject(2, longValue), setNull(3))));

        // when
        capture.inUnit(() -> capture.execute(execution(), query));

        // then
        assertThat(captured.getFirst().parameters())
            .containsExactly("7", "x".repeat(JdbcCaptureStrategy.MAX_PARAMETER_LENGTH) + "…", "null");
    }

    @Test
    @DisplayName("wykonanie w batchu przenosi jego rozmiar do zdarzenia")
    void afterQuery_whenBatchExecution_recordsBatchSize() {

        // given
        Capture capture = capture(DEV, CallSiteResolver.NONE);
        ExecutionInfo execution = execution();
        execution.setBatch(true);
        execution.setBatchSize(50);

        // when
        capture.inUnit(() -> capture.execute(execution, query("insert into order_item values (?, ?)")));

        // then
        assertAll(
            () -> assertThat(captured.getFirst().batchSize()).isEqualTo(50),
            () -> assertThat(captured.getFirst().kind()).isEqualTo(OperationKind.WRITE));
    }

    @Test
    @DisplayName("nieudane wykonanie jest oznaczone w zdarzeniu")
    void afterQuery_whenExecutionFailed_marksFailure() {

        // given
        Capture capture = capture(DEV, CallSiteResolver.NONE);
        ExecutionInfo execution = execution();
        execution.setSuccess(false);

        // when
        capture.inUnit(() -> capture.execute(execution, query(ITEMS)));

        // then
        assertThat(captured.getFirst().success()).isFalse();
    }

    @Test
    @DisplayName("bez otwartej jednostki strategia nic nie liczy, nawet miejsca wywołania")
    void afterQuery_whenNoUnitOpen_recordsNothing() {

        // given
        AtomicInteger resolved = new AtomicInteger();
        CallSiteResolver counting = (store, shape, duration) -> {

            resolved.incrementAndGet();
            return Optional.empty();
        };
        Capture capture = capture(DEV, counting);

        // when
        capture.execute(execution(), query(ITEMS));

        // then
        assertAll(
            () -> assertThat(captured).isEmpty(),
            () -> assertThat(resolved).hasValue(0));
    }

    @Test
    @DisplayName("z przechwytywaniem poza jednostką zdarzenie trafia do wspólnej jednostki")
    void afterQuery_whenOutsideCaptureEnabled_recordsWithoutUnit() {

        // given
        Capture capture = capture(DEV.withCaptureOutsideUnit(true), CallSiteResolver.NONE);

        // when
        capture.execute(execution(), query(ITEMS));

        // then
        assertThat(captured).hasSize(1);
    }

    @Test
    @DisplayName("kilka zapytań w jednym wykonaniu dzieli jego czas po równo")
    void afterQuery_whenSeveralQueries_splitsDurationEvenly() {

        // given: bez beforeQuery strategia bierze czas zmierzony przez datasource-proxy
        Capture capture = capture(DEV, CallSiteResolver.NONE);
        ExecutionInfo execution = execution();
        execution.setElapsedTime(10);
        List<QueryInfo> queries = List.of(query("delete from a"), query("delete from b"));

        // when
        capture.inUnit(() -> capture.strategy().afterQuery(execution, queries));

        // then
        assertThat(captured)
            .extracting(DataAccessEvent::duration)
            .containsExactly(Duration.ofMillis(5), Duration.ofMillis(5));
    }

    @Test
    @DisplayName("miejsce wywołania ustalone przez resolver trafia do zdarzenia")
    void afterQuery_whenCallSiteResolved_attachesIt() {

        // given
        CallSite caller = new CallSite("com.example.OrderService", "list", 42, null);
        Capture capture = capture(DEV, (store, shape, duration) -> Optional.of(caller));

        // when
        capture.inUnit(() -> capture.execute(execution(), query(ITEMS)));

        // then
        assertThat(captured.getFirst().callSite()).isEqualTo(caller);
    }

    @Test
    @DisplayName("błąd w przechwytywaniu nie wychodzi do aplikacji, bo zapytanie już się wykonało")
    void afterQuery_whenStoreSupportFails_doesNotPropagate() {

        // given
        DataStoreSupport broken = new FailingStoreSupport();
        DiagnosticsEngine engine = engine(DEV);
        JdbcCaptureStrategy strategy = new JdbcCaptureStrategy(engine, broken, DEV, CallSiteResolver.NONE, CLOCK);

        // when & then
        try (UnitOfWorkScope ignored = engine.open("GET /orders", UnitOfWorkType.HTTP)) {

            assertThatNoException().isThrownBy(() -> strategy.afterQuery(execution(), List.of(query(ITEMS))));
        }
    }

    private Capture capture(DiagnosticsSettings settings, CallSiteResolver callSites) {

        DiagnosticsEngine engine = engine(settings);
        PostgreSqlSupport store = new PostgreSqlSupport();
        JdbcCaptureStrategy strategy = new JdbcCaptureStrategy(engine, store, settings, callSites, CLOCK);
        return new Capture(engine, strategy);
    }

    private DiagnosticsEngine engine(DiagnosticsSettings settings) {

        List<DiagnosticAnalyzer> analyzers = List.of(new CollectingAnalyzer(captured));
        return new DiagnosticsEngine(settings, analyzers, List.of(), CLOCK);
    }

    private static ExecutionInfo execution() {

        ExecutionInfo execution = new ExecutionInfo();
        execution.setSuccess(true);
        return execution;
    }

    private static QueryInfo query(String sql) {

        return new QueryInfo(sql);
    }

    private static ParameterSetOperation setObject(int index, Object value) throws NoSuchMethodException {

        Method method = PreparedStatement.class.getMethod("setObject", int.class, Object.class);
        return new ParameterSetOperation(method, new Object[] {index, value});
    }

    private static ParameterSetOperation setNull(int index) throws NoSuchMethodException {

        Method method = PreparedStatement.class.getMethod("setNull", int.class, int.class);
        return new ParameterSetOperation(method, new Object[] {index, Types.VARCHAR});
    }

    private record Capture(DiagnosticsEngine engine, JdbcCaptureStrategy strategy) {

        /** Jedno wykonanie z pomiarem czasu, jak robi to datasource-proxy. */
        void execute(ExecutionInfo execution, QueryInfo query) {

            List<QueryInfo> queries = List.of(query);
            strategy.beforeQuery(execution, queries);
            strategy.afterQuery(execution, queries);
        }

        void inUnit(Runnable work) {

            try (UnitOfWorkScope ignored = engine.open("GET /orders", UnitOfWorkType.HTTP)) {

                work.run();
            }
        }
    }
}
