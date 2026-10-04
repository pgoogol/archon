package com.pgoogol.diagnostics.jdbc;

import com.pgoogol.diagnostics.core.CallSiteResolver;
import com.pgoogol.diagnostics.core.DataAccessEvent;
import com.pgoogol.diagnostics.core.DiagnosticsEngine;
import com.pgoogol.diagnostics.core.DiagnosticsMode;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;
import com.pgoogol.diagnostics.core.UnitOfWorkScope;
import com.pgoogol.diagnostics.core.UnitOfWorkType;
import com.pgoogol.diagnostics.core.analysis.DiagnosticAnalyzer;
import com.pgoogol.diagnostics.core.store.PostgreSqlSupport;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataSourceCapturePostProcessorTest {

    private final List<DataAccessEvent> captured = new ArrayList<>();

    private final DiagnosticsSettings settings = DiagnosticsSettings.defaults(DiagnosticsMode.DEV);

    private final DiagnosticsEngine engine = engine();

    @Test
    @DisplayName("owinięty HikariDataSource dalej wstrzykuje się po swoim typie")
    void postProcess_whenHikariDataSourceBean_keepsInjectionByType() {

        // given
        ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(HikariDataSource.class, HikariDataSource::new)
            .withBean(DataSourceCapturePostProcessor.class, () -> new DataSourceCapturePostProcessor(noStrategy()));

        // when & then
        runner.run(context -> assertThat(context.getBean(HikariDataSource.class)).isInstanceOf(CapturedDataSource.class));
    }

    @Test
    @DisplayName("zapytanie na połączeniu z owiniętego DataSource trafia do strategii JDBC")
    void getConnection_whenWrapped_reportsQueriesToStrategy() throws SQLException {

        // given
        DataSourceCapturePostProcessor processor = new DataSourceCapturePostProcessor(strategyProvider());
        DataSource wrapped = (DataSource) processor.postProcessAfterInitialization(stubDataSource(), "dataSource");

        // when
        try (UnitOfWorkScope ignored = engine.open("GET /orders", UnitOfWorkType.HTTP);
             Connection connection = wrapped.getConnection();
             PreparedStatement statement = connection.prepareStatement("select * from orders where id = 7")) {

            statement.executeQuery();
        }

        // then
        assertThat(captured)
            .extracting(DataAccessEvent::shape)
            .containsExactly("select * from orders where id = ?");
    }

    @Test
    @DisplayName("bean już owinięty nie dostaje drugiego owinięcia")
    void postProcess_whenAlreadyWrapped_returnsSameBean() {

        // given
        DataSourceCapturePostProcessor processor = new DataSourceCapturePostProcessor(noStrategy());
        Object wrapped = processor.postProcessAfterInitialization(stubDataSource(), "dataSource");

        // when
        Object again = processor.postProcessAfterInitialization(wrapped, "dataSource");

        // then
        assertThat(again).isSameAs(wrapped);
    }

    @Test
    @DisplayName("DataSource delegujący do innego beana zostaje bez owinięcia, żeby zapytań nie liczyć podwójnie")
    void postProcess_whenDelegatingDataSource_leavesItAlone() {

        // given
        DataSourceCapturePostProcessor processor = new DataSourceCapturePostProcessor(noStrategy());
        TransactionAwareDataSourceProxy delegating = new TransactionAwareDataSourceProxy(stubDataSource());

        // when
        Object result = processor.postProcessAfterInitialization(delegating, "transactionAwareDataSource");

        // then
        assertThat(result).isSameAs(delegating);
    }

    @Test
    @DisplayName("bean, który nie jest DataSource, przechodzi bez zmian")
    void postProcess_whenNotDataSource_returnsBeanUntouched() {

        // given
        DataSourceCapturePostProcessor processor = new DataSourceCapturePostProcessor(noStrategy());
        Object bean = new Object();

        // when
        Object result = processor.postProcessAfterInitialization(bean, "other");

        // then
        assertThat(result).isSameAs(bean);
    }

    private DiagnosticsEngine engine() {

        List<DiagnosticAnalyzer> analyzers = List.of(new CollectingAnalyzer(captured));
        return new DiagnosticsEngine(settings, analyzers, List.of());
    }

    private ObjectProvider<JdbcCaptureStrategy> strategyProvider() {

        JdbcCaptureStrategy strategy = new JdbcCaptureStrategy(engine, new PostgreSqlSupport(), settings,
            CallSiteResolver.NONE, Clock.systemUTC());
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("jdbcCaptureStrategy", strategy);
        return beans.getBeanProvider(JdbcCaptureStrategy.class);
    }

    private static ObjectProvider<JdbcCaptureStrategy> noStrategy() {

        return new StaticListableBeanFactory().getBeanProvider(JdbcCaptureStrategy.class);
    }

    /** {@code DataSource} bez bazy: połączenie i zapytanie to atrapy Mockito. */
    private static DataSource stubDataSource() {

        return new StubDataSource();
    }

    private static final class StubDataSource extends AbstractDataSource {

        @Override
        public Connection getConnection() throws SQLException {

            Connection connection = mock(Connection.class);
            PreparedStatement statement = mock(PreparedStatement.class);
            ResultSet resultSet = mock(ResultSet.class);
            when(connection.prepareStatement(anyString())).thenReturn(statement);
            when(statement.executeQuery()).thenReturn(resultSet);
            return connection;
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {

            return getConnection();
        }
    }
}
