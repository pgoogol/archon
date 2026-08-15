package com.pgoogol.httpexchangelogger.support;

import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * Reads {@code traceId} / {@code spanId} from the SLF4J MDC — the keys used by
 * Micrometer Tracing's MDC propagation (and Sleuth before it). Works with any
 * tracing setup that populates the MDC, without extra dependencies.
 */
public class MdcTraceContextProvider implements TraceContextProvider {

    public static final String TRACE_ID_KEY = "traceId";
    public static final String SPAN_ID_KEY = "spanId";

    @Override
    public Optional<TraceContext> currentTraceContext() {

        String traceId = MDC.get(TRACE_ID_KEY);
        if (!StringUtils.hasText(traceId)) {

            return Optional.empty();
        }
        return Optional.of(new TraceContext(traceId, MDC.get(SPAN_ID_KEY)));
    }

}
