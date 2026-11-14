package com.pgoogol.diagnostics.spring;

import com.pgoogol.diagnostics.metrics.DiagnosticsMeters;
import com.pgoogol.diagnostics.metrics.MetricsAnalyzer;
import com.pgoogol.diagnostics.metrics.MicrometerFindingReporter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mierniki Micrometer. Rejestr pobieramy przy zapisie, więc kolejność autokonfiguracji nie
 * ma znaczenia, a aplikacja bez rejestru po prostu nic nie zapisuje.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(prefix = "diagnostics.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
class DiagnosticsMetricsConfiguration {

    @Bean
    @ConditionalOnMissingBean
    DiagnosticsMeters diagnosticsMeters(ObjectProvider<MeterRegistry> registry) {

        return new DiagnosticsMeters(registry::getIfAvailable);
    }

    @Bean
    @ConditionalOnMissingBean
    MetricsAnalyzer metricsAnalyzer(DiagnosticsMeters meters) {

        return new MetricsAnalyzer(meters);
    }

    @Bean
    @ConditionalOnMissingBean
    MicrometerFindingReporter micrometerFindingReporter(DiagnosticsMeters meters, DataDiagnosticsProperties properties) {

        return new MicrometerFindingReporter(meters, properties.metrics().unitNameTag());
    }

    /** Limit różnych nazw jednostek w tagu; ponad limit miernik z nową nazwą jest odrzucany. */
    @Bean
    @ConditionalOnProperty(prefix = "diagnostics.metrics", name = "unit-name-tag", havingValue = "true")
    MeterFilter dataDiagnosticsUnitNameLimit(DataDiagnosticsProperties properties) {

        return MeterFilter.maximumAllowableTags(DiagnosticsMeters.PREFIX, DiagnosticsMeters.UNIT_NAME_TAG,
            properties.metrics().unitNameLimit(), MeterFilter.deny());
    }
}
