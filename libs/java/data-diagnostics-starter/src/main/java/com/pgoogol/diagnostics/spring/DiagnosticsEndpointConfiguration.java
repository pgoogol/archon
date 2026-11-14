package com.pgoogol.diagnostics.spring;

import com.pgoogol.diagnostics.core.report.FindingChatExporter;
import com.pgoogol.diagnostics.core.report.FindingFingerprint;
import com.pgoogol.diagnostics.core.report.FindingHints;
import com.pgoogol.diagnostics.core.report.FindingJsonWriter;
import com.pgoogol.diagnostics.core.report.RecentFindings;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wnioski dla interpreterów: bufor ostatnich wniosków, endpoint actuatora i eksport do
 * czatu. Wszystko powstaje tylko wtedy, gdy endpoint jest dostępny, więc aplikacja, która
 * go nie wystawia, nie trzyma bufora w pamięci.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({Endpoint.class, ConditionalOnAvailableEndpoint.class})
@ConditionalOnAvailableEndpoint(DataDiagnosticsEndpoint.class)
class DiagnosticsEndpointConfiguration {

    @Bean
    @ConditionalOnMissingBean
    RecentFindings recentFindings(DataDiagnosticsProperties properties, FindingFingerprint fingerprint) {

        return new RecentFindings(properties.recentFindingsLimit(), fingerprint);
    }

    @Bean
    @ConditionalOnMissingBean
    FindingHints findingHints() {

        return new FindingHints();
    }

    @Bean
    @ConditionalOnMissingBean
    FindingChatExporter findingChatExporter(FindingHints hints) {

        return new FindingChatExporter(hints);
    }

    @Bean
    @ConditionalOnMissingBean
    DataDiagnosticsEndpoint dataDiagnosticsEndpoint(RecentFindings recent, FindingJsonWriter jsonWriter,
                                                    FindingChatExporter chatExporter) {

        return new DataDiagnosticsEndpoint(recent, jsonWriter, chatExporter);
    }
}
