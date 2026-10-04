package com.pgoogol.diagnostics.spring;

import org.slf4j.MDC;

import java.util.Optional;

/**
 * {@code traceId} z MDC, pod kluczem, który wpisuje tam Micrometer Tracing i którego używa
 * wzorzec logu Boota. Działa też bez Micrometer Tracing, gdy aplikacja sama ustawia MDC.
 */
public class MdcTraceIdProvider implements TraceIdProvider {

    public static final String TRACE_ID_KEY = "traceId";

    @Override
    public Optional<String> currentTraceId() {

        String traceId = MDC.get(TRACE_ID_KEY);
        return Optional.ofNullable(traceId)
            .filter(value -> !value.isBlank());
    }
}
