package com.pgoogol.diagnostics.spring;

import com.pgoogol.diagnostics.core.CallSiteResolver;
import com.pgoogol.diagnostics.core.DataStore;
import com.pgoogol.diagnostics.core.DiagnosticsEngine;
import com.pgoogol.diagnostics.core.DiagnosticsMode;
import com.pgoogol.diagnostics.core.DiagnosticsSettings;
import com.pgoogol.diagnostics.core.SelectiveCallSiteResolver;
import com.pgoogol.diagnostics.core.StackWalkingCallSiteResolver;
import com.pgoogol.diagnostics.core.analysis.DiagnosticAnalyzer;
import com.pgoogol.diagnostics.core.analysis.NPlusOneAnalyzer;
import com.pgoogol.diagnostics.core.analysis.SeverityScale;
import com.pgoogol.diagnostics.core.analysis.SlowOperationAnalyzer;
import com.pgoogol.diagnostics.core.report.JsonLinesFindingReporter;
import com.pgoogol.diagnostics.core.report.LogFindingReporter;
import com.pgoogol.diagnostics.jdbc.CapturedDataSource;
import com.pgoogol.diagnostics.jdbc.DataSourceCapturePostProcessor;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class DataDiagnosticsAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(DataDiagnosticsAutoConfiguration.class));

    @Test
    @DisplayName("sama zależność włącza silnik w trybie prod z czterema analizami i wnioskami w logu")
    void autoConfiguration_whenNoProperties_startsInProdWithLogOutput() {

        // when & then
        runner.run(context -> assertAll(
            () -> assertThat(context).hasSingleBean(DiagnosticsEngine.class),
            () -> assertThat(context.getBean(DiagnosticsSettings.class).mode()).isEqualTo(DiagnosticsMode.PROD),
            () -> assertThat(context.getBeansOfType(DiagnosticAnalyzer.class)).hasSize(4),
            () -> assertThat(context).hasSingleBean(LogFindingReporter.class),
            () -> assertThat(context).doesNotHaveBean(JsonLinesFindingReporter.class),
            () -> assertThat(context).hasSingleBean(DataSourceCapturePostProcessor.class)));
    }

    @Test
    @DisplayName("w trybie dev dochodzi plik JSONL")
    void autoConfiguration_whenDevMode_addsJsonLinesOutput() {

        // when & then
        runner.withPropertyValues("diagnostics.mode=dev")
            .run(context -> assertThat(context).hasSingleBean(JsonLinesFindingReporter.class));
    }

    @Test
    @DisplayName("jawne ustawienie pliku JSONL wygrywa z trybem w obie strony")
    void autoConfiguration_whenJsonLinesSetExplicitly_followsSetting() {

        // when & then
        runner.withPropertyValues("diagnostics.mode=dev", "diagnostics.output.jsonl.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(JsonLinesFindingReporter.class));
        runner.withPropertyValues("diagnostics.output.jsonl.enabled=true")
            .run(context -> assertThat(context).hasSingleBean(JsonLinesFindingReporter.class));
    }

    @Test
    @DisplayName("diagnostics.enabled=false wyłącza całość, łącznie z owijaniem DataSource")
    void autoConfiguration_whenDisabled_createsNothing() {

        // when & then
        runner.withPropertyValues("diagnostics.enabled=false")
            .withBean(HikariDataSource.class, HikariDataSource::new)
            .run(context -> assertAll(
                () -> assertThat(context).doesNotHaveBean(DiagnosticsEngine.class),
                () -> assertThat(context).doesNotHaveBean(DataSourceCapturePostProcessor.class),
                () -> assertThat(context.getBean(HikariDataSource.class)).isNotInstanceOf(CapturedDataSource.class)));
    }

    @Test
    @DisplayName("analiza wyłączona w ustawieniach trafia do listy wyłączonych silnika")
    void autoConfiguration_whenAnalyzerDisabled_marksItInSettings() {

        // when & then
        runner.withPropertyValues("diagnostics.analyzers.n-plus-one.enabled=false")
            .run(context -> assertThat(context.getBean(DiagnosticsSettings.class).analyzerEnabled("n-plus-one"))
                .isFalse());
    }

    @Test
    @DisplayName("progi z ustawień nadpisują wartości trybu, także próg wolnej operacji dla magazynu")
    void autoConfiguration_whenThresholdsSet_analyzersUseThem() {

        // when & then
        runner.withPropertyValues(
                "diagnostics.analyzers.n-plus-one.threshold=3",
                "diagnostics.analyzers.slow-operation.threshold=250",
                "diagnostics.analyzers.slow-operation.store-thresholds.postgresql=40")
            .run(context -> {

                SlowOperationAnalyzer slow = context.getBean(SlowOperationAnalyzer.class);
                assertAll(
                    () -> assertThat(context.getBean(NPlusOneAnalyzer.class).threshold()).isEqualTo(3),
                    () -> assertThat(slow.thresholdFor(new DataStore("postgresql"))).isEqualTo(Duration.ofMillis(40)),
                    () -> assertThat(slow.thresholdFor(new DataStore("redis"))).isEqualTo(Duration.ofMillis(250)));
            });
    }

    @Test
    @DisplayName("własny bean analizy w aplikacji zastępuje wbudowany")
    void autoConfiguration_whenApplicationDefinesAnalyzer_backsOff() {

        // given
        NPlusOneAnalyzer own = new NPlusOneAnalyzer(SeverityScale.of(2), 10);

        // when & then
        runner.withBean(NPlusOneAnalyzer.class, () -> own)
            .run(context -> assertThat(context.getBean(NPlusOneAnalyzer.class)).isSameAs(own));
    }

    @Test
    @DisplayName("bean DataSource aplikacji zostaje owinięty i dalej wstrzykuje się po typie")
    void autoConfiguration_whenDataSourceBean_wrapsIt() {

        // when & then
        runner.withBean(HikariDataSource.class, HikariDataSource::new)
            .run(context -> assertThat(context.getBean(HikariDataSource.class)).isInstanceOf(CapturedDataSource.class));
    }

    @Test
    @DisplayName("miejsce wywołania: w dev przy każdej operacji, w prod tylko dla wolnych i powtarzanych")
    void autoConfiguration_whenModeGiven_choosesCallSitePolicy() {

        // when & then
        runner.withPropertyValues("diagnostics.mode=dev")
            .run(context -> assertThat(context.getBean(CallSiteResolver.class))
                .isInstanceOf(StackWalkingCallSiteResolver.class));
        runner.run(context -> assertThat(context.getBean(CallSiteResolver.class))
            .isInstanceOf(SelectiveCallSiteResolver.class));
    }

    @Test
    @DisplayName("filtr granicy HTTP rejestruje się tylko w aplikacji servletowej, przed Spring Security")
    void autoConfiguration_whenServletApplication_registersHttpBoundary() {

        // given
        WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataDiagnosticsAutoConfiguration.class));

        // when & then
        webRunner.run(context -> {

            FilterRegistrationBean<?> registration =
                context.getBean("dataDiagnosticsHttpBoundaryRegistration", FilterRegistrationBean.class);
            assertAll(
                () -> assertThat(registration.getOrder()).isEqualTo(HttpRequestBoundary.ORDER),
                () -> assertThat(context).hasSingleBean(MicrometerTraceIdProvider.class));
        });
        runner.run(context -> assertThat(context).doesNotHaveBean(HttpRequestBoundary.class));
    }
}
