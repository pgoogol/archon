package com.pgoogol.httpexchangelogger.tracing;

import com.pgoogol.httpexchangelogger.model.HttpExchangeLogEvent;

/**
 * Hook invoked once per logged exchange to enrich the current tracing span with exchange
 * metadata. Implementations must never add bodies or headers to the span.
 */
@FunctionalInterface
public interface HttpExchangeSpanEnricher {

    void enrich(HttpExchangeLogEvent event);

}
