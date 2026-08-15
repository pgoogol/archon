package com.pgoogol.httpexchangelogger.tracing;

import com.pgoogol.httpexchangelogger.support.MdcTraceContextProvider;
import com.pgoogol.httpexchangelogger.support.TraceContextProvider.TraceContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MicrometerTraceContextProviderTest {

    @Mock
    Tracer tracer;

    @Mock
    Span span;

    @Mock
    io.micrometer.tracing.TraceContext micrometerContext;

    @AfterEach
    void cleanUp() {

        MDC.clear();
    }

    @Test
    void currentTraceContext_whenTracerHasCurrentSpan_returnsItsContext() {

        // given
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(micrometerContext);
        when(micrometerContext.traceId()).thenReturn("trace-abc");
        when(micrometerContext.spanId()).thenReturn("span-def");
        MicrometerTraceContextProvider provider =
                new MicrometerTraceContextProvider(() -> tracer, new MdcTraceContextProvider());

        // when
        Optional<TraceContext> context = provider.currentTraceContext();

        // then
        assertThat(context).contains(new TraceContext("trace-abc", "span-def"));
    }

    @Test
    void currentTraceContext_whenNoCurrentSpan_fallsBackToMdc() {

        // given
        when(tracer.currentSpan()).thenReturn(null);
        MDC.put("traceId", "mdc-trace");
        MicrometerTraceContextProvider provider =
                new MicrometerTraceContextProvider(() -> tracer, new MdcTraceContextProvider());

        // when
        Optional<TraceContext> context = provider.currentTraceContext();

        // then
        assertThat(context).isPresent();
        assertThat(context.get().traceId()).isEqualTo("mdc-trace");
    }

    @Test
    void currentTraceContext_whenNoTracerBeanAvailable_fallsBackToMdc() {

        // given
        MDC.put("traceId", "mdc-trace");
        MicrometerTraceContextProvider provider =
                new MicrometerTraceContextProvider(() -> null, new MdcTraceContextProvider());

        // when
        Optional<TraceContext> context = provider.currentTraceContext();

        // then
        assertThat(context).isPresent();
        assertThat(context.get().traceId()).isEqualTo("mdc-trace");
    }

    @Test
    void currentTraceContext_whenNoTracerAndEmptyMdc_returnsEmpty() {

        // given
        MicrometerTraceContextProvider provider =
                new MicrometerTraceContextProvider(() -> null, new MdcTraceContextProvider());

        // when
        Optional<TraceContext> context = provider.currentTraceContext();

        // then
        assertThat(context).isEmpty();
    }

}
