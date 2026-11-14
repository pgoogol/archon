package com.pgoogol.diagnostics.spring;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TraceIdProviderTest {

    private final MdcTraceIdProvider mdc = new MdcTraceIdProvider();

    @AfterEach
    void clearMdc() {

        MDC.clear();
    }

    @Test
    @DisplayName("MDC z traceId daje ślad, puste MDC nie daje nic")
    void currentTraceId_whenMdcHasTraceId_returnsIt() {

        // given
        MDC.put(MdcTraceIdProvider.TRACE_ID_KEY, "6e1b9f");

        // when
        Optional<String> present = mdc.currentTraceId();
        MDC.clear();
        Optional<String> absent = mdc.currentTraceId();

        // then
        assertThat(present).contains("6e1b9f");
        assertThat(absent).isEmpty();
    }

    @Test
    @DisplayName("bieżący span Micrometer Tracing ma pierwszeństwo przed MDC")
    void currentTraceId_whenSpanActive_returnsSpanTraceId() {

        // given
        MDC.put(MdcTraceIdProvider.TRACE_ID_KEY, "from-mdc");
        Tracer tracer = tracerWithTraceId("from-span");
        MicrometerTraceIdProvider provider = new MicrometerTraceIdProvider(() -> tracer, mdc);

        // when
        Optional<String> traceId = provider.currentTraceId();

        // then
        assertThat(traceId).contains("from-span");
    }

    @Test
    @DisplayName("bez tracera albo bez spanu ślad przychodzi z MDC")
    void currentTraceId_whenNoTracerOrSpan_fallsBackToMdc() {

        // given
        MDC.put(MdcTraceIdProvider.TRACE_ID_KEY, "from-mdc");
        Tracer withoutSpan = mock(Tracer.class);
        MicrometerTraceIdProvider noTracer = new MicrometerTraceIdProvider(() -> null, mdc);
        MicrometerTraceIdProvider noSpan = new MicrometerTraceIdProvider(() -> withoutSpan, mdc);

        // when
        Optional<String> fromNoTracer = noTracer.currentTraceId();
        Optional<String> fromNoSpan = noSpan.currentTraceId();

        // then
        assertThat(fromNoTracer).contains("from-mdc");
        assertThat(fromNoSpan).contains("from-mdc");
    }

    private static Tracer tracerWithTraceId(String traceId) {

        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext context = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn(traceId);
        return tracer;
    }
}
