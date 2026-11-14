package com.pgoogol.diagnostics.spring;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@code traceId} z bieżącego spanu Micrometer Tracing. Tracer pobieramy przy każdym
 * pytaniu, bo filtr powstaje, zanim kolejność autokonfiguracji gwarantuje jego bean.
 * Bez tracera albo bez bieżącego spanu pytamy dostawcę zapasowego, zwykle MDC.
 */
public class MicrometerTraceIdProvider implements TraceIdProvider {

    private final Supplier<Tracer> tracer;

    private final TraceIdProvider fallback;

    public MicrometerTraceIdProvider(Supplier<Tracer> tracer, TraceIdProvider fallback) {

        this.tracer = Objects.requireNonNull(tracer, "dostęp do tracera jest wymagany");
        this.fallback = Objects.requireNonNull(fallback, "zapasowy dostawca śladu jest wymagany");
    }

    @Override
    public Optional<String> currentTraceId() {

        Tracer current = tracer.get();
        if (Objects.isNull(current)) {

            return fallback.currentTraceId();
        }
        Span span = current.currentSpan();
        if (Objects.isNull(span)) {

            return fallback.currentTraceId();
        }
        String traceId = span.context().traceId();
        if (Objects.isNull(traceId) || traceId.isBlank()) {

            return fallback.currentTraceId();
        }
        return Optional.of(traceId);
    }
}
