package com.pgoogol.diagnostics.spring;

import com.pgoogol.diagnostics.core.DiagnosticsSettings;
import com.pgoogol.diagnostics.core.analysis.MissingBatchAnalyzer;
import com.pgoogol.diagnostics.core.analysis.NPlusOneAnalyzer;
import com.pgoogol.diagnostics.core.analysis.OperationCountAnalyzer;
import com.pgoogol.diagnostics.core.analysis.SeverityScale;
import com.pgoogol.diagnostics.core.analysis.SlowOperationAnalyzer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Wbudowane analizy jako osobne beany. Progi przychodzą z
 * {@code diagnostics.analyzers.<id>.*}, a bez nich z wartości domyślnych trybu.
 * Wyłączoną analizę silnik pomija, ale bean zostaje, żeby dało się sprawdzić jej próg.
 */
@Configuration(proxyBeanMethods = false)
class DiagnosticsAnalyzersConfiguration {

    @Bean
    @ConditionalOnMissingBean
    NPlusOneAnalyzer nPlusOneAnalyzer(DataDiagnosticsProperties properties, DiagnosticsSettings settings) {

        long defaultThreshold = NPlusOneAnalyzer.defaultThreshold(settings.mode());
        SeverityScale scale = properties.analyzer(NPlusOneAnalyzer.ID).scale(defaultThreshold);
        return new NPlusOneAnalyzer(scale, settings.unitShapeLimit());
    }

    @Bean
    @ConditionalOnMissingBean
    MissingBatchAnalyzer missingBatchAnalyzer(DataDiagnosticsProperties properties, DiagnosticsSettings settings) {

        SeverityScale scale = properties.analyzer(MissingBatchAnalyzer.ID).scale(MissingBatchAnalyzer.DEFAULT_THRESHOLD);
        return new MissingBatchAnalyzer(scale, settings.unitShapeLimit());
    }

    @Bean
    @ConditionalOnMissingBean
    OperationCountAnalyzer operationCountAnalyzer(DataDiagnosticsProperties properties, DiagnosticsSettings settings) {

        long defaultThreshold = OperationCountAnalyzer.defaultThreshold(settings.mode());
        SeverityScale scale = properties.analyzer(OperationCountAnalyzer.ID).scale(defaultThreshold);
        return new OperationCountAnalyzer(scale, settings.unitShapeLimit());
    }

    /** Progi wolnej operacji są w milisekundach: domyślny w {@code threshold}, według magazynu w {@code store-thresholds}. */
    @Bean
    @ConditionalOnMissingBean
    SlowOperationAnalyzer slowOperationAnalyzer(DataDiagnosticsProperties properties, DiagnosticsSettings settings) {

        DataDiagnosticsProperties.Analyzer analyzer = properties.analyzer(SlowOperationAnalyzer.ID);
        Duration modeDefault = SlowOperationAnalyzer.defaultThreshold(settings.mode());
        Duration threshold = Optional.ofNullable(analyzer.threshold())
            .map(Duration::ofMillis)
            .orElse(modeDefault);
        Map<String, Duration> storeThresholds = analyzer.storeThresholds().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> Duration.ofMillis(entry.getValue())));
        return new SlowOperationAnalyzer(threshold, storeThresholds, analyzer.multiplier(), settings.unitShapeLimit());
    }
}
