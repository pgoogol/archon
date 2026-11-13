package com.pgoogol.diagnostics.spring;

import com.pgoogol.diagnostics.core.DiagnosticsEngine;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;
import com.pgoogol.diagnostics.core.analysis.RepositoryHotspotAnalyzer;
import com.pgoogol.diagnostics.core.analysis.SeverityScale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.repository.core.support.RepositoryMethodInvocationListener;

/** Repozytoria Spring Data: strategia wywołań, jej rejestracja w fabrykach i analiza gorących metod. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RepositoryMethodInvocationListener.class)
class DiagnosticsRepositoryConfiguration {

    @Bean
    @ConditionalOnMissingBean
    RepositoryCaptureStrategy repositoryCaptureStrategy(ObjectProvider<DiagnosticsEngine> engine) {

        return new RepositoryCaptureStrategy(engine::getObject);
    }

    @Bean
    static RepositoryCapturePostProcessor repositoryCapturePostProcessor(
        ObjectProvider<RepositoryCaptureStrategy> strategy) {

        return new RepositoryCapturePostProcessor(strategy);
    }

    @Bean
    @ConditionalOnMissingBean
    RepositoryHotspotAnalyzer repositoryHotspotAnalyzer(DataDiagnosticsProperties properties,
                                                        DiagnosticsSettings settings) {

        long defaultThreshold = RepositoryHotspotAnalyzer.defaultThreshold(settings.mode());
        SeverityScale scale = properties.analyzer(RepositoryHotspotAnalyzer.ID).scale(defaultThreshold);
        return new RepositoryHotspotAnalyzer(scale, settings.unitShapeLimit());
    }
}
