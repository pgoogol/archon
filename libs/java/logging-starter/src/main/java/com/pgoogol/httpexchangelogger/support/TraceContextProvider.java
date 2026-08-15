package com.pgoogol.httpexchangelogger.support;

import java.util.Optional;

/**
 * Supplies the current distributed tracing context, if any, so exchange events
 * can carry {@code traceId} / {@code spanId}.
 */
public interface TraceContextProvider {

    Optional<TraceContext> currentTraceContext();

    record TraceContext(String traceId, String spanId) {

    }

}
