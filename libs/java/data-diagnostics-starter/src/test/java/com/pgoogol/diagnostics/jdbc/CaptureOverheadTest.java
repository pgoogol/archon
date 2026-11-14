package com.pgoogol.diagnostics.jdbc;

import com.pgoogol.diagnostics.core.CallSiteResolver;
import com.pgoogol.diagnostics.core.DiagnosticsEngine;
import com.pgoogol.diagnostics.core.DiagnosticsMode;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;
import com.pgoogol.diagnostics.core.UnitOfWorkScope;
import com.pgoogol.diagnostics.core.UnitOfWorkType;
import com.pgoogol.diagnostics.core.analysis.DiagnosticAnalyzer;
import com.pgoogol.diagnostics.core.analysis.MissingBatchAnalyzer;
import com.pgoogol.diagnostics.core.analysis.NPlusOneAnalyzer;
import com.pgoogol.diagnostics.core.analysis.OperationCountAnalyzer;
import com.pgoogol.diagnostics.core.analysis.SlowOperationAnalyzer;
import com.pgoogol.diagnostics.core.store.PostgreSqlSupport;
import com.pgoogol.diagnostics.metrics.DiagnosticsMeters;
import com.pgoogol.diagnostics.metrics.MetricsAnalyzer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pomiar narzutu przechwytywania przy stałym włączeniu, w trybie prod, z analizami
 * operacji i miernikami. Atrapa JDBC odpowiada natychmiast, więc zmierzony czas to sam
 * narzut: proxy datasource-proxy, kształt zapytania, silnik i analizy. Na prawdziwej bazie
 * tego nie widać, bo szum jednego zapytania przez sieć jest o rząd wielkości większy.
 *
 * <p>Wynik trafia do logu testu. Próg w asercji jest bezpiecznikiem przed regresją o rząd
 * wielkości (np. przejściem po stosie przy każdym zapytaniu w prod), a nie celem.</p>
 */
class CaptureOverheadTest {

    private static final Logger log = LoggerFactory.getLogger(CaptureOverheadTest.class);

    private static final String QUERY = "select o1_0.id,o1_0.number from orders o1_0 where o1_0.id=?";

    private static final int QUERIES_PER_ROUND = 5_000;

    private static final int ROUNDS = 12;

    private static final long MAX_OVERHEAD_NANOS = 100_000;

    private final DiagnosticsSettings settings = DiagnosticsSettings.defaults(DiagnosticsMode.PROD);

    private final DataSource original = new InstantDataSource();

    @Test
    @DisplayName("narzut na jedno zapytanie w trybie prod zostaje w mikrosekundach")
    void query_whenCapturedInProdMode_addsMicrosecondsOfOverhead() throws SQLException {

        // given
        DiagnosticsEngine engine = engineWithOperationAnalyzersAndMeters();
        DataSource captured = wrap(engine);

        // when
        long originalNanos = bestRound(original, engine, false);
        long outsideUnitNanos = bestRound(captured, engine, false);
        long insideUnitNanos = bestRound(captured, engine, true);

        // then
        long overheadNanos = insideUnitNanos - originalNanos;
        log.info("NARZUT na zapytanie (prod): atrapa {} ns, owinięte poza jednostką {} ns, w jednostce {} ns",
            originalNanos, outsideUnitNanos, insideUnitNanos);
        assertThat(overheadNanos).isLessThan(MAX_OVERHEAD_NANOS);
    }

    private DiagnosticsEngine engineWithOperationAnalyzersAndMeters() {

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DiagnosticsMeters meters = new DiagnosticsMeters(() -> registry);
        List<DiagnosticAnalyzer> analyzers = List.of(
            NPlusOneAnalyzer.defaults(settings),
            MissingBatchAnalyzer.defaults(settings),
            SlowOperationAnalyzer.defaults(settings),
            OperationCountAnalyzer.defaults(settings),
            new MetricsAnalyzer(meters));
        return new DiagnosticsEngine(settings, analyzers, List.of());
    }

    private DataSource wrap(DiagnosticsEngine engine) {

        JdbcCaptureStrategy strategy = new JdbcCaptureStrategy(engine, new PostgreSqlSupport(), settings,
            CallSiteResolver.NONE, Clock.systemUTC());
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("jdbcCaptureStrategy", strategy);
        DataSourceCapturePostProcessor processor =
            new DataSourceCapturePostProcessor(beans.getBeanProvider(JdbcCaptureStrategy.class), () -> engine);
        return processor.wrap(original, "dataSource");
    }

    /** Najlepsza z rund, w nanosekundach na zapytanie; najlepsza, bo interesuje nas koszt po rozgrzaniu JVM. */
    private long bestRound(DataSource source, DiagnosticsEngine engine, boolean insideUnit) throws SQLException {

        long best = Long.MAX_VALUE;
        // pętla zamiast strumienia: runda rzuca SQLException
        for (int round = 0; round < ROUNDS; round++) {

            best = Math.min(best, round(source, engine, insideUnit));
        }
        return best;
    }

    private long round(DataSource source, DiagnosticsEngine engine, boolean insideUnit) throws SQLException {

        if (!insideUnit) {

            return runQueries(source);
        }
        try (UnitOfWorkScope ignored = engine.open("pomiar narzutu", UnitOfWorkType.HTTP)) {

            return runQueries(source);
        }
    }

    private long runQueries(DataSource source) throws SQLException {

        long started = System.nanoTime();
        try (Connection connection = source.getConnection()) {

            // pętla zamiast strumienia: zapytanie rzuca SQLException
            for (int query = 0; query < QUERIES_PER_ROUND; query++) {

                runQuery(connection, query);
            }
        }
        return (System.nanoTime() - started) / QUERIES_PER_ROUND;
    }

    private void runQuery(Connection connection, long id) throws SQLException {

        try (PreparedStatement statement = connection.prepareStatement(QUERY)) {

            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {

                rows.next();
            }
        }
    }
}
