package com.pgoogol.diagnostics.spring;

import com.pgoogol.diagnostics.core.DiagnosticsEngine;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;
import com.pgoogol.diagnostics.core.analysis.LongTransactionAnalyzer;
import com.pgoogol.diagnostics.core.analysis.ReadOnlyCandidateAnalyzer;
import com.pgoogol.diagnostics.core.analysis.SeverityScale;
import com.pgoogol.diagnostics.core.analysis.TransactionOperationCountAnalyzer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.TransactionExecutionListener;

/**
 * Transakcje: strategia przechwytywania i trzy analizy. Próg długiej transakcji jest
 * w milisekundach ({@code diagnostics.analyzers.long-transaction.threshold}).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(TransactionExecutionListener.class)
class DiagnosticsTransactionConfiguration {

    @Bean
    @ConditionalOnMissingBean
    TransactionCaptureStrategy transactionCaptureStrategy(ObjectProvider<DiagnosticsEngine> engine) {

        return new TransactionCaptureStrategy(engine::getObject);
    }

    @Bean
    @ConditionalOnMissingBean
    LongTransactionAnalyzer longTransactionAnalyzer(DataDiagnosticsProperties properties,
                                                    DiagnosticsSettings settings) {

        long defaultThreshold = LongTransactionAnalyzer.defaultThreshold(settings.mode());
        SeverityScale scale = properties.analyzer(LongTransactionAnalyzer.ID).scale(defaultThreshold);
        return new LongTransactionAnalyzer(scale, settings.unitShapeLimit());
    }

    @Bean
    @ConditionalOnMissingBean
    TransactionOperationCountAnalyzer transactionOperationCountAnalyzer(DataDiagnosticsProperties properties,
                                                                        DiagnosticsSettings settings) {

        SeverityScale scale = properties.analyzer(TransactionOperationCountAnalyzer.ID)
            .scale(TransactionOperationCountAnalyzer.DEFAULT_THRESHOLD);
        return new TransactionOperationCountAnalyzer(scale, settings.unitShapeLimit());
    }

    @Bean
    @ConditionalOnMissingBean
    ReadOnlyCandidateAnalyzer readOnlyCandidateAnalyzer(DiagnosticsSettings settings) {

        return ReadOnlyCandidateAnalyzer.defaults(settings);
    }
}
