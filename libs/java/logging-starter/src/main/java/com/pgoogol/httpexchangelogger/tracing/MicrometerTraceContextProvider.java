package com.pgoogol.httpexchangelogger.tracing;

import com.pgoogol.httpexchangelogger.support.TraceContextProvider;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Resolves the trace context from the Micrometer Tracing {@link Tracer}. The
 * tracer is looked up lazily (independent of auto-configuration order); when
 * no tracer bean or no current span exists, the given fallback provider is
 * consulted instead.
 */
public class MicrometerTraceContextProvider implements TraceContextProvider {

    private final Supplier<Tracer> tracerSupplier;
    private final TraceContextProvider fallback;

    private volatile Tracer resolvedTracer;
    private volatile boolean lookupDone;

    public MicrometerTraceContextProvider(Supplier<Tracer> tracerSupplier, TraceContextProvider fallback) {

        this.tracerSupplier = tracerSupplier;
        this.fallback = fallback;
    }

    @Override
    public Optional<TraceContext> currentTraceContext() {

        Tracer tracer = tracer();
        if (Objects.isNull(tracer)) {

            return fallback.currentTraceContext();
        }
        Span span = tracer.currentSpan();
        if (Objects.isNull(span)) {

            return fallback.currentTraceContext();
        }
        io.micrometer.tracing.TraceContext context = span.context();
        String traceId = context.traceId();
        if (!StringUtils.hasText(traceId)) {

            return fallback.currentTraceContext();
        }
        return Optional.of(new TraceContext(traceId, context.spanId()));
    }

    private Tracer tracer() {

        if (!lookupDone) {

            resolvedTracer = tracerSupplier.get();
            lookupDone = true;
        }
        return resolvedTracer;
    }

}
