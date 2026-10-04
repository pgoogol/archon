package com.pgoogol.diagnostics.spring;

import com.pgoogol.diagnostics.core.CallSiteResolver;
import com.pgoogol.diagnostics.core.DiagnosticsEngine;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;
import com.pgoogol.diagnostics.core.store.DataStoreSupport;
import com.pgoogol.diagnostics.jdbc.DataSourceCapturePostProcessor;
import com.pgoogol.diagnostics.jdbc.JdbcCaptureStrategy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import java.time.Clock;

/** Przechwytywanie JDBC: strategia i owijanie każdego beana {@code DataSource}. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(DelegatingDataSource.class)
class DiagnosticsJdbcConfiguration {

    /**
     * Statyczny, bo post-processor powstaje przed zwykłymi beanami; strategię dostaje jako
     * dostawcę i sięga po nią dopiero przy pierwszym zapytaniu.
     */
    @Bean
    @ConditionalOnMissingBean
    static DataSourceCapturePostProcessor dataSourceCapturePostProcessor(
        ObjectProvider<JdbcCaptureStrategy> strategy) {

        return new DataSourceCapturePostProcessor(strategy);
    }

    @Bean
    @ConditionalOnMissingBean
    JdbcCaptureStrategy jdbcCaptureStrategy(DiagnosticsEngine engine, DataStoreSupport storeSupport,
                                            DiagnosticsSettings settings, CallSiteResolver callSites,
                                            ObjectProvider<Clock> clock) {

        Clock chosenClock = clock.getIfAvailable(Clock::systemUTC);
        return new JdbcCaptureStrategy(engine, storeSupport, settings, callSites, chosenClock);
    }
}
