package com.pgoogol.diagnostics.spring;

import com.pgoogol.diagnostics.core.DiagnosticsEngine;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;
import com.pgoogol.diagnostics.core.analysis.DirtyCheckingAnalyzer;
import com.pgoogol.diagnostics.core.analysis.EntityLoadAnalyzer;
import com.pgoogol.diagnostics.core.analysis.FlushAnalyzer;
import com.pgoogol.diagnostics.core.analysis.SecondLevelCacheAnalyzer;
import com.pgoogol.diagnostics.core.analysis.SeverityScale;
import com.pgoogol.diagnostics.hibernate.HibernateCaptureStrategy;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionEventListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;

/**
 * Wnętrze Hibernate: strategia sesji i cztery analizy. Progi: {@code flush} w liczbie
 * flushy, {@code dirty-checking} w milisekundach, {@code entity-load} w encjach,
 * {@code second-level-cache} w minimalnej liczbie odczytów cache.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({SessionEventListener.class, HibernatePropertiesCustomizer.class, EntityManagerFactory.class})
class DiagnosticsHibernateConfiguration {

    @Bean
    @ConditionalOnMissingBean
    HibernateCaptureStrategy hibernateCaptureStrategy(ObjectProvider<DiagnosticsEngine> engine,
                                                      ObjectProvider<EntityManagerFactory> factories) {

        return new HibernateCaptureStrategy(engine::getObject, () -> factories.orderedStream().toList());
    }

    @Bean
    HibernatePropertiesCustomizer dataDiagnosticsHibernateCustomizer(HibernateCaptureStrategy strategy) {

        return strategy::customize;
    }

    @Bean
    @ConditionalOnMissingBean
    FlushAnalyzer flushAnalyzer(DataDiagnosticsProperties properties, DiagnosticsSettings settings) {

        SeverityScale count = properties.analyzer(FlushAnalyzer.ID).scale(FlushAnalyzer.DEFAULT_COUNT_THRESHOLD);
        return FlushAnalyzer.withCountScale(count, settings.mode());
    }

    @Bean
    @ConditionalOnMissingBean
    DirtyCheckingAnalyzer dirtyCheckingAnalyzer(DataDiagnosticsProperties properties, DiagnosticsSettings settings) {

        long defaultThreshold = DirtyCheckingAnalyzer.defaultThreshold(settings.mode());
        SeverityScale scale = properties.analyzer(DirtyCheckingAnalyzer.ID).scale(defaultThreshold);
        return new DirtyCheckingAnalyzer(scale);
    }

    @Bean
    @ConditionalOnMissingBean
    EntityLoadAnalyzer entityLoadAnalyzer(DataDiagnosticsProperties properties, DiagnosticsSettings settings) {

        long defaultThreshold = EntityLoadAnalyzer.defaultThreshold(settings.mode());
        SeverityScale scale = properties.analyzer(EntityLoadAnalyzer.ID).scale(defaultThreshold);
        return new EntityLoadAnalyzer(scale);
    }

    @Bean
    @ConditionalOnMissingBean
    SecondLevelCacheAnalyzer secondLevelCacheAnalyzer(DataDiagnosticsProperties properties) {

        Long minimum = properties.analyzer(SecondLevelCacheAnalyzer.ID).threshold();
        long chosen = Objects.requireNonNullElse(minimum, SecondLevelCacheAnalyzer.DEFAULT_MINIMUM_LOOKUPS);
        return new SecondLevelCacheAnalyzer(chosen);
    }
}
