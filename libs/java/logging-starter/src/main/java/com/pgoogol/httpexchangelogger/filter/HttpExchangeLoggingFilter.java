package com.pgoogol.httpexchangelogger.filter;

import com.pgoogol.httpexchangelogger.autoconfigure.HttpExchangeLoggerProperties;
import com.pgoogol.httpexchangelogger.factory.HttpExchangeLogEventFactory;
import com.pgoogol.httpexchangelogger.model.HttpExchangeLogEvent;
import com.pgoogol.httpexchangelogger.model.HttpLogMode;
import com.pgoogol.httpexchangelogger.resolver.EndpointLoggingModeResolver;
import com.pgoogol.httpexchangelogger.resolver.ExchangeSampler;
import com.pgoogol.httpexchangelogger.runtime.RuntimeModeOverrideManager;
import com.pgoogol.httpexchangelogger.serialization.HttpExchangeLogEventJsonWriter;
import com.pgoogol.httpexchangelogger.support.BoundedBodyCaptureHttpServletResponse;
import com.pgoogol.httpexchangelogger.support.CachedBodyHttpServletRequest;
import com.pgoogol.httpexchangelogger.support.ContentTypeMatcher;
import com.pgoogol.httpexchangelogger.support.RequestIdProvider;
import com.pgoogol.httpexchangelogger.tracing.HttpExchangeSpanEnricher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

public class HttpExchangeLoggingFilter extends OncePerRequestFilter {

    /**
     * Every logged exchange goes to this SLF4J logger as a single JSON line. Routing (console,
     * file, async, external systems) is the application's logging configuration concern —
     * attach Logback/Log4j appenders to this logger instead of library-level switches.
     */
    public static final String LOGGER_NAME = "http.exchange.logger";

    private static final Logger LOG = LoggerFactory.getLogger(HttpExchangeLoggingFilter.class);
    private static final Logger EXCHANGE_LOG = LoggerFactory.getLogger(LOGGER_NAME);

    private final HttpExchangeLoggerProperties properties;
    private final EndpointLoggingModeResolver modeResolver;
    private final HttpExchangeLogEventFactory eventFactory;
    private final HttpExchangeLogEventJsonWriter jsonWriter;
    private final RequestIdProvider requestIdProvider;
    private final ExchangeSampler sampler;
    private final RuntimeModeOverrideManager overrideManager;
    private final HttpExchangeSpanEnricher spanEnricher;

    public HttpExchangeLoggingFilter(HttpExchangeLoggerProperties properties,
                                     EndpointLoggingModeResolver modeResolver,
                                     HttpExchangeLogEventFactory eventFactory,
                                     HttpExchangeLogEventJsonWriter jsonWriter,
                                     RequestIdProvider requestIdProvider,
                                     ExchangeSampler sampler,
                                     RuntimeModeOverrideManager overrideManager,
                                     @Nullable HttpExchangeSpanEnricher spanEnricher) {

        this.properties = properties;
        this.modeResolver = modeResolver;
        this.eventFactory = eventFactory;
        this.jsonWriter = jsonWriter;
        this.requestIdProvider = requestIdProvider;
        this.sampler = sampler;
        this.overrideManager = overrideManager;
        this.spanEnricher = spanEnricher;
    }

    private static int resolveCacheLimit(int maxBodyLength) {

        if (maxBodyLength <= 0) {

            return 1;
        }
        long expanded = (long) maxBodyLength * 4L + 1L;
        if (expanded > Integer.MAX_VALUE) {

            return Integer.MAX_VALUE;
        }
        return (int) expanded;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {

        return !properties.isEnabled();
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        HttpLogMode configuredMode = modeResolver.resolve(request);
        Optional<HttpLogMode> override = overrideManager.resolve(request.getRequestURI());
        HttpLogMode effectiveMode = override.orElse(configuredMode);

        // Correlation id is established for every request the filter sees (including OFF),
        // so downstream consumers always get an X-Request-Id.
        String requestId = requestIdProvider.getOrCreateRequestId(request, response);

        if (effectiveMode == HttpLogMode.OFF) {

            filterChain.doFilter(request, response);
            return;
        }

        // An explicit runtime override expresses the intent to see these exchanges,
        // so it bypasses sampling.
        if (override.isEmpty() && !sampler.shouldSample(request.getRequestURI())) {

            filterChain.doFilter(request, response);
            return;
        }

        int cacheLimit = resolveCacheLimit(properties.getMaxBodyLength());
        // The response streams through to the container; only the first cacheLimit bytes are
        // retained for logging, so memory stays bounded no matter how large the response is.
        BoundedBodyCaptureHttpServletResponse wrappedResponse =
                new BoundedBodyCaptureHttpServletResponse(response, cacheLimit);

        // Eagerly buffer bounded textual bodies so they are logged even when the handler never
        // reads them. Everything else (form, multipart, binary, large or unknown-length bodies)
        // passes through unwrapped and its body is not captured, to avoid buffering uploads.
        CachedBodyHttpServletRequest cachedRequest = null;
        if (shouldBufferEagerly(request, cacheLimit)) {

            cachedRequest = new CachedBodyHttpServletRequest(request);
        }
        HttpServletRequest requestToUse = Objects.requireNonNullElse(cachedRequest, request);

        long startedAt = System.nanoTime();
        Throwable exception = null;

        try {

            filterChain.doFilter(requestToUse, wrappedResponse);
        } catch (ServletException | IOException | RuntimeException ex) {

            exception = ex;
            throw ex;
        } finally {

            // Characters the handler wrote via getWriter() but never flushed still sit in the
            // wrapper's writer; push them to the container before building the event.
            wrappedResponse.flushCapturedWriter();
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            try {

                byte[] requestBody = null;
                if (Objects.nonNull(cachedRequest)) {
                    requestBody = cachedRequest.getCachedBody();
                }
                HttpExchangeLogEvent event = eventFactory.create(
                        requestToUse,
                        requestBody,
                        wrappedResponse,
                        configuredMode,
                        effectiveMode,
                        requestId,
                        durationMs,
                        exception
                );
                emit(event);
            } catch (RuntimeException loggingException) {

                LOG.warn("Failed to build or emit HTTP exchange log event", loggingException);
            }
        }
    }

    private void emit(HttpExchangeLogEvent event) {

        if (Objects.nonNull(spanEnricher)) {

            spanEnricher.enrich(event);
        }
        if (EXCHANGE_LOG.isInfoEnabled()) {

            EXCHANGE_LOG.info(jsonWriter.write(event));
        }
    }

    private boolean shouldBufferEagerly(HttpServletRequest request, int cacheLimit) {

        if (properties.getMaxBodyLength() <= 0) {

            return false;
        }
        String contentType = request.getContentType();
        boolean textual = ContentTypeMatcher.isJson(contentType)
                || ContentTypeMatcher.isXml(contentType)
                || ContentTypeMatcher.isText(contentType);
        if (!textual) {

            return false;
        }
        long length = request.getContentLengthLong();
        return length > 0 && length <= cacheLimit;
    }

}
