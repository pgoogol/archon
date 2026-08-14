package com.pgoogol.httpexchangelogger.autoconfigure;

import com.pgoogol.httpexchangelogger.support.MdcTraceContextProvider;
import com.pgoogol.httpexchangelogger.support.TraceContextProvider;
import com.pgoogol.httpexchangelogger.tracing.MicrometerTraceContextProvider;
import com.pgoogol.httpexchangelogger.tracing.OpenTelemetrySpanAttributesEnricher;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.trace.Span;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class HttpExchangeLoggerTracingAutoConfigurationTest {

    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    HttpExchangeLoggerTracingAutoConfiguration.class,
                    HttpExchangeLoggerOpenTelemetryAutoConfiguration.class,
                    HttpExchangeLoggerAutoConfiguration.class));

    @Test
    void tracingAutoConfiguration_whenMicrometerTracingOnClasspath_registersMicrometerProvider() {

        // given / when
        webRunner.run(ctx -> {
            // then
            TraceContextProvider provider = ctx.getBean(TraceContextProvider.class);
            assertThat(provider).isInstanceOf(MicrometerTraceContextProvider.class);
        });
    }

    @Test
    void tracingAutoConfiguration_whenMicrometerTracingMissing_fallsBackToMdcProvider() {

        // given
        webRunner.withClassLoader(new FilteredClassLoader(Tracer.class, Span.class))
                // when
                .run(ctx -> {
                    // then
                    TraceContextProvider provider = ctx.getBean(TraceContextProvider.class);
                    assertThat(provider).isInstanceOf(MdcTraceContextProvider.class);
                });
    }

    @Test
    void openTelemetryAutoConfiguration_whenOtelApiOnClasspath_contributesSpanAttributesEnricher() {

        // given default properties / when
        webRunner.run(ctx ->
                // then
                assertThat(ctx).hasSingleBean(OpenTelemetrySpanAttributesEnricher.class));
    }

    @Test
    void openTelemetryAutoConfiguration_whenDisabledByProperty_registersNoSpanAttributesEnricher() {

        // given
        webRunner.withPropertyValues("http-exchange-logger.tracing.otel-span-attributes=false"
                // when
        ).run(ctx ->
                // then
                assertThat(ctx).doesNotHaveBean(OpenTelemetrySpanAttributesEnricher.class));
    }

    @Test
    void openTelemetryAutoConfiguration_whenOtelApiMissing_backsOff() {

        // given
        webRunner.withClassLoader(new FilteredClassLoader(Span.class))
                // when
                .run(ctx ->
                        // then
                        assertThat(ctx).doesNotHaveBean(OpenTelemetrySpanAttributesEnricher.class));
    }

}
