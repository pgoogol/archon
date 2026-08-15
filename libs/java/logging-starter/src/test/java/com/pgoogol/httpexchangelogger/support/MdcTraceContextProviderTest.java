package com.pgoogol.httpexchangelogger.support;

import com.pgoogol.httpexchangelogger.support.TraceContextProvider.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MdcTraceContextProviderTest {

    private final MdcTraceContextProvider provider = new MdcTraceContextProvider();

    @AfterEach
    void cleanUp() {

        MDC.clear();
    }

    @Test
    void currentTraceContext_whenMdcContainsTraceAndSpan_returnsBoth() {

        // given
        MDC.put("traceId", "trace-123");
        MDC.put("spanId", "span-456");

        // when
        Optional<TraceContext> context = provider.currentTraceContext();

        // then
        assertThat(context).contains(new TraceContext("trace-123", "span-456"));
    }

    @Test
    void currentTraceContext_whenMdcEmpty_returnsEmpty() {

        // given no MDC entries

        // when
        Optional<TraceContext> context = provider.currentTraceContext();

        // then
        assertThat(context).isEmpty();
    }

    @Test
    void currentTraceContext_whenOnlyTraceIdPresent_returnsContextWithNullSpan() {

        // given
        MDC.put("traceId", "trace-123");

        // when
        Optional<TraceContext> context = provider.currentTraceContext();

        // then
        assertThat(context).isPresent();
        assertThat(context.get().traceId()).isEqualTo("trace-123");
        assertThat(context.get().spanId()).isNull();
    }

}
