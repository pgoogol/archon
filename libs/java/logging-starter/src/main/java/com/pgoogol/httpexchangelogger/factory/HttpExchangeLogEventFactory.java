package com.pgoogol.httpexchangelogger.factory;

import com.pgoogol.httpexchangelogger.autoconfigure.HttpExchangeLoggerProperties;
import com.pgoogol.httpexchangelogger.model.HttpExchangeLogEvent;
import com.pgoogol.httpexchangelogger.model.HttpLogMode;
import com.pgoogol.httpexchangelogger.sanitizer.BodySanitizer;
import com.pgoogol.httpexchangelogger.sanitizer.HeaderSanitizer;
import com.pgoogol.httpexchangelogger.support.BodyExtractor;
import com.pgoogol.httpexchangelogger.support.BodyTruncator;
import com.pgoogol.httpexchangelogger.support.BoundedBodyCaptureHttpServletResponse;
import com.pgoogol.httpexchangelogger.support.ClientIpExtractor;
import com.pgoogol.httpexchangelogger.support.ContentTypeMatcher;
import com.pgoogol.httpexchangelogger.support.TraceContextProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HttpExchangeLogEventFactory {

    private static final int SERVER_ERROR_STATUS = 500;

    private final HttpExchangeLoggerProperties properties;
    private final HeaderSanitizer headerSanitizer;
    private final BodySanitizer bodySanitizer;
    private final ClientIpExtractor clientIpExtractor;
    private final ObjectMapper objectMapper;
    private final TraceContextProvider traceContextProvider;

    public HttpExchangeLogEventFactory(HttpExchangeLoggerProperties properties, HeaderSanitizer headerSanitizer, BodySanitizer bodySanitizer, ClientIpExtractor clientIpExtractor, ObjectMapper objectMapper) {

        this(properties, headerSanitizer, bodySanitizer, clientIpExtractor, objectMapper, null);
    }

    public HttpExchangeLogEventFactory(HttpExchangeLoggerProperties properties, HeaderSanitizer headerSanitizer, BodySanitizer bodySanitizer, ClientIpExtractor clientIpExtractor, ObjectMapper objectMapper, TraceContextProvider traceContextProvider) {

        this.properties = properties;
        this.headerSanitizer = headerSanitizer;
        this.bodySanitizer = bodySanitizer;
        this.clientIpExtractor = clientIpExtractor;
        this.objectMapper = objectMapper;
        this.traceContextProvider = traceContextProvider;
    }

    private static String notLogged(String contentType) {

        return "[not logged: unsupported content type %s]".formatted(contentType);
    }

    private static Throwable unwrap(Throwable throwable) {

        Throwable current = throwable;
        while (current instanceof ServletException servletException && Objects.nonNull(servletException.getCause()) && servletException.getCause() != current) {

            current = servletException.getCause();
        }
        return current;
    }

    public HttpExchangeLogEvent create(HttpServletRequest request, byte[] requestBody, BoundedBodyCaptureHttpServletResponse response, HttpLogMode configuredMode, HttpLogMode effectiveMode, String requestId, long durationMs, Throwable exception) {

        HttpExchangeLogEvent.Builder builder = HttpExchangeLogEvent.builder().requestId(requestId).method(request.getMethod()).path(request.getRequestURI()).status(resolveStatus(response, exception)).durationMs(durationMs).configuredMode(configuredMode).effectiveMode(effectiveMode);

        if (Objects.nonNull(traceContextProvider)) {

            traceContextProvider.currentTraceContext().ifPresent(context -> {
                builder.traceId(context.traceId());
                builder.spanId(context.spanId());
            });
        }

        HttpExchangeLoggerProperties.Include propertiesInclude = properties.getInclude();
        if (propertiesInclude.isQueryString()) {

            builder.queryString(request.getQueryString());
        }

        if (Objects.nonNull(exception)) {

            Throwable root = unwrap(exception);
            builder.exceptionClass(root.getClass().getName());
            builder.exceptionMessage(root.getMessage());
        }

        if (CollectionUtils.containsInstance(Set.of(HttpLogMode.BASIC, HttpLogMode.OFF), effectiveMode)) {

            return builder.build();
        }

        if (propertiesInclude.isClientIp() && Objects.nonNull(clientIpExtractor)) {

            builder.clientIp(clientIpExtractor.extract(request));
        }

        if (propertiesInclude.isHeaders()) {

            builder.requestHeaders(sanitizeHeaders(extractRequestHeaders(request)));
            builder.responseHeaders(sanitizeHeaders(extractResponseHeaders(response)));
        }

        int bodyCap = effectiveBodyCap(effectiveMode);
        applyRequestBody(request, requestBody, builder, bodyCap);
        applyResponseBody(response, builder, bodyCap);

        return builder.build();
    }

    private int effectiveBodyCap(HttpLogMode mode) {

        int max = properties.getMaxBodyLength();
        if (mode == HttpLogMode.LIMITED) {

            // maxBodyLength remains the absolute ceiling per documentation: "limit obowiązuje zawsze".
            int limited = properties.getLimitedMaxBodyLength();
            if (limited <= 0) {

                return 0;
            }
            return Math.min(limited, max);
        }
        return max;
    }

    private int resolveStatus(HttpServletResponse response, Throwable exception) {

        int status = response.getStatus();
        // When the handler threw, the container has not run its error handling yet,
        // so the wrapper still reports the default 200. Reflect a server error instead.
        if (Objects.nonNull(exception) && status < 400) {

            return SERVER_ERROR_STATUS;
        }
        return status;
    }

    private void applyRequestBody(HttpServletRequest request, byte[] requestBody, HttpExchangeLogEvent.Builder builder, int cap) {

        String contentType = request.getContentType();
        String body = BodyExtractor.toBodyString(requestBody, request.getCharacterEncoding());
        // Form bodies are not pre-buffered (would break getParameter on the original request).
        // Reconstruct them from the parameter map after the chain ran, so they are still logged.
        if (Objects.isNull(body) && Objects.nonNull(contentType) && ContentTypeMatcher.isFormUrlEncoded(contentType)) {

            body = reconstructFormBody(request);
        }
        // Only bounded textual request bodies are captured. When the request carried a body of an
        // unloggable type (binary, multipart) it is never read, so emit the same placeholder the
        // response path would instead of silently dropping it.
        if (Objects.isNull(body) && carriesUnloggableBody(request, contentType)) {

            builder.requestBody(notLogged(contentType));
            return;
        }
        BodyResult result = buildBody(body, contentType, cap, false);
        if (Objects.isNull(result)) {

            return;
        }
        builder.requestBody(result.value());
        builder.requestBodyTruncated(result.truncated());
    }

    private String reconstructFormBody(HttpServletRequest request) {

        String method = Objects.toString(request.getMethod(), "").toUpperCase(Locale.ROOT);
        if (!Set.of("POST", "PUT", "PATCH").contains(method)) {

            return null;
        }
        Map<String, String[]> parameters = request.getParameterMap();
        if (CollectionUtils.isEmpty(parameters)) {

            return null;
        }
        Set<String> queryKeys = extractQueryStringKeys(request.getQueryString());
        Charset charset = resolveCharset(request.getCharacterEncoding());
        String reconstructed = parameters.entrySet().stream()
                .filter(entry -> !queryKeys.contains(entry.getKey()))
                .flatMap(entry -> reconstructQuery(entry, charset))
                .collect(Collectors.joining("&"));
        if (reconstructed.isEmpty()) {

            return null;
        }
        return reconstructed;
    }

    private Stream<String> reconstructQuery(Map.Entry<String, String[]> entry, Charset charset) {

        return Arrays.stream(entry.getValue())
                .map(value -> encode(entry.getKey(), charset) + "=" + encode(Objects.toString(value, ""), charset));
    }

    private Set<String> extractQueryStringKeys(String queryString) {

        if (!StringUtils.hasText(queryString)) {

            return Collections.emptySet();
        }
        Set<String> keys = new HashSet<>();
        Arrays.stream(queryString.split("&"))
                .map(this::getKey)
                .map(this::decode)
                .filter(Objects::nonNull)
                .forEach(keys::add);
        return keys;
    }

    private String getKey(String pair) {

        int equals = pair.indexOf('=');
        if (equals < 0) {

            return pair;
        }
        return pair.substring(0, equals);
    }

    private String decode(String encoded) {

        try {

            return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
        } catch (Exception ex) {

            return encoded;
        }
    }

    private String encode(String value, Charset charset) {

        return URLEncoder.encode(value, charset);
    }

    private Charset resolveCharset(String encoding) {

        if (!StringUtils.hasText(encoding)) {

            return StandardCharsets.UTF_8;
        }
        try {

            return Charset.forName(encoding);
        } catch (Exception ex) {

            return StandardCharsets.UTF_8;
        }
    }

    private boolean carriesUnloggableBody(HttpServletRequest request, String contentType) {

        if (request.getContentLengthLong() <= 0 || Objects.isNull(contentType)) {

            return false;
        }
        return ContentTypeMatcher.isBinary(contentType) || !ContentTypeMatcher.isLoggable(contentType);
    }

    private void applyResponseBody(BoundedBodyCaptureHttpServletResponse response, HttpExchangeLogEvent.Builder builder, int cap) {

        String body = BodyExtractor.toBodyString(response.getCapturedBody(), response.getCharacterEncoding());
        BodyResult result = buildBody(body, response.getContentType(), cap, response.isCaptureOverflowed());
        if (Objects.isNull(result)) {

            return;
        }
        builder.responseBody(result.value());
        builder.responseBodyTruncated(result.truncated());
    }

    // captureIncomplete: the buffer held only a prefix of the payload, so the body must be
    // reported as truncated and never parsed as a complete JSON document.
    private BodyResult buildBody(String body, String contentType, int cap, boolean captureIncomplete) {

        if (!StringUtils.hasLength(body)) {

            return null;
        }
        if (cap <= 0) {

            // Body logging disabled for this mode.
            return null;
        }
        if (Objects.nonNull(contentType) && ContentTypeMatcher.isBinary(contentType)) {

            return new BodyResult(notLogged(contentType), false);
        }
        if (Objects.nonNull(contentType) && !ContentTypeMatcher.isLoggable(contentType)) {

            return new BodyResult(notLogged(contentType), false);
        }

        // Mask FIRST on the full body; truncation afterwards can never expose a secret
        // because sensitive values are already replaced.
        String sanitized = bodySanitizer.sanitize(body, contentType);

        BodyTruncator.TruncationResult truncated = BodyTruncator.truncate(sanitized, cap);
        if (truncated.truncated() || captureIncomplete) {

            return new BodyResult(truncated.value(), true);
        }

        return new BodyResult(asJsonOrString(truncated.value(), contentType), false);
    }

    private Object asJsonOrString(String value, String contentType) {

        if (Objects.nonNull(objectMapper) && ContentTypeMatcher.isJson(contentType)) {

            try {

                return objectMapper.readTree(value);
            } catch (Exception ex) {

                return value;
            }
        }
        return value;
    }

    private Map<String, List<String>> extractRequestHeaders(HttpServletRequest request) {

        Enumeration<String> names = request.getHeaderNames();
        if (Objects.isNull(names)) {

            return Collections.emptyMap();
        }
        Map<String, List<String>> headers = new LinkedHashMap<>();
        Collections.list(names).forEach(name -> headers.put(name, requestHeaderValues(request, name)));
        return headers;
    }

    private List<String> requestHeaderValues(HttpServletRequest request, String name) {

        Enumeration<String> values = request.getHeaders(name);
        if (Objects.isNull(values)) {

            return Collections.emptyList();
        }
        return Collections.list(values);
    }

    private Map<String, List<String>> extractResponseHeaders(HttpServletResponse response) {

        Collection<String> names = response.getHeaderNames();
        if (Objects.isNull(names)) {

            return Collections.emptyMap();
        }
        Map<String, List<String>> headers = new LinkedHashMap<>();
        names.forEach(name -> headers.put(name, responseHeaderValues(response, name)));
        return headers;
    }

    private List<String> responseHeaderValues(HttpServletResponse response, String name) {

        Collection<String> values = response.getHeaders(name);
        if (Objects.isNull(values)) {

            return Collections.emptyList();
        }
        return new ArrayList<>(values);
    }

    private Map<String, List<String>> sanitizeHeaders(Map<String, List<String>> headers) {

        if (Objects.isNull(headerSanitizer)) {

            return headers;
        }
        return headerSanitizer.sanitize(headers);
    }

    private record BodyResult(Object value, boolean truncated) {

    }

}
