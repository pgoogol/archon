package com.pgoogol.diagnostics.spring;

import com.pgoogol.diagnostics.core.CallSiteResolver;
import com.pgoogol.diagnostics.core.DiagnosticsEngine;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;
import com.pgoogol.diagnostics.core.SelectiveCallSiteResolver;
import com.pgoogol.diagnostics.core.StackWalkingCallSiteResolver;
import com.pgoogol.diagnostics.core.analysis.DiagnosticAnalyzer;
import com.pgoogol.diagnostics.core.analysis.MissingBatchAnalyzer;
import com.pgoogol.diagnostics.core.analysis.NPlusOneAnalyzer;
import com.pgoogol.diagnostics.core.analysis.SlowOperationAnalyzer;
import com.pgoogol.diagnostics.core.report.FindingFingerprint;
import com.pgoogol.diagnostics.core.report.FindingJsonWriter;
import com.pgoogol.diagnostics.core.report.FindingReporter;
import com.pgoogol.diagnostics.core.report.JsonLinesFindingReporter;
import com.pgoogol.diagnostics.core.report.LogFindingReporter;
import com.pgoogol.diagnostics.core.store.DataStoreSupport;
import com.pgoogol.diagnostics.core.store.PostgreSqlSupport;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Import;

import java.time.Clock;
import java.util.List;
import java.util.stream.LongStream;

/**
 * Autokonfiguracja diagnostyki dostępu do danych. Dodanie zależności włącza całość:
 * owinięte {@code DataSource}, granicę HTTP, analizy i wyjścia wniosków;
 * {@code diagnostics.enabled=false} wyłącza wszystko, także owijanie.
 *
 * <p>Każda analiza, reporter i strategia to osobny bean z {@code @ConditionalOnMissingBean},
 * więc serwis podmienia wybrany element własnym beanem tego typu, a własną analizę dokłada
 * zwykłym beanem {@link DiagnosticAnalyzer}.</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(DataDiagnosticsProperties.class)
@ConditionalOnProperty(prefix = "diagnostics", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({DiagnosticsAnalyzersConfiguration.class, DiagnosticsJdbcConfiguration.class, DiagnosticsWebConfiguration.class})
public class DataDiagnosticsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DiagnosticsSettings diagnosticsSettings(DataDiagnosticsProperties properties) {

        return properties.toSettings();
    }

    @Bean
    @ConditionalOnMissingBean
    public DataStoreSupport dataStoreSupport() {

        return new PostgreSqlSupport();
    }

    @Bean
    @ConditionalOnMissingBean
    public FindingFingerprint findingFingerprint() {

        return new FindingFingerprint();
    }

    @Bean
    @ConditionalOnMissingBean
    public FindingJsonWriter findingJsonWriter(FindingFingerprint fingerprint) {

        return new FindingJsonWriter(fingerprint);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "diagnostics.output.log", name = "enabled", havingValue = "true",
        matchIfMissing = true)
    public LogFindingReporter logFindingReporter(DiagnosticsSettings settings, FindingFingerprint fingerprint) {

        return new LogFindingReporter(settings.mode(), fingerprint);
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(OnJsonLinesOutputCondition.class)
    public JsonLinesFindingReporter jsonLinesFindingReporter(DataDiagnosticsProperties properties,
                                                             FindingJsonWriter writer) {

        DataDiagnosticsProperties.Jsonl jsonl = properties.output().jsonl();
        return new JsonLinesFindingReporter(jsonl.path(), jsonl.maxSize().toBytes(), writer);
    }

    @Bean
    @ConditionalOnMissingBean
    public DiagnosticsEngine diagnosticsEngine(DiagnosticsSettings settings,
                                               ObjectProvider<DiagnosticAnalyzer> analyzers,
                                               ObjectProvider<FindingReporter> reporters,
                                               ObjectProvider<Clock> clock) {

        List<DiagnosticAnalyzer> analyzerList = analyzers.orderedStream().toList();
        List<FindingReporter> reporterList = reporters.orderedStream().toList();
        Clock chosenClock = clock.getIfAvailable(Clock::systemUTC);
        return new DiagnosticsEngine(settings, analyzerList, reporterList, chosenClock);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutsideUnitReporting outsideUnitReporting(DiagnosticsEngine engine) {

        return new OutsideUnitReporting(engine);
    }

    /**
     * Miejsce wywołania: w dev przy każdej operacji, w prod tylko dla operacji wolnych
     * i N-tego powtórzenia kształtu. N to najniższy próg analiz powtórzeń, żeby wniosek
     * N+1 i wniosek o braku batcha dostały miejsce wywołania.
     */
    @Bean
    @ConditionalOnMissingBean
    public CallSiteResolver callSiteResolver(DiagnosticsSettings settings, DataDiagnosticsProperties properties,
                                             BeanFactory beanFactory, DiagnosticsEngine engine,
                                             ObjectProvider<SlowOperationAnalyzer> slowOperation,
                                             ObjectProvider<NPlusOneAnalyzer> nPlusOne,
                                             ObjectProvider<MissingBatchAnalyzer> missingBatch) {

        List<String> packages = applicationPackages(properties, beanFactory);
        StackWalkingCallSiteResolver walking = new StackWalkingCallSiteResolver(packages);
        return switch (settings.callSiteCapture()) {

            case EVERY_OPERATION -> walking;
            case SLOW_OR_REPEATED -> {

                SlowOperationAnalyzer slow = slowOperation.getIfAvailable(() -> SlowOperationAnalyzer.defaults(settings));
                long repetition = repetitionThreshold(settings, nPlusOne, missingBatch);
                yield new SelectiveCallSiteResolver(walking, slow::thresholdFor, repetition,
                    settings.unitShapeLimit(), engine::current);
            }
        };
    }

    private static List<String> applicationPackages(DataDiagnosticsProperties properties, BeanFactory beanFactory) {

        if (!properties.applicationPackages().isEmpty()) {

            return properties.applicationPackages();
        }
        if (!AutoConfigurationPackages.has(beanFactory)) {

            return List.of();
        }
        return AutoConfigurationPackages.get(beanFactory);
    }

    private static long repetitionThreshold(DiagnosticsSettings settings, ObjectProvider<NPlusOneAnalyzer> nPlusOne,
                                            ObjectProvider<MissingBatchAnalyzer> missingBatch) {

        long nPlusOneThreshold = nPlusOne.getIfAvailable(() -> NPlusOneAnalyzer.defaults(settings)).threshold();
        long missingBatchThreshold = missingBatch.getIfAvailable(() -> MissingBatchAnalyzer.defaults(settings))
            .threshold();
        return LongStream.of(nPlusOneThreshold, missingBatchThreshold).min().orElse(nPlusOneThreshold);
    }
}
