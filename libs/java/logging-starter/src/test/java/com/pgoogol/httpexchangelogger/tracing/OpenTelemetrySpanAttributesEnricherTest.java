package com.pgoogol.httpexchangelogger.tracing;

import com.pgoogol.httpexchangelogger.model.HttpExchangeLogEvent;
import com.pgoogol.httpexchangelogger.model.HttpLogMode;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class OpenTelemetrySpanAttributesEnricherTest {

    @Mock
    Span span;

    @Mock
    SpanContext spanContext;

    private static HttpExchangeLogEvent.Builder eventBuilder() {

        return HttpExchangeLogEvent.builder()
                .requestId("req-1")
                .method("POST")
                .path("/api/orders")
                .status(201)
                .durationMs(42)
                .configuredMode(HttpLogMode.BASIC)
                .effectiveMode(HttpLogMode.FULL);
    }

    @Test
    void enrich_whenCurrentSpanValid_setsExchangeAttributes() {

        // given
        when(span.getSpanContext()).thenReturn(spanContext);
        when(spanContext.isValid()).thenReturn(true);
        OpenTelemetrySpanAttributesEnricher enricher = new OpenTelemetrySpanAttributesEnricher(() -> span);

        // when
        enricher.enrich(eventBuilder().build());

        // then
        verify(span).setAttribute("http.request.method", "POST");
        verify(span).setAttribute("http.response.status_code", 201L);
        verify(span).setAttribute("url.path", "/api/orders");
        verify(span).setAttribute("http_exchange.request_id", "req-1");
        verify(span).setAttribute("http_exchange.mode", "FULL");
        verify(span).setAttribute("http_exchange.duration_ms", 42L);
    }

    @Test
    void enrich_whenExceptionPresent_setsExceptionAttributes() {

        // given
        when(span.getSpanContext()).thenReturn(spanContext);
        when(spanContext.isValid()).thenReturn(true);
        OpenTelemetrySpanAttributesEnricher enricher = new OpenTelemetrySpanAttributesEnricher(() -> span);

        // when
        enricher.enrich(eventBuilder()
                .status(500)
                .exceptionClass("com.example.OrderException")
                .exceptionMessage("boom")
                .build());

        // then
        verify(span).setAttribute("http_exchange.exception.type", "com.example.OrderException");
        verify(span).setAttribute("http_exchange.exception.message", "boom");
    }

    @Test
    void enrich_whenCurrentSpanInvalid_setsNothing() {

        // given
        when(span.getSpanContext()).thenReturn(spanContext);
        when(spanContext.isValid()).thenReturn(false);
        OpenTelemetrySpanAttributesEnricher enricher = new OpenTelemetrySpanAttributesEnricher(() -> span);

        // when
        enricher.enrich(eventBuilder().build());

        // then
        verify(span, never()).setAttribute(anyString(), anyString());
        verify(span, never()).setAttribute(anyString(), anyLong());
    }

    @Test
    void enrich_whenUsingRealNoopCurrentSpan_doesNotThrow() {

        // given the default supplier resolves Span.current() which is a no-op span in tests
        OpenTelemetrySpanAttributesEnricher enricher = new OpenTelemetrySpanAttributesEnricher();

        // when
        Throwable thrown = catchThrowable(() -> enricher.enrich(eventBuilder().build()));

        // then
        assertThat(thrown).isNull();
    }

}
